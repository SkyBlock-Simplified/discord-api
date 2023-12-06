package dev.sbs.discordapi.command.reference;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.parameter.Parameter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

    default @NotNull ConcurrentList<String> getCommandTree() {
        ConcurrentList<String> commandTree = Concurrent.newList(this.getName().toLowerCase());

        if (this.getGroup().isPresent())
            commandTree.add(this.getGroup().get().getName().toLowerCase());

        if (this.getParent().isPresent())
            commandTree.add(this.getParent().get().getName().toLowerCase());

        return commandTree.inverse().toUnmodifiableList();
    }

    default @NotNull String getCommandPath() {
        return String.format(
            "/%s",
            StringUtil.join(this.getCommandTree(), " ")
        );
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

    default @NotNull Type getType() {
        return Type.CHAT_INPUT;
    }

    interface Parent {

        @NotNull String getDescription();

        @NotNull String getName();

        static @NotNull Impl of(@NotNull String name, @NotNull String description) {
            return new Impl(name, description);
        }

        @Getter
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        class Impl implements Parent {

            private final @NotNull String name;
            private final @NotNull String description;

        }

    }

    interface Group {

        @NotNull String getDescription();

        @NotNull String getName();

        static @NotNull Impl of(@NotNull String name, @NotNull String description) {
            return new Impl(name, description);
        }

        @Getter
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        class Impl implements Group {

            private final @NotNull String name;
            private final @NotNull String description;

        }

    }

}
