package dev.sbs.discordapi.util;

import dev.sbs.api.util.date.RealDate;

public class DiscordDate extends RealDate {

    public DiscordDate(String duration) {
        super(duration);
    }

    public DiscordDate(long realTime) {
        super(realTime);
    }

}
