package dev.sbs.discordapi.context.interaction.deferrable.component.action;

import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;

public interface ActionComponentContext extends ComponentContext {

    ActionComponent getComponent();

    default void modify(ActionComponent actionComponent) {
        this.getResponseCacheEntry().updateResponse(
            this.getResponse()
                .mutate()
                .editPage(
                    this.getResponse()
                        .getHistoryHandler()
                        .getCurrentPage()
                        .mutate()
                        .editComponent(actionComponent)
                        .build()
                )
                .build(),
            false
        );
    }

}
