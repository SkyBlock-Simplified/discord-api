package dev.sbs.discordapi.response.component;

public interface PreservableComponent {

    boolean isPreserved();

    default boolean notPreserved() {
        return !this.isPreserved();
    }

}
