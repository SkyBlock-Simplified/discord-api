package dev.sbs.discordapi.command.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CommandGroup {

    @Getter private final String name;
    @Getter private final String description;
    @Getter private final boolean required;

}
