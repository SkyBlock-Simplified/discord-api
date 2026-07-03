package dev.simplified.discordapi.harness.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.simplified.discordapi.harness.data.TestIds;
import discord4j.common.JacksonResources;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.GuildUpdateData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.SessionStartLimitData;
import discord4j.discordjson.json.UserData;
import discord4j.discordjson.possible.Possible;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A minimal localhost stand-in for the Discord REST API, backed by reactor-netty. Serves just enough
 * endpoints for the framework's login prologue and post-connect sync, echoes bulk command overwrites
 * with synthetic ids, and records every request for assertions.
 */
public final class LocalDiscordServer {

    private static final String GATEWAY_URL = "ws://127.0.0.1/fake-gateway";
    // Empty list container returned by GET application emojis; not a Discord entity, so kept as a literal.
    private static final String EMPTY_EMOJIS_JSON = "{\"items\":[]}";
    private static final String MESSAGE_TIMESTAMP = "2020-01-01T00:00:00.000000+00:00";
    private static final String VERIFY_KEY = "0".repeat(64);

    // Discord4J's own mapper, so the discord-json immutables below serialize exactly as the bot expects.
    private final ObjectMapper mapper = JacksonResources.create().getObjectMapper();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final long botId;
    private final boolean debug;
    private DisposableServer server;

    public LocalDiscordServer(long botId) {
        this(botId, Boolean.getBoolean("harness.debug"));
    }

    public LocalDiscordServer(long botId, boolean debug) {
        this.botId = botId;
        this.debug = debug;
    }

    /**
     * Binds the server on an ephemeral loopback port.
     *
     * @return this server
     */
    public @NotNull LocalDiscordServer start() {
        this.server = HttpServer.create()
            .host("127.0.0.1")
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
        return this;
    }

    /**
     * The base url to hand to {@code DiscordConfig.withApiBaseUrl}.
     *
     * @return the loopback base url
     */
    public @NotNull String baseUrl() {
        return "http://127.0.0.1:" + this.server.port();
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
        if (this.server != null)
            this.server.disposeNow();
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
        return (request, response) -> respond(request, response, 200, ignored -> "{}");
    }

    private Publisher<Void> respond(HttpServerRequest request, HttpServerResponse response, int status, Function<String, String> responder) {
        return request.receive()
            .aggregate()
            .asString()
            .defaultIfEmpty("")
            .flatMap(body -> {
                String path = stripQuery(request.uri());
                this.requests.add(new RecordedRequest(request.method().name(), path, body));

                if (this.debug)
                    System.out.println("[mock] " + request.method().name() + " " + path + (body.isEmpty() ? "" : " " + body));

                if (responder == null)
                    return Mono.from(response.status(status).send()).then();

                String out;
                try {
                    out = responder.apply(body);
                } catch (Exception exception) {
                    out = "{}";
                }

                return Mono.from(response.header("Content-Type", "application/json").status(status).sendString(Mono.just(out))).then();
            });
    }

    private String echoCommands(String body) {
        try {
            if (body == null || body.isBlank())
                return "[]";

            JsonNode parsed = this.mapper.readTree(body);
            ArrayNode out = this.mapper.createArrayNode();

            if (parsed.isArray()) {
                for (JsonNode command : parsed) {
                    ObjectNode node = (ObjectNode) command.deepCopy();
                    String name = node.has("name") ? node.get("name").asText() : "unknown";
                    long id = TestIds.commandId(name);
                    node.put("id", Long.toString(id));
                    node.put("application_id", Long.toString(this.botId));
                    node.put("version", Long.toString(id));

                    if (!node.has("type"))
                        node.put("type", 1);

                    out.add(node);
                }
            }

            return this.mapper.writeValueAsString(out);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private static String stripQuery(String uri) {
        int index = uri.indexOf('?');
        return index < 0 ? uri : uri.substring(0, index);
    }

    /** Serializes a discord-json immutable with Discord4J's mapper into the response body. */
    private String write(@NotNull Object data) {
        try {
            return this.mapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize " + data.getClass().getSimpleName(), exception);
        }
    }

    /** The bot's self user, reused both as {@code GET /users/@me} and as the author of served messages. */
    @SuppressWarnings("deprecation") // discriminator is a required field on UserData even though deprecated
    private UserData botUser() {
        return UserData.builder()
            .id(this.botId)
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
        return this.write(GatewayData.builder().url(GATEWAY_URL).build());
    }

    private String gatewayBotJson() {
        return this.write(GatewayData.builder()
            .url(GATEWAY_URL)
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
            .id(this.botId)
            .name("TestBot")
            .description("Offline harness application")
            .botPublic(true)
            .botRequireCodeGrant(false)
            .summary("")
            .verifyKey(VERIFY_KEY)
            .build());
    }

    private String guildJson() {
        return this.write(GuildUpdateData.builder()
            .id(TestIds.GUILD_ID)
            .name("Harness Guild")
            .ownerId(TestIds.BOT_ID)
            .afkTimeout(300)
            .verificationLevel(0)
            .defaultMessageNotifications(0)
            .explicitContentFilter(0)
            .mfaLevel(0)
            .premiumTier(0)
            .preferredLocale("en-US")
            .nsfwLevel(0)
            .roles(List.of())
            .emojis(List.of())
            .build());
    }

    private String messageJson() {
        return this.write(MessageData.builder()
            .id(TestIds.REPLY_MESSAGE_ID)
            .channelId(TestIds.CHANNEL_ID)
            .author(this.botUser())
            .content("")
            .timestamp(MESSAGE_TIMESTAMP)
            .editedTimestamp(Optional.empty())
            .tts(false)
            .mentionEveryone(false)
            .pinned(false)
            .type(0)
            .build());
    }

}
