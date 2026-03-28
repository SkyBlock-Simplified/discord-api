# Discord API

Builder-driven, reactive Discord bot framework for the
[SkyBlock Simplified](https://github.com/SkyBlock-Simplified) ecosystem.
Built on [Discord4J](https://github.com/Discord4J/Discord4J) and
[Project Reactor](https://projectreactor.io/), it provides a structured
command system, component builders, paginated responses, and event listener
discovery.

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Quick Example](#quick-example)
- [Architecture](#architecture)
  - [Entry Point](#entry-point)
  - [Command System](#command-system)
  - [Response System](#response-system)
  - [Component System](#component-system)
  - [Context Hierarchy](#context-hierarchy)
  - [Listener System](#listener-system)
  - [Exception Handling](#exception-handling)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Command framework** - Slash commands, user commands, and message commands
  via `@Structure`-annotated classes with automatic Discord registration
- **Paginated responses** - Tree-based (`TreeResponse`) and form-based
  (`FormResponse`) paginated message builders with subpage navigation, item
  handlers, sort/filter/search, and auto-expiration
- **Component builders** - Quality-of-life builders for Discord's interaction
  components (`Button`, `SelectMenu`, `TextInput`, `Modal`, `RadioGroup`,
  `Checkbox`, `CheckboxGroup`) and layout components (`ActionRow`, `Container`,
  `Section`, `Separator`, `TextDisplay`), with Components V2 support
- **Context system** - Typed wrappers around Discord4J events providing
  `reply()`, `edit()`, `followup()`, `presentModal()`, and cached response
  access
- **Listener discovery** - Event listeners are auto-registered via classpath
  scanning, with support for additional runtime registration
- **Shard management** - Built-in gateway shard handling via `ShardHandler`
- **Error tracking** - Pluggable exception handler chain with built-in Discord
  embed reporting and optional [Sentry](https://sentry.io/) integration
- **Locale support** - `DiscordLocale` enum covering all Discord-supported
  BCP 47 language tags for command internationalization

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | **9.4+** | Included via wrapper (`./gradlew`) |
| Discord bot token | - | Create one at the [Discord Developer Portal](https://discord.com/developers/applications) |

### Installation

This module depends on the [api](../api) module, declared as a Maven
coordinate (`dev.sbs:api:0.1.0`). For local development you can clone both
repositories side by side and use a Gradle composite build:

```bash
git clone https://github.com/SkyBlock-Simplified/api.git
git clone https://github.com/SkyBlock-Simplified/discord-api.git
cd discord-api
```

Build the library:

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

**Required environment variables:**

```
DISCORD_TOKEN                   - Discord bot token
DEVELOPER_ERROR_LOG_CHANNEL_ID  - Discord channel ID for error logging
```

## Quick Example

```java
// Define a command
@Structure(
    name = "ping",
    description = "Replies with pong"
)
public class PingCommand extends DiscordCommand<SlashCommandContext> {

    @Override
    public Mono<Void> process(@NotNull SlashCommandContext context) {
        return context.reply("Pong!");
    }
}

// Start the bot
public class MyBot extends DiscordBot {
    public static void main(String[] args) {
        new MyBot().start(
            DiscordConfig.builder()
                .withToken(System.getenv("DISCORD_TOKEN"))
                .withCommands(PingCommand.class)
                .build()
        );
    }
}
```

## Architecture

### Entry Point

`DiscordBot` is the abstract base class for all bots. It accepts a
`DiscordConfig` (built via `DiscordConfig.builder()`) that configures the
token, gateway intents, shard count, commands, and listeners. The bot
initializes handlers, logs into Discord, and connects to the gateway.

### Command System

Commands extend `DiscordCommand<C extends CommandContext<?>>` and are annotated
with `@Structure`:

| Context Type | Command Type |
|-------------|--------------|
| `SlashCommandContext` | Slash commands (`/command`) |
| `UserCommandContext` | Right-click user commands |
| `MessageCommandContext` | Right-click message commands |

`@Structure` configures: `name`, `description`, `parent` (subcommands),
`group` (subcommand groups), `guildId` (-1 for global), `ephemeral`,
`developerOnly`, `singleton`, `botPermissions`, `userPermissions`,
`integrations`, `contexts`.

Commands are discovered via classpath scanning and registered through
`CommandHandler`.

### Response System

`Response` is a single final class built via `Response.builder()`. Pagination
behavior is determined by the `Page` type used:

| Page Type | Builder | Navigation | Use Case |
|-----------|---------|------------|----------|
| `TreePage` | `Page.builder()` | Hierarchical subpage tree | Multi-level menus |
| `FormPage` | `Page.form()` | Sequential question-based | Wizards, multi-step forms |

Responses support multiple `Page` instances (select menu navigation),
`ItemHandler` (paginated items with sort/filter/search via `EmbedItemHandler`
or `ComponentItemHandler`), interactive components, attachments, and
auto-expiration.

### Component System

Components are a top-level package independent of the response system. They
provide quality-of-life builders for Discord4J component types:

| Package | Components |
|---------|------------|
| `component/` | `Component` (root interface), `TextDisplay` |
| `component/interaction/` | `Button`, `SelectMenu`, `TextInput`, `Modal`, `RadioGroup`, `Checkbox`, `CheckboxGroup` |
| `component/layout/` | `ActionRow`, `Container`, `Section`, `Separator`, `Label` |
| `component/media/` | `Attachment`, `FileUpload`, `MediaGallery`, `Thumbnail` |
| `component/capability/` | Behavioral contracts - `EventInteractable`, `ModalUpdatable`, `Toggleable`, `UserInteractable` |
| `component/scope/` | Discord placement scoping - `AccessoryComponent`, `ContainerComponent`, `SectionComponent`, `TopLevelMessageComponent`, `TopLevelModalComponent` |

Components V2 is detected automatically when v2 component types are present.

### Context Hierarchy

Every Discord event gets a typed context wrapping the Discord4J event:

```
EventContext
├── InteractionContext
│   ├── AutoCompleteContext
│   └── DeferrableInteractionContext
│       ├── CommandContext [+ TypingContext]
│       │   ├── SlashCommandContext
│       │   ├── UserCommandContext
│       │   └── MessageCommandContext
│       └── ComponentContext [+ MessageContext]
│           ├── ActionComponentContext
│           │   ├── ButtonContext
│           │   ├── SelectMenuContext
│           │   └── OptionContext
│           ├── CheckboxContext
│           ├── CheckboxGroupContext
│           ├── RadioGroupContext
│           └── ModalContext
├── MessageContext
│   └── ReactionContext
└── ExceptionContext
```

Contexts provide: `reply()`, `edit()`, `followup()`, `presentModal()`,
`deleteFollowup()`, and access to the cached `Response` / `CachedResponse`.

### Listener System

Listeners extend `DiscordListener<T extends Event>` and are auto-registered
via classpath scanning. Built-in listeners handle:

- **Commands** - `SlashCommandListener`, `UserCommandListener`,
  `MessageCommandListener`, `AutoCompleteListener`
- **Components** - `ComponentListener`, `ButtonListener`,
  `SelectMenuListener`, `ModalListener`, `CheckboxListener`,
  `CheckboxGroupListener`, `RadioGroupListener`
- **Messages** - `MessageCreateListener`, `MessageDeleteListener`,
  `ReactionAddListener`, `ReactionRemoveListener`
- **Lifecycle** - `DisconnectListener`, `GuildCreateListener`

Additional listeners can be registered via
`DiscordConfig.Builder.withListeners()`.

### Exception Handling

Exceptions are routed through a pluggable `ExceptionHandler` chain:

| Handler | Purpose |
|---------|---------|
| `ExceptionHandler` | Abstract base class |
| `DiscordExceptionHandler` | Formats errors into Discord embeds and sends them to the user and a developer log channel |
| `SentryExceptionHandler` | Captures exceptions to [Sentry](https://sentry.io/) with enriched Discord context tags |
| `CompositeExceptionHandler` | Chains multiple handlers in sequence (e.g. Sentry capture then Discord embed) |

Command-specific exceptions (`PermissionException`, `InputException`,
`ParameterException`, etc.) are caught and rendered as user-facing error
embeds automatically.

## Project Structure

```
discord-api/
├── src/main/java/dev/sbs/discordapi/
│   ├── DiscordBot.java                 # Abstract bot entry point
│   ├── command/
│   │   ├── DiscordCommand.java         # Base command class with @Structure
│   │   ├── exception/                  # CommandException, PermissionException, etc.
│   │   └── parameter/                  # Parameter, Argument
│   ├── component/
│   │   ├── Component.java              # Root component interface
│   │   ├── TextDisplay.java            # Text display component (V2)
│   │   ├── interaction/                # Button, SelectMenu, TextInput, Modal,
│   │   │                               # RadioGroup, Checkbox, CheckboxGroup
│   │   ├── layout/                     # ActionRow, Container, Section, Separator, Label
│   │   ├── media/                      # Attachment, FileUpload, MediaGallery, Thumbnail
│   │   ├── capability/                 # EventInteractable, Toggleable, ModalUpdatable,
│   │   │                               # UserInteractable
│   │   └── scope/                      # AccessoryComponent, ContainerComponent,
│   │                                   # SectionComponent, TopLevelMessageComponent, etc.
│   ├── context/
│   │   ├── EventContext.java           # Root context interface
│   │   ├── command/                    # CommandContext, SlashCommandContext, etc.
│   │   ├── component/                  # ComponentContext, ButtonContext, ModalContext,
│   │   │                               # CheckboxContext, RadioGroupContext, etc.
│   │   └── message/                    # MessageContext, ReactionContext
│   ├── exception/                      # DiscordException, DiscordUserException, etc.
│   ├── handler/
│   │   ├── DiscordConfig.java          # Builder-pattern bot configuration
│   │   ├── CommandHandler.java         # Command registration and routing
│   │   ├── EmojiHandler.java           # Custom emoji upload/lookup
│   │   ├── DiscordLocale.java          # BCP 47 locale enum
│   │   ├── exception/                  # ExceptionHandler, DiscordExceptionHandler,
│   │   │                               # SentryExceptionHandler, CompositeExceptionHandler
│   │   ├── response/                   # ResponseHandler, CachedResponse,
│   │   │                               # ResponseEntry, ResponseFollowup
│   │   └── shard/                      # ShardHandler, Shard
│   ├── listener/
│   │   ├── command/                    # Slash, user, message command listeners
│   │   ├── component/                  # Button, select menu, modal, checkbox,
│   │   │                               # radio group listeners
│   │   ├── message/                    # Message create/delete, reaction listeners
│   │   └── lifecycle/                  # Disconnect, guild create listeners
│   ├── response/
│   │   ├── Response.java               # Response interface + TreeResponse/FormResponse
│   │   ├── Emoji.java                  # Emoji representation
│   │   ├── embed/                      # Embed, Author, Field, Footer
│   │   ├── handler/                    # HistoryHandler, PaginationHandler, OutputHandler,
│   │   │   │                           # FilterHandler, SortHandler, SearchHandler,
│   │   │   │                           # Filter, Sorter, Search
│   │   │   └── item/                   # ItemHandler, EmbedItemHandler, ComponentItemHandler
│   │   └── page/                       # Page, TreePage, FormPage, Paging, Summary,
│   │       │                           # Subpages, Question
│   │       └── item/                   # Item, AuthorItem, TitleItem, DescriptionItem, etc.
│   │           └── field/              # FieldItem, StringItem, NumberItem, ToggleItem, etc.
│   └── util/                           # DiscordReference, DiscordDate, DiscordProtocol,
│                                       # ProgressBar
├── src/test/java/                      # JUnit 5 tests, DebugBot, DiagramGenerator
├── build.gradle.kts
└── gradle/libs.versions.toml           # Version catalog
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style
guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0**.
