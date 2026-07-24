package dev.simplified.discordapi.harness.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.annotations.Log;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal localhost stand-in for the Discord REST API, backed by reactor-netty. Serves just enough
 * endpoints for the framework's login prologue and post-connect sync, echoes bulk command overwrites with
 * synthetic ids, and records every request for assertions.
 * <p>
 * The endpoint list lives in the {@link Route} table; {@code start()} loads it and appends a catch-all. The
 * response bodies come from the shared {@link HarnessEntities}, so this class holds only server plumbing.
 */
@Log
@RequiredArgsConstructor
public final class LocalDiscordServer {

    // Matches an interaction-callback path, capturing the interaction token in group 1.
    private static final Pattern CALLBACK_PATTERN = Pattern.compile("/interactions/\\d+/([^/]+)/callback");

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    // Interaction tokens already acknowledged (via a callback); a repeat is the "already acknowledged" bug.
    private final Set<String> acknowledgedTokens = ConcurrentHashMap.newKeySet();
    private final HarnessConfig config;
    private final HarnessEntities entities;
    private DisposableServer server;

    /**
     * Binds the server on an ephemeral loopback port, loading every {@link Route} ahead of the catch-all.
     *
     * @return this server
     */
    public @NotNull LocalDiscordServer start() {
        this.server = HttpServer.create()
            .host(this.config.getGatewayHost())
            .port(0)
            .route(routes -> {
                for (Route route : Route.values())
                    routes.route(this.matcher(route), this.handlerFor(route));

                routes.route(request -> true, this.catchAll());
            })
            .bindNow();
        log.debug("REST mock bound at {}", this.baseUrl());
        return this;
    }

    /**
     * The base url to hand to {@code DiscordConfig.withApiBaseUrl}. Deliberately plaintext {@code http} - this
     * is an in-process loopback stand-in for the REST API and the bot is pointed at it with a plaintext
     * (non-{@code .secure()}) client, so https would fail the handshake.
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

    /** Matches a request against a route by method and query-stripped path. */
    private Predicate<HttpServerRequest> matcher(Route route) {
        return request -> route.matches(request.method().name(), stripQuery(request.uri()));
    }

    /** Builds the handler for a route's response {@link Route.Kind}, over the shared {@code respond} pipeline. */
    private BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> handlerFor(Route route) {
        return switch (route.kind()) {
            case JSON -> (request, response) -> respond(request, response, 200, ignored -> route.body(this.entities));
            case ECHO -> (request, response) -> respond(request, response, 200, this::echoCommands);
            case NO_CONTENT -> (request, response) -> respond(request, response, 204, null);
        };
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
     * Warns when an interaction token is acknowledged more than once. Discord permits exactly one callback per
     * interaction and rejects a second with "interaction has already been acknowledged" (error 40060);
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

            var mapper = this.entities.mapper();
            JsonNode parsed = mapper.readTree(body);
            ArrayNode out = mapper.createArrayNode();

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

            return mapper.writeValueAsString(out);
        } catch (Exception exception) {
            log.warn("Failed to echo command bulk-overwrite payload; returning an empty array", exception);
            return "[]";
        }
    }

    private static String stripQuery(String uri) {
        int index = uri.indexOf('?');
        return index < 0 ? uri : uri.substring(0, index);
    }

}
