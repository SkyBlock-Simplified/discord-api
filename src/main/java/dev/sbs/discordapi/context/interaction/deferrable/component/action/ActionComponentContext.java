package dev.sbs.discordapi.context.interaction.deferrable.component.action;

import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.UserActionComponent;

public interface ActionComponentContext extends ComponentContext {

    ActionComponent<?> getComponent();

    default void modify(UserActionComponent<?> actionComponent) {
        this.getResponse().ifPresent(response -> this.getDiscordBot()
            .getResponseCache()
            .updateResponse(
                response.mutate()
                    .editPage(
                        response.getCurrentPage()
                            .mutate()
                            .editComponent(actionComponent)
                            .build()
                    )
                    .build(),
                false
            )
        );
    }

}
