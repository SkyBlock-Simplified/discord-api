package dev.sbs.discordapi.response.component.type;

import org.jetbrains.annotations.NotNull;

public interface ToggleableComponent extends MessageComponent {

    @NotNull ToggleableComponent setState(boolean enabled);

}
