package dev.sbs.discordapi.command.parameter;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.discordapi.command.exception.InvalidParameterException;
import dev.sbs.discordapi.response.Attachment;
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

@Getter
@RequiredArgsConstructor
public class Argument {

    private final @NotNull Interaction interaction;
    private final @NotNull Parameter parameter;
    private final @NotNull ApplicationCommandInteractionOptionValue value;

    public @NotNull Attachment asAttachment() {
        return this.getValueAs(
            "attachment",
            value -> Attachment.of(this.getValue().asAttachment()),
            Parameter.Type.ATTACHMENT
        );
    }

    public boolean asBoolean() {
        return getValueAs("boolean", Boolean::parseBoolean, Parameter.Type.BOOLEAN);
    }

    public @NotNull Mono<Channel> asChannel() {
        return this.getValueAs(
            "channel",
            value -> this.getInteraction()
                .getClient()
                .getChannelById(this.asSnowflake()),
            Parameter.Type.CHANNEL
        );
    }

    public int asInteger() {
        return this.getValueAs("integer", Integer::parseInt, Parameter.Type.INTEGER);
    }

    public long asLong() {
        return this.getValueAs("long", Long::parseLong, Parameter.Type.LONG);
    }

    public double asDouble() {
        return this.getValueAs("double", Double::parseDouble, Parameter.Type.DOUBLE);
    }

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

    public @NotNull String asString() {
        return getValueAs("string", Function.identity(), Parameter.Type.TEXT, Parameter.Type.WORD);
    }

    public @NotNull Mono<User> asUser() {
        return getValueAs(
            "user",
            value -> this.getInteraction()
                .getClient()
                .getUserById(this.asSnowflake()),
            Parameter.Type.USER
        );
    }

    private <T> @NotNull T getValueAs(@NotNull String parsedTypeName, @NotNull Function<String, T> transformer, @NotNull Parameter.Type... allowedTypes) {
        if (!Arrays.asList(allowedTypes).contains(this.getParameter().getType())) {
            throw SimplifiedException.of(InvalidParameterException.class)
                .withMessage("Option value cannot be converted to %s.", parsedTypeName)
                .build();
        }

        return transformer.apply(this.getValue().getRaw());
    }

}
