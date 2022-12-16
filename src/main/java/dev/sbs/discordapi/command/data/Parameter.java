package dev.sbs.discordapi.command.data;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.listener.command.MessageCommandListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.core.object.command.ApplicationCommandOption;
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
    @Getter private final boolean remainder;
    @Getter private final @NotNull Optional<Emoji> emoji;
    @Getter private final @NotNull BiFunction<String, CommandContext<?>, Boolean> validator;
    @Getter private final @NotNull Function<String, Mono<Void>> autoComplete;
    @Getter private final @NotNull ConcurrentMap<String, Object> choices;

    public static ParameterBuilder builder(@NotNull String name, @NotNull String description, @NotNull Type type) {
        if (StringUtil.isEmpty(name))
            throw SimplifiedException.of(DiscordException.class).withMessage("Parameter name cannot be NULL!").build();

        if (StringUtil.isEmpty(description))
            throw SimplifiedException.of(DiscordException.class).withMessage("Parameter description cannot be NULL!").build();

        return new ParameterBuilder(
            UUID.randomUUID(),
            name,
            description,
            type
        );
    }

    public static ParameterBuilder from(Parameter parameter) {
        return new ParameterBuilder(parameter.getUniqueId(), parameter.getName(), parameter.getDescription(), parameter.getType())
            .isRequired(parameter.isRequired())
            .isRemainder(parameter.isRemainder())
            .withAutoComplete(parameter.getAutoComplete())
            .withChoices(parameter.getChoices())
            .withEmoji(parameter.getEmoji())
            .withValidator(parameter.getValidator());
    }

    public ParameterBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ParameterBuilder implements Builder<Parameter> {

        private final UUID uniqueId;
        private final String name;
        private final String description;
        private final Type type;
        private boolean required = false;
        private boolean remainder = false;
        private Optional<Emoji> emoji = Optional.empty();
        private BiFunction<String, CommandContext<?>, Boolean> validator = NOOP_HANDLER;
        private Function<String, Mono<Void>> autoComplete = __ -> Mono.empty();
        private final ConcurrentMap<String, Object> choices = Concurrent.newMap();

        /**
         * Sets the {@link Parameter} as required by the {@link Command}.
         */
        public ParameterBuilder isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets if the {@link Parameter} is required by the {@link Command}.
         *
         * @param required True if required.
         */
        public ParameterBuilder isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets this {@link Parameter} as the catch-all for remaining arguments in message commands.
         */
        public ParameterBuilder isRemainder() {
            return this.isRemainder(true);
        }

        /**
         * Sets if this {@link Parameter} is the catch-all for remaining arguments in message commands.
         * <br><br>
         * This is only used for messages commands. ({@link MessageCommandListener})
         *
         * @param remainder True if remainder.
         */
        public ParameterBuilder isRemainder(boolean remainder) {
            this.remainder = remainder;
            return this;
        }

        /**
         * Sets a callback method for {@link Parameter} autocompletion.
         *
         * @param autoComplete The autocomplete callback method.
         */
        public ParameterBuilder withAutoComplete(@NotNull Function<String, Mono<Void>> autoComplete) {
            this.autoComplete = autoComplete;
            return this;
        }

        /**
         * Define the {@link Parameter} choices a user can choose from.
         *
         * @param choices Variable number of choices to add.
         */
        public ParameterBuilder withChoices(@NotNull Map.Entry<String, Object>... choices) {
            return this.withChoices(Arrays.asList(choices));
        }

        /**
         * Define the {@link Parameter} choices a user can choose from.
         *
         * @param choices Collection of choices to add.
         */
        public ParameterBuilder withChoices(@NotNull Iterable<Map.Entry<String, Object>> choices) {
            choices.forEach(this.choices::put);
            return this;
        }

        /**
         * Sets the {@link Emoji} that isn't even used right now.
         *
         * @param emoji The emoji.
         */
        public ParameterBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the {@link Emoji} that isn't even used right now.
         *
         * @param emoji The emoji.
         */
        public ParameterBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets a custom validator for this {@link Parameter}.
         *
         * @param validator Custom validator.
         */
        public ParameterBuilder withValidator(@NotNull BiFunction<String, CommandContext<?>, Boolean> validator) {
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
                this.remainder,
                this.emoji,
                this.validator,
                this.autoComplete,
                this.choices
            );
        }

    }
    public static final Parameter DEFAULT = new ParameterBuilder(UUID.randomUUID(), "", "", Type.TEXT).isRemainder().build();

    public boolean isValid(@NotNull Optional<String> argument, @NotNull CommandContext<?> commandContext) {
        return argument.map(value -> this.isValid(value, commandContext)).orElse(!this.isRequired());
    }

    public boolean isValid(@Nullable String argument, @NotNull CommandContext<?> commandContext) {
        return this.getValidator().apply(StringUtil.defaultIfEmpty(argument, ""), commandContext);
    }

    public enum Type {

        TEXT(ApplicationCommandOption.Type.STRING, String.class, argument -> true),
        WORD(ApplicationCommandOption.Type.STRING, String.class, "Only Letters, Numbers and Underscores are allowed!", argument -> !argument.matches("\\w")),
        INTEGER(ApplicationCommandOption.Type.INTEGER, Integer.class, FormatUtil.format("Only Numbers between {0,number,#} and {1,number,#} are allowed!", Integer.MIN_VALUE, Integer.MAX_VALUE), argument -> {
            if (NumberUtil.isCreatable(argument) && !argument.contains(".")) {
                double value = NumberUtil.createBigDecimal(argument).doubleValue();
                return value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE;
            }

            return false;
        }),
        LONG(ApplicationCommandOption.Type.INTEGER, Long.class, FormatUtil.format("Only Numbers between {0,number,#} and {1,number,#} are allowed!", Long.MIN_VALUE, Long.MAX_VALUE), argument -> {
            if (NumberUtil.isCreatable(argument) && !argument.contains(".")) {
                double value = NumberUtil.createBigDecimal(argument).doubleValue();
                return value <= Long.MAX_VALUE && value >= Long.MIN_VALUE;
            }

            return false;
        }),
        DOUBLE(ApplicationCommandOption.Type.NUMBER, Double.class, FormatUtil.format("Only Numbers between {0,number,#} and {1,number,#} are allowed!", Double.MIN_VALUE, Double.MAX_VALUE), argument -> NumberUtil.isCreatable(argument)),
        BOOLEAN(ApplicationCommandOption.Type.BOOLEAN, Boolean.class, "Only `true` or `false` values are allowed!", argument -> argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("false")),
        MENTIONABLE(ApplicationCommandOption.Type.MENTIONABLE, String.class, "Only Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_PATTERN.matcher(argument).matches()),
        USER(ApplicationCommandOption.Type.USER, User.class, "Only User Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_USER_PATTERN.matcher(argument).matches()),
        CHANNEL(ApplicationCommandOption.Type.CHANNEL, Channel.class, "Only Channel Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_CHANNEL_PATTERN.matcher(argument).matches()),
        ROLE(ApplicationCommandOption.Type.ROLE, Role.class, "Only Role Mentions and IDs are allowed!", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_ROLE_PATTERN.matcher(argument).matches()),
        EMOJI(ApplicationCommandOption.Type.MENTIONABLE, ReactionEmoji.class, "Only Emojis are allowed!", argument -> EMOJI_PATTERN.matcher(argument).matches());

        @Getter private final @NotNull ApplicationCommandOption.Type optionType;
        @Getter private final @NotNull Class<?> javaType;
        @Getter private final @NotNull Optional<String> errorMessage;
        @Getter private final @NotNull Function<String, Boolean> validator;

        Type(@NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @NotNull Function<String, Boolean> validator) {
            this(optionType, javaType, (String) null, validator);
        }

        Type(@NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @Nullable String errorMessage, @NotNull Function<String, Boolean> validator) {
            this(optionType, javaType, Optional.ofNullable(errorMessage), validator);
        }

        Type(@NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @NotNull Optional<String> errorMessage, @NotNull Function<String, Boolean> validator) {
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

}
