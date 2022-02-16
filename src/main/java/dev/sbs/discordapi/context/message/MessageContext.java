package dev.sbs.discordapi.context.message;

import dev.sbs.discordapi.context.EventContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import reactor.core.publisher.Mono;

public interface MessageContext<T extends Event> extends EventContext<T> {

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getMessage().flatMap(Message::getChannel);
    }

    Mono<Message> getMessage();

    Snowflake getMessageId();

}
