package dev.sbs.discordapi.context.scope;

import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.response.Response;
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
        return Mono.just(this.getResponse())
            .doOnNext(response -> response.mutate().editPage(
                    response.getHistoryHandler()
                    .getCurrentPage()
                    .mutate()
                    .editComponent(actionComponent)
                    .build()
            ))
            .then();
    }

}
