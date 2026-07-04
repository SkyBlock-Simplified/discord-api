package dev.simplified.discordapi.listener;

import dev.simplified.discordapi.context.EternalBuildContext;
import dev.simplified.discordapi.response.Response;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the rebuild function for an eternal (reboot-surviving) response.
 *
 * <p>
 * Methods annotated with {@code @Eternal} are discovered at startup by scanning
 * {@link dev.simplified.discordapi.command.DiscordCommand DiscordCommand} subclasses and
 * {@link EternalComponentListener} subclasses, and registered by their {@link #value() key} in the
 * global component dispatcher. The same builder is invoked at creation time (when a caller sends the
 * eternal response) and at hydration time (when a component on the persisted message is interacted
 * with after a restart), so it is the single source of truth for the response's structure.
 *
 * <p>
 * The annotated method must:
 * <ul>
 *   <li>declare exactly one parameter assignable from {@link EternalBuildContext} - it carries the
 *       opaque {@link EternalBuildContext#getPayload() payload}, bot, channel, guild, and user, but
 *       deliberately exposes no {@code getResponse()} (there is no response to read while building
 *       one)</li>
 *   <li>return a {@link Response} built deterministically from the payload</li>
 * </ul>
 *
 * <p>
 * Identity and persistence are owned by the framework: the returned response's unique id, eternal
 * marker, and navigation coordinate are stamped and restored around the builder, so the method body
 * should not concern itself with them.
 *
 * @see EternalComponentListener
 * @see Component
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Eternal {

    /**
     * The stable key identifying this rebuild function. A caller marks a response eternal with the
     * same key via {@link Response.Builder#asEternal}, and the persisted record routes hydration
     * back to this method.
     */
    String value();

    /**
     * Optional auto-refresh interval in seconds. Reserved for the scheduled-refresh sweep; a
     * non-positive value (the default) means the response only re-renders on demand via
     * {@link dev.simplified.discordapi.DiscordBot#refreshEternal}.
     */
    long refresh() default 0;

}
