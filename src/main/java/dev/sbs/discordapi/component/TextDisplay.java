package dev.sbs.discordapi.component;

import dev.sbs.discordapi.component.type.ContainerComponent;
import dev.sbs.discordapi.component.type.SectionComponent;
import dev.sbs.discordapi.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.component.type.TopLevelModalComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextDisplay implements ContainerComponent, SectionComponent, TopLevelMessageComponent, TopLevelModalComponent {

    private final @NotNull String content;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TextDisplay textDisplay = (TextDisplay) o;

        return Objects.equals(this.getContent(), textDisplay.getContent());
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
        return discord4j.core.object.component.TextDisplay.of(this.getContent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getContent());
    }

}
