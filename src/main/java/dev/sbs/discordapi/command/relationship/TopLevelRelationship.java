package dev.sbs.discordapi.command.relationship;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import org.jetbrains.annotations.NotNull;

public interface TopLevelRelationship extends Relationship {

    @NotNull ConcurrentList<? extends Relationship> getSubCommands();

}
