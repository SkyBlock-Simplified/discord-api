package dev.simplified.discordapi.listener;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.event.BotEvent;
import dev.simplified.discordapi.util.DiscordReference;
import dev.simplified.reflection.Reflection;
import dev.simplified.util.StringUtil;
import dev.simplified.annotations.Getter;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.util.function.Function;

/**
 * Abstract base for listeners of bot-internal {@link BotEvent BotEvents},
 * binding a specific event subtype to a reactive handler function.
 *
 * <p>
 * Subclasses are discovered via classpath scanning and registered through
 * {@link DiscordBot}, parallel to the Discord4J-event-bound
 * {@link DiscordListener}. The resolved event class and a human-readable
 * title (derived from the subclass name) are captured at construction time.
 *
 * @param <T> the {@link BotEvent} subtype this listener handles
 * @see BotEvent
 * @see DiscordListener
 * @see DiscordReference
 */
@Getter
public abstract class BotEventListener<T extends BotEvent> extends DiscordReference implements Function<T, Publisher<Void>> {

    /** The resolved {@link BotEvent} subclass this listener is bound to. */
    private final @NotNull Class<T> eventClass;

    /** A human-readable title derived from the listener's class name. */
    private final @NotNull String title;

    /**
     * Constructs a new {@code BotEventListener} bound to the given bot instance.
     *
     * @param discordBot the bot instance this listener belongs to
     */
    public BotEventListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.eventClass = Reflection.getSuperClass(this);
        this.title = StringUtil.join(this.getClass().getSimpleName().split("(?=\\p{Upper})"), " ");
    }

}
