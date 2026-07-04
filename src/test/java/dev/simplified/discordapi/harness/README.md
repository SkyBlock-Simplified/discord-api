# Offline Discord Test Harness

Runs a bot **fully offline** so a slash command, a component/modal interaction, a right-click
user/message command, or a message event executes the **real** code path
(`Listener -> apply/process -> callback -> reply/edit`) with no network and no human. Tests push a
simulated gateway event and assert on the REST calls the bot makes in response.

The harness is split into two layers with a strict one-way dependency (**consumer → harness**, never the
reverse), so the harness can later be extracted into its own project:

- **`harness/`** (this package) - reusable, discord4j-specific **server** infrastructure. It stands in for
  Discord itself and has no concept of a bot.
- **`integration/`** - this project's **consumer**: `HarnessBot` (the project's `DiscordBot`) plus
  `IntegrationHarness`, which connects the bot to a harness server and exposes the driving DSL.

## How it works (hybrid relay)

- **`LocalDiscordServer`** (`harness/rest/`) - a tiny reactor-netty HTTP server that stands in for the
  Discord REST API, serves the endpoints the bot touches, and **records every request** for assertions.
- **`FakeGatewayClient`** (`harness/gateway/`) - an in-JVM `discord4j.gateway.GatewayClient` (no socket). It
  replays a `READY` + `GatewayStateChange.connected()` handshake so login completes, and lets tests push
  further dispatches into Discord4J's *real* pipeline (`DispatchHandlers` -> events -> entities -> your
  listeners).
- **`OfflineHarness`** (`harness/`) - the **server** aggregate: owns the REST mock, the fake gateway, the
  `DispatchFactory`, the `HarnessConfig` identity, and the durable eternal store. Exposes `baseUrl()`,
  `gateway()`, `dispatches()`, and the request-assertion helpers. Knows nothing about a bot.
- **`IntegrationHarness`** (`integration/`) - this project's **consumer/driver**: builds `HarnessBot`'s
  `DiscordConfig` pointed at the server, boots it on a daemon thread, and exposes the DSL below.

The bot is redirected onto the harness via the opt-in framework seams `DiscordConfig.withApiBaseUrl(...)`,
`withRestReactorResources(...)` and `withGatewayClientFactory(...)` (all no-op by default).

## Quick start

```java
try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
    harness.sendSlashCommand("ping");                 // push a simulated /ping interaction
    harness.awaitInteractionCallback();               // apply() defers
    RecordedRequest reply = harness.awaitInteractionReply();
    assertTrue(reply.bodyContains("pong"));
}
```

Add a test command under `integration/command/` (it is discovered by classpath scan of that package). Use
`context.buildResponse()` (NOT bare `Response.builder()`, which NPEs without a bot).

## Driving events (DSL, on `IntegrationHarness`)

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

`RecordedRequest` exposes `method()`, `path()` (query stripped), `body()`, and `bodyContains(text)`. These
helpers live on the server (`OfflineHarness`) and are re-exposed by `IntegrationHarness` for convenience.

## Ids and tokens

`HarnessConfig` (built via `HarnessConfig.builder().build()`) holds the canonical ids and a
structurally-valid fake token (its first segment base64-decodes to the bot id, matching
`TokenUtil.getSelfId`). It is passed to `OfflineHarness`, `LocalDiscordServer`, and `DispatchFactory` at
construction; a test can vary it and reach it via `harness.config()`. Command ids are derived
deterministically from the command name (`config.commandId(name)`) so the mock's bulk-overwrite echo and
simulated interactions agree. The REST mock mints `config.getReplyMessageId()` for every reply/followup
message. Pass a customized `HarnessConfig` to `new IntegrationHarness(config)` to change the identity.

## Debugging

The harness logs through Log4j2 (no `System.out`). By level:

- **INFO** - the driver narrates each driven event (`-> slash command /ping`, `-> click button ...`) plus
  boot/connect lifecycle. On by default.
- **DEBUG** - REST mock bind/stop, per-request `[mock] METHOD /path body`, and gateway handshake/close.
- **TRACE** - every dispatch the factory builds and every event emitted into the pipeline.
- **WARN** - conditions Discord itself would reject or report: an interaction acknowledged twice
  (error 40060), a REST endpoint the mock does not model, or a swallowed mock error.
- **ERROR** - a bot startup failure on the daemon thread (otherwise silent) or a wait that timed out.

Raise the loggers in `log4j2-test.xml` to see the detail (`dev.simplified.discordapi.harness` for the
server's REST/gateway detail, `dev.simplified.discordapi.integration` for the driver's narration):

```xml
<Logger name="dev.simplified.discordapi.harness" level="TRACE"/>
<Logger name="dev.simplified.discordapi.integration" level="DEBUG"/>
```

`-Dharness.debug=true` elevates the per-request `[mock] ...` firehose to INFO so it shows without touching
the config - the fastest way to see how far a pathway got. Framework logs surface at INFO; noisy transport
libs are dampened to WARN.

```
./gradlew test --tests "dev.simplified.discordapi.integration.test.*" -Dharness.debug=true
```

## Notes

Deep design notes, the verified Discord4J 3.3.1 contract, and the running log of framework bugs this
harness has caught live in `notes/offline-harness/` (git-ignored). The harness has already found and
verified fixes for two "entire feature broken" bugs (modals dropping input; context-menu commands never
registered), plus several smaller issues.
