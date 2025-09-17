package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.builder.annotation.BuildFlag;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.LabelComponent;
import dev.sbs.discordapi.response.component.type.TopLevelModalComponent;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Optional;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Label implements TopLevelModalComponent {

    private final @NotNull String title;
    private final @NotNull Optional<String> description;
    private final @NotNull LabelComponent component;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Label label = (Label) o;

        return new EqualsBuilder()
            .append(this.getTitle(), label.getTitle())
            .append(this.getDescription(), label.getDescription())
            .append(this.getComponent(), label.getComponent())
            .build();
    }

    public static @NotNull Builder from(@NotNull Label label) {
        return builder()
            .withTitle(label.getTitle())
            .withDescription(label.getDescription())
            .withComponent(label.getComponent());
    }

    @Override
    public @NotNull discord4j.core.object.component.Label getD4jComponent() {
        return (discord4j.core.object.component.Label) discord4j.core.object.component.Label.fromData(
            ComponentData.builder()
                .type(Component.Type.LABEL.getValue())
                .label(Possible.of(this.getTitle()).map(Optional::of))
                .description(Possible.of(this.getDescription()))
                .component(this.getComponent().getD4jComponent().getData())
                .build()
        );
    }

    @Override
    public @NotNull Type getType() {
        return Type.LABEL;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getTitle())
            .append(this.getDescription())
            .append(this.getComponent())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<Label> {

        @Range(from = 1, to = 45)
        @BuildFlag(notEmpty = true, limit = 45)
        private String title;
        @BuildFlag(limit = 100)
        private Optional<String> description = Optional.empty();
        @BuildFlag(notEmpty = true)
        private Optional<LabelComponent> component = Optional.empty();

        /**
         * Sets the {@link LabelComponent} for the {@link Label}.
         * <ul>
         *     <li>This must be provided</li>
         * </ul>
         *
         * @param component The component to set for the label.
         */
        public Builder withComponent(@NotNull LabelComponent component) {
            this.component = Optional.of(component);
            return this;
        }

        /**
         * Sets the description text of the {@link Label}.
         *
         * @param description The description of the button.
         * @param args The objects used to format the url.
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description text of the {@link Label}.
         *
         * @param description The label of the button.
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description text of the {@link Label}.
         *
         * @param description The description of the button.
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the title text of the {@link Label}.
         *
         * @param title The title of the button.
         * @param args The objects used to format the url.
         */
        public Builder withTitle(@PrintFormat @NotNull String title, @Nullable Object... args) {
            return this.withTitle(String.format(title, args));
        }

        /**
         * Sets the title text of the {@link Label}.
         *
         * @param title The label of the button.
         */
        public Builder withTitle(@NotNull String title) {
            this.title = title;
            return this;
        }

        @Override
        public @NotNull Label build() {
            Reflection.validateFlags(this);

            return new Label(
                this.title,
                this.description,
                this.component.orElseThrow()
            );
        }

    }

}
