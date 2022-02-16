package dev.sbs.discordapi.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.CommandData;
import dev.sbs.discordapi.util.DiscordObject;

public class PrefixCommand extends DiscordObject implements CommandData {

    protected PrefixCommand(DiscordBot discordBot) {
        super(discordBot);
    }

}
