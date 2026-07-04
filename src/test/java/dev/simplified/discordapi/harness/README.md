# Offline Discord Test Harness

A standalone, discord4j-specific **fake Discord** - a localhost REST server plus an in-JVM gateway - that you
connect a bot to and drive **fully offline**. A slash command, a component/modal interaction, a right-click
user/message command, or a message event executes the **real** code path
(`Listener -> apply/process -> callback -> reply/edit`) with no network and no human: you push a simulated
gateway event and assert on the REST calls the bot makes in response.

This package depends only on discord4j (never on the framework), so it can be extracted into its own project
that the framework then depends on. The dependency arrow points one way: **consumer → harness**.

## Two layers

- **`harness/`** (this package) - the reusable **server**. It stands in for Discord and has no concept of a
  bot:
  - **`LocalDiscordServer`** (`rest/`) - a tiny reactor-netty HTTP server that serves the REST endpoints the
    bot touches and **records every request** for assertions.
  - **`FakeGatewayClient`** (`gateway/`) - an in-JVM `discord4j.gateway.GatewayClient` (no socket). It
    replays a `READY` + `GatewayStateChange.connected()` handshake so login completes, then lets you push
    further dispatches into Discord4J's *real* pipeline (`DispatchHandlers` -> events -> entities ->
    listeners).
  - **`DispatchFactory`** (`gateway/`) - builds the simulated dispatches (slash, component, modal, context
    menu, message-create) for a given identity; **`SlashOption`** is a resolved leaf option.
  - **`OfflineHarness`** - the server aggregate. Owns the pieces above and the **`HarnessConfig`** identity,
    and exposes `baseUrl()`, `gateway()`, `dispatches()`, and the request-assertion helpers.
  - **`RecordedRequest`** (`rest/`) - a captured request (`method()`, `path()`, `body()`, `bodyContains()`).
- **the consumer** (in this repository, the `integration` package) - your own `DiscordBot` wired to the
  harness, plus optionally a fluent driver. This repo ships **`IntegrationHarness`**, which wraps an
  `OfflineHarness`, boots a bot against it, and adds the ergonomic DSL below.

## Connecting a bot

Point the bot's REST client at the server and hand it the fake gateway; both are opt-in seams that are no-op
by default (in this framework, `DiscordConfig.withApiBaseUrl(...)`, `withRestReactorResources(...)`,
`withGatewayClientFactory(...)`):

```java
OfflineHarness harness = new OfflineHarness();               // fresh REST mock + fake gateway
// wire your bot's DiscordConfig to the harness:
//   .withApiBaseUrl(harness.baseUrl())                      // REST -> localhost mock
//   .withGatewayClientFactory(options -> harness.gateway()) // gateway -> in-JVM fake
//   .withRestReactorResources(plaintextRest)                // plaintext http to localhost (no TLS)
// then boot the bot and wait for it to report connected.
```

`IntegrationHarness` does exactly this for `HarnessBot`; see it for the full recipe.

## Harness API (`OfflineHarness`)

Drive events by emitting a dispatch built by the factory; assert on the recorded REST traffic:

```java
harness.gateway().emit(harness.dispatches().slashCommand("ping"));  // push /ping
RecordedRequest reply = harness.awaitInteractionReply();            // last PATCH .../messages/@original
assertTrue(reply.bodyContains("pong"));
```

