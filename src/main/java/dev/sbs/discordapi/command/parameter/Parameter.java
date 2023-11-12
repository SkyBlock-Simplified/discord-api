package dev.sbs.discordapi.command.parameter;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.data.Range;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.impl.SlashCommand;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Parameter {

    private static final BiFunction<String, CommandContext<?>, Boolean> NOOP_HANDLER = (s_, c_) -> true;
    private static final Function<String, Mono<Void>> NOOP_COMPLETE = v_ -> Mono.empty();
    private static final Pattern MENTIONABLE_PATTERN = Pattern.compile("<(?:@!?&?|#)[\\d]+>");
    private static final Pattern MENTIONABLE_USER_PATTERN = Pattern.compile("<@!?[\\d]+>");
    private static final Pattern MENTIONABLE_ROLE_PATTERN = Pattern.compile("<@!?&[\\d]+>");
    private static final Pattern MENTIONABLE_CHANNEL_PATTERN = Pattern.compile("<#[\\d]+>");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("<:[\\w]+:[\\d]+>");

    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull String name;
    @Getter private final @NotNull String description;
    @Getter private final @NotNull Type type;
    @Getter private final boolean required;
    @Getter private final @NotNull Optional<Emoji> emoji;
    @Getter private final @NotNull ConcurrentSet<Channel.Type> channelTypes;
    @Getter private final @NotNull Range<Double> sizeLimit;
    @Getter private final @NotNull Range<Integer> lengthLimit;
    @Getter private final @NotNull BiFunction<String, CommandContext<?>, Boolean> validator;
    @Getter private final @NotNull Function<String, Mono<Void>> autoComplete;
    @Getter private final @NotNull ConcurrentLinkedMap<String, Object> choices;

    public static @NotNull Builder builder(@NotNull String name, @NotNull String description, @NotNull Type type) {
        if (StringUtil.isEmpty(name))
            throw SimplifiedException.of(DiscordException.class).withMessage("Parameter name cannot be NULL!").build();

        if (StringUtil.isEmpty(description))
            throw SimplifiedException.of(DiscordException.class).withMessage("Parameter description cannot be NULL!").build();

        return new Builder(
            UUID.randomUUID(),
            name,
            description,
            type
        );
    }

    public static @NotNull Builder from(@NotNull Parameter parameter) {
        return new Builder(parameter.getUniqueId(), parameter.getName(), parameter.getDescription(), parameter.getType())
            .isRequired(parameter.isRequired())
            .withEmoji(parameter.getEmoji())
            .withChannelTypes(parameter.getChannelTypes())
            .withSizeLimit(parameter.getSizeLimit().getMinimum(), parameter.getSizeLimit().getMaximum())
            .withLengthLimit(parameter.getLengthLimit().getMinimum(), parameter.getLengthLimit().getMaximum())
            .withValidator(parameter.getValidator())
            .withAutoComplete(parameter.getAutoComplete())
            .withChoices(parameter.getChoices());
    }

    public boolean isAutocompleting() {
        return !NOOP_COMPLETE.equals(this.getAutoComplete());
    }

    public boolean isValid(@NotNull Optional<String> argument, @NotNull CommandContext<?> commandContext) {
        return argument.map(value -> this.isValid(value, commandContext)).orElse(!this.isRequired());
    }

    public boolean isValid(@Nullable String argument, @NotNull CommandContext<?> commandContext) {
        return this.getValidator().apply(StringUtil.defaultIfEmpty(argument, ""), commandContext);
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @Getter
    public enum Type {

        TEXT(3, ApplicationCommandOption.Type.STRING, String.class, argument -> true),
        WORD(3, ApplicationCommandOption.Type.STRING, String.class, "Only Letters, Numbers and Underscores are allowed!", argument -> !argument.matches("\\w")),
        INTEGER(4, ApplicationCommandOption.Type.INTEGER, Integer.class, "Only Integers are allowed!", argument -> {
            if (NumberUtil.isCreatable(argument) && !argument.contains(".")) {
                double value = NumberUtil.createBigDecimal(argument).doubleValue();
                return value <= Double.MAX_VALUE && value >= Double.MIN_VALUE;
            }

            return false;
        }),
        LONG(4, ApplicationCommandOption.Type.INTEGER, Double.class, "Only Doubles are allowed!", argument -> {
            if (NumberUtil.isCreatable(argument) && !argument.contains(".")) {
                double value = NumberUtil.createBigDecimal(argument).doubleValue();
                return value <= Double.MAX_VALUE && value >= Double.MIN_VALUE;
            }

            return false;
        }),
        BOOLEAN(5, ApplicationCommandOption.Type.BOOLEAN, Boolean.class, "Only `true` or `false` values are allowed!", argument -> argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("false")),
        USER(6, ApplicationCommandOption.Type.USER, User.class, "Only User Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_USER_PATTERN.matcher(argument).matches()),
        CHANNEL(7, ApplicationCommandOption.Type.CHANNEL, Channel.class, "Only Channel Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_CHANNEL_PATTERN.matcher(argument).matches()),
        ROLE(8, ApplicationCommandOption.Type.ROLE, Role.class, "Only Role Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_ROLE_PATTERN.matcher(argument).matches()),
        MENTIONABLE(9, ApplicationCommandOption.Type.MENTIONABLE, String.class, "Only Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_PATTERN.matcher(argument).matches()),
        EMOJI(9, ApplicationCommandOption.Type.MENTIONABLE, ReactionEmoji.class, "Only Emojis are allowed!", argument -> EMOJI_PATTERN.matcher(argument).matches()),
        DOUBLE(10, ApplicationCommandOption.Type.NUMBER, Double.class, String.format("Only Numbers between %s and %s are allowed!", Double.MIN_VALUE, Double.MAX_VALUE), NumberUtil::isCreatable),
        ATTACHMENT(11, ApplicationCommandOption.Type.ATTACHMENT, Attachment.class, "Only Attachments are allowed!", argument -> true);

        private final int value;
        private final @NotNull ApplicationCommandOption.Type optionType;
        private final @NotNull Class<?> javaType;
        private final @NotNull Optional<String> errorMessage;
        private final @NotNull Function<String, Boolean> validator;

        Type(int value, @NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @NotNull Function<String, Boolean> validator) {
            this(value, optionType, javaType, (String) null, validator);
        }

        Type(int value, @NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @Nullable String errorMessage, @NotNull Function<String, Boolean> validator) {
            this(value, optionType, javaType, Optional.ofNullable(errorMessage), validator);
        }

        Type(int value, @NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @NotNull Optional<String> errorMessage, @NotNull Function<String, Boolean> validator) {
            this.value = value;
            this.optionType = optionType;
            this.javaType = javaType;
            this.errorMessage = errorMessage;
            this.validator = validator;
        }

        public boolean isValid(@NotNull Optional<String> argument) {
            return argument.map(this::isValid).orElse(false);
        }

        public boolean isValid(@Nullable String argument) {
            return this.getValidator().apply(StringUtil.defaultIfEmpty(argument, ""));
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements dev.sbs.api.util.builder.Builder<Parameter> {

        private final UUID uniqueId;
        private final String name;
        private final String description;
        private final Type type;
        private boolean required = false;
        private Optional<Emoji> emoji = Optional.empty();
        private Range<Double> sizeLimit = Range.between(Double.MIN_VALUE, Double.MAX_VALUE);
        private Range<Integer> lengthLimit = Range.between(0, 6000);
        private final ConcurrentSet<Channel.Type> channelTypes = Concurrent.newSet();
        private BiFunction<String, CommandContext<?>, Boolean> validator = NOOP_HANDLER;
        private Function<String, Mono<Void>> autoComplete = NOOP_COMPLETE;
        private final ConcurrentLinkedMap<String, Object> choices = Concurrent.newLinkedMap();

        /**
         * Clears the channel types.
         */
        public Builder clearChannelTypes() {
            this.channelTypes.clear();
            return this;
        }

        /**
         * Sets the {@link Parameter} as required by the {@link SlashCommand}.
         */
        public Builder isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets if the {@link Parameter} is required by the {@link SlashCommand}.
         *
         * @param required True if required.
         */
        public Builder isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets a callback method for {@link Parameter} autocompletion.
         * <br><br>
         * This will not be used if choices exist.
         *
         * @param autoComplete The autocomplete callback method.
         */
        public Builder withAutoComplete(@NotNull Function<String, Mono<Void>> autoComplete) {
            this.autoComplete = autoComplete;
            return this;
        }

        /**
         * Sets the channel types to restrict the channel parameter to.
         * <br><br>
         * This will throw an error if you have not started with {@link Type}.CHANNEL.
         *
         * @param channelTypes The varargs channel types to limit this parameter to.
         */
        public Builder withChannelTypes(@NotNull Channel.Type... channelTypes) {
            return this.withChannelTypes(Arrays.asList(channelTypes));
        }

        /**
         * Sets the channel types to restrict the channel parameter to.
         * <br><br>
         * This will throw an error if you have not started with {@link Type}.CHANNEL.
         *
         * @param channelTypes The iterable of channel types to limit this parameter to.
         */
        public Builder withChannelTypes(@NotNull Iterable<Channel.Type> channelTypes) {
            if (this.type != Type.CHANNEL) {
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("You can only specify channel types for parameters of type Channel!")
                    .build();
            }

            channelTypes.forEach(this.channelTypes::add);
            return this;
        }

        /**
         * Define the {@link Parameter} choices a user can choose from.
         *
         * @param choices Variable number of choices to add.
         */
        public Builder withChoices(@NotNull Map.Entry<String, Object>... choices) {
            return this.withChoices(Arrays.asList(choices));
        }

        /**
         * Define the {@link Parameter} choices a user can choose from.
         *
         * @param choices Collection of choices to add.
         */
        public Builder withChoices(@NotNull Iterable<Map.Entry<String, Object>> choices) {
            choices.forEach(this.choices::put);
            return this;
        }

        /**
         * Sets the {@link Emoji} that isn't even used right now.
         *
         * @param emoji The emoji.
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the {@link Emoji} that isn't even used right now.
         *
         * @param emoji The emoji.
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Set the length limit for STRING and WORD {@link Type types}.
         *
         * @param minimum Minimum number of characters, default 0.
         * @param maximum Maximum number of characters, default 6000.
         */
        public Builder withLengthLimit(int minimum, int maximum) {
            maximum = Range.between(1, 6000).fit(maximum);
            minimum = Range.between(0, maximum).fit(minimum);
            this.lengthLimit = Range.between(minimum, maximum);
            return this;
        }

        /**
         * Set the size limit for INTEGER and DOUBLE {@link Type types}.
         *
         * @param minimum Minimum number, default Double.MIN_VALUE.
         * @param maximum Maximum number, default Double.MAX_VALUE.
         */
        public Builder withSizeLimit(double minimum, double maximum) {
            this.sizeLimit = Range.between(minimum, maximum);
            return this;
        }

        /**
         * Sets a custom validator for this {@link Parameter}.
         *
         * @param validator Custom validator.
         */
        public Builder withValidator(@NotNull BiFunction<String, CommandContext<?>, Boolean> validator) {
            this.validator = validator;
            return this;
        }

        @Override
        public Parameter build() {
            return new Parameter(
                this.uniqueId,
                this.name,
                this.description,
                this.type,
                this.required,
                this.emoji,
                this.channelTypes,
                this.sizeLimit,
                this.lengthLimit,
                this.validator,
                this.autoComplete,
                this.choices
            );
        }

    }

}
