package dev.sbs.discordapi.util;

import dev.sbs.api.util.date.RealDate;
import dev.sbs.api.util.helper.FormatUtil;
import discord4j.common.util.Snowflake;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Optional;

public class DiscordDate extends RealDate {

    public static long DISCORD_EPOCH = 1420070400000L;

    public DiscordDate(@NotNull String duration) {
        super(duration);
    }

    public DiscordDate(long realTime) {
        super(realTime);
    }

    // https://discord.com/developers/docs/reference#snowflakes
    public DiscordDate(@NotNull Snowflake snowflake) {
        super((snowflake.asLong() >> 22) + DISCORD_EPOCH);
    }

    public String asFormat(@NotNull Type type) {
        return type.asFormat(this.getRealTime());
    }

    /**
     * Format Type
     * <br><br>
     * All sample dates show Month before Day.
     */
    public enum Type {

        /**
         * MM/dd/yyyy
         */
        DATE("d", "MM/dd/yyyy"),
        /**
         * MMMM d, yyyy
         */
        FULL_DATE("D", "MMMM d, yyyy"),
        /**
         * h:m a
         */
        TIME("t", "h:m a"),
        /**
         * h:m:s a
         */
        FULL_TIME("T", "h:m:s a"),
        /**
         * MMMM d, yyyy h:m a
         */
        FULL_DATE_TIME("f", "MMMM d, yyyy h:m a"),
        /**
         * EEEE, MMMM d, yyyy h:m a
         */
        WEEKDAY_FULL_DATE_TIME("F", "EEEE, MMMM d, yyyy h:m a"),
        /**
         * Shows the date and time relative to {@link Instant#now()}.
         */
        RELATIVE("R"),
        /**
         * The current date and time in milliseconds.
         */
        TIMESTAMP(Optional.empty());

        @Getter private final Optional<String> format;
        @Getter private final Optional<SimpleDateFormat> dateFormat;

        Type(@NotNull String format) {
            this(Optional.of(format));
        }

        Type(@NotNull Optional<String> format) {
            this(format, Optional.empty());
        }

        Type(@NotNull String format, @Nullable String dateFormat) {
            this(Optional.of(format), Optional.ofNullable(dateFormat));
        }

        Type(@NotNull Optional<String> format, @NotNull Optional<String> dateFormat) {
            this.format = format;
            this.dateFormat = dateFormat.map(SimpleDateFormat::new);
        }

        public String asFormat(long milliseconds) {
            return this.getFormat().map(format -> FormatUtil.format("<t:{0,number,#}:{1}>", milliseconds, format)).orElse(String.valueOf(milliseconds));
        }

    }

}
