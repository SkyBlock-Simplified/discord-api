package dev.sbs.discordapi.context.deferrable.component.action;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
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
 * Context for an individual {@link SelectMenu.Option} within a select menu interaction,
 * extending {@link ActionComponentContext} with access to both the parent {@link SelectMenu}
 * and the specific option that was selected.
 *
 * <p>
 * Instances are created via the {@link #of} factory method from an existing
 * {@link SelectMenuContext} and a specific option. The {@link #modify} method allows
 * transforming the individual option via its builder while keeping the parent select menu intact.
 *
 * @see SelectMenuContext
 * @see SelectMenu.Option
 */
public interface OptionContext extends ActionComponentContext {

    /**
     * Returns the underlying {@link SelectMenuInteractionEvent} from the parent select menu.
     *
     * @return the select menu interaction event
     */
    @Override
    @NotNull SelectMenuInteractionEvent getEvent();

    /**
     * Returns the parent {@link SelectMenu} containing the selected option.
     *
     * @return the parent select menu
     */
    @Override
    @NotNull SelectMenu getComponent();

    /**
     * Returns the specific {@link SelectMenu.Option} this context represents.
     *
     * @return the selected option
     */
    @NotNull SelectMenu.Option getOption();

    /**
     * Modifies the selected option by applying a transformation function to its builder,
     * then updates the option within the parent select menu on the current response page.
     *
     * @param optionBuilder a function that transforms the option builder
     * @return a mono completing when the option has been updated in the select menu
     */
    default Mono<Void> modify(@NotNull Function<SelectMenu.Option.Builder, SelectMenu.Option.Builder> optionBuilder) {
        return this.modify(
            this.getComponent()
                .mutate()
                .editOption(
                    optionBuilder.apply(this.getOption().mutate()).build()
                )
                .build()
        );
    }

    /**
     * Creates a new {@code OptionContext} from an existing select menu context, response,
     * and a specific option.
     *
     * @param selectMenuContext the parent select menu context
     * @param response the cached response containing the select menu
     * @param option the specific option to create context for
     * @return a new option context
     */
    static @NotNull OptionContext of(@NotNull SelectMenuContext selectMenuContext, @NotNull Response response, @NotNull SelectMenu.Option option) {
        return new Impl(
            selectMenuContext.getDiscordBot(),
            selectMenuContext.getEvent(),
            response.getUniqueId(),
            selectMenuContext.getComponent(),
            option,
            selectMenuContext.getFollowup()
        );
    }

    /**
     * Default implementation of {@link OptionContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements OptionContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying select menu interaction event. */
        private final @NotNull SelectMenuInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The parent select menu containing the option. */
        private final @NotNull SelectMenu component;

        /** The specific option this context represents. */
        private final @NotNull SelectMenu.Option option;

        /** The associated followup, if any. */
        private final @NotNull Optional<Followup> followup;

    }

}
