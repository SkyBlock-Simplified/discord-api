package dev.sbs.discordapi.command.parameter;

import dev.sbs.discordapi.command.exception.ParameterException;
import dev.sbs.discordapi.component.media.Attachment;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.function.Function;

/**
 * A resolved slash command argument, wrapping the raw
 * {@link ApplicationCommandInteractionOptionValue} together with its
 * {@link Parameter} definition and the originating {@link Interaction}.
 *
 * <p>
 * Type-safe accessors ({@link #asString()}, {@link #asInteger()}, {@link #asUser()}, etc.)
 * verify that the underlying {@link Parameter.Type} is compatible before converting the
 * raw value, throwing a {@link ParameterException} on mismatch.
 *
 * @see Parameter
 */
@Getter
@RequiredArgsConstructor
public class Argument {

    /**
     * The Discord interaction this argument originated from.
     */
    private final @NotNull Interaction interaction;

    /**
     * The parameter definition this argument corresponds to.
     */
    private final @NotNull Parameter parameter;

    /**
     * The raw option value supplied by the user.
     */
    private final @NotNull ApplicationCommandInteractionOptionValue value;

    /**
     * Converts this argument to an {@link Attachment}.
     *
     * @return the resolved attachment
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#ATTACHMENT}
     */
    public @NotNull Attachment asAttachment() {
        return this.getValueAs(
            "attachment",
            value -> Attachment.from(this.getValue().asAttachment()).build(),
            Parameter.Type.ATTACHMENT
        );
    }

    /**
     * Converts this argument to a boolean value.
     *
     * @return the parsed boolean
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#BOOLEAN}
     */
    public boolean asBoolean() {
        return getValueAs("boolean", Boolean::parseBoolean, Parameter.Type.BOOLEAN);
    }

    /**
     * Resolves this argument to a Discord {@link Channel}.
     *
     * @return a mono emitting the resolved channel
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#CHANNEL}
     */
    public @NotNull Mono<Channel> asChannel() {
        return this.getValueAs(
            "channel",
            value -> this.getInteraction()
                .getClient()
                .getChannelById(this.asSnowflake()),
            Parameter.Type.CHANNEL
        );
    }

    /**
     * Converts this argument to an integer value.
     *
     * @return the parsed integer
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#INTEGER}
     */
    public int asInteger() {
        return this.getValueAs("integer", Integer::parseInt, Parameter.Type.INTEGER);
    }

    /**
     * Converts this argument to a long value.
     *
     * @return the parsed long
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#LONG}
     */
    public long asLong() {
        return this.getValueAs("long", Long::parseLong, Parameter.Type.LONG);
    }

    /**
     * Converts this argument to a double value.
     *
     * @return the parsed double
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#DOUBLE}
     */
    public double asDouble() {
        return this.getValueAs("double", Double::parseDouble, Parameter.Type.DOUBLE);
    }

    /**
     * Formats this argument as a Discord mention string (e.g., {@code <@123>}, {@code <#456>}).
     *
     * @return the formatted mention string
     * @throws ParameterException if the parameter type is not one of
     *         {@link Parameter.Type#USER}, {@link Parameter.Type#ROLE},
     *         {@link Parameter.Type#CHANNEL}, or {@link Parameter.Type#MENTIONABLE}
     */
    public @NotNull String asMentionable() {
        return this.getValueAs(
            "mentionable",
            value -> String.format(
                "<%s%s%s>",
                (this.getParameter().getType() == Parameter.Type.CHANNEL ? "#" : "@"),
                (this.getParameter().getType() == Parameter.Type.ROLE ? "&" : ""),
                this.getValue()
            ),
            Parameter.Type.USER,
            Parameter.Type.ROLE,
            Parameter.Type.CHANNEL,
            Parameter.Type.MENTIONABLE
        );
    }

    /**
     * Resolves this argument to a Discord {@link Role}.
     *
     * @return a mono emitting the resolved role
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#ROLE}
     */
    public @NotNull Mono<Role> asRole() {
        return this.getValueAs(
            "role",
            value -> this.getInteraction()
                .getClient()
                .getRoleById(
                    this.getInteraction().getGuildId().orElseThrow(),
                    this.asSnowflake()
                ),
            Parameter.Type.ROLE
        );
    }

    /**
     * Converts this argument to a {@link Snowflake} identifier.
     *
     * @return the parsed snowflake
     * @throws ParameterException if the parameter type is not one of
     *         {@link Parameter.Type#USER}, {@link Parameter.Type#ROLE},
     *         {@link Parameter.Type#CHANNEL}, or {@link Parameter.Type#MENTIONABLE}
     */
    public @NotNull Snowflake asSnowflake() {
        return this.getValueAs(
            "snowflake",
            Snowflake::of,
            Parameter.Type.USER,
            Parameter.Type.ROLE,
            Parameter.Type.CHANNEL,
            Parameter.Type.MENTIONABLE
        );
    }

    /**
     * Returns this argument's raw value as a string.
     *
     * @return the raw string value
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#TEXT}
     *         or {@link Parameter.Type#WORD}
     */
    public @NotNull String asString() {
        return getValueAs("string", Function.identity(), Parameter.Type.TEXT, Parameter.Type.WORD);
    }

    /**
     * Resolves this argument to a Discord {@link User}.
     *
     * @return a mono emitting the resolved user
     * @throws ParameterException if the parameter type is not {@link Parameter.Type#USER}
     */
    public @NotNull Mono<User> asUser() {
        return getValueAs(
            "user",
            value -> this.getInteraction()
                .getClient()
                .getUserById(this.asSnowflake()),
            Parameter.Type.USER
        );
    }

    /**
     * Validates that this argument's parameter type is among the allowed types, then
     * transforms the raw string value using the given function.
     *
     * @param parsedTypeName the human-readable name of the target type for error messages
     * @param transformer the function that converts the raw string value
     * @param allowedTypes the parameter types permitted for this conversion
     * @param <T> the return type
     * @return the transformed value
     * @throws ParameterException if the parameter type is not in {@code allowedTypes}
     */
    private <T> @NotNull T getValueAs(@NotNull String parsedTypeName, @NotNull Function<String, T> transformer, @NotNull Parameter.Type... allowedTypes) {
        if (!Arrays.asList(allowedTypes).contains(this.getParameter().getType())) {
            throw new ParameterException(
                this.getParameter(),
                this.getValue().getRaw(),
                "Option value cannot be converted to %s.",
                parsedTypeName
            );
        }

        return transformer.apply(this.getValue().getRaw());
    }

}
