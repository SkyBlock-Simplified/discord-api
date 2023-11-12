package dev.sbs.discordapi.command.reference;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.discordapi.command.parameter.Parameter;
import discord4j.core.object.command.ApplicationCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface SlashCommandReference extends CommandReference {

    @Override
    default boolean doesMatch(@NotNull ConcurrentList<String> commandTree) {
        return switch (commandTree.size()) {
            case 3 -> this.getParent().isPresent() && this.getParent().get().getName().equals(commandTree.get(0)) &&
                this.getGroup().isPresent() && this.getGroup().get().getName().equals(commandTree.get(1)) &&
                this.getName().equals(commandTree.get(2));
            case 2 -> this.getParent().isPresent() && this.getParent().get().getName().equals(commandTree.get(0)) &&
                this.getName().equals(commandTree.get(1));
            default -> this.getName().equals(commandTree.get(0));
        };
    }

    default @NotNull Optional<Group> getGroup() {
        return Optional.empty();
    }

    default @NotNull Optional<String> getLongDescription() {
        return Optional.empty();
    }

    default @NotNull Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        index = Math.max(0, index);
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    default @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList();
    }

    default @NotNull Optional<Parent> getParent() {
        return Optional.empty();
    }

    default @NotNull ApplicationCommand.Type getType() {
        return ApplicationCommand.Type.CHAT_INPUT;
    }

}
