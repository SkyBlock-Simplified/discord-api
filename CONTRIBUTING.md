# Contributing to Discord API

Thank you for your interest in contributing! This document explains how to get
started, what to expect during the review process, and the conventions this
project follows.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
  - [Running the Debug Bot](#running-the-debug-bot)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Commit Messages](#commit-messages)
  - [Testing](#testing)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Project Architecture](#project-architecture)
- [Legal](#legal)

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Git](https://git-scm.com/) | 2.x+ | For cloning and contributing |
| [IntelliJ IDEA](https://www.jetbrains.com/idea/) | Latest | Recommended IDE |
| Discord bot token | - | Create one at the [Discord Developer Portal](https://discord.com/developers/applications) |

**Required environment variables:**

| Variable | Description |
|----------|-------------|
| `DISCORD_TOKEN` | Discord bot token |
| `DEVELOPER_ERROR_LOG_CHANNEL_ID` | Discord channel ID for error logging |

### Development Setup

1. **Fork and clone the repository**

   [Fork the repository](https://github.com/SkyBlock-Simplified/discord-api/fork),
   then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/discord-api.git
   cd discord-api
   ```

2. **Clone the `api` module alongside** (for local development)

   This module depends on the [api](https://github.com/SkyBlock-Simplified/api)
   module (`dev.sbs:api:0.1.0`). For local development, clone the `api`
   repository alongside this one and use a Gradle composite build.

   ```bash
   cd ..
   git clone https://github.com/SkyBlock-Simplified/api.git
   ```

3. **Build the project**

   ```bash
   cd discord-api
   ./gradlew build
   ```

4. **Open in IntelliJ IDEA**

   Open the project root as a Gradle project. Ensure the Lombok plugin is
   installed and annotation processing is enabled.

5. **Verify the setup**

   ```bash
   ./gradlew test
   ```

### Running the Debug Bot

A `DebugBot` class in `src/test/` allows testing commands in isolation without
starting the full bot. Set the required environment variables and run it
directly from IntelliJ or via Gradle:

```bash
./gradlew test --tests "*.debug.DebugBot"
```

## Making Changes

### Branching Strategy

- Create a feature branch from `master` for your work.
- Use a descriptive branch name: `fix/modal-submit-handler`,
  `feat/media-gallery-component`, `docs/response-examples`.

```bash
git checkout -b feat/my-feature master
```

### Code Style

#### General

- **Reactive** - All command and listener handlers return `Mono<Void>` using
  Project Reactor. Never block the event loop.
- **Collections** - Always use `Concurrent.newList()`, `Concurrent.newMap()`,
  etc. instead of standard Java collections.
- **Annotations** - Use `@NotNull` / `@Nullable` from `org.jetbrains.annotations`
  on all public method parameters and return types.
- **Lombok** - Use `@Getter`, `@RequiredArgsConstructor`, `@Log4j2`, etc.
  The logger field is non-static (`lombok.log.fieldIsStatic = false`).
- **Builder pattern** - Use `ClassBuilder<T>` with `@BuildFlag` validation.
  Follow the existing pattern in `Response.builder()`, `Page.builder()`,
  `Button.builder()`, etc.

#### Javadoc

- **Class level** - Noun phrase describing what the type is.
- **Method level** - Active verb, third person singular.
- **Tags** - `@param`, `@return`, `@throws` on public methods. Lowercase
  sentence fragments, no trailing period. Single space after param name.
- **Punctuation** - Only single hyphens (` - `) as separators.
- Never use `@author` or `@since`.

#### Commands

- Every `DiscordCommand` subclass must have a `@Structure` annotation with
  a unique `name`.
- Implement `process(C context)` and return `Mono<Void>`.
- Use `getParameters()` to define slash command options.

### Commit Messages

Write clear, concise commit messages that describe *what* changed and *why*.

```
Add Container component for Discord Components V2

Implements the new container layout component that wraps other
components with optional accent color and spoiler support.
```

- Use the imperative mood ("Add", "Fix", "Update").
- Keep the subject line under 72 characters.
- Add a body when the *why* isn't obvious from the subject.

### Testing

Tests use JUnit 5 (Jupiter):

```bash
./gradlew test
```

- The `DebugBot` in `src/test/` is the primary way to test commands
  interactively against a live Discord gateway.
- Unit tests for component builders, context logic, and handler state
  don't require a live connection.

## Submitting a Pull Request

1. **Push your branch** to your fork.

   ```bash
   git push origin feat/my-feature
   ```

2. **Open a Pull Request** against the `master` branch of
   [SkyBlock-Simplified/discord-api](https://github.com/SkyBlock-Simplified/discord-api).

3. **In the PR description**, include:
   - A summary of the changes and the motivation behind them.
   - Steps to test or verify the changes.
   - Screenshots or recordings of Discord interactions if applicable.

4. **Respond to review feedback.** PRs may go through one or more rounds of
   review before being merged.

### What gets reviewed

- Correctness of reactive chains (no blocking calls, proper error handling).
- Adherence to the builder pattern and component type system.
- Impact on downstream modules (`simplified-bot`).
- Compatibility with Discord's API and Components V2 flag behavior.

## Reporting Issues

Use [GitHub Issues](https://github.com/SkyBlock-Simplified/discord-api/issues)
to report bugs or request features.

When reporting a bug, include:

- **Java version** (`java --version`)
- **Discord4J version** (check `gradle/libs.versions.toml`)
- **Operating system**
- **Full error stacktrace** (if applicable)
- **Steps to reproduce**
- **Expected vs. actual behavior**

## Project Architecture

A brief overview to help you find your way around the codebase:

```
src/main/java/dev/sbs/discordapi/
├── DiscordBot.java             # Abstract entry point (configure -> login -> connect)
├── command/
│   ├── DiscordCommand.java     # Base command class with @Structure annotation
│   ├── exception/              # CommandException, PermissionException, InputException, etc.
│   └── parameter/              # Parameter, Argument
├── component/
│   ├── Component.java          # Root component interface
│   ├── TextDisplay.java        # Text display component (V2)
│   ├── interaction/            # Button, SelectMenu, TextInput, Modal,
│   │                           # RadioGroup, Checkbox, CheckboxGroup
│   ├── layout/                 # ActionRow, Container, Section, Separator, Label
│   ├── media/                  # Attachment, FileUpload, MediaGallery, Thumbnail
│   ├── capability/             # EventInteractable, Toggleable, ModalUpdatable,
│   │                           # UserInteractable
│   └── scope/                  # AccessoryComponent, ContainerComponent,
│                               # SectionComponent, TopLevelMessageComponent, etc.
├── context/
│   ├── EventContext.java       # Root context interface
│   ├── command/                # CommandContext, SlashCommandContext, AutoCompleteContext, etc.
│   ├── component/              # ComponentContext, ButtonContext, SelectMenuContext,
│   │                           # ModalContext, CheckboxContext, RadioGroupContext, etc.
│   └── message/                # MessageContext, ReactionContext
├── exception/                  # DiscordException, DiscordUserException, etc.
├── handler/
│   ├── DiscordConfig.java      # Builder-pattern bot configuration
│   ├── CommandHandler.java     # Command registration and routing
│   ├── EmojiHandler.java       # Custom emoji upload/lookup
│   ├── DiscordLocale.java      # BCP 47 locale enum
│   ├── exception/              # ExceptionHandler, DiscordExceptionHandler,
│   │                           # SentryExceptionHandler, CompositeExceptionHandler
│   ├── response/               # ResponseHandler, CachedResponse, ResponseEntry,
│   │                           # ResponseFollowup
│   └── shard/                  # ShardHandler, Shard
├── listener/
│   ├── command/                # Slash, user, message command listeners
│   ├── component/              # Button, select menu, modal, checkbox,
│   │                           # radio group listeners
│   ├── message/                # Message create/delete, reaction listeners
│   └── lifecycle/              # Disconnect, guild create listeners
├── response/
│   ├── Response.java           # Response interface + TreeResponse/FormResponse
│   ├── Emoji.java              # Emoji representation
│   ├── embed/                  # Embed, Author, Field, Footer
│   ├── handler/                # HistoryHandler, PaginationHandler, OutputHandler,
│   │   │                       # FilterHandler, SortHandler, SearchHandler
│   │   └── item/               # ItemHandler, EmbedItemHandler, ComponentItemHandler
│   └── page/                   # Page, TreePage, FormPage, Paging, Summary, Subpages
│       └── item/               # Item, AuthorItem, TitleItem, DescriptionItem, etc.
│           └── field/          # FieldItem, StringItem, NumberItem, ToggleItem, etc.
└── util/                       # DiscordReference, DiscordDate, DiscordProtocol, ProgressBar
```

### Key extension points

- **New command** - Extend `DiscordCommand<SlashCommandContext>` (or other
  context type) and annotate with `@Structure`.
- **New component** - Implement the relevant `Component` interface and add a
  builder following the existing pattern.
- **New listener** - Extend `DiscordListener<T extends Event>` in the
  `listener/` package. It will be discovered automatically via classpath
  scanning.
- **New response type** - Implement the `Response` interface with a custom
  `HistoryHandler`.
- **New exception handler** - Extend `ExceptionHandler` and register it via
  `DiscordConfig` or wrap it in a `CompositeExceptionHandler`.

## Legal

By submitting a pull request, you agree that your contributions are licensed
under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0),
the same license that covers this project.
