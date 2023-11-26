package dev.sbs.discordapi.command.parameter;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.discordapi.command.exception.parameter.InvalidParameterException;
import dev.sbs.discordapi.response.Attachment;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.function.Function;

public record Argument(
    @Getter @NotNull Interaction interaction,
    @Getter @NotNull Parameter parameter,
    @Getter @NotNull ApplicationCommandInteractionOptionValue value
) {

    public @NotNull Attachment asAttachment() {
        return this.getValueAs(
            "attachment",
            value -> Attachment.of(this.value().asAttachment()),
            Parameter.Type.ATTACHMENT
        );
    }

    public boolean asBoolean() {
        return getValueAs("boolean", Boolean::parseBoolean, Parameter.Type.BOOLEAN);
    }

    public @NotNull Mono<Channel> asChannel() {
        return this.getValueAs(
            "channel",
            value -> this.interaction()
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
                (this.parameter().getType() == Parameter.Type.CHANNEL ? "#" : "@"),
                (this.parameter().getType() == Parameter.Type.ROLE ? "&" : ""),
                this.value()
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
            value -> this.interaction()
                .getClient()
                .getRoleById(
                    this.interaction().getGuildId().orElseThrow(),
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
            value -> this.interaction()
                .getClient()
                .getUserById(this.asSnowflake()),
            Parameter.Type.USER
        );
    }

    private <T> @NotNull T getValueAs(@NotNull String parsedTypeName, @NotNull Function<String, T> transformer, @NotNull Parameter.Type... allowedTypes) {
        if (!Arrays.asList(allowedTypes).contains(this.parameter().getType())) {
            throw SimplifiedException.of(InvalidParameterException.class)
                .withMessage("Option value cannot be converted to %s.", parsedTypeName)
                .build();
        }

        return transformer.apply(this.value().getRaw());
    }

}
