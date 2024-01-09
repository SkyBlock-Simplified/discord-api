package dev.sbs.discordapi.debug.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.CommandId;
import dev.sbs.discordapi.command.impl.SlashCommand;
import dev.sbs.discordapi.context.deferrable.application.SlashCommandContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.handler.ItemHandler;
import dev.sbs.discordapi.response.page.handler.search.Search;
import dev.sbs.discordapi.response.page.handler.sorter.Sorter;
import dev.sbs.discordapi.response.page.item.FooterItem;
import dev.sbs.discordapi.response.page.item.field.StringItem;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;

@CommandId("898bec98-db76-4e8a-813c-2de707cc60a1")
public class DebugItemsCommand extends SlashCommand {

    protected DebugItemsCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Debug Item Handler";
    }

    @Override
    public long getGuildId() {
        return 652148034448261150L;
    }

    @NotNull
    @Override
    public String getName() {
        return "items";
    }

    @NotNull
    @Override
    public Optional<Parent> getParent() {
        return Parent.op(
            "debug",
            "Debug Command"
        );
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
