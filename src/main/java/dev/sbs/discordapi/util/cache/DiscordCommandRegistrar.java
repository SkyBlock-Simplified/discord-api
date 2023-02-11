package dev.sbs.discordapi.util.cache;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.command_data.command_configs.CommandConfigModel;
import dev.sbs.api.data.model.discord.command_data.command_configs.CommandConfigSqlModel;
import dev.sbs.api.data.model.discord.command_data.command_groups.CommandGroupModel;
import dev.sbs.api.data.model.discord.command_data.command_parents.CommandParentModel;
import dev.sbs.api.data.sql.SqlRepository;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.data.tuple.Pair;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.relationship.DataRelationship;
import dev.sbs.discordapi.command.relationship.Relationship;
import dev.sbs.discordapi.util.base.DiscordHelper;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class DiscordCommandRegistrar extends DiscordHelper {

    private final static Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    @Getter private final @NotNull Relationship.Root rootCommandRelationship;
    @Getter private final @NotNull Optional<CommandParentModel> prefix;
    @Getter private final @NotNull ConcurrentSet<Class<? extends Command>> commands;

    DiscordCommandRegistrar(@NotNull DiscordBot discordBot, @NotNull Optional<CommandParentModel> optionalPrefix, @NotNull ConcurrentSet<Class<? extends Command>> commands) {
        super(discordBot);

        this.getLog().info("Validating Commands");
        ConcurrentMap<Class<? extends Command>, Command> initializedCommands = this.validateCommands(commands, optionalPrefix);

        this.getLog().info("Registering Commands");
        this.prefix = optionalPrefix;
        this.commands = Concurrent.newUnmodifiableSet(commands);
        this.rootCommandRelationship = this.getRootRelationshipTree(initializedCommands, optionalPrefix);
    }

    private Relationship.Root getRootRelationshipTree(@NotNull ConcurrentMap<Class<? extends Command>, Command> commands, @NotNull Optional<CommandParentModel> optionalPrefix) {
        ConcurrentList<Relationship> subCommandRelationships = Concurrent.newList();

        // Top-Level Commands
        commands.stream()
            .filter(commandEntry -> Objects.isNull(commandEntry.getValue().getConfig().getParent()))
            .map(commandEntry -> new Relationship.Command(
                commandEntry.getKey(),
                commandEntry.getValue()
            ))
            .forEach(subCommandRelationships::add);

        // SubCommands
        SimplifiedApi.getRepositoryOf(CommandParentModel.class)
            .matchAll(CommandParentModel::notPrefix)
            .stream()
            .map(commandParentModel -> new Relationship.Parent(
                commandParentModel,
                this.getCommandRelationships(
                    commands,
                    commandParentModel
                )
            ))
            .forEach(subCommandRelationships::add);

        return new Relationship.Root(
            optionalPrefix,
            subCommandRelationships
        );
    }

    private ConcurrentList<Relationship.Command> getCommandRelationships(@NotNull ConcurrentMap<Class<? extends Command>, Command> commands, @NotNull CommandParentModel commandParentModel) {
        return commands.stream()
            .filter(commandEntry -> Objects.nonNull(commandEntry.getValue().getConfig().getParent()))
            .filter(commandEntry -> Objects.equals(commandEntry.getValue().getConfig().getParent(), commandParentModel))
            .map(commandEntry -> new Relationship.Command(
                commandEntry.getKey(),
                commandEntry.getValue()
            ))
            .collect(Concurrent.toList());
    }

    private @NotNull ConcurrentMap<Class<? extends Command>, Command> validateCommands(ConcurrentSet<Class<? extends Command>> commands, Optional<CommandParentModel> optionalPrefix) {
        ConcurrentList<CommandConfigModel> commandConfigModels = SimplifiedApi.getRepositoryOf(CommandConfigModel.class).findAll();

        ConcurrentMap<Class<? extends Command>, CommandConfigModel> commandConfigMap = commands.stream()
            .map(commandClass -> Pair.of(
                commandClass,
                getCommandAnnotation(commandClass)
            ))
            .filter(commandLink -> {
                if (commandLink.getRight().isEmpty()) {
                    this.getLog().info("''{0}'' command must be annotated by the @CommandId annotation!", commandLink.getLeft().getName());
                    return false;
                }

                return true;
            })
            .map(commandLink -> {
                UUID commandId = StringUtil.toUUID(commandLink.getRight().get().value());
                Optional<CommandConfigModel> commandConfigModel = commandConfigModels.findFirst(CommandConfigModel::getUniqueId, commandId);

                // Create Missing Command Config
                if (commandConfigModel.isEmpty()) {
                    CommandConfigSqlModel newCommandConfigModel = new CommandConfigSqlModel();
                    newCommandConfigModel.setUniqueId(commandId);
                    newCommandConfigModel.setName(commandId.toString().substring(0, 8));
                    newCommandConfigModel.setDeveloperOnly(true);
                    newCommandConfigModel.setEnabled(true);
                    newCommandConfigModel.setInheritingPermissions(true);
                    this.getLog().info("Creating new CommandConfigModel for ''{0}''.", commandLink.getLeft().getName());
                    commandConfigModel = Optional.of(((SqlRepository<CommandConfigSqlModel>) SimplifiedApi.getRepositoryOf(CommandConfigSqlModel.class)).save(newCommandConfigModel));
                }

                return Pair.of(commandLink.getLeft(), commandConfigModel.get());
            })
            .filter(commandEntry -> {
                if (!validCommandPattern.matcher(commandEntry.getRight().getName()).matches()) {
                    this.getLog().info("''{0}'' command name ''{1}'' must only contain english characters!", this.getClass().getName(), commandEntry.getRight().getName());
                    return false;
                }

                String prefixName = optionalPrefix.map(CommandParentModel::getKey).orElse("");
                if (prefixName.equalsIgnoreCase(commandEntry.getRight().getName())) {
                    this.getLog().info("Command ''{0}'' conflicts with prefix ''{1}''!", commandEntry.getRight().getName(), prefixName);
                    return false;
                }

                return true;
            })
            .collect(Concurrent.toMap());

        return commandConfigMap.stream()
            .filter(commandEntry -> {
                Optional<Map.Entry<Class<? extends Command>, CommandConfigModel>> commandOverlap = commandConfigMap.stream() // Compare Commands
                    .filter(compareEntry -> !commandEntry.getValue().getUniqueId().equals(compareEntry.getValue().getUniqueId()))
                    .filter(compareEntry -> Objects.equals(commandEntry.getValue().getParent(), compareEntry.getValue().getParent()))
                    .filter(compareEntry -> commandEntry.getValue().getName().equalsIgnoreCase(compareEntry.getValue().getName()))
                    .findAny();

                if (commandOverlap.isPresent()) {
                    this.getLog().info("Command ''{0}'' conflicts with ''{1}''!", commandEntry.getValue().getName(), commandOverlap.get().getValue().getName());
                    return false;
                }

                return true;
            })
            .map(commandEntry -> Pair.of(
                commandEntry.getKey(),
                Reflection.of(commandEntry.getKey()).newInstance(this.getDiscordBot())
            ))
            .collect(Concurrent.toMap());
    }

    public final ConcurrentList<ApplicationCommandRequest> getSlashCommands() {

        return this.getRootCommandRelationship()
            .getSubCommands()
            .stream()
            .filter(DataRelationship.class::isInstance)
            .map(DataRelationship.class::cast)
            .map(relationship -> ApplicationCommandRequest.builder() // Create Command
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .name(relationship.getName())
                .description(relationship.getDescription())
                .defaultPermission(true)
                // Handle SubCommand Groups
                .addAllOptions(
                    Concurrent.newList(
                            (relationship instanceof Relationship.Parent ?
                                ((Relationship.Parent) relationship).getSubCommands() :
                                Concurrent.newList())
                        )
                        .stream()
                        .flatMap(subRelationship -> subRelationship.getInstance().getGroup().stream())
                        .distinct()
                        .map(commandGroup -> ApplicationCommandOptionData.builder()
                            .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
                            .name(commandGroup.getKey().toLowerCase())
                            .description(commandGroup.getDescription())
                            .required(commandGroup.isRequired())
                            .addAllOptions(
                                Concurrent.newList(
                                        (relationship instanceof Relationship.Parent ?
                                            ((Relationship.Parent) relationship).getSubCommands() :
                                            Concurrent.newList())
                                    )
                                    .matchAll(subRelationship -> subRelationship.getInstance().getGroup().isPresent())
                                    .stream()
                                    .filter(subRelationship -> Objects.equals(
                                        subRelationship.getInstance()
                                            .getGroup()
                                            .map(CommandGroupModel::getKey)
                                            .map(String::toLowerCase)
                                            .orElse(""),
                                        commandGroup.getKey().toLowerCase()
                                    ))
                                    .map(this::buildCommand)
                                    .collect(Concurrent.toList())
                            )
                            .build()
                        )
                        .collect(Concurrent.toList())
                )
                // Handle SubCommands
                .addAllOptions(
                    Concurrent.newList(
                            (relationship instanceof Relationship.Parent ?
                                ((Relationship.Parent) relationship).getSubCommands() :
                                Concurrent.newList())
                        )
                        .matchAll(subRelationship -> subRelationship.getInstance().getGroup().isEmpty())
                        .stream()
                        .map(this::buildCommand)
                        .collect(Concurrent.toList())
                )
                // Handle Parameters
                .addAllOptions(
                    Concurrent.newList(
                        (relationship instanceof Relationship.Command ?
                            ((Relationship.Command) relationship).getInstance().getParameters() :
                            Concurrent.newList())
                        )
                        .stream()
                        .map(this::buildParameter)
                        .collect(Concurrent.toList())
                )
                .build()
            )
            .collect(Concurrent.toList());
    }

    public final ApplicationCommandOptionData buildCommand(Relationship.Command relationship) {
        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
            .name(relationship.getInstance().getConfig().getName())
            .description(relationship.getInstance().getDescription())
            .addAllOptions(
                relationship.getInstance()
                    .getParameters()
                    .stream()
                    .map(this::buildParameter).collect(Concurrent.toList())
            )
            .build();
    }

    public final ApplicationCommandOptionData buildParameter(Parameter parameter) {
        return ApplicationCommandOptionData.builder()
            .type(parameter.getType().getOptionType().getValue())
            .name(parameter.getName())
            .description(parameter.getDescription())
            .required(parameter.isRequired())
            .choices(
                parameter.getChoices()
                    .stream()
                    .map(entry -> ApplicationCommandOptionChoiceData.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build()
                    )
                    .collect(Concurrent.toList())
            )
            .build();
    }

    public Mono<Void> updateSlashCommands() {
        return this.getDiscordBot()
            .getGateway()
            .getRestClient()
            .getApplicationService()
            .bulkOverwriteGuildApplicationCommand(
                this.getDiscordBot().getClientId().asLong(),
                this.getDiscordBot().getMainGuild().getId().asLong(),
                this.getSlashCommands()
            )
            .then();
    }

    public static Builder builder(@NotNull DiscordBot discordBot) {
        return new Builder(discordBot);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<DiscordCommandRegistrar> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<? extends Command>> subCommands = Concurrent.newSet();
        private Optional<CommandParentModel> prefix = Optional.empty();

        public Builder addCommand(@NotNull Class<? extends Command> subCommand) {
            this.subCommands.add(subCommand);
            return this;
        }

        public Builder withPrefix(@Nullable Optional<CommandParentModel> prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder withCommands(@NotNull Iterable<Class<? extends Command>> subCommands) {
            subCommands.forEach(this.subCommands::add);
            return this;
        }

        @Override
        public DiscordCommandRegistrar build() {
            return new DiscordCommandRegistrar(
                this.discordBot,
                this.prefix,
                this.subCommands
            );
        }

    }

}
