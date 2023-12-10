package dev.sbs.discordapi.util;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.DataConfig;
import dev.sbs.api.data.model.Model;
import dev.sbs.api.data.yaml.YamlConfig;
import dev.sbs.api.data.yaml.annotation.Flag;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Emoji;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.gateway.ShardInfo;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
@Setter
@SuppressWarnings("rawtypes")
public final class DiscordConfig extends YamlConfig {

    @Getter
    private static @NotNull Optional<Function<String, Optional<Emoji>>> emojiLocator;

    @Flag(secure = true)
    private @NotNull String token;
    private long mainGuildId;
    private @NotNull Optional<Long> debugChannelId;
    private @NotNull Optional<Class<? extends Model>> dataModel;
    private @NotNull Optional<DataConfig<? extends Model>> dataConfig;
    private ConcurrentSet<Class<? extends DiscordListener>> listeners;
    private ConcurrentSet<Class<? extends CommandReference>> commands;
    private @NotNull AllowedMentions allowedMentions;
    private @NotNull IntentSet intents;
    @Getter(AccessLevel.NONE)
    private @NotNull Function<ShardInfo, ClientPresence> clientPresence;
    private @NotNull MemberRequestFilter memberRequestFilter;
    private @NotNull Level logLevel;
    private Optional<Runnable> databaseConnectedEvent;
    private Optional<Consumer<GatewayDiscordClient>> gatewayConnectedEvent;
    private Optional<Runnable> gatewayDisconnectedEvent;

    private DiscordConfig(
        @NotNull String fileName,
        @NotNull File configDir,
        @NotNull ConcurrentList<String> header,
        @NotNull String token,
        long mainGuildId,
        @NotNull Optional<Long> debugChannelId,
        @NotNull Optional<DataConfig<? extends Model>> dataConfig,
        @NotNull Optional<Function<String, Optional<Emoji>>> emojiLocator,
        @NotNull ConcurrentSet<Class<? extends DiscordListener>> listeners,
        @NotNull ConcurrentSet<Class<? extends CommandReference>> commands,
        @NotNull AllowedMentions allowedMentions,
        @NotNull IntentSet intents,
        @NotNull Function<ShardInfo, ClientPresence> clientPresence,
        @NotNull MemberRequestFilter memberRequestFilter,
        @NotNull Level logLevel,
        @NotNull Optional<Runnable> databaseConnectedEvent,
        @NotNull Optional<Consumer<GatewayDiscordClient>> gatewayConnectedEvent,
        @NotNull Optional<Runnable> gatewayDisconnectedEvent
    ) {
        super(fileName, configDir, header);
        this.token = token;
        this.mainGuildId = mainGuildId;
        this.debugChannelId = debugChannelId;
        this.dataConfig = dataConfig;
        DiscordConfig.emojiLocator = emojiLocator;
        this.listeners = listeners.toUnmodifiableSet();
        this.commands = commands.toUnmodifiableSet();
        this.allowedMentions = allowedMentions;
        this.intents = intents;
        this.clientPresence = clientPresence;
        this.memberRequestFilter = memberRequestFilter;
        this.logLevel = logLevel;
        this.databaseConnectedEvent = databaseConnectedEvent;
        this.gatewayConnectedEvent = gatewayConnectedEvent;
        this.gatewayDisconnectedEvent = gatewayDisconnectedEvent;
    }

    public @NotNull DiscordBot createBot() {
        return this.createBot(DiscordBot::new);
    }

