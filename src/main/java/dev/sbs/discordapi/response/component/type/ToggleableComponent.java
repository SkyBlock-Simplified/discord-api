package dev.sbs.discordapi.response.component.type;

import dev.sbs.discordapi.response.component.Component;

public interface ToggleableComponent extends Component {

    boolean isEnabled();

    default boolean isDisabled() {
        return !this.isEnabled();
    }

    default void setEnabled() {
        this.setEnabled(true);
    }

    void setEnabled(boolean value);

}
