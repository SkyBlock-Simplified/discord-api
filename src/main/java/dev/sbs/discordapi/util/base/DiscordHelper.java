package dev.sbs.discordapi.util.base;

import dev.sbs.discordapi.DiscordBot;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@Getter
@Log4j2
public abstract class DiscordHelper extends DiscordReference {

    private final @NotNull DiscordBot discordBot;

    protected DiscordHelper(@NotNull DiscordBot discordBot) {
        this.discordBot = discordBot;
    }

    public final @NotNull Logger getLog() {
        return log;
    }

}
