package dev.sbs.discordapi.context.interaction.deferrable.component.action;

import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public interface ActionComponentContext extends ComponentContext {

    @NotNull ActionComponent getComponent();

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
