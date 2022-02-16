package dev.sbs.discordapi.command.data;

import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class Argument {

    @Getter private final @NotNull Parameter parameter;
    @Getter private final @NotNull ConcurrentList<Data> data;

    public Argument(@NotNull Parameter parameter, @NotNull Data data) {
        this(parameter, Concurrent.newList(data));
    }

    public Argument(@NotNull Parameter parameter, @NotNull ConcurrentList<Data> data) {
        this.parameter = parameter;
        this.data = Concurrent.newUnmodifiableList(data);
    }

    public Optional<String> getValue() {
        return this.getData(0).getValue();
    }

    public Data getData(int index) {
        return this.data.get(index);
    }

    public static class Data {

        @Getter private final @NotNull Optional<String> value;
        @Getter private final @NotNull ConcurrentList<String> options;

        public Data() {
            this(Optional.empty());
        }

        public Data(@Nullable String value) {
            this(Optional.ofNullable(value));
        }

        public Data(@NotNull Optional<String> value) {
            this(value, Concurrent.newList());
        }

        public Data(@Nullable String value, @NotNull ConcurrentList<String> options) {
            this(Optional.ofNullable(value), options);
        }

        public Data(@NotNull Optional<String> value, @NotNull ConcurrentList<String> options) {
            this.value = value;
            this.options = Concurrent.newUnmodifiableList(options);
        }

    }

}
