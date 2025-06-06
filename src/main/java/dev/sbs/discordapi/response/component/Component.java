package dev.sbs.discordapi.response.component;

import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import discord4j.core.object.entity.Message;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public interface Component {

    /**
     * Flattens the structure of components, recursively traversing through nested {@link LayoutComponent} instances
     * and returning a stream of all components, including the current instance.
     *
     * @return A stream containing all components within the current component hierarchy, flattened into a single stream.
     */
    default @NotNull Stream<Component> flattenComponents() {
        if (this instanceof LayoutComponent layoutComponent) {
            return Stream.concat(
                Stream.of(this),
                layoutComponent.getComponents()
                    .stream()
                    .flatMap(component -> {
                        Stream<Component> selfStream = Stream.of(component);

                        if (component instanceof LayoutComponent)
                            return Stream.concat(selfStream, component.flattenComponents());
                        else
                            return selfStream;
                    })
            );
        }

        return Stream.of(this);
    }

    @NotNull discord4j.core.object.component.MessageComponent getD4jComponent();

    @NotNull Type getType();

    /**
     * Represents the various types of components or layout elements that can exist within a message's structure.
     * Each type is associated with an integer value, which is used for identification and mapping.
     * <p>
     * Some specific types may require the use of certain flags, such as {@link Message.Flag#IS_COMPONENTS_V2},
     * which influences their behavior or interpretation.
     * <p>
     * The types include common components such as buttons, select menus, text inputs, as well as more complex
     * layout elements such as sections, containers, and media galleries.
     */
    @Getter
    @RequiredArgsConstructor
    enum Type {

        UNKNOWN(-1),
        ACTION_ROW(1),
        BUTTON(2),
        SELECT_MENU(3),
        TEXT_INPUT(4),
        SELECT_MENU_USER(5),
        SELECT_MENU_ROLE(6),
        SELECT_MENU_MENTIONABLE(7),
        SELECT_MENU_CHANNEL(8),
        SECTION(9, true),
        TEXT_DISPLAY(10, true),
        THUMBNAIL(11, true),
        MEDIA_GALLERY(12, true),
        FILE(13, true),
        SEPARATOR(14, true),
        CONTAINER(17, true);

        private final int value;

        /**
         * Gets if this Type requires the use of {@link Message.Flag#IS_COMPONENTS_V2}.
         */
        private final boolean requireFlag;

        Type(int value) {
            this(value, false);
        }

        public static @NotNull Type of(int value) {
            return switch (value) {
                case 1 -> ACTION_ROW;
                case 2 -> BUTTON;
                case 3 -> SELECT_MENU;
                case 4 -> TEXT_INPUT;
                case 5 -> SELECT_MENU_USER;
                case 6 -> SELECT_MENU_ROLE;
                case 7 -> SELECT_MENU_MENTIONABLE;
                case 8 -> SELECT_MENU_CHANNEL;
                case 9 -> SECTION;
                case 10 -> TEXT_DISPLAY;
                case 11 -> THUMBNAIL;
                case 12 -> MEDIA_GALLERY;
                case 13 -> FILE;
                case 14 -> SEPARATOR;
                case 17 -> CONTAINER;
                default -> UNKNOWN;
            };
        }

    }

}
