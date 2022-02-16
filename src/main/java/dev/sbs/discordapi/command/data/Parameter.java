package dev.sbs.discordapi.command.data;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.response.Emoji;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.Getter;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Parameter {

    public static final Parameter DEFAULT = new Parameter("", "", Type.TEXT, false, true);
    private static final BiFunction<String, CommandContext<?>, Boolean> NOOP_HANDLER = (s_, c_) -> true;
    private static final Pattern MENTIONABLE_PATTERN = Pattern.compile("<(?:@!?&?|#)[\\d]+>");
    private static final Pattern MENTIONABLE_USER_PATTERN = Pattern.compile("<@!?[\\d]+>");
    private static final Pattern MENTIONABLE_ROLE_PATTERN = Pattern.compile("<@!?&[\\d]+>");
    private static final Pattern MENTIONABLE_CHANNEL_PATTERN = Pattern.compile("<#[\\d]+>");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("<:[\\w]+:[\\d]+>");
    @Getter private final String name;
    @Getter private final Type type;
    @Getter private final String description;
    @Getter private final boolean required;
    @Getter private final boolean remainder;
    @Getter private final Optional<Emoji> emoji;
    @Getter private final BiFunction<String, CommandContext<?>, Boolean> validator;

    public Parameter(String name, String description, Type type) {
        this(name, description, type, true);
    }

    public Parameter(String name, String description, Type type, boolean required) {
        this(name, description, type, required, (Emoji) null);
    }

    public Parameter(String name, String description, Type type, boolean required, boolean remainder) {
        this(name, description, type, required, remainder, (Emoji) null);
    }

    public Parameter(String name, String description, Type type, Emoji emoji) {
        this(name, description, type, true, emoji);
    }

    public Parameter(String name, String description, Type type, boolean required, Emoji emoji) {
        this(name, description, type, required, false, emoji, null);
    }

    public Parameter(String name, String description, Type type, boolean required, boolean remainder, Emoji emoji) {
        this(name, description, type, required, remainder, emoji, null);
    }

    public Parameter(String name, String description, Type type, BiFunction<String, CommandContext<?>, Boolean> validator) {
        this(name, description, type, true, validator);
    }

    public Parameter(String name, String description, Type type, boolean required, BiFunction<String, CommandContext<?>, Boolean> validator) {
        this(name, description, type, required, false, null, validator);
    }

    public Parameter(String name, String description, Type type, boolean required, boolean remainder, BiFunction<String, CommandContext<?>, Boolean> validator) {
        this(name, description, type, required, remainder, null, validator);
    }

    public Parameter(String name, String description, Type type, boolean required, boolean remainder, Emoji emoji, BiFunction<String, CommandContext<?>, Boolean> validator) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.remainder = remainder;
        this.emoji = Optional.ofNullable(emoji);
        this.validator = validator == null ? NOOP_HANDLER : validator;
    }

    public boolean isValid(String argument, CommandContext<?> commandContext) {
        return this.getValidator().apply(argument, commandContext);
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

        @Getter private final ApplicationCommandOption.Type optionType;
        @Getter private final Class<?> javaType;
        @Getter private final Optional<String> errorMessage;
        @Getter private final Function<String, Boolean> validator;

        Type(ApplicationCommandOption.Type optionType, Class<?> javaType, Function<String, Boolean> validator) {
            this(optionType, javaType, (String) null, validator);
        }

        Type(ApplicationCommandOption.Type optionType, Class<?> javaType, String errorMessage, Function<String, Boolean> validator) {
            this(optionType, javaType, Optional.ofNullable(errorMessage), validator);
        }

        Type(ApplicationCommandOption.Type optionType, Class<?> javaType, Optional<String> errorMessage, Function<String, Boolean> validator) {
            this.optionType = optionType;
            this.javaType = javaType;
            this.errorMessage = errorMessage;
            this.validator = validator;
        }

        public boolean isValid(String argument) {
            return this.getValidator().apply(argument);
        }

    }

}
