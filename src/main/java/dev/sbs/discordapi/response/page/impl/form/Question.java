package dev.sbs.discordapi.response.page.impl.form;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Question<T> {

    private final @NotNull FieldItem<T> fieldItem;
    private final @NotNull String identifier;
    private final @NotNull String title;
    private final @NotNull Optional<String> description;

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    public static <T> @NotNull Builder<T> from(@NotNull Question<T> question) {
        return builder();
    }

    public @NotNull Optional<T> getAnswer() {
        return this.getFieldItem().getValue();
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    public static class Builder<T> implements dev.sbs.api.util.builder.Builder<Question<T>> {

        @BuildFlag(notEmpty = true)
        private Optional<FieldItem<T>> fieldItem = Optional.empty();
        @BuildFlag(notEmpty = true)
        private Optional<String> identifier = Optional.empty();
        @BuildFlag(notEmpty = true)
        private Optional<String> title = Optional.empty();
        private Optional<String> description = Optional.empty();

        @Override
        public @NotNull Question<T> build() {
            Reflection.validateFlags(this);
            return new Question<>(
                this.fieldItem.orElseThrow(),
                this.identifier.orElseThrow(),
                this.title.orElseThrow(),
                this.description
            );
        }

    }

}
