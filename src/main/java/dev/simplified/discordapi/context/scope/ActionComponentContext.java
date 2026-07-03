package dev.simplified.discordapi.context.scope;

import dev.simplified.discordapi.component.scope.ActionComponent;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.context.component.SelectMenuContext;
import dev.simplified.discordapi.response.Response;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Interaction scope for component contexts targeting an {@link ActionComponent}, extending
 * {@link ComponentContext} with the ability to modify the triggering component within the
 * current {@link Response} page.
 *
 * @see ButtonContext
 * @see SelectMenuContext
 */
public interface ActionComponentContext extends ComponentContext {

    /** {@inheritDoc} */
    @Override
    @NotNull ActionComponent getComponent();

    /**
     * Replaces the triggering component in the current response page with the given
     * action component.
     *
     * @param actionComponent the replacement action component
     * @return a mono completing when the component has been updated in the page
     */
    default Mono<Void> modify(@NotNull ActionComponent actionComponent) {
        return Mono.fromRunnable(() -> this.getResponse()
            .getHistoryHandler()
            .editCurrentPage(page -> page.mutate().editComponent(actionComponent).build()));
    }

}
