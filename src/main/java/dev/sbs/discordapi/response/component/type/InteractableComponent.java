package dev.sbs.discordapi.response.component.type;

import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface InteractableComponent<T extends ComponentContext> extends IdentifiableComponent {

    @NotNull Function<T, Mono<Void>> getInteraction();

    boolean isDeferEdit();

}
