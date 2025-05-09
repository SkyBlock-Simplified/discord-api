package dev.sbs.discordapi.response.component;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import dev.sbs.discordapi.response.component.type.v2.SectionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextDisplay implements Component, TopLevelMessageComponent, ContainerComponent, SectionComponent {

    private final @NotNull String identifier = UUID.randomUUID().toString();
    private final @NotNull String content;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TextDisplay textDisplay = (TextDisplay) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), textDisplay.getIdentifier())
            .append(this.getContent(), textDisplay.getContent())
            .build();
    }

    @Override
    public @NotNull Type getType() {
        return Type.TEXT_DISPLAY;
    }

    public static @NotNull TextDisplay of(@NotNull String content) {
        return new TextDisplay(content);
    }

    public static @NotNull TextDisplay of(@NotNull @PrintFormat String content, @Nullable Object... args) {
        return new TextDisplay(String.format(content, args));
    }

    @Override
    public @NotNull discord4j.core.object.component.TextDisplay getD4jComponent() {
        return discord4j.core.object.component.TextDisplay.of(
            this.getContent()
        );
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getContent())
            .build();
    }

}
