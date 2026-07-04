package dev.simplified.discordapi.handler.response;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.util.DiscordReference;
import dev.simplified.scheduler.ScheduledTask;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled reaper that evicts expired {@link CachedResponse} entries from the hot tier.
 *
 * <p>
 * Replaces the previous inline sweep in {@code DiscordBot.connect()}: it runs a single well-formed
 * reactive pipeline (no nested {@code subscribe}), isolates errors per entry so one failing message
 * edit cannot abort the sweep, and guards against overlapping ticks. Mortal entries have their
 * components disabled and reactions cleared on the backing message; eternal entries are evicted from
 * the hot tier <b>without</b> disabling, so the cold record survives and the message re-hydrates on
 * the next interaction.
 *
 * <p>
 * Lifecycle: {@link #start()} schedules the sweep; the task is cancelled either explicitly via
 * {@link #stop()} or globally when {@code DisconnectListener} shuts the scheduler down on gateway
 * disconnect.
 */
public final class ResponseExpiryTask extends DiscordReference {

    /** How often the sweep runs, in seconds. */
    private static final long SWEEP_INTERVAL_SECONDS = 1L;

    /** Guards against a slow sweep overlapping the next scheduled tick. */
    private final @NotNull AtomicBoolean sweeping = new AtomicBoolean(false);

    private ScheduledTask task;

    /**
     * Constructs a new expiry task for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ResponseExpiryTask(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** Schedules the recurring sweep on the bot's scheduler. */
    public void start() {
        this.task = this.getDiscordBot().getScheduler().scheduleAsync(
            this::runSweepBlocking,
            0L,
            SWEEP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    /**
     * Runs one sweep to completion on the scheduler's (virtual) thread, logging any unexpected
     * failure the per-entry isolation did not already absorb.
     */
    private void runSweepBlocking() {
        try {
            this.runSweep().block();
        } catch (RuntimeException exception) {
            this.getLog().error("Response expiry sweep failed unexpectedly", exception);
        }
    }

    /** Cancels the recurring sweep. */
    public void stop() {
        if (this.task != null)
            this.task.cancel();
    }

    /**
     * Runs a single expiry sweep: removes every expired entry from the hot tier, disabling mortal
     * messages and leaving eternal messages intact. Skips (returns empty) when a previous sweep is
     * still running. Exposed for deterministic one-shot testing via {@code runSweep().block()}.
     *
     * @return a mono completing when the sweep finishes
     */
    @NotNull Mono<Void> runSweep() {
        if (!this.sweeping.compareAndSet(false, true))
            return Mono.empty();

        return this.getDiscordBot()
            .getResponseLocator()
            .findExpired()
            .concatMap(this::expire)
            .then()
            .doFinally(signal -> this.sweeping.set(false));
    }

    /** Evicts one expired entry, isolating any failure so the rest of the sweep proceeds. */
    private @NotNull Mono<Void> expire(@NotNull CachedResponse entry) {
        return this.getDiscordBot()
            .getResponseLocator()
            .evict(entry.getUniqueId())
            .then(Mono.defer(() -> entry.isEternal() ? Mono.empty() : this.disableAndReap(entry)))
            .onErrorResume(throwable -> this.reportExpiryError(entry, throwable));
    }

    /** Clears reactions and disables the components on a mortal entry's backing message. */
    private @NotNull Mono<Void> disableAndReap(@NotNull CachedResponse entry) {
        return this.getDiscordBot()
            .getGateway()
            .getChannelById(entry.getChannelId())
            .ofType(MessageChannel.class)
            .flatMap(channel -> channel.getMessageById(entry.getMessageId()))
            .flatMap(message -> message.removeAllReactions()
                .then(message.edit(entry.getResponse()
                    .mutate()
                    .disableAllComponents()
                    .isRenderingPagingComponents(false)
                    .build()
                    .getD4jEditSpec(this.getDiscordBot().getEmojiHandler())
                ))
            )
            .then();
    }

    /** Logs a benign per-entry reap failure (deleted message, missing permissions) without aborting the sweep. */
    private @NotNull Mono<Void> reportExpiryError(@NotNull CachedResponse entry, @NotNull Throwable throwable) {
        this.getLog().warn(
            "Failed to reap expired response {} (message {}): {}",
            entry.getUniqueId(),
            entry.getMessageId().asString(),
            throwable.toString()
        );
        return Mono.empty();
    }

}
