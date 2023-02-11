package dev.sbs.discordapi.command.relationship;

import dev.sbs.discordapi.command.data.CommandData;
import org.jetbrains.annotations.NotNull;

public interface ClassRelationship extends Relationship {

    @NotNull Class<? extends CommandData> getCommandClass();

}
