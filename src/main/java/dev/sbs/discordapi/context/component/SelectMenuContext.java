package dev.sbs.discordapi.context.component;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Context for select menu component interactions, extending {@link ActionComponentContext}
 * with access to the interacted {@link SelectMenu}, its selected options, and a convenience
 * method for modifying the select menu via its builder.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link SelectMenuInteractionEvent} is dispatched. Individual selected options can be
 * processed through {@link OptionContext}.
 *
 * @see OptionContext
 * @see SelectMenu.Option
 */
public interface SelectMenuContext extends ActionComponentContext {

    /**
     * Returns the underlying {@link SelectMenuInteractionEvent}.
     *
     * @return the select menu interaction event
     */
    @Override
    @NotNull SelectMenuInteractionEvent getEvent();

    /**
     * Returns the {@link SelectMenu} that was interacted with to trigger this context.
     *
     * @return the interacted select menu
     */
    @Override
    @NotNull SelectMenu getComponent();

    /**
     * Returns the list of currently selected options from the select menu.
     *
     * @return the selected options
     */
    default @NotNull ConcurrentList<SelectMenu.Option> getSelected() {
        return this.getComponent().getSelected();
    }

    /**
     * Modifies the select menu by applying a transformation function to its builder,
     * then replaces the select menu in the current response page.
     *
     * @param selectMenuBuilder a function that transforms the select menu builder
     * @return a mono completing when the select menu has been updated in the page
     */
    default Mono<Void> modify(@NotNull Function<SelectMenu.Builder, SelectMenu.Builder> selectMenuBuilder) {
        return this.modify(selectMenuBuilder.apply(this.getComponent().mutate()).build());
    }

    /**
     * Creates a new {@code SelectMenuContext} for the given event, response, and select menu.
     *
     * @param discordBot the bot instance
     * @param event the select menu interaction event
     * @param cachedMessage the cached response containing the select menu
     * @param selectMenu the select menu that was interacted with
     * @param followup the associated followup, if any
     * @return a new select menu context
     */
    static SelectMenuContext of(@NotNull DiscordBot discordBot, @NotNull SelectMenuInteractionEvent event, @NotNull Response cachedMessage, SelectMenu selectMenu, @NotNull Optional<Followup> followup) {
        return new Impl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            selectMenu,
            followup
        );
    }

    /**
     * Default implementation of {@link SelectMenuContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SelectMenuContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying select menu interaction event. */
        private final @NotNull SelectMenuInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The select menu that was interacted with. */
        private final @NotNull SelectMenu component;

        /** The associated followup, if any. */
        private final @NotNull Optional<Followup> followup;

    }

}
