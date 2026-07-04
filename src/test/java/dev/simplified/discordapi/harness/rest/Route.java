package dev.simplified.discordapi.harness.rest;

import dev.simplified.discordapi.harness.json.HarnessEntities;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * The declarative REST route table for {@link LocalDiscordServer}: one constant per faked endpoint, pairing an
 * HTTP method and a path-matching regex with how the mock should respond. {@code start()} loads every route
 * from here, keeping the endpoint list in one readable place as the harness grows.
 *
 * <p>
 * Path patterns are matched against the query-stripped request path; a Discord {@code {snowflake}} path
 * parameter is written as {@code [^/]+} (one segment), or {@code \\d+} where the original endpoint was numeric.
 * The routes are registered in declaration order, ahead of {@link LocalDiscordServer}'s catch-all.
 */
enum Route {

    SELF_USER("GET", "/users/@me", Kind.JSON, HarnessEntities::userJson),
    GATEWAY("GET", "/gateway", Kind.JSON, HarnessEntities::gatewayJson),
    GATEWAY_BOT("GET", "/gateway/bot", Kind.JSON, HarnessEntities::gatewayBotJson),
    APPLICATION_INFO("GET", "/oauth2/applications/@me", Kind.JSON, HarnessEntities::applicationInfoJson),
    EMOJIS("GET", "/applications/[^/]+/emojis", Kind.JSON, HarnessEntities::emptyEmojisJson),
    GUILD("GET", "/guilds/[^/]+", Kind.JSON, HarnessEntities::guildJson),
    GLOBAL_COMMANDS("PUT", "/applications/[^/]+/commands", Kind.ECHO, null),
    GUILD_COMMANDS("PUT", "/applications/[^/]+/guilds/[^/]+/commands", Kind.ECHO, null),
    INTERACTION_CALLBACK("POST", "/interactions/[^/]+/[^/]+/callback", Kind.NO_CONTENT, null),
    WEBHOOK_MESSAGE("POST", "/webhooks/[^/]+/[^/]+", Kind.JSON, HarnessEntities::messageJson),
    CHANNEL_MESSAGE("POST", "/channels/[^/]+/messages", Kind.JSON, HarnessEntities::messageJson),
    EDIT_CHANNEL_MESSAGE("PATCH", "/channels/\\d+/messages/\\d+", Kind.JSON, HarnessEntities::messageJson),
    EDIT_ORIGINAL_MESSAGE("PATCH", ".*/messages/@original", Kind.JSON, HarnessEntities::messageJson),

    // Followup messages (webhook + interaction token)
    EDIT_FOLLOWUP("PATCH", "/webhooks/[^/]+/[^/]+/messages/\\d+", Kind.JSON, HarnessEntities::messageJson),
    DELETE_FOLLOWUP("DELETE", "/webhooks/[^/]+/[^/]+/messages/[^/]+", Kind.NO_CONTENT, null),

    // Channel and message fetch/delete
    GET_CHANNEL("GET", "/channels/\\d+", Kind.JSON, HarnessEntities::channelJson),
    GET_CHANNEL_MESSAGE("GET", "/channels/\\d+/messages/\\d+", Kind.JSON, HarnessEntities::messageJson),
    DELETE_CHANNEL_MESSAGE("DELETE", "/channels/\\d+/messages/\\d+", Kind.NO_CONTENT, null),

    // Reactions
    GET_REACTIONS("GET", "/channels/\\d+/messages/\\d+/reactions/[^/]+", Kind.JSON, HarnessEntities::reactionUsersJson),
    ADD_SELF_REACTION("PUT", "/channels/\\d+/messages/\\d+/reactions/[^/]+/@me", Kind.NO_CONTENT, null),
    DELETE_SELF_REACTION("DELETE", "/channels/\\d+/messages/\\d+/reactions/[^/]+/@me", Kind.NO_CONTENT, null),
    DELETE_USER_REACTION("DELETE", "/channels/\\d+/messages/\\d+/reactions/[^/]+/\\d+", Kind.NO_CONTENT, null),
    DELETE_EMOJI_REACTIONS("DELETE", "/channels/\\d+/messages/\\d+/reactions/[^/]+", Kind.NO_CONTENT, null),

    // Application emojis
    CREATE_EMOJI("POST", "/applications/[^/]+/emojis", Kind.JSON, HarnessEntities::emojiJson);

    /** How a matched route produces its response. */
    enum Kind {

        /** Serialize the route's entity body with a {@code 200}. */
        JSON,

        /** Echo a command bulk-overwrite payload with synthetic ids ({@code 200}). */
        ECHO,

        /** Acknowledge with a {@code 204} and no body. */
        NO_CONTENT

    }

    private final String method;
    private final String pathRegex;
    private final Kind kind;
    private final @Nullable Function<HarnessEntities, String> body;

    Route(@NotNull String method, @NotNull @Language("RegExp") String pathRegex, @NotNull Kind kind, @Nullable Function<HarnessEntities, String> body) {
        this.method = method;
        this.pathRegex = pathRegex;
        this.kind = kind;
        this.body = body;
    }

    @NotNull Kind kind() {
        return this.kind;
    }

    /**
     * Whether the given request method and query-stripped path match this route.
     *
     * @param method the request method (e.g. {@code GET})
     * @param path the query-stripped request path
     * @return whether this route handles the request
     */
    boolean matches(@NotNull String method, @NotNull String path) {
        return this.method.equals(method) && path.matches(this.pathRegex);
    }

    /**
     * Renders this route's JSON body from the shared entities.
     *
     * @param entities the shared entity factory
     * @return the serialized body, or {@code null} for a route with no entity body
     */
    @Nullable String body(@NotNull HarnessEntities entities) {
        return this.body == null ? null : this.body.apply(entities);
    }

}
