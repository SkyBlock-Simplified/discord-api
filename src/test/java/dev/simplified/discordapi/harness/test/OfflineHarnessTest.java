package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.OfflineHarness;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@link OfflineHarness} server aggregate: it wires the REST mock, the fake gateway, and the
 * dispatch factory, records requests for assertions, emits dispatches, and tears down. Framework-free - no bot
 * is booted.
 */
class OfflineHarnessTest {

    private OfflineHarness harness;

    @BeforeEach
    void boot() {
        this.harness = new OfflineHarness();
    }

    @AfterEach
    void close() {
        this.harness.close();
    }

    @Test
    @SuppressWarnings("HttpUrlsUsage") // the loopback mock is deliberately plaintext http
    void wires_the_server_pieces() {
        assertNotNull(this.harness.config());
        assertNotNull(this.harness.gateway());
        assertNotNull(this.harness.dispatches());
        assertTrue(this.harness.baseUrl().startsWith("http://"), "base url should be plaintext loopback; got " + this.harness.baseUrl());
    }

    @Test
    void records_and_awaits_requests() {
        HttpProbe.get(this.harness.baseUrl() + "/users/@me");

        assertNotNull(this.harness.awaitRequest(request -> request.method().equals("GET") && request.path().equals("/users/@me")));
        assertEquals(1, this.harness.requests().size());
    }

    @Test
    void counts_interaction_callbacks() {
        HttpProbe.request(HttpMethod.POST, this.harness.baseUrl() + "/interactions/1/tok-xyz/callback", "");

        assertNotNull(this.harness.awaitInteractionCallback());
        assertEquals(1, this.harness.callbackCount("tok-xyz"));
    }

    @Test
    void gateway_emits_into_the_dispatch_pipeline() {
        this.harness.gateway().emit(this.harness.dispatches().messageCreate(1L));

        var dispatch = this.harness.gateway().dispatch().take(1).blockFirst(Duration.ofSeconds(5));
        assertNotNull(dispatch, "the emitted dispatch should surface on the gateway flux");
    }

    @Test
    void await_returns_when_satisfied_and_throws_on_timeout() {
        this.harness.await(() -> true, Duration.ofSeconds(1), "should pass immediately");
        assertThrows(IllegalStateException.class,
            () -> this.harness.await(() -> false, Duration.ofMillis(50), "never satisfied"));
    }

    @Test
    void close_stops_the_rest_mock() {
        try (OfflineHarness closable = new OfflineHarness()) {
            String url = closable.baseUrl() + "/users/@me";
            assertNotNull(HttpProbe.get(url)); // reachable while open
            closable.close();
            assertThrows(Exception.class, () -> HttpProbe.get(url), "requests should fail once the mock is stopped");
        }
    }

}
