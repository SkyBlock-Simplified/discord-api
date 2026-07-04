package dev.simplified.discordapi.listener;

import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.context.scope.ComponentContext;
import dev.simplified.discordapi.response.Response;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an annotation-dispatched component interaction handler.
 *
 * <p>
 * Methods annotated with {@code @Component} are discovered at startup by
 * scanning {@link DiscordCommand} subclasses and {@link EternalComponentListener}
 * subclasses. Each annotated method is registered by its {@link #value() custom id}
 * pattern in a global routing map. When a component interaction arrives whose
 * {@code customId} matches the route, the framework dispatches the event to
 * this method.
 *
 * <p>
 * Routes are matched in two passes:
 * <ul>
 *   <li><b>Exact</b> - the literal {@link #value() value} matches the
 *       interaction's {@code customId} verbatim. Exact routes always win over
 *       regex routes</li>
 *   <li><b>Regex</b> - when {@link #regex()} is {@code true}, the
 *       {@link #value()} is compiled as a {@link java.util.regex.Pattern Pattern}
 *       and tested against the {@code customId}. Multiple matching regex routes
 *       result in the interaction being dropped with an error log</li>
 * </ul>
 *
 * <p>
 * The annotated method must:
 * <ul>
 *   <li>declare exactly one parameter of a {@link ComponentContext} subtype
 *       (for example {@code ButtonContext}, {@code SelectMenuContext},
 *       {@code ModalContext})</li>
 *   <li>return a {@link org.reactivestreams.Publisher Publisher&lt;Void&gt;}
 *       (typically {@code Mono<Void>})</li>
 * </ul>
 *
 * <p>
 * A component whose custom id matches a route may be backed by a currently
 * cached response (in the in-memory locator) or by an eternal response that is
 * transparently rehydrated on demand. The handler signature and the context it
 * receives are identical in both cases; by the time the handler runs, a real
 * {@link ComponentContext} over a real {@link Response} always exists.
 *
 * @see EternalComponentListener
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Component {

    /**
     * The custom id pattern this method handles. Treated as a literal string
     * unless {@link #regex()} is {@code true}, in which case it is compiled
     * as a {@link java.util.regex.Pattern Pattern}.
     */
    String value();

    /**
     * Whether {@link #value()} should be compiled as a regular expression.
     * Defaults to {@code false} (literal match).
     */
    boolean regex() default false;

    /**
     * Optional override for the cache time-to-live in seconds applied to any
     * {@link Response Response} created inside
     * this handler. A non-positive value means no override is applied and the
     * response's own time-to-live is used.
     */
    long cacheTtl() default 0;

}
