package dev.simplified.discordapi.handler.response;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.discordapi.integration.HarnessBot;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import discord4j.common.util.Snowflake;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the previously-dead {@code update} write-through: on an eternal entry whose navigation
 * coordinate changed, {@link EternalResponseLocator#update} persists it to the repository; when it
 * is unchanged, the write is skipped. {@code update} touches only the repository and the entry, so
 * the bot and dispatcher dependencies are irrelevant here.
 */
class EternalResponseLocatorUpdateTest {

    @Test
    void persists_nav_state_when_it_changed() {
        Response response = eternalResponse();
        NavState live = NavState.capture(response.getHistoryHandler());
        UUID responseId = response.getUniqueId();
        Snowflake messageId = Snowflake.of(800000000000000001L);

        // Cold record seeded with an empty (stale) coordinate.
        InMemoryEternalResponseRepository repository = InMemoryEternalResponseRepository.of();
        repository.save(record(responseId, messageId, NavState.empty())).block();

        // Entry carries the stale coordinate; the live response sits on the first page.
        CachedResponse entry = entry(responseId, messageId, response, NavState.empty());

        new EternalResponseLocator(repository, () -> null, bot()).update(entry).block();

        EternalResponseRecord persisted = repository.findByResponseId(responseId).block();
        assertNotNull(persisted);
        assertEquals(live, persisted.navState(), "a changed nav coordinate should be persisted");
        assertEquals(live, entry.getNavState(), "the entry's cached nav coordinate should be refreshed");
    }

    @Test
    void skips_the_write_when_nav_state_is_unchanged() {
        Response response = eternalResponse();
        NavState live = NavState.capture(response.getHistoryHandler());
        UUID responseId = response.getUniqueId();
        Snowflake messageId = Snowflake.of(800000000000000002L);

        InMemoryEternalResponseRepository repository = InMemoryEternalResponseRepository.of();
        EternalResponseRecord original = record(responseId, messageId, live);
        repository.save(original).block();

        CachedResponse entry = entry(responseId, messageId, response, live);

        new EternalResponseLocator(repository, () -> null, bot()).update(entry).block();

        // Unchanged coordinate → no durable write → the record (including updatedAt) is untouched.
        assertEquals(original, repository.findByResponseId(responseId).block());
    }

    /** A bot instance only to satisfy the locator's non-null constructor; {@code update()} never touches it. */
    private static DiscordBot bot() {
        return new HarnessBot(DiscordConfig.builder().withToken("test-token").withMainGuildId(1L).build());
    }

    private static Response eternalResponse() {
        return Response.builder()
            .withPages(
                Page.builder()
                    .withContent("home")
                    .withComponents(ActionRow.of(
                        Button.builder().withStyle(Button.Style.PRIMARY).withLabel("x").withIdentifier("b").build()
                    ))
                    .build()
            )
            .asEternal("k", "")
            .build();
    }

    private static EternalResponseRecord record(UUID responseId, Snowflake messageId, NavState navState) {
        return new EternalResponseRecord(
            responseId, messageId, Snowflake.of(1L), Optional.empty(), Snowflake.of(2L),
            "k", "", navState, Instant.EPOCH, Instant.EPOCH
        );
    }

    private static CachedResponse entry(UUID responseId, Snowflake messageId, Response response, NavState navState) {
        return CachedResponse.builder()
            .withUniqueId(responseId)
            .withMessageId(messageId)
            .withChannelId(Snowflake.of(1L))
            .withUserId(Snowflake.of(2L))
            .withResponse(response)
            .withBuilderKey("k")
            .withNavState(navState)
            .build();
    }

}
