package dev.sbs.discordapi.response.component.action;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.discordapi.context.message.interaction.component.ComponentContext;
import dev.sbs.discordapi.response.component.Component;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

public abstract class ActionComponent<T extends ComponentContext> extends Component {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionComponent that)) return false;

        return new EqualsBuilder().append(this.getUniqueId(), that.getUniqueId()).build();
    }

    @Override
    public abstract discord4j.core.object.component.ActionComponent getD4jComponent();

    public abstract Function<T, Mono<Void>> getInteraction();

    public abstract @NotNull UUID getUniqueId();

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(this.getUniqueId()).build();
    }

    public abstract boolean isDeferEdit();

    public abstract boolean isPaging();

}
