package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.discordapi.context.deferrable.component.action.SelectMenuContext;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.handler.history.TreeHistoryHandler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntitySelectMenu implements SelectMenu {

    // TODO: Abstract/Interface this class into StringSelectMenu and EntitySelectMenu
    private final @NotNull String userIdentifier;
    private final @NotNull Optional<String> placeholder;
    private final int minValues;
    private final int maxValues;
    private final boolean placeholderShowingSelectedOption;
    private final @NotNull ConcurrentList<Option> options;
    private final boolean deferEdit;
    @Getter(AccessLevel.NONE)
    private final @NotNull ConcurrentList<Option> selected = Concurrent.newList();
    private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> userInteraction;
    private final @NotNull Type menuType;
    private boolean enabled;

    public static @NotNull EntityBuilder builder() {
        return new EntityBuilder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        EntitySelectMenu that = (EntitySelectMenu) o;

        return new EqualsBuilder()
            .append(this.isEnabled(), that.isEnabled())
            .append(this.isPlaceholderShowingSelectedOption(), that.isPlaceholderShowingSelectedOption())
            .append(this.isDeferEdit(), that.isDeferEdit())
            .append(this.getUserIdentifier(), that.getUserIdentifier())
            .append(this.getPlaceholder(), that.getPlaceholder())
            .append(this.getMinValues(), that.getMinValues())
            .append(this.getMaxValues(), that.getMaxValues())
            .append(this.getOptions(), that.getOptions())
            .append(this.getSelected(), that.getSelected())
            .append(this.getMenuType(), that.getMenuType())
            .build();
    }

    public static @NotNull EntityBuilder from(@NotNull EntitySelectMenu selectMenu) {
        return new EntityBuilder()
            .withIdentifier(selectMenu.getUserIdentifier())
            .setDisabled(selectMenu.isEnabled())
            .withPlaceholder(selectMenu.getPlaceholder())
            .withMinValues(selectMenu.getMinValues())
            .withMaxValues(selectMenu.getMaxValues())
            .withPlaceholderShowingSelectedOption(selectMenu.isPlaceholderShowingSelectedOption())
            .withOptions(selectMenu.getOptions())
            .onInteract(selectMenu.getUserInteraction())
            .withType(selectMenu.getMenuType());
    }

    public @NotNull ConcurrentList<Option> getSelected() {
        return this.selected.toUnmodifiableList();
    }

    @Override
    public @NotNull Component.Type getType() {
        return this.getMenuType().getInternalType();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getUserIdentifier())
            .append(this.isEnabled())
            .append(this.getPlaceholder())
            .append(this.getMinValues())
            .append(this.getMaxValues())
            .append(this.isPlaceholderShowingSelectedOption())
            .append(this.getOptions())
            .append(this.isDeferEdit())
            .append(this.getSelected())
            .append(this.getMenuType())
            .build();
    }

    @Override
    public @NotNull EntityBuilder mutate() {
        return from(this);
    }

    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class EntityBuilder extends SelectMenu.Builder {

        private Type type = Type.UNKNOWN;

        /**
         * Updates an existing {@link Option}.
         *
         * @param option The option to update.
         */
        public EntityBuilder editOption(@NotNull Option option) {
            this.options.stream()
                .filter(innerOption -> innerOption.getUniqueId().equals(option.getUniqueId()))
                .findFirst()
                .ifPresent(innerOption -> {
                    int index = this.options.indexOf(innerOption);
                    this.options.remove(index);
                    this.options.add(index, option);
                });
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link EntitySelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public EntityBuilder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link EntitySelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public EntityBuilder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link EntitySelectMenu} as disabled.
         */
        public EntityBuilder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link EntitySelectMenu} should be disabled.
         *
         * @param value True to disable the select menu.
         */
        public EntityBuilder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets this {@link EntitySelectMenu} as deferred when interacting.
         */
        public EntityBuilder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link EntitySelectMenu} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        public EntityBuilder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the {@link EntitySelectMenu} as enabled.
         */
        public EntityBuilder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets if the {@link EntitySelectMenu} should be enabled.
         *
         * @param value True to enable the button.
         */
        public EntityBuilder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link EntitySelectMenu}.
         *
         * @param identifier The identifier to use.
         */
        public EntityBuilder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link EntitySelectMenu}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public EntityBuilder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Add {@link Option Options} to the {@link EntitySelectMenu}.
         *
         * @param options Variable number of options to add.
         */
        public EntityBuilder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Add {@link Option Options} {@link EntitySelectMenu}.
         *
         * @param options Collection of options to add.
         */
        public EntityBuilder withOptions(@NotNull Iterable<Option> options) {
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the placeholder text to show on the {@link EntitySelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public EntityBuilder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text to show on the {@link EntitySelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public EntityBuilder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link EntitySelectMenu}.
         *
         * @param minValues The minimum number of selected {@link Option Options}.
         */
        public EntityBuilder withMinValues(int minValues) {
            this.minValues = minValues;
            return this;
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link EntitySelectMenu}.
         *
         * @param maxValues The maximum number of selected {@link Option Options}.
         */
        public EntityBuilder withMaxValues(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        /**
         * Sets the {@link EntitySelectMenu} to override its placeholder with the selected {@link Option}.
         */
        public EntityBuilder withPlaceholderShowingSelectedOption() {
            return this.withPlaceholderShowingSelectedOption(true);
        }

        /**
         * Sets if the {@link EntitySelectMenu} should override its placeholder with the selected {@link Option}.
         *
         * @param value True to override the placeholder with the selected option.
         */
        public EntityBuilder withPlaceholderShowingSelectedOption(boolean value) {
            this.placeholderShowingSelectedOption = value;
            return this;
        }

        /**
         * Sets the type of the {@link EntitySelectMenu}.
         *
         * @param type The type to set.
         */
        public EntityBuilder withType(@NotNull Type type) {
            this.type = type;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link EntitySelectMenu} component.
         */
        @Override
        public @NotNull EntitySelectMenu build() {
            Reflection.validateFlags(this);

            if (this.type == Type.UNKNOWN)
                throw new IllegalStateException("Type must be set.");

            return new EntitySelectMenu(
                this.identifier,
                this.placeholder,
                this.minValues,
                this.maxValues,
                this.placeholderShowingSelectedOption,
                this.options,
                this.deferEdit,
                this.interaction,
                this.type,
                this.enabled
            );
        }

    }

    @Getter
    @RequiredArgsConstructor
    public enum Type {

        UNKNOWN(Component.Type.UNKNOWN),
        USER(Component.Type.SELECT_MENU_USER),
        ROLE(Component.Type.SELECT_MENU_ROLE),
        MENTIONABLE(Component.Type.SELECT_MENU_MENTIONABLE),
        CHANNEL(Component.Type.SELECT_MENU_CHANNEL);

        private final @NotNull Component.Type internalType;

        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(type -> type.getInternalType().getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    @Getter
    @RequiredArgsConstructor
    @SuppressWarnings("unchecked")
    public enum PageType {

        NONE(__ -> Mono.empty()),
        PAGE_SELECTOR(context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelected().getFirst().orElseThrow().getValue();
            response.getHistoryHandler().gotoTopLevelPage(selectedValue);
        })),
        SUBPAGE_SELECTOR(context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelected().getFirst().orElseThrow().getValue();

            if (selectedValue.equals("BACK"))
                response.getHistoryHandler().gotoPreviousPage();
            else
                ((TreeHistoryHandler<?, String>) response.getHistoryHandler()).gotoSubPage(selectedValue);
        })),
        ITEM(context -> context.consumeResponse(response -> {
            /*
             TODO
                Build a viewer that converts the item list
                into something that either lists data about an item
                or a sub-list from PageItem

             TODO
                Viewer will need the ability to enable editing
                and code to do something on save
             */
        }));

        private final @NotNull Function<SelectMenuContext, Mono<Void>> interaction;

    }

}
