package dev.simplified.discordapi.harness.gateway.dispatch;

import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.discordjson.json.gateway.MessageCreate;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the message/channel/guild gateway event dispatches. Currently the bot-authored {@code MESSAGE_CREATE}
 * used to drive a cached response's {@code onCreate} handler.
 */
@Log4j2
public final class MessageDispatches {

    private final HarnessEntities entities;

    public MessageDispatches(@NotNull HarnessEntities entities) {
        this.entities = entities;
    }

    /**
     * Builds a {@code MESSAGE_CREATE} dispatch for a bot-authored message with the given id, used to drive a
     * cached response's {@code onCreate} handler.
     *
     * @param messageId the message id (must match a cached response)
     * @return the message-create dispatch
     */
    public @NotNull Dispatch messageCreate(long messageId) {
        log.trace("Building MESSAGE_CREATE for message {}", messageId);
        return MessageCreate.builder().message(this.entities.interactionMessage(messageId)).build();
    }

}
