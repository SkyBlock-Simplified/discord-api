package dev.sbs.discordapi.listener;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.core.event.domain.Event;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.util.function.Function;

@Getter
public abstract class DiscordListener<T extends Event> extends DiscordReference implements Function<T, Publisher<Void>> {

    private final @NotNull Class<T> eventClass;
    private final @NotNull String title;

    public DiscordListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.eventClass = Reflection.getSuperClass(this);
        this.title = StringUtil.join(this.getClass().getSimpleName().split("(?=\\p{Upper})"), " ");
    }

}
