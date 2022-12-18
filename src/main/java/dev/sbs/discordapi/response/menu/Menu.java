package dev.sbs.discordapi.response.menu;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.menu.item.MenuItem;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class Menu extends MenuItem {

    @Getter protected final long buildTime = System.currentTimeMillis();
    @Getter protected final ConcurrentList<MenuItem> items;

    private Menu(@NotNull UUID uniqueId, @NotNull ConcurrentList<MenuItem> items) {
        super(uniqueId, Type.MENU);
        this.items = Concurrent.newUnmodifiableList(items);
    }

    public static MenuBuilder builder() {
        return new MenuBuilder(UUID.randomUUID());
    }

    public MenuBuilder mutate() {
        return new MenuBuilder(this.getUniqueId())
            .withItems(this.getItems());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MenuBuilder implements Builder<Menu> {

        private final UUID uniqueId;
        private final ConcurrentList<MenuItem> menuItems = Concurrent.newList();

        /**
         * Adds {@link MenuItem MenuItems} to the {@link Menu}.
         *
         * @param items Variable number of menu items to add.
         */
        public MenuBuilder withItems(@NotNull MenuItem... items) {
            return this.withItems(Arrays.asList(items));
        }

        /**
         * Adds {@link MenuItem MenuItems} to the {@link Menu}.
         *
         * @param items Collection of menu items to add.
         */
        public MenuBuilder withItems(@NotNull Iterable<MenuItem> items) {
            if (this.menuItems.size() == Field.MAX_ALLOWED)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Number of menu items cannot exceed {0}!", Field.MAX_ALLOWED)
                    .build();

            List<MenuItem> itemList = List.class.isAssignableFrom(items.getClass()) ? (List<MenuItem>) items : StreamSupport.stream(items.spliterator(), false).toList();
            IntStream.range(0, Math.min(itemList.size(), (Field.MAX_ALLOWED - this.menuItems.size()))).forEach(index -> this.menuItems.add(itemList.get(index)));
            return this;
        }

        @Override
        public Menu build() {
            return new Menu(
                this.uniqueId,
                this.menuItems
            );
        }

    }

}
