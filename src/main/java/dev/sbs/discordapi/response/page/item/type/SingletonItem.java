package dev.sbs.discordapi.response.page.item.type;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface SingletonItem<T> extends Item {

    @NotNull Optional<T> getValue();

}
