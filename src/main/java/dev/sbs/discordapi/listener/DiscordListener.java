package dev.sbs.discordapi.listener;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.base.DiscordHelper;
import discord4j.core.event.domain.Event;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.util.function.Function;

public abstract class DiscordListener<T extends Event> extends DiscordHelper implements Function<T, Publisher<Void>> {

    @Getter private final @NotNull Class<T> eventClass;
    @Getter private final @NotNull String title;

    public DiscordListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.eventClass = Reflection.getSuperClass(this);
        this.title = StringUtil.join(this.getClass().getSimpleName().split("(?=\\p{Upper})"), " ");
    }

}
