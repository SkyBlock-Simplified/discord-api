package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.AnnotatedButtonCommand;
import dev.simplified.discordfauxrig.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the cache-hit {@link dev.simplified.discordapi.listener.Component @Component} annotation
 * dispatch path of {@code ComponentListener}: a click on a cached button whose custom id matches an
 * annotation route runs {@code dispatchAnnotation}, and the annotation handler wins over the inline
 * handler. The eternal (cache-miss) path is covered by {@code EternalResponseInteractionTest}.
 */
class AnnotationRouteInteractionTest {

    @Test
    void cached_annotation_route_wins_over_inline() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            // Plant the annotation-routed button.
            harness.sendSlashCommand("annotated");
            harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-annotated")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Click it - the @Component route runs instead of the button's inline handler.
            harness.clickButton(harness.config().getReplyMessageId(), AnnotatedButtonCommand.CACHED_ID);

            RecordedRequest edit = harness.awaitRequest(
                request -> request.path().contains("button-token-" + AnnotatedButtonCommand.CACHED_ID)
                    && request.body().contains(AnnotatedButtonCommand.ANNOTATED_CONTENT),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains(AnnotatedButtonCommand.ANNOTATED_CONTENT),
                "the annotation route should edit the content; body=" + edit.body()
            );
            assertTrue(
                harness.requests().stream().noneMatch(request -> request.body().contains(AnnotatedButtonCommand.INLINE_CONTENT)),
                "the bypassed inline handler must never run"
            );
            assertEquals(1, harness.callbackCount("button-token-" + AnnotatedButtonCommand.CACHED_ID),
                "the annotation-routed click must acknowledge exactly once");
        }
    }

}
