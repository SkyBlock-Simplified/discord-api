package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.handler.DiscordConfig;
import org.jetbrains.annotations.NotNull;

/**
 * A concrete {@link DiscordBot} for the offline harness. Runs the blocking two-phase startup on a
 * daemon thread so the test thread can drive simulated events once the bot reports connected.
 */
public final class HarnessBot extends DiscordBot {

    public HarnessBot(@NotNull DiscordConfig config) {
        super(config);
    }

    /**
     * Starts the bot's {@code login()} then {@code connect()} lifecycle on a daemon thread. The thread
     * blocks in {@code connect()} to keep the fake gateway "online" for the test's duration.
     *
     * @return the started daemon thread
     */
    public @NotNull Thread bootAsync() {
        Thread thread = new Thread(this::start, "harness-bot");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

}
