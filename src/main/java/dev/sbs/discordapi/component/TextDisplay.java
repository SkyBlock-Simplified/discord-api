package dev.sbs.discordapi.component;

import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.scope.ContainerComponent;
import dev.sbs.discordapi.component.scope.SectionComponent;
import dev.sbs.discordapi.component.scope.TopLevelMessageComponent;
import dev.sbs.discordapi.component.scope.TopLevelModalComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An immutable text display component for Discord's Components V2 system.
 *
 * <p>
 * Renders plain or formatted text content within a message. This component can be placed
 * inside a {@link Container Container}, within a {@link Section Section}, or at the top
 * level of both messages and modals.
 *
 * <p>
 * Instances are created via the {@link #of(String)} or {@link #of(String, Object...)}
 * factory methods. Equality is determined solely by {@link #getContent() content}.
 *
 * @see Container
 * @see Section
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextDisplay implements ContainerComponent, SectionComponent, TopLevelMessageComponent, TopLevelModalComponent {

    /** The text content rendered by this display. */
    private final @NotNull String content;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TextDisplay textDisplay = (TextDisplay) o;

        return Objects.equals(this.getContent(), textDisplay.getContent());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.TEXT_DISPLAY;
    }

    /**
     * Creates a new {@link TextDisplay} with the given content.
     *
     * @param content the text content to display
     * @return a new text display instance
     */
    public static @NotNull TextDisplay of(@NotNull String content) {
        return new TextDisplay(content);
    }

    /**
     * Creates a new {@link TextDisplay} with formatted content using
     * {@link String#format(String, Object...)}.
     *
     * @param content the format string
     * @param args the format arguments
     * @return a new text display instance with the formatted content
     */
    public static @NotNull TextDisplay of(@NotNull @PrintFormat String content, @Nullable Object... args) {
        return new TextDisplay(String.format(content, args));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.TextDisplay getD4jComponent() {
        return discord4j.core.object.component.TextDisplay.of(this.getContent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getContent());
    }

}
