package dev.simplified.discordapi.harness.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.simplified.discordapi.harness.data.TestIds;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A minimal localhost stand-in for the Discord REST API, backed by reactor-netty. Serves just enough
 * endpoints for the framework's login prologue and post-connect sync, echoes bulk command overwrites
 * with synthetic ids, and records every request for assertions.
 */
public final class LocalDiscordServer {

    private static final String GATEWAY_JSON = "{\"url\":\"ws://127.0.0.1/fake-gateway\"}";
    private static final String EMPTY_EMOJIS_JSON = "{\"items\":[]}";

    private final ObjectMapper mapper = new ObjectMapper();
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
                routes.get("/gateway", fixed(GATEWAY_JSON));
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

    private String userJson() {
        return ("{\"id\":\"%d\",\"username\":\"TestBot\",\"discriminator\":\"0000\",\"global_name\":\"TestBot\","
            + "\"avatar\":null,\"bot\":true,\"system\":false,\"mfa_enabled\":false,\"flags\":0,\"public_flags\":0}")
            .formatted(this.botId);
    }

    private static String gatewayBotJson() {
        return "{\"url\":\"ws://127.0.0.1/fake-gateway\",\"shards\":1,"
            + "\"session_start_limit\":{\"total\":1000,\"remaining\":1000,\"reset_after\":0,\"max_concurrency\":1}}";
    }

    private String applicationInfoJson() {
        return ("{\"id\":\"%d\",\"name\":\"TestBot\",\"icon\":null,\"description\":\"Offline harness application\","
            + "\"bot_public\":true,\"bot_require_code_grant\":false,"
            + "\"verify_key\":\"0000000000000000000000000000000000000000000000000000000000000000\","
            + "\"flags\":0,\"summary\":\"\"}")
            .formatted(this.botId);
    }

    private static String guildJson() {
        return ("{\"id\":\"%d\",\"name\":\"Harness Guild\",\"icon\":null,\"splash\":null,\"discovery_splash\":null,"
            + "\"owner_id\":\"%d\",\"afk_channel_id\":null,\"afk_timeout\":300,\"verification_level\":0,"
            + "\"default_message_notifications\":0,\"explicit_content_filter\":0,\"roles\":[],\"emojis\":[],"
            + "\"features\":[],\"mfa_level\":0,\"application_id\":null,\"system_channel_id\":null,"
            + "\"system_channel_flags\":0,\"rules_channel_id\":null,\"vanity_url_code\":null,\"description\":null,"
            + "\"banner\":null,\"premium_tier\":0,\"preferred_locale\":\"en-US\",\"public_updates_channel_id\":null,"
            + "\"nsfw_level\":0,\"premium_progress_bar_enabled\":false}")
            .formatted(TestIds.GUILD_ID, TestIds.BOT_ID);
    }

    private String messageJson() {
        return ("{\"id\":\"" + TestIds.REPLY_MESSAGE_ID + "\",\"channel_id\":\"%d\","
            + "\"author\":{\"id\":\"%d\",\"username\":\"TestBot\",\"discriminator\":\"0000\",\"avatar\":null,\"bot\":true},"
            + "\"content\":\"\",\"timestamp\":\"2020-01-01T00:00:00.000000+00:00\",\"edited_timestamp\":null,"
            + "\"tts\":false,\"mention_everyone\":false,\"mentions\":[],\"mention_roles\":[],\"attachments\":[],"
            + "\"embeds\":[],\"pinned\":false,\"type\":0}")
            .formatted(TestIds.CHANNEL_ID, this.botId);
    }

}
