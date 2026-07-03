# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

This module (Java 21, Gradle 9.4+) is an **included build** named `discord4j-framework` within the `Simplified-Dev` monorepo. It has its own `gradlew`, so tasks can be run bare from this directory, or prefixed with `:discord4j-framework:` from the monorepo root (`../`). The `:discord-api:` project path no longer exists.

```bash
# Build this module (from this directory)
./gradlew build

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Generate SVG hierarchy diagrams
./gradlew generateDiagrams

# Or, from the monorepo root:
./gradlew :discord4j-framework:test
```

**Required environment variables:** `DISCORD_TOKEN`, `DEVELOPER_ERROR_LOG_CHANNEL_ID`

The debug bot (`src/test/.../debug/DebugBot.java`) can be run directly to test commands in isolation.

## Architecture Overview

This module is a **framework layer on top of Discord4J** that provides a builder-driven, reactive API for building Discord bots. Entry point: `DiscordBot` (sole class in root `discordapi` package). Configuration via `DiscordConfig` (in `handler/`).

```
DiscordBot (abstract) → DiscordConfig (handler/) → initialize() → login() + connect()
    ├── CommandHandler        — registers & routes commands
    ├── EmojiHandler          — manages custom emoji upload/lookup
    ├── ExceptionHandler      — abstract base in handler/exception/
    │   ├── DiscordExceptionHandler  — formats errors into Discord embeds
    │   ├── SentryExceptionHandler   — captures to Sentry with Discord context
    │   └── CompositeExceptionHandler — chains multiple handlers in sequence
    ├── ResponseHandler       — caches active Response messages (handler/response/)
    └── ShardHandler          — gateway shard management (handler/shard/)
```

### Command System

Commands extend `DiscordCommand<C extends CommandContext<?>>` and are annotated with `@Structure(...)`:

```
DiscordCommand<SlashCommandContext>    → Slash commands (/command)
DiscordCommand<UserCommandContext>     → Right-click user commands
DiscordCommand<MessageCommandContext>  → Right-click message commands
```

- `@Structure` defines: `name`, `description`, `parent` (for subcommands), `group` (for subcommand groups), `guildId` (-1 for global), `ephemeral`, `developerOnly`, `singleton`, `botPermissions`, `userPermissions`, `integrations`, `contexts`
- `getParameters()` returns `ConcurrentUnmodifiableList<Parameter>` for slash command options
- `process(C context)` is the abstract method to implement command logic, returns `Mono<Void>`
- Commands are discovered via `Reflection.getResources().filterPackage(...).getTypesOf(DiscordCommand.class)` and registered through `CommandHandler`
- The `apply()` method in `DiscordCommand` handles permission checks, parameter validation, and error handling before calling `process()`
- Command-specific exceptions in `command/exception/`: `CommandException`, `PermissionException`, `BotPermissionException`, `DeveloperPermissionException`, `InputException`, `ExpectedInputException`, `ParameterException`, `DisabledCommandException`, `SingletonCommandException`

### Response System

`Response` is a single `final class` built via `Response.builder()`. It manages a `HistoryHandler<Page, String>` for page navigation and a `PaginationHandler` for building pagination components (buttons, select menus, modals).

Page hierarchy:
```
Page (interface)
├── TreePage  — implements Subpages<TreePage>; supports nested subpages, embeds, content
└── FormPage  — form/question pages for sequential input
```

- `Page.builder()` → `TreePage.TreePageBuilder`
- `Page.form()` → `FormPage.QuestionBuilder`
- `Response.builder()` builds the response; `Response.from()` creates a pre-filled builder from an existing response; `response.mutate()` is shorthand for `Response.from(this)`

Response features:
- Multiple `Page` instances (select menu navigation)
- `ItemHandler<T>` for paginated items with sort/filter/search
  - `EmbedItemHandler` — renders items as embed fields
  - `ComponentItemHandler` — renders items as `Section` components
- Interactive components (`Button`, `SelectMenu`, `TextInput`, `Modal`, `RadioGroup`, `Checkbox`, `CheckboxGroup`)
- Attachments, embeds, reactions
- Auto-expiration via `timeToLive` (5-300 seconds)
- Automatic Discord4J spec generation (`getD4jCreateSpec()`, `getD4jEditSpec()`, etc.)
- **Persistence** via `Response.builder().isPersistent(true)` — writes the entry through to a JPA cold tier so the message survives bot restarts. Requires a matching `@PersistentResponse`-annotated builder method on the dispatching `DiscordCommand` (or a `PersistentComponentListener`) that accepts an `EventContext<?>` and returns a `Response`. Persistent components must use explicit user-supplied `customId` strings so `@Component(customId)` handlers can route the click after hydration. See the `handler/response/` and `handler/PersistentComponentHandler` sections below for the full flow.

### Component System (top-level `component/` package)

Components are a top-level package (`component/`), independent of `response/`. They are quality-of-life builders for their Discord4J counterparts and can be constructed independently.

