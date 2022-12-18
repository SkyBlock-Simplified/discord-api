package dev.sbs.discordapi.response.menu.item.singleton;

import dev.sbs.discordapi.response.menu.item.MenuItem;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public abstract class SingletonMenuItem<T> extends MenuItem {

    @Getter private final @NotNull Optional<T> value;

    protected SingletonMenuItem(@NotNull UUID uniqueId, @NotNull Type type, @NotNull Optional<T> value) {
        super(uniqueId, type);
        this.value = value;
    }

}