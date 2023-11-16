package dev.sbs.discordapi.context.interaction.autocomplete;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.context.interaction.TypingContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface AutoCompleteContext extends TypingContext<ChatInputAutoCompleteEvent> {

    @NotNull Argument getArgument();

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEvent().getInteraction().getChannel();
    }

    @Override
    @NotNull SlashCommandReference getCommand();

    @NotNull
    @Override
    default Snowflake getChannelId() {
        return this.getEvent().getInteraction().getChannelId();
    }

    @Override
    default Mono<Guild> getGuild() {
        // Guild in ChatInputInteractionEvent#getInteraction Empty
        return Mono.justOrEmpty(this.getGuildId()).flatMap(guildId -> this.getDiscordBot().getGateway().getGuildById(guildId));
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default @NotNull User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

    @Override
    default @NotNull CommandReference.Type getType() {
        return CommandReference.Type.CHAT_INPUT;
    }

    static @NotNull AutoCompleteContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputAutoCompleteEvent event, @NotNull SlashCommandReference slashCommand, @NotNull Argument argument) {
        return new AutoCompleteContextImpl(discordBot, event, slashCommand, argument);
    }

}