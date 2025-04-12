package dev.sbs.discordapi.command.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum InstallContext {

    UNKNOWN(-1),
    /**
     * Installable to servers
     */
    GUILD(0),
    /**
     * Installable to users
     */
    USER(1);

    /**
     * The underlying value as represented by Discord.
     */
    private final int value;

    public static @NotNull InstallContext of(final int value) {
        return switch (value) {
            case 0 -> GUILD;
            case 1 -> USER;
            default -> UNKNOWN;
        };
    }

    public static @NotNull Integer[] intValues(@NotNull InstallContext[] contexts) {
        return Arrays.stream(contexts).map(InstallContext::getValue).toArray(Integer[]::new);
    }

}
