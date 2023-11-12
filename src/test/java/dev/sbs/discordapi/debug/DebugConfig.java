package dev.sbs.discordapi.debug;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.util.helper.ResourceUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.util.DiscordConfig;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

public class DebugConfig extends DiscordConfig {

    private final Optional<UUID> hypixelApiKey = ResourceUtil.getEnv("HYPIXEL_API_KEY").map(StringUtil::toUUID);

    public DebugConfig(@NotNull File configDir, @NotNull String fileName, String... header) {
        super(null, configDir, fileName, header);
        this.hypixelApiKey.ifPresent(value -> SimplifiedApi.getKeyManager().add("HYPIXEL_API_KEY", value));
    }

    public Optional<UUID> getHypixelApiKey() {
        return this.hypixelApiKey;
    }

}
