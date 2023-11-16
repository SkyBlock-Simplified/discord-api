package dev.sbs.discordapi.response.page.item;

import dev.sbs.discordapi.response.embed.Field;
import org.jetbrains.annotations.NotNull;

public interface SingletonFieldItem {

    @NotNull Field getRenderField();

}
