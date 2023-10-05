package dev.sbs.discordapi.util.base;

import dev.sbs.discordapi.DiscordBot;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class DiscordHelper extends DiscordReference {

    private final @NotNull DiscordBot discordBot;
    private final @NotNull Logger log;

    protected DiscordHelper(@NotNull DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.log = LogManager.getLogger(this);
    }

}
