package dev.sbs.discordapi.listener.deferrable.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.MessageCommandReference;
import dev.sbs.discordapi.context.deferrable.command.MessageCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class MessageCommandListener extends DiscordListener<MessageInteractionEvent> {

    public MessageCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull MessageInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getCommandsById(event.getCommandId().asLong())))
            .single()
            .cast(MessageCommandReference.class)
            .flatMap(command -> command.apply(
                MessageCommandContext.of(
                    this.getDiscordBot(),
                    event,
                    command
                )
            ));
    }

}
