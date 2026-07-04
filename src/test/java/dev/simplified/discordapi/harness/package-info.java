/**
 * A standalone, discord4j-specific offline test harness: a fake Discord REST server
 * ({@link dev.simplified.discordapi.harness.rest.LocalDiscordServer LocalDiscordServer}) and a fake in-JVM
 * gateway ({@link dev.simplified.discordapi.harness.gateway.FakeGatewayClient FakeGatewayClient}), wired
 * together by {@link dev.simplified.discordapi.harness.OfflineHarness OfflineHarness} over a deterministic
 * identity ({@link dev.simplified.discordapi.harness.HarnessConfig HarnessConfig}).
 *
 * <p>
 * The harness stands in for Discord itself and holds no concept of a bot: a consumer wires its own
 * {@link dev.simplified.discordapi.DiscordBot DiscordBot} to the server's REST base url and fake gateway,
 * pushes simulated dispatches built by
 * {@link dev.simplified.discordapi.harness.gateway.DispatchFactory DispatchFactory}, and asserts on the
 * captured {@link dev.simplified.discordapi.harness.rest.RecordedRequest RecordedRequest}s.
 *
 * <p>
 * This package is meant to be extractable into its own project, so it never depends on any consumer's test
 * code; the dependency arrow points only inward, from a consumer to the harness. This project's own consumer
 * lives in {@code dev.simplified.discordapi.integration}.
 */
package dev.simplified.discordapi.harness;
