package dev.sbs.discordapi.util;

import dev.sbs.api.data.yaml.YamlConfig;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.ResourceUtil;
import dev.sbs.api.util.helper.StringUtil;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.Getter;
import lombok.Setter;

import java.io.File;

public class DiscordConfig extends YamlConfig {

    @Getter @Setter
    private String discordToken = ResourceUtil.getEnv("DISCORD_TOKEN").orElse("");

    @Getter @Setter
    private String defaultUnicodeEmoji = ResourceUtil.getEnv("DEFAULT_UNICODE_EMOJI").orElse(StringUtil.unescapeJava("\\u2699"));

    @Getter @Setter
    private long mainGuildId = ResourceUtil.getEnv("DISCORD_MAIN_GUILD_ID").map(NumberUtil::tryParseLong).orElse(-1L);

    public DiscordConfig(File configDir, String fileName, String... header) {
        super(configDir, fileName, header);
    }

    public final ReactionEmoji getDefaultCommandEmoji() {
        return ReactionEmoji.of(null, this.getDefaultUnicodeEmoji(), false);
    }

}
