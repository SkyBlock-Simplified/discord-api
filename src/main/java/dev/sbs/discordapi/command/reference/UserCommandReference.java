package dev.sbs.discordapi.command.reference;

import dev.sbs.discordapi.context.deferrable.application.UserCommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface UserCommandReference extends CommandReference<UserCommandContext> {

    default @NotNull Optional<String> getLongDescription() {
        return Optional.empty();
    }

    default @NotNull Type getType() {
        return Type.USER;
    }

}
