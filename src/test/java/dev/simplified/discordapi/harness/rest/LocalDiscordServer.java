package dev.simplified.discordapi.harness.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.simplified.discordapi.harness.HarnessConfig;
import discord4j.common.JacksonResources;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.GuildUpdateData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.SessionStartLimitData;
import discord4j.discordjson.json.UserData;
import discord4j.discordjson.possible.Possible;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal localhost stand-in for the Discord REST API, backed by reactor-netty. Serves just enough
 * endpoints for the framework's login prologue and post-connect sync, echoes bulk command overwrites
 * with synthetic ids, and records every request for assertions.
 */
@Log4j2
@RequiredArgsConstructor
public final class LocalDiscordServer {

    // Empty list container returned by GET application emojis; not a Discord entity, so kept as a literal.
    private static final String EMPTY_EMOJIS_JSON = "{\"items\":[]}";

    // Matches an interaction-callback path, capturing the interaction token in group 1.
    private static final Pattern CALLBACK_PATTERN = Pattern.compile("/interactions/\\d+/([^/]+)/callback");

    // Discord4J's own mapper, so the discord-json immutables below serialize exactly as the bot expects.
    private final ObjectMapper mapper = JacksonResources.create().getObjectMapper();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    // Interaction tokens already acknowledged (via a callback); a repeat is the "already acknowledged" bug.
    private final Set<String> acknowledgedTokens = ConcurrentHashMap.newKeySet();
    private final HarnessConfig config;
    private DisposableServer server;

    /**
     * Binds the server on an ephemeral loopback port.
     *
     * @return this server
     */
    public @NotNull LocalDiscordServer start() {
        this.server = HttpServer.create()
            .host(this.config.getGatewayHost())
            .port(0)
            .route(routes -> {
                routes.get("/users/@me", fixed(userJson()));
                routes.get("/gateway", fixed(gatewayJson()));
                routes.get("/gateway/bot", fixed(gatewayBotJson()));
                routes.get("/oauth2/applications/@me", fixed(applicationInfoJson()));
                routes.get("/applications/{app}/emojis", fixed(EMPTY_EMOJIS_JSON));
                routes.get("/guilds/{guild}", fixed(guildJson()));
                routes.put("/applications/{app}/commands", commandEcho());
                routes.put("/applications/{app}/guilds/{guild}/commands", commandEcho());
                routes.post("/interactions/{id}/{token}/callback", noContent());
                routes.post("/webhooks/{app}/{token}", fixed(messageJson()));
                routes.post("/channels/{channel}/messages", fixed(messageJson()));
                routes.route(
                    request -> "PATCH".equals(request.method().name()) && stripQuery(request.uri()).matches("/channels/\\d+/messages/\\d+"),
                    fixed(messageJson())
                );
                routes.route(
                    request -> "PATCH".equals(request.method().name()) && stripQuery(request.uri()).endsWith("/messages/@original"),
                    fixed(messageJson())
                );
                routes.route(request -> true, catchAll());
            })
            .bindNow();
        log.debug("REST mock bound at {}", this.baseUrl());
        return this;
    }

    /**
     * The base url to hand to {@code DiscordConfig.withApiBaseUrl}. Deliberately plaintext {@code http} -
     * this is an in-process loopback stand-in for the REST API and the bot is pointed at it with a
     * plaintext (non-{@code .secure()}) client, so https would fail the handshake.
     *
     * @return the loopback base url
     */
    @SuppressWarnings("HttpUrlsUsage") // plaintext loopback mock, never a real endpoint
    public @NotNull String baseUrl() {
        return "http://" + this.config.getGatewayHost() + ":" + this.server.port();
    }

    public int port() {
        return this.server.port();
    }

    /**
     * The requests captured so far, in arrival order.
     *
     * @return the recorded requests
     */
    public @NotNull List<RecordedRequest> requests() {
        return this.requests;
    }

    public void stop() {
        if (this.server != null) {
            log.debug("Stopping REST mock at {}", this.baseUrl());
            this.server.disposeNow();
        }
    }

    private BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> fixed(String body) {
        return (request, response) -> respond(request, response, 200, ignored -> body);
    }

    private BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> commandEcho() {
        return (request, response) -> respond(request, response, 200, this::echoCommands);
    }

    private BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> noContent() {
        return (request, response) -> respond(request, response, 204, null);
    }

    private BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> catchAll() {
        return (request, response) -> respond(request, response, 200, ignored -> {
            log.warn(
                "No mock route for {} {} - returning an empty JSON object; the bot hit a REST endpoint the harness does not model",
                request.method().name(),
                stripQuery(request.uri())
            );
            return "{}";
        });
    }

    private Publisher<Void> respond(HttpServerRequest request, HttpServerResponse response, int status, Function<String, String> responder) {
        return request.receive()
            .aggregate()
            .asString()
            .defaultIfEmpty("")
            .flatMap(body -> {
                String method = request.method().name();
                String path = stripQuery(request.uri());
                this.requests.add(new RecordedRequest(method, path, body));

                // -Dharness.debug=true elevates the per-request firehose to INFO; otherwise it stays at DEBUG.
                Level level = this.config.isDebug() ? Level.INFO : Level.DEBUG;
                log.log(level, "[mock] {} {}{}", method, path, body.isEmpty() ? "" : " " + body);

                this.warnOnDoubleAcknowledge(method, path);

                if (responder == null)
                    return Mono.from(response.status(status).send()).then();

                String out;
                try {
                    out = responder.apply(body);
                } catch (Exception exception) {
                    log.warn("Mock responder threw for {} {}; returning an empty JSON object", method, path, exception);
                    out = "{}";
                }

                return Mono.from(response.header("Content-Type", "application/json").status(status).sendString(Mono.just(out))).then();
            });
    }

