package dev.sbs.discordapi.response.component.interaction;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class InteractionComponent extends Component implements IdentifiableComponent {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InteractionComponent that)) return false;

        return new EqualsBuilder()
            .append(this.getUniqueId(), that.getUniqueId())
            .build();
    }

    public abstract @NotNull UUID getUniqueId();

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(this.getUniqueId()).build();
    }

}
