package dev.sbs.discordapi.command.relationship;

import org.jetbrains.annotations.NotNull;

public interface DataRelationship extends Relationship {

    @NotNull String getDescription();

}
