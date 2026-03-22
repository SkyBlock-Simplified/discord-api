package dev.sbs.discordapi.component;

import dev.sbs.discordapi.component.layout.LayoutComponent;
import discord4j.core.object.entity.Message;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Root interface for all Discord message components.
 *
 * <p>
 * Every component in the Discord message component hierarchy - including interactive elements
 * such as buttons and select menus, layout containers such as action rows and sections, and
 * display elements such as text displays and thumbnails - implements this interface.
 *
 * <p>
 * Components can be converted to their Discord4J counterparts via {@link #getD4jComponent()}
 * and identified by their {@link Type}. Hierarchical component trees can be traversed using
 * {@link #flattenComponents()}, which recursively expands {@link LayoutComponent} children
 * into a flat stream.
 *
 * @see LayoutComponent
 */
public interface Component {

    /**
     * Recursively flattens this component and all nested {@link LayoutComponent} children
     * into a single stream.
     *
     * <p>
     * For leaf components, returns a stream containing only {@code this}. Layout components
     * override this method to concatenate their own stream with the flattened streams of
     * their children.
     *
     * @return a stream of all components in this component's hierarchy
     */
    default @NotNull Stream<Component> flattenComponents() {
        return Stream.of(this);
    }

    /** The Discord4J representation of this component. */
    @NotNull discord4j.core.object.component.BaseMessageComponent getD4jComponent();

    /** The Discord component type of this component. */
    @NotNull Type getType();

    /**
     * Enumeration of Discord component types, each mapped to its integer type identifier.
     *
     * <p>
     * Certain types introduced in Components V2 require the {@link Message.Flag#IS_COMPONENTS_V2}
     * flag to be set on the message; these types have {@link #isRequireFlag()} returning
     * {@code true}.
     */
    @Getter
    @RequiredArgsConstructor
    enum Type {

        /** Unrecognized or unmapped component type. */
        UNKNOWN(-1),
        /** A row of interactive components. */
        ACTION_ROW(1),
        /** A clickable button. */
        BUTTON(2),
        /** A string-based select menu. */
        SELECT_MENU_STRING(3),
        /** A text input field for modals. */
        TEXT_INPUT(4),
        /** A user-based select menu. */
        SELECT_MENU_USER(5),
        /** A role-based select menu. */
        SELECT_MENU_ROLE(6),
        /** A mentionable-based select menu. */
        SELECT_MENU_MENTIONABLE(7),
        /** A channel-based select menu. */
        SELECT_MENU_CHANNEL(8),
        /** A section layout grouping text and an accessory (Components V2). */
        SECTION(9, true),
        /** A text display element (Components V2). */
        TEXT_DISPLAY(10, true),
        /** A small image displayed alongside a section (Components V2). */
        THUMBNAIL(11, true),
        /** A gallery of media items (Components V2). */
        MEDIA_GALLERY(12, true),
        /** A file reference (Components V2). */
        FILE(13, true),
        /** A visual separator between components (Components V2). */
        SEPARATOR(14, true),
        /** A container grouping multiple components (Components V2). */
        CONTAINER(17, true),
        /** A label wrapping an interactive component. */
        LABEL(18),
        /** A file upload component. */
        FILE_UPLOAD(19);

        /** The Discord integer type identifier. */
        private final int value;

        /** Whether this type requires {@link Message.Flag#IS_COMPONENTS_V2} on the message. */
        private final boolean requireFlag;

        Type(int value) {
            this(value, false);
        }

        /**
         * Resolves a {@link Type} from its Discord integer type identifier.
         *
         * @param value the integer type identifier
         * @return the matching type, or {@link #UNKNOWN} if no match is found
         */
        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(type -> type.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

}
