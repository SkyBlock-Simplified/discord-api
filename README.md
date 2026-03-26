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
  components (`Button`, `SelectMenu`, `TextInput`, `Modal`) and layout
  components (`ActionRow`, `Container`, `Section`, `Separator`), with
  Components V2 support
- **Context system** - Typed wrappers around Discord4J events providing
  `reply()`, `edit()`, `followup()`, `presentModal()`, and cached response
  access
- **Listener discovery** - Event listeners are auto-registered via classpath
  scanning, with support for additional runtime registration
- **Shard management** - Built-in gateway shard handling via `ShardHandler`

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | **8.12+** | Included via wrapper (`./gradlew`) |
| Discord bot token | - | Create one at the [Discord Developer Portal](https://discord.com/developers/applications) |

### Installation

This module depends on the [api](../api) module. For local development, clone
both repositories side by side:

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
DISCORD_TOKEN                   — Discord bot token
DEVELOPER_ERROR_LOG_CHANNEL_ID  — Discord channel ID for error logging
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

Two response types handle paginated, interactive messages:

| Type | Builder | Navigation | Use Case |
|------|---------|------------|----------|
| `TreeResponse` | `Response.builder()` | Hierarchical subpage tree | Multi-level menus |
| `FormResponse` | `Response.form()` | Sequential index-based | Wizards, multi-step forms |

Both support `Page` instances (select menu navigation), `ItemHandler`
(paginated fields with sort/filter/search), interactive components,
attachments, and auto-expiration.

### Component System

Components are a top-level package independent of the response system. They
provide quality-of-life builders for Discord4J component types:

| Package | Components |
|---------|------------|
| `component/interaction/` | `Button`, `SelectMenu`, `TextInput`, `Modal` |
| `component/layout/` | `ActionRow`, `Container`, `Section`, `Separator`, `Label` |
| `component/media/` | `Attachment`, `FileUpload`, `MediaGallery`, `Thumbnail` |
| `component/type/` | Capability interfaces (`EventComponent`, `ToggleableComponent`, etc.) |

Components V2 is detected automatically when v2 component types are present.

### Context Hierarchy

Every Discord event gets a typed context wrapping the Discord4J event:

```
EventContext
├── InteractionContext
│   ├── DeferrableInteractionContext
│   │   ├── SlashCommandContext
│   │   ├── UserCommandContext
│   │   ├── MessageCommandContext
│   │   ├── ButtonContext
│   │   ├── SelectMenuContext
│   │   └── ModalContext
│   └── AutoCompleteContext
├── MessageContext
└── ReactionContext
```

Contexts provide: `reply()`, `edit()`, `followup()`, `presentModal()`,
`deleteFollowup()`, and access to the cached `Response` / `CachedResponse`.

### Listener System

Listeners extend `DiscordListener<T extends Event>` and are auto-registered
via classpath scanning. Built-in listeners handle:

- **Commands** - `SlashCommandListener`, `UserCommandListener`,
  `MessageCommandListener`, `AutoCompleteListener`
- **Components** - `ButtonListener`, `SelectMenuListener`, `ModalListener`
- **Messages** - `MessageCreateListener`, `MessageDeleteListener`,
  `ReactionAddListener`, `ReactionRemoveListener`
- **Lifecycle** - `DisconnectListener`, `GuildCreateListener`

Additional listeners can be registered via
`DiscordConfig.Builder.withListeners()`.

## Project Structure

```
discord-api/
├── src/main/java/dev/sbs/discordapi/
│   ├── DiscordBot.java                 # Abstract bot entry point
│   ├── command/                        # DiscordCommand, @Structure
│   ├── component/
│   │   ├── interaction/                # Button, SelectMenu, TextInput, Modal
│   │   ├── layout/                     # ActionRow, Container, Section, etc.
│   │   ├── media/                      # Attachment, FileUpload, MediaGallery
│   │   └── type/                       # Capability interfaces
│   ├── context/
│   │   ├── command/                    # SlashCommandContext, AutoCompleteContext
│   │   ├── component/                  # ButtonContext, SelectMenuContext, etc.
│   │   └── message/                    # MessageContext, ReactionContext
│   ├── handler/                        # CommandHandler, ResponseHandler, etc.
│   ├── listener/
│   │   ├── command/                    # Command event listeners
│   │   ├── component/                  # Component event listeners
│   │   ├── message/                    # Message event listeners
│   │   └── lifecycle/                  # Gateway lifecycle listeners
│   └── response/
│       ├── impl/                       # TreeResponse, FormResponse
│       ├── handler/                    # HistoryHandler, ItemHandler, OutputHandler
│       └── page/                       # TreePage, FormPage
├── src/test/java/                      # JUnit 5 tests and DebugBot
├── build.gradle.kts
└── gradle/libs.versions.toml           # Version catalog
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style
guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0**.
