package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.command.AnnotatedButtonCommand;
import dev.simplified.discordapi.harness.data.TestIds;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two {@link dev.simplified.discordapi.listener.Component @Component} annotation dispatch
 * paths of {@code ComponentListener} that the button/modal verticals do not exercise:
 * <ul>
 *   <li><b>cache hit</b> - a click on a cached button whose custom id matches an annotation route
 *       runs {@code dispatchAnnotation}, and the annotation handler wins over the inline handler</li>
 *   <li><b>cache miss (eternal)</b> - a click on an uncached message whose custom id matches a route
 *       runs {@code tryDispatchEternal}, producing an effect a plain drop never would</li>
 * </ul>
 * Both assert the interaction is acknowledged exactly once.
 */
class AnnotationRouteInteractionTest {

    /** A message id that is never cached, forcing the eternal (cache-miss) dispatch path. */
    private static final long UNCACHED_MESSAGE_ID = 888800000000000042L;

    @Test
    void cached_annotation_route_wins_over_inline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            // Plant the annotation-routed button.
            harness.sendSlashCommand("annotated");
            harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-annotated")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Click it - the @Component route runs instead of the button's inline handler.
            harness.clickButton(TestIds.REPLY_MESSAGE_ID, AnnotatedButtonCommand.CACHED_ID);

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

    @Test
    void eternal_annotation_route_dispatches_on_cache_miss() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            // The route registers at connect, but the event listeners register on a concurrent ConnectEvent
            // subscription, so a bare boot does not guarantee the listener is live. Warm the pipeline up with a
            // slash command and await its reply (the component listener registers alongside the slash listener),
            // then emit the eternal click on a DIFFERENT, uncached message id.
            harness.sendSlashCommand("annotated");
            harness.awaitInteractionReply();
            harness.awaitComponentRoute(AnnotatedButtonCommand.ETERNAL_ID, Duration.ofSeconds(10));
            harness.gateway().emit(harness.dispatches().button(UNCACHED_MESSAGE_ID, AnnotatedButtonCommand.ETERNAL_ID));

            // A drop would only defer; the eternal handler additionally creates a followup.
            RecordedRequest followup = harness.awaitRequest(
                request -> request.method().equals("POST")
                    && request.path().contains("/webhooks/")
                    && request.path().contains("button-token-" + AnnotatedButtonCommand.ETERNAL_ID)
                    && request.body().contains(AnnotatedButtonCommand.ETERNAL_CONTENT),
                Duration.ofSeconds(10)
            );

            assertTrue(
                followup.body().contains(AnnotatedButtonCommand.ETERNAL_CONTENT),
                "the eternal route should create a followup; body=" + followup.body()
            );
            assertEquals(1, harness.callbackCount("button-token-" + AnnotatedButtonCommand.ETERNAL_ID),
                "the eternal click must acknowledge exactly once");
        }
    }

}
