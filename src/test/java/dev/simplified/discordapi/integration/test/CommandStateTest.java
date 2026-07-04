package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.InMemoryCommandStateResolver;
import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.gateway.SlashOption;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.ConfigGetCommand;
import dev.simplified.discordapi.integration.command.PingCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime command enable/disable vertical: a developer toggles a loaded command off at runtime through the
 * {@code CommandStateResolver}, and the dispatch pathway ({@code SlashCommandListener} ->
 * {@code DiscordCommand.apply} -> resolver) rejects it with the framework's "Disabled Command" embed - all
 * offline. The default resolver is the in-memory one, fetched live from the command handler and toggled by
 * {@code CommandKey}, exactly as a downstream bot would.
 *
 * <p>
 * The default actor is not the application owner, so {@code isDeveloper} is false for it; the developer-bypass
 * branch is covered by pointing the interaction actor at the harness owner ({@code developerUserId}), so
 * {@code isDeveloper} resolves true and a disabled command still runs.
 */
class CommandStateTest {

    private static final Duration BOOT = Duration.ofSeconds(30);

    @Test
    void disabled_command_is_rejected_for_non_developer() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(BOOT)) {
            resolver(harness).disable(command(harness, PingCommand.class));

            harness.sendSlashCommand("ping");

            RecordedRequest reply = harness.awaitRequest(request -> request.bodyContains("This command is currently disabled"));
            assertFalse(reply.bodyContains("pong"), "disabled command must not run process(); body=" + reply.body());
        }
    }

    @Test
    void re_enabled_command_runs_again() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(BOOT)) {
            InMemoryCommandStateResolver resolver = resolver(harness);
            DiscordCommand<?> ping = command(harness, PingCommand.class);

            resolver.disable(ping);
            resolver.enable(ping);

            harness.sendSlashCommand("ping");

            RecordedRequest reply = harness.awaitRequest(request -> request.bodyContains("pong"));
            assertTrue(reply.bodyContains("pong"), "re-enabled command should reply; body=" + reply.body());
        }
    }

    @Test
    void developer_bypasses_a_disabled_command() {
        // Point the interaction actor at the application owner, so isDeveloper() is true and the disabled gate
        // is bypassed even though the command is disabled for everyone else.
        long developerId = 555555555555555555L;
        HarnessConfig asDeveloper = HarnessConfig.builder()
            .withUserId(developerId)
            .withDeveloperUserId(developerId)
            .build();

        try (IntegrationHarness harness = new IntegrationHarness(asDeveloper).boot(BOOT)) {
            resolver(harness).disable(command(harness, PingCommand.class));

            harness.sendSlashCommand("ping");

            RecordedRequest reply = harness.awaitRequest(request -> request.bodyContains("pong"));
            assertTrue(reply.bodyContains("pong"), "developer should bypass the disabled gate; body=" + reply.body());
        }
    }

    @Test
    void disabling_one_subcommand_leaves_its_sibling_enabled() {
        // config get and config user add share ONE Discord command id (the 'config' parent) but resolve to
        // distinct CommandKeys, so disabling one must never disable the other.
        try (IntegrationHarness harness = new IntegrationHarness().boot(BOOT)) {
            resolver(harness).disable(command(harness, ConfigGetCommand.class));

            harness.sendSubCommand("config", "user", "add", SlashOption.text("name", "alice"));
            RecordedRequest sibling = harness.awaitRequest(request -> request.bodyContains("config user add name=alice"));
            assertTrue(sibling.bodyContains("config user add name=alice"), "enabled sibling subcommand should run; body=" + sibling.body());

            harness.sendSubCommand("config", "get", SlashOption.text("key", "foo"));
            RecordedRequest disabled = harness.awaitRequest(request -> request.bodyContains("This command is currently disabled"));
            assertFalse(disabled.bodyContains("config get key="), "disabled subcommand must not run process(); body=" + disabled.body());
        }
    }

    private static InMemoryCommandStateResolver resolver(IntegrationHarness harness) {
        return (InMemoryCommandStateResolver) harness.bot().getCommandHandler().getStateResolver();
    }

    @SuppressWarnings("rawtypes")
    private static DiscordCommand<?> command(IntegrationHarness harness, Class<? extends DiscordCommand> type) {
        return harness.bot().getCommandHandler().getLoadedCommands().stream()
            .filter(type::isInstance)
            .findFirst()
            .orElseThrow(() -> new AssertionError("command not loaded: " + type.getSimpleName()));
    }

}
