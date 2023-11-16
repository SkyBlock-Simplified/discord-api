package dev.sbs.discordapi.response.page.item;

import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
abstract class SingletonItem<T> extends Item {

    private final @NotNull Optional<T> value;

    protected SingletonItem(
        @NotNull SelectMenu.Option option,
        @NotNull Type type,
        boolean editable,
        @NotNull Optional<T> value) {
        super(option, type, editable);
        this.value = value;
    }

}