package dev.sbs.discordapi.handler.response;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

@Getter
public class Followup extends BaseEntry {

    private final @NotNull String identifier;

    Followup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        super(channelId, userId, messageId, response, response);
        this.identifier = identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Followup followup = (Followup) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), followup.getIdentifier())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getIdentifier())
            .build();
    }

    @Override
    public boolean isFollowup() {
        return true;
    }

    public Mono<Followup> updateResponse(@NotNull Response response) {
        super.setUpdatedResponse(response);
        return Mono.just(this);
    }

}