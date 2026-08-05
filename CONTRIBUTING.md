# Contributing to discord4j-framework

Thank you for your interest in contributing! This document explains how to get
started, what to expect during the review process, and the conventions this
project follows.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
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
| Discord bot token | - | Only to run a real bot; **not** needed to build or test |

**Environment variables** - required only when running a real bot against
Discord. The test suite runs fully offline and needs neither.

| Variable | Description |
|----------|-------------|
| `DISCORD_TOKEN` | Discord bot token |
| `DEVELOPER_ERROR_LOG_CHANNEL_ID` | Discord channel ID for error logging |

### Development Setup

1. **Fork and clone the repository**

   [Fork the repository](https://github.com/simplified-dev/discord4j-framework/fork),
   then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/discord4j-framework.git
   cd discord4j-framework
   ```

2. **Build the project**

   ```bash
   ./gradlew build
   ```

3. **Open in IntelliJ IDEA**

   Open the project root as a Gradle project. Ensure annotation processing is
   enabled - the `io.github.simplified-dev:annotations` processor generates the
   getters, logger fields, and equality pairs.

4. **Verify the setup**

   ```bash
   ./gradlew test
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
- **Annotation processor** - Use `@Getter`, `@Log`, and the equality-pair
  generator from `io.github.simplified-dev:annotations`. Lombok is no longer
  used anywhere in this project.
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

Tests use JUnit 5 (Jupiter) and run **fully offline** - no token, no network:

```bash
./gradlew test
./gradlew test -Dharness.debug=true   # show every REST call the bot made
```

- **Integration tests** boot a real bot against
  [discord4j-fauxrig](https://github.com/simplified-dev/discord4j-fauxrig), a
  localhost stand-in for Discord. `IntegrationHarness` wraps it with readiness
  waits and send helpers; add test commands under `integration/command/`, which
  is classpath-scanned.
- **Unit tests** for component builders, context logic, and handler state need
  no harness at all.

A change to command dispatch, component routing, or the response lifecycle
should come with an integration test that drives the real path.

## Submitting a Pull Request

1. **Push your branch** to your fork.

   ```bash
   git push origin feat/my-feature
   ```

2. **Open a Pull Request** against the `master` branch of
   [simplified-dev/discord4j-framework](https://github.com/simplified-dev/discord4j-framework).

3. **In the PR description**, include:
   - A summary of the changes and the motivation behind them.
   - Steps to test or verify the changes.
   - Screenshots or recordings of Discord interactions if applicable.

4. **Respond to review feedback.** PRs may go through one or more rounds of
   review before being merged.

### What gets reviewed

- Correctness of reactive chains (no blocking calls, proper error handling).
- Adherence to the builder pattern and component type system.
- Impact on downstream bots that consume this framework.
- Compatibility with Discord's API and Components V2 flag behavior.
- Integration coverage for anything touching dispatch or the response lifecycle.

## Reporting Issues

Use [GitHub Issues](https://github.com/simplified-dev/discord4j-framework/issues)
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
src/main/java/dev/simplified/discordapi/
├── DiscordBot.java             # Abstract entry point (configure -> login -> connect)
├── command/
│   ├── DiscordCommand.java     # Base command class with @Structure annotation
│   ├── CommandStateResolver.java  # Runtime enable/disable, keyed by CommandKey
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
├── event/                      # BotEvent + lifecycle bot events
├── handler/
│   ├── DiscordConfig.java      # Builder-pattern bot configuration
│   ├── CommandHandler.java     # Command registration and routing
│   ├── ComponentDispatcher.java   # @Component / @Eternal route registry
│   ├── EmojiHandler.java       # Custom emoji upload/lookup
│   ├── DiscordLocale.java      # BCP 47 locale enum
│   ├── exception/              # ExceptionHandler, DiscordExceptionHandler,
│   │                           # SentryExceptionHandler, CompositeExceptionHandler
│   ├── response/               # ResponseLocator (InMemory/Eternal/Composite),
│   │                           # CachedResponse, NavState, ResponseExpiryTask,
│   │                           # EternalResponseRepository + Record
│   └── shard/                  # ShardHandler, Shard
├── listener/
│   ├── Component.java          # @Component click-handler annotation
│   ├── Eternal.java            # @Eternal response-rebuild annotation
│   ├── command/                # Slash, user, message command listeners
│   ├── component/              # ComponentListener (all kinds, polymorphic)
│   ├── message/                # Message create/delete, reaction listeners
│   └── lifecycle/              # Disconnect, guild create listeners
├── response/
│   ├── Response.java           # Final class; built via Response.builder()
│   ├── Emoji.java              # Emoji representation
│   ├── EmojiResolver.java      # Emoji resolution injected at render time
│   ├── embed/                  # Embed, Author, Field, Footer
│   ├── handler/                # HistoryHandler, PaginationHandler, OutputHandler,
│   │   │                       # FilterHandler, SortHandler, SearchHandler
│   │   └── item/               # ItemHandler, EmbedItemHandler, ComponentItemHandler
│   └── page/                   # Page, TreePage, Paging, Subpages
│       ├── editor/             # EditorPage, InPageEditSession, field/, modal/
│       └── item/               # Item, AuthorItem, TitleItem, DescriptionItem, etc.
│           └── field/          # FieldItem, StringItem, NumberItem, ToggleItem, etc.
└── util/                       # DiscordReference, DiscordDate, DiscordProtocol, ProgressBar
```

### Key extension points

- **New command** - Extend `DiscordCommand<SlashCommandContext>` (or other
  context type) and annotate with `@Structure`.
- **New component** - Implement the relevant `Component` interface and add a
  builder following the existing pattern.
- **New listener** - Extend `DiscordListener<T extends Event>` (Discord4J
  events) or `BotEventListener<T extends BotEvent>` (bot lifecycle events) in
  the `listener/` package. Both are discovered automatically via classpath
  scanning.
- **New page type** - `Response` is a final class; extend the page hierarchy
  (`Page` / `TreePage` / `EditorPage`) rather than the response itself.
- **New shared click handler** - Annotate a method with `@Component(customId)`
  on a `DiscordCommand` or an `EternalComponentListener` subclass.
- **New exception handler** - Extend `ExceptionHandler` and register it via
  `DiscordConfig` or wrap it in a `CompositeExceptionHandler`.

## Legal

By submitting a pull request, you agree that your contributions are licensed
under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0),
the same license that covers this project.
