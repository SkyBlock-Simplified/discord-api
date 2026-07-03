package dev.simplified.discordapi.response;

import dev.simplified.discordapi.response.page.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the bot-decoupled {@link Response}: it builds with no {@link dev.simplified.discordapi.DiscordBot}
 * (the [H1] fix), and its navigation position survives {@link Response#mutate()} via the {@code NavState}
 * coordinate rather than the old inline breadcrumb replay.
 */
class ResponseNavigationTest {

    @Test
    void builds_without_a_bot_and_opens_on_the_first_page() {
        // A bare Response.builder() with no bot - this alone would have thrown the [H1] NPE before decoupling.
        Page first = Page.builder().withContent("one").build();
        Response response = Response.builder()
            .withPages(first, Page.builder().withContent("two").build())
            .build();

        assertEquals(first.getOption().getValue(), response.getHistoryHandler().getCurrentPage().getOption().getValue());
    }

    @Test
    void navigation_survives_mutation() {
        Page second = Page.builder().withContent("two").build();
        Response response = Response.builder()
            .withPages(Page.builder().withContent("one").build(), second)
            .build();

        String secondId = second.getOption().getValue();
        response.getHistoryHandler().gotoTopLevelPage(secondId);
        assertEquals(secondId, response.getHistoryHandler().getCurrentPage().getOption().getValue(), "precondition: navigated to the second page");

        // mutate() reuses the page objects by reference; the top-level history stack is rebuilt and restored via NavState.
        Response mutated = response.mutate().build();
        assertEquals(secondId, mutated.getHistoryHandler().getCurrentPage().getOption().getValue(), "navigation position should survive mutate()");
    }

}
