package dev.sbs.discordapi.command;

import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandId {

    /**
     * The id of the discord command. Used for database storage.
     *
     * @return The discord command id.
     */
    @Pattern("[\\dA-Fa-f]{8}-[\\dA-Fa-f]{4}-[\\dA-Fa-f]{4}-[\\dA-Fa-f]{4}-[\\dA-Fa-f]{12}")
    @NotNull String value();

}