    public <T extends DiscordBot> @NotNull T createBot(@NotNull Function<DiscordConfig, T> bot) {
        return bot.apply(this);
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull ClientPresence getClientPresence(@NotNull ShardInfo shardInfo) {
        return this.clientPresence.apply(shardInfo);
    }

    public static class Builder implements dev.sbs.api.util.builder.Builder<DiscordConfig> {

        // Config File
        @BuildFlag(required = true)
        private String fileName;
        @BuildFlag(required = true)
        private File directory = SimplifiedApi.getCurrentDirectory();
        private ConcurrentList<String> header = Concurrent.newList();

        // Settings
        @BuildFlag(required = true)
        private Optional<String> token = Optional.empty();
        @BuildFlag(required = true)
        private Optional<Long> mainGuildId = Optional.empty();
        private Optional<Long> debugChannelId = Optional.empty();
        private Optional<Class<? extends Model>> dataModel = Optional.empty();
        private Optional<DataConfig<? extends Model>> dataConfig = Optional.empty();
        private Optional<Function<String, Optional<Emoji>>> emojiLocator = Optional.empty();

        // Collections
        private ConcurrentSet<Class<? extends DiscordListener>> listeners = Concurrent.newSet();
        private ConcurrentSet<Class<? extends CommandReference>> commands = Concurrent.newSet();
        @BuildFlag(required = true)
        private AllowedMentions allowedMentions = AllowedMentions.builder().build();
        @BuildFlag(required = true)
        private IntentSet intents = IntentSet.nonPrivileged();
        @BuildFlag(required = true)
        private Function<ShardInfo, ClientPresence> clientPresence = __ -> ClientPresence.online();
        @BuildFlag(required = true)
        private MemberRequestFilter memberRequestFilter = MemberRequestFilter.all();
        @BuildFlag(required = true)
        private Level logLevel = Level.WARN;

        // Events
        private Optional<Runnable> databaseConnectedEvent = Optional.empty();
        private Optional<Consumer<GatewayDiscordClient>> gatewayConnectedEvent = Optional.empty();
        private Optional<Runnable> gatewayDisconnectedEvent = Optional.empty();

        public Builder onDatabaseConnected(@Nullable Runnable databaseConnectedEvent) {
            return this.onDatabaseConnected(Optional.ofNullable(databaseConnectedEvent));
        }

        public Builder onDatabaseConnected(@NotNull Optional<Runnable> databaseConnectedEvent) {
            this.databaseConnectedEvent = databaseConnectedEvent;
            return this;
        }

        public Builder onGatewayConnected(@Nullable Consumer<GatewayDiscordClient> gatewayConnectedEvent) {
            return this.onGatewayConnected(Optional.ofNullable(gatewayConnectedEvent));
        }

        public Builder onGatewayConnected(@NotNull Optional<Consumer<GatewayDiscordClient>> gatewayConnectedEvent) {
            this.gatewayConnectedEvent = gatewayConnectedEvent;
            return this;
        }

        public Builder onGatewayDisconnected(@Nullable Runnable gatewayConnectedEvent) {
            return this.onGatewayDisconnected(Optional.ofNullable(gatewayConnectedEvent));
        }

        public Builder onGatewayDisconnected(@NotNull Optional<Runnable> gatewayConnectedEvent) {
            this.gatewayDisconnectedEvent = gatewayConnectedEvent;
            return this;
        }

        public Builder withAllowedMentions(@NotNull AllowedMentions allowedMentions) {
            this.allowedMentions = allowedMentions;
            return this;
        }

        public Builder withClientPresence(@NotNull ClientPresence clientPresence) {
            return this.withClientPresence(__ -> clientPresence);
        }

        public Builder withClientPresence(@NotNull Function<ShardInfo, ClientPresence> clientPresence) {
            this.clientPresence = clientPresence;
            return this;
        }

        public Builder withCommands(@NotNull String packagePath) {
            this.commands.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getSubtypesOf(CommandReference.class)
            );
            return this;
        }

        public Builder withCommands(@NotNull Class<? extends CommandReference<?>>... commands) {
            this.commands.addAll(commands);
            return this;
        }

        public Builder withCommands(@NotNull Iterable<Class<CommandReference>> commands) {
            commands.forEach(this.commands::add);
            return this;
        }

        public Builder withDataConfig(@Nullable DataConfig<? extends Model> dataConfig) {
            return this.withDataConfig(Optional.ofNullable(dataConfig));
        }

        public Builder withDataConfig(@NotNull Optional<DataConfig<? extends Model>> dataConfig) {
            this.dataConfig = dataConfig;
            return this;
        }

        public Builder withDebugChannelId(long debugChannelId) {
            return this.withDebugChannelId(Optional.of(debugChannelId));
        }

        public Builder withDebugChannelId(Optional<Long> debugChannelId) {
            this.debugChannelId = debugChannelId;
            return this;
        }

        public Builder withDirectory(@NotNull File directory) {
            this.directory = directory;
            return this;
        }

        public Builder withDisabledIntents(@NotNull IntentSet disabledIntents) {
            this.intents = IntentSet.all().andNot(disabledIntents);
            return this;
        }

        public Builder withEmojiLocator(@Nullable Function<String, Optional<Emoji>> emojiLocator) {
            return this.withEmojiLocator(Optional.ofNullable(emojiLocator));
        }

        public Builder withEmojiLocator(@NotNull Optional<Function<String, Optional<Emoji>>> emojiLocator) {
            this.emojiLocator = emojiLocator;
            return this;
        }

        public Builder withEnabledIntents(@NotNull IntentSet enabledIntents) {
            this.intents = enabledIntents;
            return this;
        }

        public Builder withFileName(@NotNull String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder withHeader(@NotNull String... header) {
            this.header.addAll(header);
            return this;
        }

        public Builder withHeader(@NotNull Iterable<String> header) {
            header.forEach(this.header::add);
            return this;
        }

        public Builder withListeners(@NotNull String packagePath) {
            this.listeners.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getSubtypesOf(DiscordListener.class)
            );
            return this;
        }

        public Builder withListeners(@NotNull Class<? extends DiscordListener<? extends Event>>... listeners) {
            this.listeners.addAll(listeners);
            return this;
        }

        public Builder withListeners(@NotNull Iterable<Class<? extends DiscordListener<? extends Event>>> listeners) {
            listeners.forEach(this.listeners::add);
            return this;
        }

        public Builder withLogLevel(@NotNull Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder withMainGuildId(long mainGuildId) {
            return this.withMainGuildId(Optional.of(mainGuildId));
        }

        public Builder withMainGuildId(@NotNull Optional<Long> mainGuildId) {
            this.mainGuildId = mainGuildId;
            return this;
        }

        public Builder withMemberRequestFilter(@NotNull MemberRequestFilter memberRequestFilter) {
            this.memberRequestFilter = memberRequestFilter;
            return this;
        }

        public Builder withToken(@NotNull String token) {
            return this.withToken(Optional.of(token));
        }

        public Builder withToken(@NotNull Optional<String> token) {
            this.token = token;
            return this;
        }

        @Override
        public @NotNull DiscordConfig build() {
            Reflection.validateFlags(this);

            return new DiscordConfig(
                this.fileName,
                this.directory,
                this.header,
                this.token.orElseThrow(),
                this.mainGuildId.orElseThrow(),
                this.debugChannelId,
                this.dataConfig,
                this.emojiLocator,
                this.listeners,
                this.commands,
                this.allowedMentions,
                this.intents,
                this.clientPresence,
                this.memberRequestFilter,
                this.logLevel,
                this.databaseConnectedEvent,
                this.gatewayConnectedEvent,
                this.gatewayDisconnectedEvent
            );
        }

    }

}
