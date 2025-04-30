package dev.sbs.discordapi.response.component.type;

import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface EventComponent<T extends ComponentContext> {

    @NotNull Function<T, Mono<Void>> getInteraction();

    boolean isDeferEdit();

}
