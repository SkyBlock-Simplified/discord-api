package dev.sbs.discordapi.command;

import dev.sbs.discordapi.response.Emoji;
import lombok.Getter;

import java.util.Optional;

public enum Category {

    UNCATEGORIZED("Uncategorized", "Uncategorized Commands"),
    DEVELOPER("Developer", "Developer Commands"),
    CONFIG("Config", "Config Commands"),
    PLAYER("Player", "Player Commands"),
    ADMINISTRATOR("Administrator", "Admin Commands"),
    STAFF("Staff", "Staff Commands"),
    REPUTATION("Reputation", "Reputation Commands"),
    HYPIXEL_REPORTS("Hypixel Reports", "Hypixel Report Commands"),
    HYPIXEL_VERIFICATION("Hypixel Verification", "Hypixel Verification Commands");

    @Getter private final String name;
    @Getter private final String description;
    @Getter private final Optional<Emoji> emoji;

    Category(String name, String description) {
        this(name, description, null);
    }

    Category(String name, String description, Emoji emoji) {
        this.name = name;
        this.description = description;
        this.emoji = Optional.ofNullable(emoji);
    }

}
