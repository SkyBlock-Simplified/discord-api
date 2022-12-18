package dev.sbs.discordapi.response.menu.item.field;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.menu.item.MenuItem;
import discord4j.core.spec.EmbedCreateFields;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class FieldMenuItem extends MenuItem {

    @Getter protected final @NotNull Field field;

    protected FieldMenuItem(@NotNull UUID uniqueId, @NotNull Field field) {
        super(uniqueId, Type.FIELD);
        this.field = field;
    }

    public EmbedCreateFields.Field getD4jField() {
        return EmbedCreateFields.Field.of(
            FormatUtil.format(
                "{0}{1}",
                this.getField().getEmoji().map(Emoji::asSpacedFormat).orElse(""),
                this.getField().getName().orElse(Field.ZERO_WIDTH_SPACE)
            ),
            this.getField().getValue().orElse(Field.ZERO_WIDTH_SPACE),
            this.getField().isInline()
        );
    }

}