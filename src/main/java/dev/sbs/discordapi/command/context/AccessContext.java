package dev.sbs.discordapi.command.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum AccessContext {

    UNKNOWN(-1),
    /**
     * Interaction can be used within servers
     */
    GUILD(0),
    /**
     * Interaction can be used within DMs with the app's bot user
     */
    DIRECT_MESSAGE(1),
    /**
     * Interaction can be used within Group DMs and DMs other than the app's bot user
     */
    PRIVATE_CHANNEL(2);

    /**
     * The underlying value as represented by Discord.
     */
    private final int value;

    public static @NotNull AccessContext of(final int value) {
        return switch (value) {
            case 0 -> GUILD;
            case 1 -> DIRECT_MESSAGE;
            case 2 -> PRIVATE_CHANNEL;
            default -> UNKNOWN;
        };
    }

    public static @NotNull Integer[] intValues(@NotNull AccessContext[] contexts) {
        return Arrays.stream(contexts).map(AccessContext::getValue).toArray(Integer[]::new);
    }

}
