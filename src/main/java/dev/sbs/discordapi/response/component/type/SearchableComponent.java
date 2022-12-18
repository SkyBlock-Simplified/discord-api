package dev.sbs.discordapi.response.component.type;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface SearchableComponent {

    @NotNull Optional<String> getIdentifier();

}
