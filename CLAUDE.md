# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

This is a submodule of the SkyBlock-Simplified multi-module Gradle project (Java 17). Run commands from the monorepo root (`../`).

```bash
# Build this module
./gradlew :discord-api:build

# Run tests
./gradlew :discord-api:test

# Clean build
./gradlew :discord-api:clean :discord-api:build
```

**Required environment variables:** `DISCORD_TOKEN`, `DEVELOPER_ERROR_LOG_CHANNEL_ID`

The debug bot (`src/test/.../debug/DebugBot.java`) can be run directly to test commands in isolation.

## Architecture Overview

This module is a **framework layer on top of Discord4J** that provides a builder-driven, reactive API for building Discord bots. Entry point: `DiscordBot` (sole class in root `discordapi` package). Configuration via `DiscordConfig` (in `handler/`).

```
DiscordBot (abstract) → DiscordConfig (handler/) → initialize() → login() + connect()
    ├── CommandHandler    — registers & routes commands
    ├── ResponseHandler   — caches active Response messages (ConcurrentList<CachedResponse>)
    ├── EmojiHandler      — manages custom emoji upload/lookup
    └── ExceptionHandler  — handles errors from commands/listeners
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

### Response System

`Response` is an interface with two implementations:

- **`TreeResponse`** (`Response.builder()`) — standard paginated responses with hierarchical subpage navigation via `TreeHistoryHandler`
- **`FormResponse`** (`Response.form()`) — sequential form/wizard-style responses with index-based navigation via `IndexHistoryHandler`

Both support:
- Multiple `Page` instances (select menu navigation)
- `ItemHandler` for paginated field items with sort/filter/search
- Interactive components (`Button`, `SelectMenu`, `TextInput`, `Modal`)
- Attachments, embeds, reactions
- Auto-expiration via `timeToLive` (5-300 seconds)
- Automatic Discord4J spec generation (`getD4jCreateSpec()`, `getD4jEditSpec()`, etc.)

Page hierarchy:
```
Page (interface)
├── TreePage  — supports nested subpages (TreeHistoryHandler), embeds, content
└── FormPage  — form/question pages for sequential input
```

### Component System (top-level `component/` package)

Components are a top-level package (`component/`), independent of `response/`. They are quality-of-life builders for their Discord4J counterparts and can be constructed independently.

```
component/                — Component (interface), TextDisplay
component/interaction/    — ActionComponent, Button, SelectMenu, TextInput, Modal
component/layout/         — LayoutComponent, ActionRow, Container, Section, Separator, Label
component/media/          — Attachment, FileUpload, MediaData, MediaGallery, Thumbnail
component/type/           — capability interfaces for type filtering:
    AccessoryComponent, ContainerComponent, EventComponent, LabelComponent,
    SectionComponent, ToggleableComponent, TopLevelMessageComponent,
    TopLevelModalComponent, UserInteractComponent
```

Components support Discord's Components V2 flag (`IS_COMPONENTS_V2`) — detected automatically when v2 component types are present.

### Context Hierarchy

Every event gets a typed context wrapping the Discord4J event:

```
context/                  — EventContext, InteractionContext, DeferrableInteractionContext, ExceptionContext
context/command/          — CommandContext, SlashCommandContext, UserCommandContext,
                            MessageCommandContext, AutoCompleteContext, TypingContext
context/component/        — ComponentContext, ActionComponentContext, ButtonContext,
                            SelectMenuContext, OptionContext, ModalContext
context/message/          — MessageContext, ReactionContext
```

Contexts provide: `reply()`, `edit()`, `followup()`, `presentModal()`, `deleteFollowup()`, and access to the cached `Response`/`CachedResponse`.

### Listener System

All listeners extend `DiscordListener<T extends Event>` and are auto-registered via classpath scanning of the `dev.sbs.discordapi.listener` package. Additional listeners can be registered through `DiscordConfig.Builder.withListeners()`.

### Handler Classes (in `response/handler/`)

- **`HistoryHandler`** — abstract base for page navigation state
  - `TreeHistoryHandler` — tree-based page traversal (back/forward through subpages)
  - `IndexHistoryHandler` — linear index-based page traversal
- **`ItemHandler`** — manages paginated item lists with `FilterHandler`, `SortHandler`, `SearchHandler`
- **`OutputHandler`** — renders items into embed fields

## Key Patterns

- **Lombok** used extensively: `@Getter`, `@RequiredArgsConstructor`, `@Log4j2`, `@Builder`, etc. Logger field is non-static (`lombok.log.fieldIsStatic = false` in root `lombok.config`).
- **`@NotNull`/`@Nullable`** from JetBrains on all method parameters and return types.
- **Reactive** — all command/listener handlers return `Mono<Void>` using Project Reactor.
- **`DiscordReference`** — base class for anything needing bot access; provides `getDiscordBot()`, `getEmoji()`, `isDeveloper()`, permission helpers.
- **Builder pattern everywhere** — `Response.builder()`, `Page.builder()`, `Button.builder()`, `Embed.builder()`, `DiscordConfig.builder()`, all using `ClassBuilder<T>` with `@BuildFlag` validation.
- **`Concurrent.*` collections** — thread-safe `ConcurrentList`, `ConcurrentMap`, `ConcurrentSet` from the `api` module used instead of standard Java collections.
- **`optionalProject()`** in `build.gradle.kts` — allows building standalone by falling back to JitPack snapshot if the `:api` subproject is absent.
- **Mutate pattern** — immutable objects provide `mutate()` returning a pre-filled builder for modification (e.g., `response.mutate().isEphemeral().build()`).
- **`Component.Type`** enum maps to Discord's integer component type IDs and tracks which types require the Components V2 flag.
