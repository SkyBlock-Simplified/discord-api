package dev.sbs.discordapi.util;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.DataConfig;
import dev.sbs.api.data.yaml.YamlConfig;
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

    @Setter private @NotNull Optional<String> discordToken = ResourceUtil.getEnv("DISCORD_TOKEN");
    @Setter private long mainGuildId = ResourceUtil.getEnv("DISCORD_MAIN_GUILD_ID").map(NumberUtil::tryParseLong).orElse(-1L);
    @Setter private @NotNull String defaultUnicodeEmoji = ResourceUtil.getEnv("DEFAULT_UNICODE_EMOJI").orElse(StringUtil.unescapeUnicode("\\u2699"));
    @Setter private DataConfig<?> dataConfig;

    public DiscordConfig(@NotNull DataConfig<?> dataConfig, @NotNull String fileName, @NotNull String... header) {
        this(dataConfig, SimplifiedApi.getCurrentDirectory(), fileName, header);
    }

    public DiscordConfig(@NotNull DataConfig<?> dataConfig, @NotNull File configDir, @NotNull String fileName, @NotNull String... header) {
        super(configDir, fileName, header);
        this.dataConfig = dataConfig;
    }

    public final ReactionEmoji getDefaultCommandEmoji() {
        return ReactionEmoji.of(null, this.getDefaultUnicodeEmoji(), false);
    }

}
