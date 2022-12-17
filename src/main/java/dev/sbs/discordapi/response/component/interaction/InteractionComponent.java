package dev.sbs.discordapi.response.component.interaction;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.component.Component;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

public abstract class InteractionComponent<T extends ComponentContext> extends Component {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InteractionComponent that)) return false;

        return new EqualsBuilder()
            .append(this.getUniqueId(), that.getUniqueId())
            .build();
    }

    public abstract Function<T, Mono<Void>> getInteraction();

    public abstract @NotNull UUID getUniqueId();

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(this.getUniqueId()).build();
    }

    public abstract boolean isDeferEdit();

}
