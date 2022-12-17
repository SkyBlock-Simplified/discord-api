package dev.sbs.discordapi.response.component.type;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface IdentifiableComponent {

    @NotNull UUID getUniqueId();

}
