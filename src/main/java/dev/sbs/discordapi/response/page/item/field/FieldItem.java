package dev.sbs.discordapi.response.page.item.field;

import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.page.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface FieldItem<T> extends Item {

    default @NotNull Field getRenderField() {
        return this.getRenderField(this.isInline());
    }

    default @NotNull Field getRenderField(boolean inline) {
        return Field.builder()
            .withName(this.getRenderName())
            .withValue(this.getRenderValue())
            .isInline(inline)
            .build();
    }

    default @NotNull String getRenderName() {
        return this.getOption().getLabel();
    }

    @NotNull String getRenderValue();

    @Override
    default @NotNull Type getType() {
        return Type.FIELD;
    }

    @NotNull Optional<T> getValue();

    boolean isInline();

    @Override
    default boolean isSingular() {
        return false;
    }

}
