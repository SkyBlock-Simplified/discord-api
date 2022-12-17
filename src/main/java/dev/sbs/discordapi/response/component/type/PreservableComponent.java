package dev.sbs.discordapi.response.component.type;

public interface PreservableComponent {

    boolean isPreserved();

    default boolean notPreserved() {
        return !this.isPreserved();
    }

}
