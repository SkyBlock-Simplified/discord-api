package dev.simplified.discordapi.integration.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.listener.Component;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /annotated} slash command for the offline harness that exercises the
 * {@link Component @Component} annotation dispatch paths of {@code ComponentListener}.
 *
 * <p>
 * The reply plants a button whose custom id ({@link #CACHED_ID}) matches a {@code @Component}
 * route, so a cached click routes through {@code dispatchAnnotation} (the annotation handler wins
 * over the inline handler).
 */
@Structure(
    name = "annotated",
    description = "Replies with an annotation-routed button"
)
public class AnnotatedButtonCommand extends DiscordCommand<SlashCommandContext> {

    /** Custom id of the planted button, routed to {@link #onCached(ButtonContext)}. */
    public static final @NotNull String CACHED_ID = "anno_cached";
    /** Content the annotation handler edits to (proves the annotation path ran, not the inline one). */
    public static final @NotNull String ANNOTATED_CONTENT = "annotated";
    /** Content the bypassed inline handler would edit to (must never appear). */
    public static final @NotNull String INLINE_CONTENT = "inline-ran";

    public AnnotatedButtonCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(
                    Page.builder()
                        .withContent("press it")
                        .withComponents(
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.PRIMARY)
                                    .withLabel("Annotated")
                                    .withIdentifier(CACHED_ID)
                                    // Inline handler that must be bypassed by the annotation route.
                                    .onInteract(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent(INLINE_CONTENT))))
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
        );
    }

    /**
     * Annotation route for the cached button. Wins over the inline handler and edits the content,
     * verifying the {@code dispatchAnnotation} (cache-hit) path.
     */
    @Component(CACHED_ID)
    Mono<Void> onCached(@NotNull ButtonContext context) {
        return context.edit(response -> response.editCurrentPage(builder -> builder.withContent(ANNOTATED_CONTENT)));
    }

}
