package dev.sbs.discordapi.response.page.item;

import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

abstract class SingletonItem<T> extends PageItem {

    @Getter private final @NotNull Optional<T> value;

    protected SingletonItem(
        @NotNull SelectMenu.Option option,
        @NotNull Type type,
        boolean editable,
        @NotNull Optional<T> value) {
        super(option.getIdentifier(), Optional.of(option), type, editable);
        this.value = value;
    }

}