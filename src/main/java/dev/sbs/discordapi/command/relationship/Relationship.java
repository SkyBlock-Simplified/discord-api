package dev.sbs.discordapi.command.relationship;

import dev.sbs.api.data.model.discord.command_data.command_parents.CommandParentModel;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.UserPermission;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.util.base.DiscordHelper;
import discord4j.rest.util.Permission;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface Relationship {

    @NotNull String getName();
    @NotNull ConcurrentSet<Permission> getPermissions();
    @NotNull ConcurrentSet<UserPermission> getUserPermissions();

    @RequiredArgsConstructor(access = AccessLevel.PUBLIC)
    class Command implements ClassRelationship, DataRelationship {

        @Getter private final @NotNull Class<? extends dev.sbs.discordapi.command.Command> commandClass;
        @Getter private final @NotNull dev.sbs.discordapi.command.Command instance;

        public Embed createHelpEmbed() {
            return this.createHelpEmbed(true);
        }

        public Embed createHelpEmbed(boolean isSlashCommand) {
            String commandPath = this.getInstance().getCommandPath(isSlashCommand);
            ConcurrentList<Parameter> parameters = this.getInstance().getParameters();

            Embed.EmbedBuilder embedBuilder = Embed.builder()
                .withAuthor("Help", DiscordHelper.getEmoji("STATUS_INFO").map(Emoji::getUrl))
                .withTitle("Command :: {0}", this.getInstance().getConfig().getName())
                .withDescription(this.getInstance().getConfig().getLongDescription())
                .withTimestamp(Instant.now())
                .withColor(Color.DARK_GRAY);

            if (ListUtil.notEmpty(parameters)) {
                embedBuilder.withField(
                    "Usage",
                    FormatUtil.format(
                        """
                            <> - Required Parameters
                            [] - Optional Parameters
    
                            {0} {1}""",
                        commandPath,
                        StringUtil.join(
                            parameters.stream()
                                .map(parameter -> parameter.isRequired() ? FormatUtil.format("<{0}>", parameter.getName()) : FormatUtil.format("[{0}]", parameter.getName()))
                                .collect(Concurrent.toList()),
                            " "
                        )
                    )
                );
            }

            if (ListUtil.notEmpty(this.getInstance().getExampleArguments())) {
                embedBuilder.withField(
                    "Examples",
                    StringUtil.join(
                        this.getInstance()
                            .getExampleArguments()
                            .stream()
                            .map(example -> FormatUtil.format("{0} {1}", commandPath, example))
                            .collect(Concurrent.toList()),
                        "\n"
                    )
                );
            }

            return embedBuilder.build();
        }

        @Override
        public @NotNull String getDescription() {
            return this.getInstance().getConfig().getDescription();
        }

        @Override
        public @NotNull String getName() {
            return this.getInstance().getConfig().getName();
        }

        @Override
        public @NotNull ConcurrentSet<Permission> getPermissions() {
            return this.getInstance().getPermissions();
        }

        @Override
        public @NotNull ConcurrentSet<UserPermission> getUserPermissions() {
            return this.getInstance().getUserPermissions();
        }

        public @NotNull UUID getUniqueId() {
            return this.getInstance().getConfig().getUniqueId();
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PUBLIC)
    class Parent implements TopLevelRelationship, DataRelationship {

        @Getter private final @NotNull CommandParentModel value;
        @Getter private final @NotNull ConcurrentList<Command> subCommands;

        @Override
        public @NotNull String getDescription() {
            return this.getValue().getDescription();
        }

        @Override
        public @NotNull String getName() {
            return this.getValue().getKey();
        }

        @Override
        public @NotNull ConcurrentSet<Permission> getPermissions() {
            return Concurrent.newUnmodifiableSet();
        }

        @Override
        public @NotNull ConcurrentSet<UserPermission> getUserPermissions() {
            return Concurrent.newUnmodifiableSet();
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PUBLIC)
    class Root implements TopLevelRelationship {

        @Getter private final @NotNull Optional<CommandParentModel> value;
        @Getter private final @NotNull ConcurrentList<Relationship> subCommands;

        @Override
        public @NotNull String getName() {
            return this.getValue()
                .map(CommandParentModel::getKey)
                .orElse("");
        }

        public @NotNull ConcurrentList<Command> getCommands() {
            return this.getSubCommands()
                .stream()
                .filter(Command.class::isInstance)
                .map(relationship -> (Command) relationship)
                .collect(Concurrent.toList())
                .toUnmodifiableList();
        }

        public @NotNull ConcurrentList<Parent> getParentCommands() {
            return this.getSubCommands()
                .stream()
                .filter(Parent.class::isInstance)
                .map(relationship -> (Parent) relationship)
                .collect(Concurrent.toList())
                .toUnmodifiableList();
        }

        @Override
        public @NotNull ConcurrentSet<Permission> getPermissions() {
            return Concurrent.newUnmodifiableSet();
        }

        @Override
        public @NotNull ConcurrentSet<UserPermission> getUserPermissions() {
            return Concurrent.newUnmodifiableSet();
        }

    }

}