| Call | Does |
|---|---|
| `baseUrl()` | REST base url of the localhost mock (for your bot's config) |
| `gateway()` | the fake gateway; `gateway().emit(dispatch)` pushes a raw dispatch |
| `dispatches()` | the `DispatchFactory` (see below) |
| `config()` | the `HarnessConfig` identity backing this run |
| `requests()` | all recorded requests, in order |
| `awaitRequest(predicate[, timeout])` | last request matching the predicate (default 10s) |
| `awaitInteractionReply()` | last `PATCH .../messages/@original` |
| `awaitInteractionCallback()` | last `POST .../callback` |
| `callbackCount(tokenContains)` | how many times an interaction was acknowledged (Discord permits one) |
| `await(condition, timeout, message)` | generic poll primitive (used by the consumer's readiness waits) |
| `close()` | stop the server and fake gateway |

### Building dispatches (`DispatchFactory`, via `dispatches()`)

| Builder | Simulates |
|---|---|
| `slashCommand(name, options...)` | `/name` chat-input interaction (type 2) |
| `slashSubCommand(parent, group, sub, options...)` | `/parent [group] sub` subcommand interaction |
| `button(messageId, customId)` | button click (component interaction, type 3) |
| `selectMenu(messageId, customId, values...)` | string select interaction |
| `modalSubmit(messageId, modalId, inputId, value)` | modal submit (type 5) |
| `modalSubmitValues(messageId, modalId, radioId, radioValue, checkboxId, checkboxChecked)` | modal submit carrying radio + checkbox state |
| `messageCreate(messageId)` | bot-authored `MESSAGE_CREATE` (drives `onCreate`) |
| `userCommand(name, targetUserId)` | right-click user command (type 2, data.type 2) |
| `messageCommand(name, targetMessageId)` | right-click message command (type 2, data.type 3) |

Interactions with no `guild_id` run as private-channel interactions, skipping bot-permission/channel
resolution - the low-friction default. Slash options are supplied as `SlashOption` leaves
(`SlashOption.text(name, value)`); the factory places them at the correct depth for flat commands, bare
subcommands, or grouped subcommands, matching the `parent [group] sub` tree the framework resolves from
`@Structure`.

## Fluent driver (this repository: `IntegrationHarness`)

`IntegrationHarness` wraps the harness with readiness waits (it peeks the bot's registries) and one-liner
send helpers, so a test reads top to bottom:

```java
try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
    harness.sendSlashCommand("ping");
    harness.awaitInteractionCallback();               // apply() defers
    RecordedRequest reply = harness.awaitInteractionReply();
    assertTrue(reply.bodyContains("pong"));
}
```

Add a test command under `integration/command/` (discovered by classpath scan of that package). Use
`context.buildResponse()` (NOT bare `Response.builder()`, which NPEs without a bot).

| Call | Simulates (waits for readiness, then emits) |
|---|---|
| `sendSlashCommand(name, options...)` | `/name`, with optional top-level options |
| `sendSubCommand(parent, [group,] sub, options...)` | bare or grouped subcommand |
| `sendUserCommand(name, targetUserId)` | right-click user command |
| `sendMessageCommand(name, targetMessageId)` | right-click message command |
| `clickButton(messageId, customId)` | button click |
| `clickSelectMenu(messageId, customId, values...)` | select menu |
| `submitModal(messageId, modalId, inputId, value)` | modal submit |
| `emitMessageCreate(messageId)` | bot-authored `MESSAGE_CREATE` |

Component/modal/onCreate helpers first wait for the target message to be cached (`awaitResponseCached`). The
`await*`/`requests()`/`callbackCount()` assertions are re-exposed from the underlying `OfflineHarness`.

## Ids and tokens

`HarnessConfig` (built via `HarnessConfig.builder().build()`) holds the canonical ids and a
structurally-valid fake token (its first segment base64-decodes to the bot id, matching
`TokenUtil.getSelfId`). It is passed to `OfflineHarness`, `LocalDiscordServer`, and `DispatchFactory` at
construction; reach it via `harness.config()`. Command ids are derived deterministically from the command
name (`config.commandId(name)`) so the mock's bulk-overwrite echo and simulated interactions agree. The REST
mock mints `config.getReplyMessageId()` for every reply/followup message. Pass a customized `HarnessConfig`
to change the identity.

The eternal (reboot-surviving) response flow is driven by keeping a cold store across two boots: build two
consumers over the same store (the second, with a fresh hot tier, re-hydrates from it). In this repo that is
`new IntegrationHarness(config, sharedStore)` twice; the harness server itself is fresh each boot.

## Debugging

The harness logs through Log4j2 (no `System.out`). By level:

- **INFO** - the driver narrates each driven event (`-> slash command /ping`, `-> click button ...`) plus
  boot/connect lifecycle. On by default.
- **DEBUG** - REST mock bind/stop, per-request `[mock] METHOD /path body`, and gateway handshake/close.
- **TRACE** - every dispatch the factory builds and every event emitted into the pipeline.
- **WARN** - conditions Discord itself would reject or report: an interaction acknowledged twice
  (error 40060), a REST endpoint the mock does not model, or a swallowed mock error.
- **ERROR** - a bot startup failure on the daemon thread (otherwise silent) or a wait that timed out.

Raise the loggers in `log4j2-test.xml` (`dev.simplified.discordapi.harness` for the server's REST/gateway
detail, `dev.simplified.discordapi.integration` for the driver's narration):

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

Deep design notes, the verified Discord4J 3.3.1 contract, and the running log of framework bugs this harness
has caught live in `notes/offline-harness/` (git-ignored). The harness has already found and verified fixes
for two "entire feature broken" bugs (modals dropping input; context-menu commands never registered), plus
several smaller issues.
