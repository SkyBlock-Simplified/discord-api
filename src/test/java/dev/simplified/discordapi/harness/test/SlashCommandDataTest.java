package dev.simplified.discordapi.harness.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.gateway.SlashOption;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slash command data vertical: drives full chat-input {@code data} - top-level options, bare subcommands,
 * and grouped subcommands - through the real pathway ({@code SlashCommandListener} ->
 * {@code DiscordCommand.apply} -> {@code process}), asserting both that a fully resolved {@code @Structure}
 * registers as the correct {@code parent -> group -> subcommand} tree and that each interaction routes to
 * its leaf command with its option resolved, entirely offline.
 */
class SlashCommandDataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void top_level_option_resolves_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand("echo", SlashOption.text("text", "hello harness"));

            RecordedRequest reply = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-echo")
                    && request.path().endsWith("/messages/@original")
            );

            assertTrue(reply.bodyContains("echo hello harness"), "top-level option should resolve; body=" + reply.body());
        }
    }

    @Test
    void bare_subcommand_routes_and_resolves_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSubCommand("config", "get", SlashOption.text("key", "color"));

            RecordedRequest reply = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-config-get")
                    && request.path().endsWith("/messages/@original")
            );

            assertTrue(reply.bodyContains("config get key=color"), "bare subcommand should route and resolve; body=" + reply.body());
        }
    }

    @Test
    void grouped_subcommand_routes_and_resolves_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSubCommand("config", "user", "add", SlashOption.text("name", "alice"));

            RecordedRequest reply = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-config-user-add")
                    && request.path().endsWith("/messages/@original")
            );

            assertTrue(reply.bodyContains("config user add name=alice"), "grouped subcommand should route and resolve; body=" + reply.body());
        }
    }

    @Test
    void fully_resolved_structure_registers_full_command_tree() throws Exception {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.awaitCommandRegistered("config", Duration.ofSeconds(10));

            RecordedRequest overwrite = harness.awaitRequest(
                request -> request.method().equals("PUT")
                    && request.path().endsWith("/commands")
                    && !request.path().contains("/guilds/") // global overwrite, not a per-guild one
            );

            JsonNode config = commandNamed(overwrite.body(), "config");
            assertNotNull(config, "config parent should be registered; body=" + overwrite.body());
            assertEquals(ApplicationCommand.Type.CHAT_INPUT.getValue(), config.path("type").asInt(), "config is a chat-input command");

            // Bare subcommand: config get
            JsonNode get = optionNamed(config, "get");
            assertNotNull(get, "config should contain the 'get' subcommand; body=" + overwrite.body());
            assertEquals(ApplicationCommandOption.Type.SUB_COMMAND.getValue(), get.path("type").asInt(), "'get' is a subcommand");

            // Grouped subcommand: config user add (the fully resolved @Structure)
            JsonNode userGroup = optionNamed(config, "user");
            assertNotNull(userGroup, "config should contain the 'user' subcommand group; body=" + overwrite.body());
            assertEquals(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue(), userGroup.path("type").asInt(), "'user' is a subcommand group");

            JsonNode add = optionNamed(userGroup, "add");
            assertNotNull(add, "the 'user' group should contain the 'add' subcommand; body=" + overwrite.body());
            assertEquals(ApplicationCommandOption.Type.SUB_COMMAND.getValue(), add.path("type").asInt(), "'add' is a subcommand");
            assertNotNull(optionNamed(add, "name"), "'add' should declare its 'name' option; body=" + overwrite.body());
        }
    }

    private static @Nullable JsonNode commandNamed(String body, String name) throws Exception {
        for (JsonNode command : MAPPER.readTree(body)) {
            if (name.equals(command.path("name").asText()))
                return command;
        }

        return null;
    }

    private static @Nullable JsonNode optionNamed(JsonNode parent, String name) {
        for (JsonNode option : parent.path("options")) {
            if (name.equals(option.path("name").asText()))
                return option;
        }

        return null;
    }

}
