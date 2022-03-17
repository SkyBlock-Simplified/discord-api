package dev.sbs.discordapi.util.base;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.DiscordLogger;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public abstract class DiscordHelper extends DiscordReference {

    @Getter private final DiscordBot discordBot;
    @Getter private final DiscordLogger log;

    protected DiscordHelper(@NotNull DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.log = new DiscordLogger(this.getDiscordBot(), this.getClass());
    }

}