```
component/                — Component (interface), TextDisplay
component/interaction/    — Button, SelectMenu, TextInput, Modal,
                            RadioGroup, Checkbox, CheckboxGroup
component/layout/         — ActionRow, Container, Section, Separator, Label
component/media/          — Attachment, FileUpload, MediaData, MediaGallery, Thumbnail
component/capability/     — application-level behavioral contracts:
    EventInteractable, ModalUpdatable, Toggleable, UserInteractable
component/scope/          — Discord placement scoping interfaces:
    ActionComponent, LayoutComponent, AccessoryComponent, ContainerComponent,
    LabelComponent, SectionComponent, TopLevelMessageComponent, TopLevelModalComponent
```

Components support Discord's Components V2 flag (`IS_COMPONENTS_V2`) — detected automatically when v2 component types are present.

### Context Hierarchy

Every event gets a typed context wrapping the Discord4J event:

```
context/                  — EventContext
context/scope/            — MessageContext, InteractionContext, DeferrableInteractionContext,
                            CommandContext, ComponentContext, ActionComponentContext
context/capability/       — ExceptionContext, TypingContext
context/command/          — SlashCommandContext, UserCommandContext,
                            MessageCommandContext, AutoCompleteContext
context/component/        — ButtonContext, SelectMenuContext, OptionContext, ModalContext,
                            CheckboxContext, CheckboxGroupContext, RadioGroupContext
context/message/          — ReactionContext
```

`ComponentContext` extends both `MessageContext` and `DeferrableInteractionContext` (diamond via interfaces).

Contexts provide: `reply()`, `edit()`, `followup()`, `presentModal()`, `deleteFollowup()`, and access to the cached `Response`/`CachedResponse`.

### Listener System

There are two parallel listener hierarchies, both auto-registered via classpath scanning of the `dev.sbs.discordapi.listener` package:

- **`DiscordListener<T extends discord4j.core.event.domain.Event>`** — handles Discord4J gateway events. Subscribed to Discord4J's `EventDispatcher`. Errors are routed through the `ExceptionHandler` chain.
- **`BotEventListener<T extends BotEvent>`** — handles bot-internal events emitted by `DiscordBot` itself (lifecycle hooks, future custom events). Subscribed to a `Sinks.Many<BotEvent>` replay sink owned by `DiscordBot` (last 16 events replayed to late subscribers, so listeners registered inside `connect()` still receive events emitted during `login()`). Errors are logged locally.

Additional listeners of either type can be registered through `DiscordConfig.Builder.withListeners()` (Discord4J events) or `withBotEventListeners()` (bot events).

```
listener/                 — DiscordListener, BotEventListener (base classes)
listener/command/         — SlashCommandListener, UserCommandListener,
                            MessageCommandListener, AutoCompleteListener
listener/component/       — ComponentListener, ButtonListener, SelectMenuListener,
                            ModalListener, CheckboxListener, CheckboxGroupListener,
                            RadioGroupListener
listener/message/         — MessageCreateListener, MessageDeleteListener,
                            ReactionListener, ReactionAddListener, ReactionRemoveListener
listener/lifecycle/       — DisconnectListener (BotEventListener), GuildCreateListener
listener/                 — PersistentComponentListener (base for shared @Component
                            and @PersistentResponse hosts; classpath-scanned at startup)
                          — Component (annotation in dev.sbs.discordapi.listener)
```

#### Bot Event Hierarchy

`dev.sbs.discordapi.event` houses internal events that are emitted by `DiscordBot` and consumed by `BotEventListener` subclasses. Lifecycle hooks (`onClientCreated`, `onGatewayConnected`, `onGatewayDisconnect`) are NOT exposed as protected methods on `DiscordBot` — `DiscordBot` is the single bridge that translates Discord4J gateway events into bot events, and listeners are the only extension point.

```
event/                    — BotEvent (marker interface)
event/lifecycle/          — ClientCreatedBotEvent, GatewayConnectBotEvent,
                            GatewayDisconnectBotEvent
```

### Handler Classes

**`handler/exception/`** — pluggable error handling chain:
- **`ExceptionHandler`** — abstract base class (extends `DiscordReference`)
- **`DiscordExceptionHandler`** — formats errors into Discord embeds, sends to user and developer log channel
- **`SentryExceptionHandler`** — captures exceptions to Sentry with enriched Discord context tags
- **`CompositeExceptionHandler`** — chains multiple handlers in sequence

