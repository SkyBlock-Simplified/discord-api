package dev.sbs.discordapi.debug.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.handler.cache.ItemHandler;
import dev.sbs.discordapi.response.page.handler.search.Search;
import dev.sbs.discordapi.response.page.handler.sorter.Sorter;
import dev.sbs.discordapi.response.page.item.FooterItem;
import dev.sbs.discordapi.response.page.item.field.StringItem;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.stream.IntStream;

@Structure(
    parent = @Structure.Parent(
        name = "debug",
        description = "Debugging Commands"
    ),
    name = "items",
    guildId = 652148034448261150L,
    description = "Debug Item Handler"
)
public class DebugItemsCommand extends DiscordCommand<SlashCommandContext> {

    protected DebugItemsCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            Response.builder()
                .withTimeToLive(60)
                .withPages(
                    Page.builder()
                        .withItemHandler(
                            ItemHandler.builder(Test.class)
                                .withItems(tests)
                                .withFilters((test, index, size) -> test.getIndex() < 100)
                                .withAmountPerPage(15)
                                .withVariable("WORLD", "World!")
                                .withStaticItems(
                                    StringItem.builder()
                                        .withValue("Hello ${WORLD}")
                                        .isInline(false)
                                        .build(),
                                    FooterItem.builder()
                                        .withText("You are seeing ${CACHED_SIZE}/${FILTERED_SIZE} items.")
                                        .build()
                                )
                                .withTransformer((test, index, size) -> StringItem.builder()
                                    .withLabel(test.getName())
                                    .withValue(test.getValue())
                                    .isInline()
                                    .build()
                                )
                                .withSorters(
                                    Sorter.<Test>builder()
                                        .withComparators(Comparator.comparingInt(Test::getIndex))
                                        .withLabel("Index")
                                        .build()
                                )
                                .withSearch(
                                    Search.<Test>builder()
                                        .withPlaceholder("Input name.")
                                        .withLabel("Name")
                                        .withPredicates((testItem, value) -> testItem.getName().equalsIgnoreCase(value))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
            );
    }

    private static @NotNull ConcurrentList<Test> tests = IntStream.range(0, 200)
        .mapToObj(Test::new)
        .collect(Concurrent.toUnmodifiableList());

    @Getter
    static class Test {

        private final int index;
        private final String name;
        private final boolean even;
        private final String value;

        public Test(int index) {
            this.index = index;
            this.name = "Name " + index;
            this.even = (index & 1) == 0; // Even
            this.value = "Value " + index;
        }

    }

}
