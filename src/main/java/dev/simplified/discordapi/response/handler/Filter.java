package dev.simplified.discordapi.response.handler;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.function.TriPredicate;
import dev.simplified.discordapi.component.capability.UserInteractable;
import dev.simplified.discordapi.component.interaction.CheckboxGroup;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NoArgsConstructor;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@EqualsAndHashCode
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Filter<T> implements TriPredicate<T, Long, Long>, UserInteractable {

    private final @NotNull String identifier;
    private final @NotNull String label;
    private final @NotNull Optional<String> description;
    private final @NotNull ConcurrentList<TriPredicate<T, Long, Long>> predicates;
    private final boolean enabled;

    @Override
    public boolean test(@NotNull T item, Long index, Long size) {
        return !this.isEnabled() || this.getPredicates()
            .stream()
            .allMatch(predicate -> predicate.test(item, index, size));
    }

    /**
     * Builds a {@link CheckboxGroup.Option} from this filter's fields.
     *
     * @return the built checkbox option
     */
    public @NotNull CheckboxGroup.Option buildOption() {
        CheckboxGroup.Option.Builder optionBuilder = CheckboxGroup.Option.builder()
            .withLabel(this.getLabel())
            .withValue(this.getIdentifier());

        this.getDescription().ifPresent(optionBuilder::withDescription);
        return optionBuilder.build();
    }

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    public static <T> @NotNull Builder<T> from(@NotNull Filter<T> filter) {
        return new Builder<T>()
            .withIdentifier(filter.getIdentifier())
            .withLabel(filter.getLabel())
            .withDescription(filter.getDescription())
            .withTriPredicates(filter.getPredicates())
            .isEnabled(filter.isEnabled());
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> {

        private String identifier = UUID.randomUUID().toString();
        @BuildFlag(notEmpty = true)
        private Optional<String> label = Optional.empty();
        private Optional<String> description = Optional.empty();
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
         *
         * @param value true to enable
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
         *
         * @param value true to disable
         */
        public Builder<T> isDisabled(boolean value) {
            this.enabled = !value;
            return this;
        }

        /**
         * Adds predicates used to filter items.
         *
         * @param predicates collection of filters to apply to {@link T}
         */
        @SafeVarargs
        public final Builder<T> withPredicates(@NotNull Predicate<T>... predicates) {
            return this.withPredicates(Arrays.asList(predicates));
        }

        /**
         * Adds predicates used to filter items.
         *
         * @param predicates collection of filters to apply to {@link T}
         */
        public Builder<T> withPredicates(@NotNull Iterable<Predicate<T>> predicates) {
            predicates.forEach(predicate -> this.predicates.add((t, index, size) -> predicate.test(t)));
            return this;
        }

        /**
         * Adds predicates used to filter items.
         *
         * @param predicates collection of filters to apply to {@link T}
         */
        @SafeVarargs
        public final Builder<T> withTriPredicates(@NotNull TriPredicate<T, Long, Long>... predicates) {
            return this.withTriPredicates(Arrays.asList(predicates));
        }

        /**
         * Adds predicates used to filter items.
         *
         * @param predicates collection of filters to apply to {@link T}
         */
        public Builder<T> withTriPredicates(@NotNull Iterable<TriPredicate<T, Long, Long>> predicates) {
            predicates.forEach(this.predicates::add);
            return this;
        }

        /**
         * Sets the description of the {@link Filter}.
         *
         * @param description the description to use
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link Filter}.
         *
         * @param description the description to use
         * @param args the objects used to format the description
         */
        public Builder<T> withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Filter}.
         *
         * @param description the description to use
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the identifier of the {@link Filter}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder<T> withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the label of the {@link Filter}.
         *
         * @param label the label of the filter
         */
        public Builder<T> withLabel(@NotNull String label) {
            this.label = Optional.of(label);
            return this;
        }

        /**
         * Sets the label of the {@link Filter}.
         *
         * @param label the label of the filter
         * @param args the objects used to format the label
         */
        public Builder<T> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.label = Optional.of(String.format(label, args));
            return this;
        }

        public @NotNull Filter<T> build() {
            Reflection.validateFlags(this);

            return new Filter<>(
                this.identifier,
                this.label.orElseThrow(),
                this.description,
                this.predicates.toUnmodifiable(),
                this.enabled
            );
        }

    }

}
