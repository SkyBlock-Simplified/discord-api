package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import dev.simplified.discordapi.harness.rest.LocalDiscordServer;
import io.netty.handler.codec.http.HttpMethod;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises every {@link dev.simplified.discordapi.harness.rest.LocalDiscordServer} route over real HTTP: each
 * faked endpoint serves the expected status and body shape, the command bulk-overwrite echo rewrites ids, and
 * an unmodelled path falls through to the catch-all. Framework-free - it drives only the harness server.
 */
class LocalDiscordServerTest {

    private final HarnessConfig config = HarnessConfig.builder().build();
    private LocalDiscordServer server;

    @BeforeEach
    void start() {
        this.server = new LocalDiscordServer(this.config, new HarnessEntities(this.config)).start();
    }

    @AfterEach
    void stop() {
        this.server.stop();
    }

    /** One case per declared route: method, path, request body (if any), expected status and body substring. */
    private record Case(@Nullable String label, HttpMethod method, String path, @Nullable String body, int status, String contains) {
    }

    @Test
    void serves_every_route() {
        List<Case> cases = List.of(
            new Case("self user", HttpMethod.GET, "/users/@me", null, 200, "TestBot"),
            new Case("gateway", HttpMethod.GET, "/gateway", null, 200, "fake-gateway"),
            new Case("gateway bot", HttpMethod.GET, "/gateway/bot", null, 200, "session_start_limit"),
            new Case("application info", HttpMethod.GET, "/oauth2/applications/@me", null, 200, "Offline harness application"),
            new Case("emojis", HttpMethod.GET, "/applications/111/emojis", null, 200, "items"),
            new Case("guild", HttpMethod.GET, "/guilds/222", null, 200, "Harness Guild"),
            new Case("global commands", HttpMethod.PUT, "/applications/111/commands", "[{\"name\":\"ping\",\"type\":1}]", 200, "ping"),
            new Case("guild commands", HttpMethod.PUT, "/applications/111/guilds/222/commands", "[{\"name\":\"echo\",\"type\":1}]", 200, "echo"),
            new Case("interaction callback", HttpMethod.POST, "/interactions/1/tok-abc/callback", "", 204, ""),
            new Case("webhook message", HttpMethod.POST, "/webhooks/111/tok-abc", "{}", 200, "TestBot"),
            new Case("channel message", HttpMethod.POST, "/channels/333/messages", "{}", 200, "TestBot"),
            new Case("edit channel message", HttpMethod.PATCH, "/channels/333/messages/999", "{}", 200, "TestBot"),
            new Case("edit original", HttpMethod.PATCH, "/webhooks/111/tok-abc/messages/@original", "{}", 200, "TestBot"),
            new Case("edit followup", HttpMethod.PATCH, "/webhooks/111/tok-abc/messages/999", "{}", 200, "TestBot"),
            new Case("delete followup", HttpMethod.DELETE, "/webhooks/111/tok-abc/messages/999", null, 204, ""),
            new Case("get channel", HttpMethod.GET, "/channels/333", null, 200, "\"type\":0"),
            new Case("get channel message", HttpMethod.GET, "/channels/333/messages/999", null, 200, "TestBot"),
            new Case("delete channel message", HttpMethod.DELETE, "/channels/333/messages/999", null, 204, ""),
            new Case("get reactions", HttpMethod.GET, "/channels/333/messages/999/reactions/smile", null, 200, "[]"),
            new Case("add self reaction", HttpMethod.PUT, "/channels/333/messages/999/reactions/smile/@me", null, 204, ""),
            new Case("delete self reaction", HttpMethod.DELETE, "/channels/333/messages/999/reactions/smile/@me", null, 204, ""),
            new Case("delete user reaction", HttpMethod.DELETE, "/channels/333/messages/999/reactions/smile/444", null, 204, ""),
            new Case("delete emoji reactions", HttpMethod.DELETE, "/channels/333/messages/999/reactions/smile", null, 204, ""),
            new Case("create emoji", HttpMethod.POST, "/applications/111/emojis", "{\"name\":\"harness\"}", 200, "harness")
        );

        for (Case route : cases) {
            HttpProbe.Response response = HttpProbe.request(route.method(), this.server.baseUrl() + route.path(), route.body());
            assertEquals(route.status(), response.status(), () -> route.label() + " status; body=" + response.body());
            assertTrue(response.bodyContains(route.contains()), () -> route.label() + " body should contain '" + route.contains() + "'; got " + response.body());
        }
    }

    @Test
    void echoes_commands_with_synthetic_ids() {
        HttpProbe.Response response = HttpProbe.request(
            HttpMethod.PUT, this.server.baseUrl() + "/applications/111/commands", "[{\"name\":\"ping\",\"type\":1}]");

        assertEquals(200, response.status());
        assertTrue(response.bodyContains("\"id\":\"" + this.config.commandId("ping") + "\""),
            "echo should stamp the deterministic command id; got " + response.body());
        assertTrue(response.bodyContains("\"application_id\":\"" + this.config.getBotId() + "\""),
            "echo should stamp the application id; got " + response.body());
    }

    @Test
    void unmodelled_route_falls_through_to_catch_all() {
        HttpProbe.Response response = HttpProbe.get(this.server.baseUrl() + "/some/unmodelled/path");

        assertEquals(200, response.status());
        assertEquals("{}", response.body());
    }

    @Test
    void records_every_request_in_order() {
        HttpProbe.get(this.server.baseUrl() + "/users/@me");
        HttpProbe.get(this.server.baseUrl() + "/guilds/222");

        List<String> paths = this.server.requests().stream().map(request -> request.method() + " " + request.path()).toList();
        assertEquals(List.of("GET /users/@me", "GET /guilds/222"), paths);
    }

}
