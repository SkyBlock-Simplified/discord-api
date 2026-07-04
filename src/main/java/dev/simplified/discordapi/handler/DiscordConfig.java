package dev.simplified.discordapi.handler;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.event.BotEvent;
import dev.simplified.discordapi.feature.extractor.ExtractorStore;
import dev.simplified.discordapi.feature.extractor.InMemoryExtractorStore;
import dev.simplified.discordapi.handler.response.EternalResponseRepository;
import dev.simplified.discordapi.handler.response.InMemoryEternalResponseRepository;
import dev.simplified.discordapi.listener.BotEventListener;
import dev.simplified.discordapi.listener.DiscordListener;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.reflection.info.ResourceInfo;
import dev.simplified.util.Logging;
import dev.simplified.yaml.annotation.Flag;
import discord4j.common.ReactorResources;
import discord4j.core.event.domain.Event;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.gateway.GatewayClient;
import discord4j.gateway.GatewayOptions;
import discord4j.gateway.ShardInfo;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

@Getter
@AllArgsConstructor
@SuppressWarnings("rawtypes")
public final class DiscordConfig {

    @Flag(secure = true)
    private final @NotNull String token;
    private final long mainGuildId;
    private final @NotNull Optional<Long> logChannelId;
    @Flag(secure = true)
    private final @NotNull Optional<String> sentryDsn;
    private final ConcurrentSet<Class<? extends DiscordListener>> listeners;
    private final ConcurrentSet<Class<? extends BotEventListener>> botEventListeners;
    private final ConcurrentSet<Class<DiscordCommand>> commands;
    private final ConcurrentSet<ResourceInfo> emojis;
    private final @NotNull AllowedMentions allowedMentions;
    private final @NotNull IntentSet intents;
    @Getter(AccessLevel.NONE)
    private final @NotNull Function<ShardInfo, ClientPresence> clientPresence;
    private final @NotNull MemberRequestFilter memberRequestFilter;
    private final @NotNull Logging.Level logLevel;
    private final @NotNull ExtractorStore extractorStore;
    private final @NotNull EternalResponseRepository eternalRepository;

    // Endpoint overrides (custom Discord-compatible endpoint / self-host / proxy / offline test harness)
    private final @NotNull Optional<String> apiBaseUrl;
    private final @NotNull Optional<ReactorResources> restReactorResources;
    private final @NotNull Optional<Function<GatewayOptions, GatewayClient>> gatewayClientFactory;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull ClientPresence getClientPresence(@NotNull ShardInfo shardInfo) {
        return this.clientPresence.apply(shardInfo);
    }

    public static class Builder {

        // Settings
        @BuildFlag(nonNull = true)
        private Optional<String> token = Optional.empty();
        @BuildFlag(nonNull = true)
        private Optional<Long> mainGuildId = Optional.empty();
        private Optional<Long> logChannelId = Optional.empty();
        @Flag(secure = true)
        private Optional<String> sentryDsn = Optional.empty();

        // Collections
        private ConcurrentSet<Class<? extends DiscordListener>> listeners = Concurrent.newSet();
        private ConcurrentSet<Class<? extends BotEventListener>> botEventListeners = Concurrent.newSet();
        private ConcurrentSet<Class<DiscordCommand>> commands = Concurrent.newSet();
        private ConcurrentSet<ResourceInfo> emojis = Concurrent.newSet();
        @BuildFlag(nonNull = true)
        private AllowedMentions allowedMentions = AllowedMentions.builder().build();
        @BuildFlag(nonNull = true)
        private IntentSet intents = IntentSet.nonPrivileged();
        @BuildFlag(nonNull = true)
        private Function<ShardInfo, ClientPresence> clientPresence = __ -> ClientPresence.online();
        @BuildFlag(nonNull = true)
        private MemberRequestFilter memberRequestFilter = MemberRequestFilter.all();
        @BuildFlag(nonNull = true)
        private Logging.Level logLevel = Logging.Level.WARN;
        @BuildFlag(nonNull = true)
        private ExtractorStore extractorStore = InMemoryExtractorStore.of();
        @BuildFlag(nonNull = true)
        private EternalResponseRepository eternalRepository = InMemoryEternalResponseRepository.of();

