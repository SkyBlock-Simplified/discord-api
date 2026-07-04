package dev.simplified.discordapi.integration.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.context.EternalBuildContext;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.listener.Component;
import dev.simplified.discordapi.listener.Eternal;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /eternal} slash command for the offline harness that exercises the eternal
 * (reboot-surviving) response flow end to end.
 *
 * <p>
 * {@link #process(SlashCommandContext)} sends an eternal response via {@code replyEternal}, whose
 * structure is rebuilt by the {@link Eternal @Eternal} {@link #build(EternalBuildContext) builder}
 * keyed by {@link #BUILDER_KEY}. The response plants a button ({@link #BUTTON_ID}) routed to the
 * {@link Component @Component} {@link #onClick(ButtonContext) handler}. After a simulated reboot
 * (a fresh harness sharing the cold store), a click re-hydrates the real button and dispatches the
 * handler, which edits the content to {@link #HANDLED_CONTENT}.
 */
@Structure(
    name = "eternal",
    description = "Creates an eternal button that survives reboots"
)
public class EternalButtonCommand extends DiscordCommand<SlashCommandContext> {

    /** Stable key of the registered rebuild function. */
    public static final @NotNull String BUILDER_KEY = "eternal_button";
    /** Custom id of the planted eternal button, routed to {@link #onClick(ButtonContext)}. */
    public static final @NotNull String BUTTON_ID = "eternal_btn";
    /** Content the builder renders initially (and after a rebuild). */
    public static final @NotNull String INITIAL_CONTENT = "eternal ready";
    /** Content the click handler edits to (proves a real dispatch ran against a real component). */
    public static final @NotNull String HANDLED_CONTENT = "eternal clicked";

    public EternalButtonCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.replyEternal(BUILDER_KEY, "");
    }

    /**
     * Rebuild function - deterministic from the payload, invoked identically at creation and at
     * hydration. Plants the button routed to {@link #onClick(ButtonContext)}.
     */
    @Eternal(BUILDER_KEY)
    Response build(@NotNull EternalBuildContext context) {
        return context.buildResponse()
            .withPages(
                Page.builder()
                    .withContent(INITIAL_CONTENT)
                    .withComponents(
                        ActionRow.of(
                            Button.builder()
                                .withStyle(Button.Style.PRIMARY)
                                .withLabel("Eternal")
                                .withIdentifier(BUTTON_ID)
                                .build()
                        )
                    )
                    .build()
            )
            .build();
    }

    /** Click handler for the eternal button; edits the content to prove a real dispatch ran. */
    @Component(BUTTON_ID)
    Mono<Void> onClick(@NotNull ButtonContext context) {
        return context.edit(response -> response.editCurrentPage(builder -> builder.withContent(HANDLED_CONTENT)));
    }

}
