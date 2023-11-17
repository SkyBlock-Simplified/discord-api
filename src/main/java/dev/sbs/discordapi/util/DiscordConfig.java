package dev.sbs.discordapi.util;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.yaml.YamlConfig;
import dev.sbs.api.data.yaml.annotation.Secure;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.ResourceUtil;
import dev.sbs.api.util.helper.StringUtil;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;

@Getter
public abstract class DiscordConfig extends YamlConfig {

    @Secure
    @Setter private @NotNull Optional<String> discordToken = ResourceUtil.getEnv("DISCORD_TOKEN");
    @Setter private long mainGuildId = ResourceUtil.getEnv("DISCORD_MAIN_GUILD_ID").map(NumberUtil::tryParseLong).orElse(-1L);
    @Setter private @NotNull String defaultUnicodeEmoji = ResourceUtil.getEnv("DEFAULT_UNICODE_EMOJI").orElse(StringUtil.unescapeUnicode("\\u2699"));
    @Setter private long debugChannelId = ResourceUtil.getEnv("DEVELOPER_ERROR_LOG_CHANNEL_ID").map(NumberUtil::tryParseLong).orElse(0L);

    public DiscordConfig(@NotNull String fileName, @NotNull String... header) {
        this(fileName, SimplifiedApi.getCurrentDirectory(), header);
    }

    public DiscordConfig(@NotNull String fileName, @NotNull File configDir, @NotNull String... header) {
        super(fileName, configDir, header);
    }

    public final ReactionEmoji getDefaultCommandEmoji() {
        return ReactionEmoji.of(null, this.getDefaultUnicodeEmoji(), false);
    }

}
