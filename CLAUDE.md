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
    ├── ResponseLocator       — hot cache + cold eternal repository (handler/response/)
    ├── ComponentDispatcher   — @Component/@Eternal route registry (handler/)
    ├── ResponseExpiryTask    — scheduled hot-cache reaper (handler/response/)
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
├── TreePage    — implements Subpages<TreePage>; supports nested subpages, embeds, content
└── EditorPage  — page/editor/; in-place page editing
```

- `Page.builder()` → `TreePage.TreePageBuilder` (the concrete page)
- `Response.builder()` builds the response; `Response.from()` creates a pre-filled builder from an existing response; `response.mutate()` is shorthand for `Response.from(this)`

Response features:
- Multiple `Page` instances (select menu navigation)
- `ItemHandler<T>` for paginated items with sort/filter/search
  - `EmbedItemHandler` — renders items as embed fields
  - `ComponentItemHandler` — renders items as `Section` components
- Interactive components (`Button`, `SelectMenu`, `TextInput`, `Modal`, `RadioGroup`, `Checkbox`, `CheckboxGroup`)
- Attachments, embeds, reactions
- Auto-expiration via `timeToLive` (5-300 seconds)
- Automatic Discord4J spec generation (`getD4jCreateSpec(EmojiResolver)`, `getD4jEditSpec(EmojiResolver)`, etc.) — `Response`/`Page`/`Component` are pure data; emoji resolution is injected at render time via `response/EmojiResolver` (sourced from `EventContext.getEmojis()`)
- **Eternal (reboot-surviving) responses** via `Response.builder().asEternal(builderKey, payload)` (usually through `EventContext.replyEternal(builderKey, payload)`). The structure is rebuilt on demand by an `@Eternal(builderKey)`-annotated method that returns a `Response`; only a tiny coordinate (ids + `builderKey` + opaque `payload` + `NavState`) is persisted to the storage-agnostic cold store. See the `handler/response/` section below for the full flow.

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

There are two parallel listener hierarchies, both auto-registered via classpath scanning of the `dev.simplified.discordapi.listener` package:

- **`DiscordListener<T extends discord4j.core.event.domain.Event>`** — handles Discord4J gateway events. Subscribed to Discord4J's `EventDispatcher`. Errors are routed through the `ExceptionHandler` chain.
- **`BotEventListener<T extends BotEvent>`** — handles bot-internal events emitted by `DiscordBot` itself (lifecycle hooks, future custom events). Subscribed to a `Sinks.Many<BotEvent>` replay sink owned by `DiscordBot` (last 16 events replayed to late subscribers, so listeners registered inside `connect()` still receive events emitted during `login()`). Errors are logged locally.

Additional listeners of either type can be registered through `DiscordConfig.Builder.withListeners()` (Discord4J events) or `withBotEventListeners()` (bot events).

```
listener/                 — DiscordListener, BotEventListener (base classes)
listener/command/         — SlashCommandListener, UserCommandListener,
                            MessageCommandListener, AutoCompleteListener
listener/component/       — ComponentListener (single listener; all component kinds
                            dispatched polymorphically)
listener/message/         — MessageCreateListener, MessageDeleteListener,
                            ReactionAddListener, ReactionRemoveListener
listener/lifecycle/       — DisconnectListener (BotEventListener), GuildCreateListener
listener/                 — EternalComponentListener (base for shared @Component click
                            handlers and @Eternal builders; classpath-scanned at startup)
                          — Component (@Component click-handler annotation)
                          — Eternal (@Eternal response-rebuild annotation)
