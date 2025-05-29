package dev.sbs.discordapi.response.handler.item.filter;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.builder.annotation.BuildFlag;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.stream.triple.TriPredicate;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Filter<T> implements TriPredicate<T, Long, Long> {

    private final @NotNull SelectMenu.Option option;
    private final @NotNull ConcurrentList<TriPredicate<T, Long, Long>> predicates;
    private final boolean enabled;

    @Override
    public boolean test(@NotNull T item, Long index, Long size) {
        return !this.isEnabled() || this.getPredicates()
            .stream()
            .allMatch(predicate -> predicate.test(item, index, size));
    }

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Filter<?> filter = (Filter<?>) o;

        return new EqualsBuilder()
            .append(this.getOption(), filter.getOption())
            .append(this.getPredicates(), filter.getPredicates())
            .append(this.isEnabled(), filter.isEnabled())
            .build();
    }

    public static <T> @NotNull Builder<T> from(@NotNull Filter<T> filter) {
        return new Builder<T>()
            .withOption(filter.getOption())
            .withTriPredicates(filter.getPredicates())
            .isEnabled(filter.isEnabled());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getOption())
            .append(this.getPredicates())
            .append(this.isEnabled())
            .build();
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> implements ClassBuilder<Filter<T>> {

        @BuildFlag(nonNull = true)
        private SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        @BuildFlag(nonNull = true)
        private final ConcurrentList<TriPredicate<T, Long, Long>> predicates = Concurrent.newList();
        private boolean enabled = false;

        /**
         * Sets this filter as enabled.
         */
        public Builder<T> isEnabled() {
            return this.isEnabled(true);
        }

        /**
         * Sets this filter as enabled if given true.
         * @param value True to enable.
         */
        public Builder<T> isEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets this filter as disabled.
         */
        public Builder<T> isDisabled() {
            return this.isDisabled(true);
        }

        /**
         * Sets this filter as disabled if given true.
         * @param value True to disable.
         */
        public Builder<T> isDisabled(boolean value) {
            this.enabled = !value;
            return this;
        }

        /**
         * Adds predicates used to filter {@link FieldItem RenderItems}.
         *
         * @param predicates Collection of filters to apply to {@link T}.
         */
        public Builder<T> withPredicates(@NotNull Predicate<T>... predicates) {
            return this.withPredicates(Arrays.asList(predicates));
        }

        /**
         * Adds predicates used to filter {@link FieldItem RenderItems}.
         *
         * @param predicates Collection of filters to apply to {@link T}.
         */
        public Builder<T> withPredicates(@NotNull Iterable<Predicate<T>> predicates) {
            predicates.forEach(predicate -> this.predicates.add((t, index, size) -> predicate.test(t)));
            return this;
        }

        /**
         * Adds predicates used to filter {@link FieldItem RenderItems}.
         *
         * @param predicates Collection of filters to apply to {@link T}.
         */
        public Builder<T> withTriPredicates(@NotNull TriPredicate<T, Long, Long>... predicates) {
            return this.withTriPredicates(Arrays.asList(predicates));
        }

        /**
         * Adds predicates used to filter {@link FieldItem RenderItems}.
         *
         * @param predicates Collection of filters to apply to {@link T}.
         */
        public Builder<T> withTriPredicates(@NotNull Iterable<TriPredicate<T, Long, Long>> predicates) {
            predicates.forEach(this.predicates::add);
            return this;
        }

        /**
         * Sets the description of the {@link Filter}.
         *
         * @param description The description to use.
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link Filter}.
         *
         * @param description The description to use.
         * @param args The objects used to format the description.
         */
        public Builder<T> withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Filter}.
         *
         * @param description The description to use.
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        /**
         * Sets the emoji of the {@link Filter}.
         * <br><br>
         * This is used for the {@link Button#getEmoji()}.
         *
         * @param emoji The emoji to use.
         */
        public Builder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link Filter}.
         * <br><br>
         * This is used for the {@link Button#getEmoji()}.
         *
         * @param emoji The emoji to use.
         */
        public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Sets the label of the {@link Filter}.
         * <br><br>
         * This is used for the {@link Button}.
         *
         * @param label The label of the field item.
         */
        public Builder<T> withLabel(@NotNull String label) {
            this.optionBuilder.withLabel(label);
            return this;
        }

        /**
         * Sets the label of the {@link Filter}.
         * <br><br>
         * This is used for the {@link Button}.
         *
         * @param label The label of the field item.
         * @param args The objects used to format the label.
         */
        public Builder<T> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public Builder<T> withOption(@NotNull SelectMenu.Option option) {
            this.optionBuilder = SelectMenu.Option.from(option);
            return this;
        }

        @Override
        public @NotNull Filter<T> build() {
            Reflection.validateFlags(this);

            return new Filter<>(
                this.optionBuilder.build(),
                this.predicates.toUnmodifiableList(),
                this.enabled
            );
        }

    }

}
