package dev.sbs.discordapi.command.reference;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.context.interaction.deferrable.application.UserCommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface UserCommandReference extends CommandReference<UserCommandContext> {

    @Override
    default boolean doesMatch(@NotNull ConcurrentList<String> commandTree) {
        return this.getName().equals(commandTree.get(0));
    }

    default @NotNull Optional<String> getLongDescription() {
        return Optional.empty();
    }

    default @NotNull Type getType() {
        return Type.USER;
    }

}