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

    public enum Type {

        UNKNOWN(-1),
        MENU(1),
        AUTHOR(2),
        TITLE(3),
        DESCRIPTION(4),
        THUMBNAIL_URL(5),
        IMAGE_URL(6),
        FIELD(7),
        FOOTER(8),
        TIMESTAMP(9);

        @Getter private final int value;

        Type(int value) {
            this.value = value;
        }

        public static Type of(int value) {
            return Arrays.stream(values()).filter(style -> style.getValue() == value).findFirst().orElse(UNKNOWN);
        }

    }

}