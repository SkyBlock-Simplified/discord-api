package dev.sbs.discordapi.util;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.sql.SqlConfig;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.ResourceUtil;
import dev.sbs.api.util.helper.StringUtil;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;

public class DiscordConfig extends SqlConfig {

    @Getter @Setter
    private Optional<String> discordToken = ResourceUtil.getEnv("DISCORD_TOKEN");

    @Getter @Setter
    private String defaultUnicodeEmoji = ResourceUtil.getEnv("DEFAULT_UNICODE_EMOJI").orElse(StringUtil.unescapeJava("\\u2699"));

    @Getter @Setter
    private long mainGuildId = ResourceUtil.getEnv("DISCORD_MAIN_GUILD_ID").map(NumberUtil::tryParseLong).orElse(-1L);

    public DiscordConfig(@NotNull String fileName, @NotNull String... header) {
        this(SimplifiedApi.getCurrentDirectory(), fileName, header);
    }

    public DiscordConfig(@NotNull File configDir, @NotNull String fileName, @NotNull String... header) {
        super(configDir, fileName, header);
    }

    public final ReactionEmoji getDefaultCommandEmoji() {
        return ReactionEmoji.of(null, this.getDefaultUnicodeEmoji(), false);
    }

}
