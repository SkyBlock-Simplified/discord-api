package dev.sbs.discordapi.listener.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.UserCommandReference;
import dev.sbs.discordapi.context.interaction.deferrable.application.UserCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public final class UserCommandListener extends DiscordListener<UserInteractionEvent> {

    public UserCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull UserInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMap(commandData -> Mono.justOrEmpty(this.getCommandById(event.getCommandId().asLong())))
            .cast(UserCommandReference.class)
            .flatMap(command -> command.apply(
                UserCommandContext.of(
                    this.getDiscordBot(),
                    event,
                    command
                )
            ));
    }

}
