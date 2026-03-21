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

This module is a **framework layer on top of Discord4J** that provides a builder-driven, reactive API for building Discord bots. The core flow is:

```
DiscordBot (abstract) → DiscordConfig (builder) → initialize() → login() + connect()
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

### Component Hierarchy

```
Component (interface)
├── LayoutComponent (abstract) — contains child components
│   ├── ActionRow     — row of buttons/select menus/text inputs
│   ├── Container     — v2 container component
│   ├── Section       — v2 section with text + accessory
│   ├── Separator     — v2 separator
│   └── Label         — v2 label
├── ActionComponent (abstract) — interactive components
│   ├── Button        — clickable button with styles, PageType for paging controls
│   ├── SelectMenu    — dropdown with Option entries, PageType for page/subpage/item selection
│   └── TextInput     — modal text field
├── TextDisplay       — v2 text display
├── Thumbnail         — v2 thumbnail media
├── Attachment        — file attachment
├── MediaGallery      — v2 media gallery
├── FileUpload        — v2 file upload
└── Modal             — modal dialog containing ActionRows of TextInputs
```

Components support Discord's Components V2 flag (`IS_COMPONENTS_V2`) — detected automatically when v2 component types are present.

### Context Hierarchy

Every event gets a typed context wrapping the Discord4J event:

```
EventContext
└── InteractionContext
    ├── AutoCompleteContext
    └── DeferrableInteractionContext
        ├── CommandContext
        │   ├── SlashCommandContext
        │   ├── UserCommandContext
        │   └── MessageCommandContext
        └── ComponentContext
            ├── ActionComponentContext
            │   ├── ButtonContext
            │   └── SelectMenuContext (→ OptionContext)
            └── ModalContext
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
