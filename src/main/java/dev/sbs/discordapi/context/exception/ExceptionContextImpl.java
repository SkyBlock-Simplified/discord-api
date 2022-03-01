package dev.sbs.discordapi.context.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.embed.Embed;
import discord4j.core.event.domain.Event;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class ExceptionContextImpl<T extends Event> implements ExceptionContext<T> {

    @Getter private final DiscordBot discordBot;
    @Getter private final EventContext<T> eventContext;
    @Getter private final UUID uniqueId = UUID.randomUUID();
    @Getter private final Throwable exception;
    @Getter private final String title;
    @Getter private final Optional<Consumer<Embed.EmbedBuilder>> embedBuilderConsumer;

}
