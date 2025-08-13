package dev.sbs.discordapi.listener.deferrable.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.deferrable.command.MessageCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class MessageCommandListener extends DiscordListener<MessageInteractionEvent> {

    public MessageCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    @SuppressWarnings("all")
    public Publisher<Void> apply(@NotNull MessageInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getDiscordBot().getCommandHandler().getCommandsById(event.getCommandId().asLong())))
            .single()
            .map(command -> (DiscordCommand<MessageCommandContext>) command)
            .flatMap(command -> command.apply(
                MessageCommandContext.of(
                    this.getDiscordBot(),
                    event,
                    command.getStructure()
                )
            ))
            .subscribeOn(Schedulers.boundedElastic());
    }

}
