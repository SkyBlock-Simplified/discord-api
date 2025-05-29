package dev.sbs.discordapi.response.component.media;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.ClassBuilder;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.TopLevelComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MediaGallery implements Component, TopLevelComponent, ContainerComponent {

    private final @NotNull ConcurrentList<Thumbnail> items;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull Builder from(@NotNull MediaGallery mediaGallery) {
        return builder()
            .withItems(mediaGallery.getItems());
    }

    @Override
    public @NotNull discord4j.core.object.component.MediaGallery getD4jComponent() {
        return discord4j.core.object.component.MediaGallery.of(
            this.getItems()
                .stream()
                .map(Thumbnail::getD4jGalleryItem)
                .collect(Concurrent.toList())
        );
    }

    @Override
    public @NotNull Type getType() {
        return Type.MEDIA_GALLERY;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<MediaGallery> {

        private @NotNull ConcurrentList<Thumbnail> items = Concurrent.newList();

        public Builder withItem(@NotNull Thumbnail thumbnail) {
            this.items.add(thumbnail);
            return this;
        }

        public Builder withItems(@NotNull Thumbnail... items) {
            return this.withItems(Arrays.asList(items));
        }

        public Builder withItems(@NotNull Iterable<Thumbnail> items) {
            items.forEach(this.items::add);
            return this;
        }

        public @NotNull MediaGallery build() {
            return new MediaGallery(this.items);
        }

    }

}