    /**
     * Warns when an interaction token is acknowledged more than once. Discord permits exactly one callback
     * per interaction and rejects a second with "interaction has already been acknowledged" (error 40060);
     * surfacing it here catches the double-acknowledge class of bug this harness has hunted before.
     */
    private void warnOnDoubleAcknowledge(@NotNull String method, @NotNull String path) {
        if (!"POST".equals(method) || !path.endsWith("/callback"))
            return;

        Matcher matcher = CALLBACK_PATTERN.matcher(path);

        if (matcher.matches() && !this.acknowledgedTokens.add(matcher.group(1)))
            log.warn("Interaction '{}' acknowledged more than once - Discord rejects this as already acknowledged (40060)", matcher.group(1));
    }

    private String echoCommands(String body) {
        try {
            if (body == null || body.isBlank())
                return "[]";

            JsonNode parsed = this.mapper.readTree(body);
            ArrayNode out = this.mapper.createArrayNode();

            if (parsed.isArray()) {
                for (JsonNode command : parsed) {
                    ObjectNode node = command.deepCopy();
                    String name = node.has("name") ? node.get("name").asText() : "unknown";
                    long id = this.config.commandId(name);
                    node.put("id", Long.toString(id));
                    node.put("application_id", Long.toString(this.config.getBotId()));
                    node.put("version", Long.toString(id));

                    if (!node.has("type"))
                        node.put("type", 1);

                    out.add(node);
                }
            }

            return this.mapper.writeValueAsString(out);
        } catch (Exception exception) {
            log.warn("Failed to echo command bulk-overwrite payload; returning an empty array", exception);
            return "[]";
        }
    }

    private static String stripQuery(String uri) {
        int index = uri.indexOf('?');
        return index < 0 ? uri : uri.substring(0, index);
    }

    /**
     * The current time as an ISO-8601 extended offset date-time (the format Discord sends for message
     * timestamps), so each served message carries a real datetime rather than a fixed placeholder.
     */
    private static String messageTimestamp() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Serializes a discord-json immutable with Discord4J's mapper into the response body. */
    private String write(@NotNull Object data) {
        try {
            return this.mapper.writeValueAsString(data);
        } catch (Exception exception) {
            log.error("Failed to serialize {} for a mock response", data.getClass().getSimpleName(), exception);
            throw new IllegalStateException("Failed to serialize " + data.getClass().getSimpleName(), exception);
        }
    }

    /** The bot's self user, reused both as {@code GET /users/@me} and as the author of served messages. */
    @SuppressWarnings("deprecation") // discriminator is a required field on UserData even though deprecated
    private UserData botUser() {
        return UserData.builder()
            .id(this.config.getBotId())
            .username("TestBot")
            .discriminator("0000")
            .globalName(Optional.of("TestBot"))
            .avatar(Optional.empty())
            .bot(Possible.of(true))
            .build();
    }

    private String userJson() {
        return this.write(this.botUser());
    }

    private String gatewayJson() {
        return this.write(GatewayData.builder().url(this.config.getGatewayUrl()).build());
    }

    private String gatewayBotJson() {
        return this.write(GatewayData.builder()
            .url(this.config.getGatewayUrl())
            .shards(1)
            .sessionStartLimit(SessionStartLimitData.builder()
                .total(1000)
                .remaining(1000)
                .resetAfter(0)
                .maxConcurrency(1)
                .build())
            .build());
    }

    @SuppressWarnings("deprecation") // summary is a required field on ApplicationInfoData even though deprecated
    private String applicationInfoJson() {
        return this.write(ApplicationInfoData.builder()
            .id(this.config.getBotId())
            .name("TestBot")
            .description("Offline harness application")
            .botPublic(true)
            .botRequireCodeGrant(false)
            .summary("")
            .verifyKey(this.config.getVerifyKey())
            .build());
    }

    private String guildJson() {
        return this.write(GuildUpdateData.builder()
            .id(this.config.getGuildId())
            .name("Harness Guild")
            .ownerId(this.config.getBotId())
            .afkTimeout(300)
            .verificationLevel(Guild.VerificationLevel.NONE.getValue())
            .defaultMessageNotifications(0)
            .explicitContentFilter(Guild.ContentFilterLevel.DISABLED.getValue())
            .mfaLevel(Guild.MfaLevel.NONE.getValue())
            .premiumTier(Guild.PremiumTier.NONE.getValue())
            .preferredLocale("en-US")
            .nsfwLevel(Guild.NsfwLevel.DEFAULT.getValue())
            .roles(List.of())
            .emojis(List.of())
            .build());
    }

    private String messageJson() {
        return this.write(MessageData.builder()
            .id(this.config.getReplyMessageId())
            .channelId(this.config.getChannelId())
            .author(this.botUser())
            .content("")
            .timestamp(messageTimestamp())
            .editedTimestamp(Optional.empty())
            .tts(false)
            .mentionEveryone(false)
            .pinned(false)
            .type(0)
            .build());
    }

}
