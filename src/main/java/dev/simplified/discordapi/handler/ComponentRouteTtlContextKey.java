package dev.simplified.discordapi.handler;

import dev.simplified.discordapi.listener.Component;
import dev.simplified.discordapi.listener.component.ComponentListener;
import dev.simplified.discordapi.response.Response;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;

import java.time.Duration;

/**
 * Holds the singleton Reactor {@link Context} key carrying a per-route cache
 * time-to-live override down the reactive pipeline.
 *
 * <p>
 * Writers:
 * <ul>
 *   <li>{@link ComponentListener ComponentListener}
 *       wraps its annotation-dispatched handler invocation with
 *       {@code .contextWrite(ctx -> ctx.put(KEY, Duration.ofSeconds(route.getCacheTtl())))}
 *       when the matched {@link Component @Component} route declares a positive
 *       {@link Component#cacheTtl() cacheTtl}</li>
 * </ul>
 *
 * <p>
 * Reader:
 * <ul>
 *   <li>The response locator's {@code store} method uses
 *       {@code Mono.deferContextual(ctx -> ...)} to read the key when caching
 *       a {@link Response Response} created inside the handler. When present,
 *       the value overrides the default expiry computed from
 *       {@link Response#getTimeToLive() Response.timeToLive}</li>
 * </ul>
 *
 * <p>
 * Using a Reactor {@link Context} key instead of a {@link ThreadLocal} ensures
 * the binding survives scheduler boundaries within a single reactive pipeline,
 * which the Discord4J dispatch chain relies on.
 */
@UtilityClass
public final class ComponentRouteTtlContextKey {

    /** The Reactor {@link Context} key under which the per-route TTL {@link Duration} is stored. */
    public static final String KEY = "dev.simplified.discordapi.component-route-ttl";

}
