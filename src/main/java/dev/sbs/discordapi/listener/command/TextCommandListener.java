package dev.sbs.discordapi.listener.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.context.message.text.TextCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public class TextCommandListener extends DiscordListener<MessageCreateEvent> {

    public TextCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(MessageCreateEvent messageCreateEvent) {
        return Mono.just(messageCreateEvent)
            .filter(event -> !event.getMessage().getAuthor().map(User::isBot).orElse(true)) // Ignore Bots
            .filter(event -> StringUtil.isNotEmpty(event.getMessage().getContent()))
            .flatMap(event -> {
                String message = StringUtil.defaultIfEmpty(event.getMessage().getContent(), "");
                String[] messageArguments = message.split(" ");

                return Mono.justOrEmpty(this.getDiscordBot().getCommandRegistrar().getRootCommandRelationship())
                    .filter(rootRelationship -> this.doesCommandMatch(rootRelationship, messageArguments[0]))
                    .flatMap(prefixCommand -> Mono.justOrEmpty(this.getDeepestCommand(messageArguments)))
                    .flatMap(relationship -> {
                        ConcurrentList<String> remainingArguments = Concurrent.newList(messageArguments);

                        // Trim Parent Commands
                        relationship.getInstance()
                            .getParentCommands()
                            .stream()
                            .filter(parentRelationship -> this.doesCommandMatch(parentRelationship, remainingArguments.get(0)))
                            .forEach(__ -> remainingArguments.remove(0));

                        // Store Used Alias
                        String commandAlias = remainingArguments.get(0);

                        // Trim Command
                        if (this.doesCommandMatch(relationship, remainingArguments.get(0)))
                            remainingArguments.remove(0);

                        // Build Arguments
                        ConcurrentList<Argument> arguments = Concurrent.newList();
                        if (ListUtil.notEmpty(relationship.getInstance().getParameters())) {
                            ConcurrentList<Parameter> parameters = relationship.getInstance().getParameters();

                            for (int i = 0; i < parameters.size(); i++) {
                                Parameter parameter = parameters.get(i);

                                if (parameter.isRemainder()) {
                                    arguments.add(new Argument(parameter, this.getRemainder(remainingArguments)));
                                    remainingArguments.clear();
                                    break;
                                }

                                // Parse Argument Data
                                Argument.Data argumentData = new Argument.Data(ListUtil.notEmpty(remainingArguments) ? remainingArguments.remove(0) : null);
                                arguments.add(new Argument(parameter, argumentData));
                            }
                        }

                        // Handle Remaining Arguments
                        if (ListUtil.notEmpty(remainingArguments))
                            arguments.add(new Argument(Parameter.DEFAULT, this.getRemainder(remainingArguments)));

                        // Build Context
                        TextCommandContext textCommandContext = TextCommandContext.of(
                            this.getDiscordBot(),
                            event,
                            relationship,
                            commandAlias,
                            arguments
                        );

                        // Apply Command
                        return relationship.getInstance().apply(textCommandContext);
                    });
            });
    }

    private ConcurrentList<Argument.Data> getRemainder(ConcurrentList<String> remainingArguments) {
        return remainingArguments.stream()
            .map(Argument.Data::new)
            .collect(Concurrent.toList());
    }

}