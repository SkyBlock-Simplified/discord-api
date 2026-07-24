package dev.simplified.discordapi.integration;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;

/**
 * A concrete {@link DiscordBot} for the offline harness. Runs the blocking two-phase startup on a
 * daemon thread so the test thread can drive simulated events once the bot reports connected.
 */
@Log
public final class HarnessBot extends DiscordBot {

    public HarnessBot(@NotNull DiscordConfig config) {
        super(config);
    }

    /**
     * Starts the bot's {@code login()} then {@code connect()} lifecycle on a daemon thread. The thread
     * blocks in {@code connect()} to keep the fake gateway "online" for the test's duration. An uncaught
     * exception handler logs any startup failure that would otherwise die silently on the daemon thread,
     * leaving the test to time out with no clue.
     *
     * @return the started daemon thread
     */
    public @NotNull Thread bootAsync() {
        log.info("Booting harness bot on a daemon thread");
        Thread thread = new Thread(this::start, "harness-bot");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((failedThread, throwable) ->
            log.error("Harness bot thread '{}' died during startup", failedThread.getName(), throwable));
        thread.start();
        return thread;
    }

}
