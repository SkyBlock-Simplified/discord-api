package dev.sbs.discordapi.command.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public enum TypeContext {

    UNKNOWN(-1),
    /**
     * Slash commands; a text-based command that shows up when a user types /
     */
    CHAT_INPUT(1),
    /**
     * A UI-based command that shows up when you right click or tap on a user
     */
    USER(2),
    /**
     * A UI-based command that shows up when you right click or tap on a message
     */
    MESSAGE(3),
    /**
     * A UI-based command that represents the primary way to invoke an app's Activity
     */
    PRIMARY_ENTRY_POINT(4);

    /**
     * The underlying value as represented by Discord.
     */
    private final int value;

    public static @NotNull TypeContext of(final int value) {
        return switch (value) {
            case 1 -> CHAT_INPUT;
            case 2 -> USER;
            case 3 -> MESSAGE;
            case 4 -> PRIMARY_ENTRY_POINT;
            default -> UNKNOWN;
        };
    }

}
