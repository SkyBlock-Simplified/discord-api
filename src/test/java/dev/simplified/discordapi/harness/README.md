# Offline Discord Test Harness

Runs the bot **fully offline** so a slash command, a component/modal interaction, a right-click
user/message command, or a message event executes the **real** code path
(`Listener -> apply/process -> callback -> reply/edit`) with no network and no human. Tests push a
simulated gateway event and assert on the REST calls the bot makes in response.

## How it works (hybrid relay)

- **`LocalDiscordServer`** - a tiny reactor-netty HTTP server that stands in for the Discord REST API,
  serves the endpoints the bot touches, and **records every request** for assertions.
- **`FakeGatewayClient`** - an in-JVM `discord4j.gateway.GatewayClient` (no socket). It replays a
  `READY` + `GatewayStateChange.connected()` handshake so login completes, and lets tests push further
  dispatches into Discord4J's *real* pipeline (`DispatchHandlers` -> events -> entities -> your listeners).
- **`HarnessBot`** - a `DiscordBot` pointed at both, started on a daemon thread.
- **`OfflineHarness`** - wires it together and exposes the DSL below.

The bot is redirected via the opt-in framework seams `DiscordConfig.withApiBaseUrl(...)`,
`withRestReactorResources(...)` and `withGatewayClientFactory(...)` (all no-op by default).

## Quick start

```java
try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
    harness.sendSlashCommand("ping");                 // push a simulated /ping interaction
    harness.awaitInteractionCallback();               // apply() defers
    RecordedRequest reply = harness.awaitInteractionReply();
    assertTrue(reply.bodyContains("pong"));
}
```

Add a test command under `harness/command/` (it is discovered by classpath scan of that package). Use
`context.buildResponse()` (NOT bare `Response.builder()`, which NPEs without a bot).

## Driving events (DSL)

| Call | Simulates |
|---|---|
| `sendSlashCommand(name, options...)` | `/name` chat-input interaction (type 2), with optional top-level options |
| `sendSubCommand(parent, sub, options...)` | `/parent sub` bare subcommand interaction |
| `sendSubCommand(parent, group, sub, options...)` | `/parent group sub` grouped subcommand interaction |
| `sendUserCommand(name, targetUserId)` | right-click user command (type 2, data.type 2) |
| `sendMessageCommand(name, targetMessageId)` | right-click message command (type 2, data.type 3) |
| `clickButton(messageId, customId)` | button click (component interaction, type 3) |
| `submitModal(messageId, modalId, inputId, value)` | modal submit (type 5) |
| `emitMessageCreate(messageId)` | bot-authored `MESSAGE_CREATE` (drives `onCreate`) |
| `gateway().emit(dispatch)` | any raw dispatch (build via `dispatches()`) |

Interactions with no `guild_id` run as private-channel interactions, skipping bot-permission/channel
resolution - the low-friction default the DSL uses. Component/modal/onCreate helpers first wait for the
target message to be cached (`awaitResponseCached`).

Slash options are supplied as `SlashOption` leaves (`SlashOption.text(name, value)`); the dispatch factory
places them at the correct depth for flat commands, bare subcommands, or grouped subcommands, matching the
`parent [group] sub` tree the framework resolves from `@Structure`.

## Asserting (DSL)

| Call | Returns |
|---|---|
| `awaitInteractionReply()` | last `PATCH .../messages/@original` |
| `awaitInteractionCallback()` | last `POST .../callback` |
| `awaitRequest(predicate[, timeout])` | last request matching the predicate |
| `requests()` | all recorded requests, in order |

`RecordedRequest` exposes `method()`, `path()` (query stripped), `body()`, and `bodyContains(text)`.

## Ids and tokens

`HarnessConfig` (built via `HarnessConfig.builder().build()`) holds the canonical ids and a
structurally-valid fake token (its first segment base64-decodes to the bot id, matching
`TokenUtil.getSelfId`). It is passed to `OfflineHarness`, `LocalDiscordServer`, and `DispatchFactory` at
construction; a test can vary it and reach it via `harness.config()`. Command ids are derived
deterministically from the command name (`config.commandId(name)`) so the mock's bulk-overwrite echo and
simulated interactions agree. The REST mock mints `config.getReplyMessageId()` for every reply/followup
message. Pass a customized `HarnessConfig` to `new OfflineHarness(config)` to change the identity.

## Debugging

Run with `-Dharness.debug=true` to print every `[mock] METHOD /path body` line the server receives - the
fastest way to see how far a pathway got. Framework logs surface at INFO (see `log4j2-test.xml`); noisy
transport libs are dampened to WARN.

```
./gradlew test --tests "dev.simplified.discordapi.harness.*" -Dharness.debug=true
```

## Notes

Deep design notes, the verified Discord4J 3.3.1 contract, and the running log of framework bugs this
harness has caught live in `notes/offline-harness/` (git-ignored). The harness has already found and
verified fixes for two "entire feature broken" bugs (modals dropping input; context-menu commands never
registered), plus several smaller issues.