```

#### Bot Event Hierarchy

`dev.simplified.discordapi.event` houses internal events that are emitted by `DiscordBot` and consumed by `BotEventListener` subclasses. Lifecycle hooks (`onClientCreated`, `onGatewayConnected`, `onGatewayDisconnect`) are NOT exposed as protected methods on `DiscordBot` — `DiscordBot` is the single bridge that translates Discord4J gateway events into bot events, and listeners are the only extension point.

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

**`handler/response/`** — one `ResponseLocator` contract with hot and cold tiers as peer implementations and a dumb composite chain (modeled on `CompositeExceptionHandler`). "Locator" = a cache tier resolving live `CachedResponse`s; "Repository" = the durable record backend one layer down:
- **`ResponseLocator`** — the reactive contract: `findForInteraction` (the only rebuild-capable read; defaults to `findByMessage`), the plain finders (`findByMessage`/`findByResponseId`/`findFollowupBy*`), `store`/`storeFollowup`, `update` (nav-coordinate write-through), `evict(UUID)` (hot-only drop, for the reaper + temporary cleanup), `deleteByMessage(Snowflake)` (all-tier, message-keyed teardown), `seed(CachedResponse)` (cache a pre-built entry, returning the canonical instance), `findExpired`.
- **`InMemoryResponseLocator`** — hot tier backed by a `uniqueId → CachedResponse` map plus a `messageId → uniqueId` index for O(1) lookups. `store` copies the response's `builderKey` onto the entry; `seed` is an atomic `computeIfAbsent` returning the canonical entry.
- **`EternalResponseLocator`** — cold peer that rebuilds eternals on demand from an `EternalResponseRepository`. The ONE place that knows how to rebuild a `Response` (`findForInteraction`), persist a cold record (`store`), write NavState through on change (`update`, dirty-checked), and tear down (`deleteByMessage`). Every other method is a truthful no-op, so plain reads never fire an `@Eternal` builder.
- **`CompositeResponseLocator`** — holds `List<ResponseLocator>` (hot-first), knowing nothing but the interface. Reads resolve to the first tier that answers; `findForInteraction` additionally promotes a cold-tier hit up into the hot tier via `seed` (read-through cache promotion, single-flight on the canonical). Writes fan out to every tier.
- **`CachedResponse`** — single concrete entry type representing both top-level replies and followups (followups have `parentId` set). Lifecycle is a `State` enum (`IDLE`, `BUSY`, `DEFERRED`, `ACKNOWLEDGED`); content dirty-tracking flows through `Response.isCacheUpdateRequired()`. Eternal entries carry `builderKey` (`isEternal()`), so the reaper evicts them without disabling their components.
- **`NavState`** — `Serializable`, value-equal snapshot of the mutable navigation coordinate (current page, item page, page history) with `capture`/`applyTo`; carried across `Response.from()`/`mutate()` and persisted in the cold record.
- **`EternalResponseRepository`** (SPI) + **`InMemoryEternalResponseRepository`** (default) + **`GsonEternalResponseRepository`** (file/JSON built-in) + **`EternalResponseRecord`** (the small persisted coordinate) — the storage-agnostic durable backend, used only by `EternalResponseLocator`. The app plugs a database-backed implementation (e.g. Hibernate) via `DiscordConfig.Builder.withEternalRepository(...)`; only the flat record ever crosses the boundary, never the rendered response.
- **`ResponseExpiryTask`** — the scheduled reaper (replaces the old inline `connect()` sweep): one error-isolated pipeline, overlap-guarded, `evict`-ing mortal entries with component-disable and eternal entries without. Reaped by `DisconnectListener`'s `scheduler.shutdown()`.

**Eternal component/builder routing** (`handler/ComponentDispatcher`) — one registry for both hot and cold:
- Scanned at bot startup over loaded `DiscordCommand` instances and `EternalComponentListener` subclasses.
- `@Component(value[, regex, cacheTtl])`-annotated methods (`listener/Component`) register a click route from custom id → `MethodHandle` (exact + regex). Methods take a `ComponentContext` subtype and return a `Publisher<Void>`. The same route serves hot and (post-hydration) cold clicks.
- `@Eternal(value)`-annotated methods (`listener/Eternal`) register a rebuild route from `builderKey` → `MethodHandle`. Methods take an `EternalBuildContext` and return a `Response`. Invoked at creation, at hydration, and at refresh.
- `context/EternalBuildContext` is a lightweight `EventContext` that deliberately does NOT extend `MessageContext`, so a builder cannot call `getResponse()` while building one.

**Eternal response flow:**
1. `DiscordConfig.Builder.withEternalRepository(EternalResponseRepository)` supplies the durable backend (defaults to `InMemoryEternalResponseRepository`; `GsonEternalResponseRepository` for a file); `DiscordBot` builds a `CompositeResponseLocator` over `[InMemoryResponseLocator, EternalResponseLocator]`, the cold peer wrapping the repository + the (late-bound) `@Eternal` registry.
2. A command's `process()` calls `context.replyEternal(builderKey, payload)`: the framework invokes the `@Eternal` builder to build the `Response`, stamps its id + eternal marker, replies, and `store` writes both the hot entry and a cold `EternalResponseRecord`.
3. After a restart, when the user clicks the persisted component, `ComponentListener.apply` calls `responseLocator.findForInteraction(event)`. The composite misses the hot tier, reads the cold record, invokes the registered `@Eternal` builder with an `EternalBuildContext` to rebuild the `Response`, restores `NavState`, seeds the hot tier, and returns the hydrated `CachedResponse`.
4. Dispatch then proceeds exactly as for a hot hit — `matchComponent` finds the real rebuilt component and the `@Component` handler runs against a real `Response`; a navigating handler's `NavState` is written through to the cold record via `update`. `MessageDeleteListener` tears down both tiers via `deleteByMessage`; `DiscordBot.refreshEternal(responseId)` re-renders an eternal from its record without a hot entry.

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
