package dev.sbs.discordapi.response.menu.item;

import dev.sbs.api.data.model.Model;
import dev.sbs.discordapi.response.menu.item.field.ModelMenuItem;
import dev.sbs.discordapi.response.menu.item.field.ToggleMenuItem;
import dev.sbs.discordapi.response.menu.item.field.primitive.NumberMenuItem;
import dev.sbs.discordapi.response.menu.item.field.primitive.OptionsMenuItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class MenuItem {

    @Getter protected final @NotNull UUID uniqueId;
    @Getter protected final @NotNull Type type;

    public static <T extends Model> ModelMenuItem.ModelMenuItemBuilder<T> modelBuilder(Class<T> modelClass) {
        return ModelMenuItem.builder(modelClass);
    }

    public static OptionsMenuItem.OptionsMenuItemBuilder stringBuilder() {
        return OptionsMenuItem.builder();
    }

    public static <T extends Number> NumberMenuItem.NumberMenuItemBuilder<T> numberBuilder(Class<T> numberClass) {
        return NumberMenuItem.builder(numberClass);
    }

    public static ToggleMenuItem.ToggleMenuItemBuilder toggleBuilder() {
        return ToggleMenuItem.builder();
    }

    /*
    Build a base MenuItem to inherit from and setup versions that do the following:
    - Yes/No / On/Off (Same file, toggle for verbage)
    - Temporary Selections (Optimizer Command)
    - Permanent Changes (Database Changes)

    The base should have a save function that only works for permanent changes (may rewrite this later if i think of something better).
    Setup a choice for when to save, on change or at the end of the menu.
    Create an annotation? that lets Menu build MenuItem's automatically so i don't have to actually use these builders everywhere.
     */

    public enum Type {

        UNKNOWN(-1),
        AUTHOR(1),
        TITLE(2),
        DESCRIPTION(3),
        THUMBNAIL_URL(4),
        IMAGE_URL(5),
        FIELD(6),
        FOOTER(7),
        TIMESTAMP(8);

        // timestamp

        @Getter private final int value;

        Type(int value) {
            this.value = value;
        }

        public static Type of(int value) {
            return Arrays.stream(values()).filter(style -> style.getValue() == value).findFirst().orElse(UNKNOWN);
        }

    }

}