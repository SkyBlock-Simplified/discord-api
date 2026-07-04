package dev.simplified.discordapi.harness.test;

import io.netty.handler.codec.http.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * A minimal blocking HTTP client for exercising the loopback REST mock in harness tests. Kept separate from
 * the {@code *Test} classes so JUnit does not collect it.
 */
final class HttpProbe {

    /** A captured HTTP response. */
    record Response(int status, @NotNull String body) {

        boolean bodyContains(@NotNull String text) {
            return this.body.contains(text);
        }

    }

    private HttpProbe() {
    }

    /**
     * Sends a request to the loopback mock and blocks for the response.
     *
     * @param method the HTTP method
     * @param url the absolute url
     * @param body the request body, or {@code null} for none
     * @return the status and body
     */
    static @NotNull Response request(@NotNull HttpMethod method, @NotNull String url, @Nullable String body) {
        HttpClient.RequestSender sender = HttpClient.create().request(method).uri(url);
        HttpClient.ResponseReceiver<?> receiver = body == null ? sender : sender.send(ByteBufFlux.fromString(Mono.just(body)));

        Response response = receiver
            .responseSingle((res, buf) -> buf.asString()
                .defaultIfEmpty("")
                .map(read -> new Response(res.status().code(), read)))
            .block(Duration.ofSeconds(10));

        if (response == null)
            throw new IllegalStateException("No response from " + method + " " + url);

        return response;
    }

    static @NotNull Response get(@NotNull String url) {
        return request(HttpMethod.GET, url, null);
    }

}
