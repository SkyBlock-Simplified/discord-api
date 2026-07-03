package dev.simplified.discordapi.harness.rest;

/**
 * A single HTTP request captured by {@link LocalDiscordServer} for test assertions.
 *
 * @param method the HTTP method name (e.g. {@code GET}, {@code PUT})
 * @param path the request path with the query string stripped
 * @param body the aggregated request body, or an empty string if none
 */
public record RecordedRequest(String method, String path, String body) {
}