**`handler/response/`** — two-tier response cache (hot in-memory + optional cold JPA):
- **`ResponseLocator`** — reactive interface exposing `findByMessage`, `findForInteraction`, `findByResponseId`, `findFollowupByIdentifier`, `store`, `storeFollowup`, `update`, `remove`, `findExpired`. Persistence branching is internal to implementations.
- **`InMemoryResponseLocator`** — hot tier backed by a `uniqueId → CachedResponse` map plus a `messageId → uniqueId` index for O(1) lookups.
- **`JpaResponseLocator`** — cold tier writing `PersistentResponseEntity` rows via raw `JpaSession.transaction(...)`. Reads run on `Schedulers.boundedElastic()`.
- **`CompositeResponseLocator`** — wraps both tiers, performs cold-tier hydration on hot-tier miss via the `PersistentComponentHandler` builder route registry.
- **`CachedResponse`** — single concrete entry type representing both top-level replies and followups (followups have `parentId` set). Lifecycle is a `State` enum (`IDLE`, `BUSY`, `DEFERRED`); content dirty-tracking flows through `Response.isCacheUpdateRequired()`. Persistent entries carry `ownerClass` + `builderId` for hydration.
- **`NavState`** — `@GsonType`-marked snapshot of the mutable navigation coordinates (current page, item page, page history) persisted to the `nav_state` JSON column.
- **`jpa/PersistentResponseEntity`** — `@Entity` backing the `discord_persistent_response` table; followups are independent rows with `parent_id` set, cascade-deleted in app code.

**`handler/PersistentComponentHandler`** — routing registry for persistent component interactions:
- Scanned at bot startup over loaded `DiscordCommand` instances and `PersistentComponentListener` subclasses.
- `@Component(customId)`-annotated methods (in `listener.dev.simplified.discordapi.Component`) register a route from custom id → `MethodHandle`. Methods take a `ComponentContext` subtype and return a `Publisher<Void>`.
- `@PersistentResponse([id])`-annotated methods (in `dev.sbs.discordapi.response.PersistentResponse`) register a route from `(ownerClass, builderId)` → `MethodHandle`. Methods take an `EventContext<?>` and return a `Response`. Invoked at both creation time (with the dispatching command's context) and hydration time (with a `HydrationContext`).
- `HydrationContext` (`context/HydrationContext`) is a lightweight `EventContext<ComponentInteractionEvent>` that intentionally does NOT extend `MessageContext`, so builder methods cannot accidentally call `getResponse()` against a not-yet-existing cache entry.

**`handler/DispatchingClassContextKey`** — Reactor `Context` key (`"dev.sbs.discordapi.dispatching-class"`) carrying the dispatching `Class<?>` through the reactive pipeline. Written by `DiscordCommand.apply` (around `process()`) and by `ComponentListener.dispatchPersistent` (around the `@Component` invocation). Read by `InMemoryResponseLocator.store` to bind the owner class to persistent entries.

**Persistent response flow:**
1. `DiscordConfig.Builder.withJpaConfig(JpaConfig)` enables the cold tier; `DiscordBot` derives an internal `JpaConfig` whose `RepositoryFactory` scans `dev.sbs.discordapi.handler.response.jpa` and connects its own `JpaSession` so the discord-api entity discovery is independent of any user-supplied factory.
2. A command's `process()` calls `context.reply(Response.builder().isPersistent(true)...build())`. `EventContext.reply` delegates to `responseLocator.store`, which writes through to both the hot tier and the cold tier.
3. After a restart, when the user clicks the persistent component, `ComponentListener` calls `responseLocator.findForInteraction(event)`. The composite locator misses the hot tier, hits the JPA row, looks up the registered `@PersistentResponse` builder via `PersistentComponentHandler`, invokes it with a `HydrationContext` to rebuild the `Response`, restores the persisted `NavState`, seeds the hot tier, and returns the hydrated `CachedResponse`.
4. `ComponentListener.dispatchPersistent` then routes to the `@Component`-annotated handler via `MethodHandle`, wrapping the publisher with the dispatching class context key so any nested `reply()` from inside the handler is also persistence-aware.

**`response/handler/`** — page navigation and pagination:
- **`HistoryHandler<P, I>`** — generic stack-based page navigation (sibling and child navigation via `Subpages`)
- **`PaginationHandler`** — builds pagination components (buttons, select menus, sort/filter/search modals) with emoji access
- **`OutputHandler<T>`** — interface for cache-invalidation contract
- **`ItemHandler<T>`** — interface for paginated item lists; implementations: `EmbedItemHandler` (embed fields), `ComponentItemHandler` (sections)
- **`FilterHandler`** / **`SortHandler`** / **`SearchHandler`** — item filtering, sorting, and search state
- **`Filter`** / **`Sorter`** / **`Search`** — builder-pattern definitions for filter/sort/search criteria

## Module-Specific Patterns

- **`DiscordReference`** — base class for anything needing bot access; provides `getDiscordBot()`, `getEmoji()`, `isDeveloper()`, permission helpers.
- **`Component.Type`** enum maps to Discord's integer component type IDs and tracks which types require the Components V2 flag.
- **Library dependencies** — declared directly as JitPack coordinates (`com.github.simplified-dev:*:master-SNAPSHOT`) in `build.gradle.kts`. The module is standalone and does not depend on a SkyBlock-Simplified `api` module.
