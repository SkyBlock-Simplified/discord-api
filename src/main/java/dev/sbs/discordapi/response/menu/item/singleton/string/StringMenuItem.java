package dev.sbs.discordapi.response.menu.item.singleton.string;

import dev.sbs.discordapi.response.menu.item.singleton.SingletonMenuItem;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public abstract class StringMenuItem extends SingletonMenuItem<String> {

    protected StringMenuItem(@NotNull UUID uniqueId, @NotNull Type type, @NotNull Optional<String> value) {
        super(uniqueId, type, value);
    }

}