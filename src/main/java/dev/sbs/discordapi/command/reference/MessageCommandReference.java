package dev.sbs.discordapi.command.reference;

import dev.sbs.discordapi.context.deferrable.application.MessageCommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface MessageCommandReference extends CommandReference<MessageCommandContext> {

    default @NotNull Optional<String> getLongDescription() {
        return Optional.empty();
    }

    default @NotNull Type getType() {
        return Type.MESSAGE;
    }

}