        // Endpoint overrides (default empty = stock Discord)
        private Optional<String> apiBaseUrl = Optional.empty();
        private Optional<ReactorResources> restReactorResources = Optional.empty();
        private Optional<Function<GatewayOptions, GatewayClient>> gatewayClientFactory = Optional.empty();

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
                    .getTypesOf(DiscordCommand.class)
            );
            return this;
        }

        public Builder withCommands(@NotNull Class<DiscordCommand>... commands) {
            this.commands.addAll(commands);
            return this;
        }

        public Builder withCommands(@NotNull Iterable<Class<DiscordCommand>> commands) {
            commands.forEach(this.commands::add);
            return this;
        }

        public Builder withEmojis(@NotNull ResourceInfo... emojis) {
            this.emojis.addAll(emojis);
            return this;
        }

        public Builder withEmojis(@NotNull Iterable<ResourceInfo> emojis) {
            emojis.forEach(this.emojis::add);
            return this;
        }

        public Builder withLogChannelId(long debugChannelId) {
            return this.withLogChannelId(Optional.of(debugChannelId));
        }

        public Builder withLogChannelId(Optional<Long> debugChannelId) {
            this.logChannelId = debugChannelId;
            return this;
        }

        public Builder withSentryDsn(@NotNull String sentryDsn) {
            return this.withSentryDsn(Optional.of(sentryDsn));
        }

        public Builder withSentryDsn(@NotNull Optional<String> sentryDsn) {
            this.sentryDsn = sentryDsn;
            return this;
        }

        public Builder withDisabledIntents(@NotNull IntentSet disabledIntents) {
            this.intents = IntentSet.all().andNot(disabledIntents);
            return this;
        }

        public Builder withEnabledIntents(@NotNull IntentSet enabledIntents) {
            this.intents = enabledIntents;
            return this;
        }

        public Builder withListeners(@NotNull String packagePath) {
            this.listeners.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getSubtypesOf(DiscordListener.class)
            );
            this.botEventListeners.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getSubtypesOf(BotEventListener.class)
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

        public Builder withBotEventListeners(@NotNull Class<? extends BotEventListener<? extends BotEvent>>... botEventListeners) {
            this.botEventListeners.addAll(botEventListeners);
            return this;
        }

        public Builder withBotEventListeners(@NotNull Iterable<Class<? extends BotEventListener<? extends BotEvent>>> botEventListeners) {
            botEventListeners.forEach(this.botEventListeners::add);
            return this;
        }

        public Builder withLogLevel(@NotNull Logging.Level logLevel) {
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

        /**
         * Sets the {@link ExtractorStore} backing the {@code /extractor} and {@code /extract}
         * slash commands. Defaults to {@link InMemoryExtractorStore} - bots that want
         * persistent extractors should plug a database-backed implementation.
         *
         * @param extractorStore the store implementation
         * @return this builder
         */
        public Builder withExtractorStore(@NotNull ExtractorStore extractorStore) {
            this.extractorStore = extractorStore;
            return this;
        }

        /**
         * Sets the {@link EternalResponseRepository} backing eternal (reboot-surviving) responses.
         * Defaults to {@link InMemoryEternalResponseRepository} - bots that want eternal messages to
         * survive restarts should plug a durable implementation (for example Hibernate).
         *
         * @param eternalRepository the store implementation
         * @return this builder
         */
        public Builder withEternalRepository(@NotNull EternalResponseRepository eternalRepository) {
            this.eternalRepository = eternalRepository;
            return this;
        }

        /**
         * Overrides the Discord REST API base url (defaults to the stock Discord endpoint).
         * <p>
         * Redirects all REST traffic - and, transitively, the gateway endpoint resolved from
         * {@code GET /gateway} - to a custom Discord-compatible endpoint such as a self-hosted server,
         * a proxy, or a local offline test server.
         *
         * @param apiBaseUrl the base url, e.g. {@code http://localhost:8080/api/v10}
         * @return this builder
         */
        public Builder withApiBaseUrl(@NotNull String apiBaseUrl) {
            this.apiBaseUrl = Optional.of(apiBaseUrl);
            return this;
        }

        /**
         * Supplies custom {@link ReactorResources} for the REST client, for example a non-secure
         * {@code HttpClient} when targeting a plaintext {@code http://} endpoint.
         *
         * @param restReactorResources the reactor resources
         * @return this builder
         */
        public Builder withRestReactorResources(@NotNull ReactorResources restReactorResources) {
            this.restReactorResources = Optional.of(restReactorResources);
            return this;
        }

        /**
         * Replaces the gateway client with a caller-supplied factory.
         * <p>
         * Advanced hook used by the offline test harness to inject a fake in-JVM {@link GatewayClient}
         * in place of a live gateway connection.
         *
         * @param gatewayClientFactory the factory producing a gateway client from the resolved options
         * @return this builder
         */
        public Builder withGatewayClientFactory(@NotNull Function<GatewayOptions, GatewayClient> gatewayClientFactory) {
            this.gatewayClientFactory = Optional.of(gatewayClientFactory);
            return this;
        }

        public @NotNull DiscordConfig build() {
            Reflection.validateFlags(this);

            return new DiscordConfig(
                this.token.orElseThrow(),
                this.mainGuildId.orElseThrow(),
                this.logChannelId,
                this.sentryDsn,
                this.listeners.toUnmodifiable(),
                this.botEventListeners.toUnmodifiable(),
                this.commands.toUnmodifiable(),
                this.emojis.toUnmodifiable(),
                this.allowedMentions,
                this.intents,
                this.clientPresence,
                this.memberRequestFilter,
                this.logLevel,
                this.extractorStore,
                this.eternalRepository,
                this.apiBaseUrl,
                this.restReactorResources,
                this.gatewayClientFactory
            );
        }

    }

}
