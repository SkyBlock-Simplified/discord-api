package dev.simplified.discordapi.handler;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.handler.locale.LocaleEntry;
import dev.simplified.discordapi.util.DiscordReference;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.info.ResourceInfo;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central registry for Discord application command localizations. Loads
 * {@code resources/locale/<shortName>/commands.json} from the classpath on
 * construction and exposes lookup methods that {@link CommandHandler}
 * consumes to populate {@code name_localizations} and
 * {@code description_localizations} on every outgoing
 * {@code ApplicationCommandRequest}, {@code ApplicationCommandOptionData},
 * and {@code ApplicationCommandOptionChoiceData}.
 *
 * <p>
 * Commands may also supply programmatic overrides via
 * {@link DiscordCommand#getLocaleOverrides()}; programmatic entries take
 * precedence over classpath JSON on key collision.
 *
 * @see LocaleEntry
 * @see DiscordLocale
 * @see <a href="https://discord.com/developers/docs/interactions/application-commands#localization">Application Commands - Localization</a>
 */
@Log
public final class LocaleHandler extends DiscordReference {

    /** Classpath prefix under which locale files are discovered. */
    private static final String RESOURCE_PREFIX = "locale/";

    /** Expected filename under each {@code locale/<shortName>/} directory. */
    private static final String FILE_NAME = "commands.json";

    /**
     * Outer key is {@code Target.name() + "|" + path.toLowerCase()}, inner
     * map maps each configured locale to its translated value.
     */
    private final @NotNull ConcurrentMap<String, ConcurrentMap<DiscordLocale, String>> overrides = Concurrent.newMap();

    /**
     * Constructs a new {@code LocaleHandler} and eagerly loads all
     * {@code locale/<shortName>/commands.json} files from the classpath.
     *
     * @param discordBot the bot this handler belongs to
     */
    public LocaleHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.loadFromClasspath();
    }

    /**
     * Merges programmatic overrides returned by each loaded command's
     * {@link DiscordCommand#getLocaleOverrides()} into the in-memory index.
     * Programmatic entries replace any classpath entry that maps to the
     * same {@code (target, path, locale)} triple.
     *
     * @param commands the validated commands to scan for overrides
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void applyProgrammaticOverrides(@NotNull Iterable<DiscordCommand> commands) {
        int merged = 0;

        for (DiscordCommand command : commands) {
            ConcurrentList<LocaleEntry> entries = (ConcurrentList<LocaleEntry>) command.getLocaleOverrides();

            for (LocaleEntry entry : entries) {
                this.put(entry);
                merged++;
            }
        }

        if (merged > 0)
            log.info("Merged {} programmatic locale entries", merged);
    }

    /**
     * Returns the {@code name_localizations} map for the given command path.
     *
     * @param commandPath the lowercase command path (e.g. {@code "parent.group.name"})
     * @return a map of BCP 47 tag to translated name, empty if unmapped
     */
    public @NotNull Map<String, String> getCommandNameLocalizations(@NotNull String commandPath) {
        return this.lookup(LocaleEntry.Target.COMMAND_NAME, commandPath);
    }

    /**
     * Returns the {@code description_localizations} map for the given command path.
     *
     * @param commandPath the lowercase command path
     * @return a map of BCP 47 tag to translated description, empty if unmapped
     */
    public @NotNull Map<String, String> getCommandDescriptionLocalizations(@NotNull String commandPath) {
        return this.lookup(LocaleEntry.Target.COMMAND_DESCRIPTION, commandPath);
    }

    /**
     * Returns the {@code name_localizations} map for the given option on the given command.
     *
     * @param commandPath the lowercase command path
     * @param optionName the option name as declared in the command
     * @return a map of BCP 47 tag to translated name, empty if unmapped
     */
    public @NotNull Map<String, String> getOptionNameLocalizations(@NotNull String commandPath, @NotNull String optionName) {
        return this.lookup(LocaleEntry.Target.OPTION_NAME, commandPath + "#" + optionName.toLowerCase());
    }

    /**
     * Returns the {@code description_localizations} map for the given option on the given command.
     *
     * @param commandPath the lowercase command path
     * @param optionName the option name as declared in the command
     * @return a map of BCP 47 tag to translated description, empty if unmapped
     */
    public @NotNull Map<String, String> getOptionDescriptionLocalizations(@NotNull String commandPath, @NotNull String optionName) {
        return this.lookup(LocaleEntry.Target.OPTION_DESCRIPTION, commandPath + "#" + optionName.toLowerCase());
    }

    /**
     * Returns the {@code name_localizations} map for the given choice on an option.
     *
     * @param commandPath the lowercase command path
     * @param optionName the option name on which the choice is declared
     * @param choiceName the choice name as declared on the option
     * @return a map of BCP 47 tag to translated choice name, empty if unmapped
     */
    public @NotNull Map<String, String> getChoiceNameLocalizations(@NotNull String commandPath, @NotNull String optionName, @NotNull String choiceName) {
        return this.lookup(LocaleEntry.Target.CHOICE_NAME, commandPath + "#" + optionName.toLowerCase() + ":" + choiceName);
    }

    private @NotNull Map<String, String> lookup(@NotNull LocaleEntry.Target target, @NotNull String path) {
        ConcurrentMap<DiscordLocale, String> byLocale = this.overrides.get(key(target, path));

        if (byLocale == null || byLocale.isEmpty())
            return Map.of();

        return byLocale.entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(e -> e.getKey().getShortName(), Map.Entry::getValue));
    }

    private void put(@NotNull LocaleEntry entry) {
        this.overrides
            .computeIfAbsent(key(entry.getTarget(), entry.getPath()), k -> Concurrent.newMap())
            .put(entry.getLocale(), entry.getValue());
    }

    private static @NotNull String key(@NotNull LocaleEntry.Target target, @NotNull String path) {
        return target.name() + "|" + path.toLowerCase();
    }

    private void loadFromClasspath() {
        Gson gson = new Gson();
        ConcurrentList<ResourceInfo> files = Reflection.getResources()
            .getResources(RESOURCE_PREFIX)
            .stream()
            .filter(r -> r.getResourceName().endsWith("/" + FILE_NAME))
            .collect(Concurrent.toList());

        for (ResourceInfo file : files)
            this.loadFile(file, gson);
    }

    private void loadFile(@NotNull ResourceInfo resource, @NotNull Gson gson) {
        String directoryShortName = directoryShortNameOf(resource.getResourceName());
        Optional<DiscordLocale> locale = DiscordLocale.byShortName(directoryShortName);

        if (locale.isEmpty()) {
            log.warn("Skipping '{}': directory '{}' does not match any DiscordLocale shortName", resource.getResourceName(), directoryShortName);
            return;
        }

        LocaleFile parsed;
        try (InputStream stream = resource.toStream()) {
            if (stream == null) {
                log.warn("Skipping '{}': unable to open resource stream", resource.getResourceName());
                return;
            }

            parsed = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), LocaleFile.class);
        } catch (JsonSyntaxException | java.io.IOException ex) {
            log.warn("Skipping '{}': {}", resource.getResourceName(), ex.getMessage());
            return;
        }

        if (parsed == null || parsed.entries == null) {
            log.warn("Skipping '{}': file has no entries", resource.getResourceName());
            return;
        }

        if (parsed.locale != null && !parsed.locale.equalsIgnoreCase(directoryShortName))
            log.warn("Locale mismatch in '{}': declared '{}', directory '{}' - using directory", resource.getResourceName(), parsed.locale, directoryShortName);

        int loaded = 0;
        for (RawEntry raw : parsed.entries) {
            if (raw == null || raw.target == null || raw.path == null || raw.value == null || raw.path.isBlank() || raw.value.isBlank()) {
                log.warn("Skipping malformed entry in '{}'", resource.getResourceName());
                continue;
            }

            LocaleEntry.Target target;
            try {
                target = LocaleEntry.Target.valueOf(raw.target);
            } catch (IllegalArgumentException ex) {
                log.warn("Skipping entry in '{}': unknown target '{}'", resource.getResourceName(), raw.target);
                continue;
            }

            this.put(LocaleEntry.builder()
                .withLocale(locale.get())
                .withTarget(target)
                .withPath(raw.path.toLowerCase())
                .withValue(raw.value)
                .build());
            loaded++;
        }

        log.info("Loaded {} locale entries for {}", loaded, locale.get().getShortName());
    }

    private static @NotNull String directoryShortNameOf(@NotNull String resourceName) {
        int end = resourceName.lastIndexOf('/' + FILE_NAME);
        if (end < 0) return "";
        int start = resourceName.lastIndexOf('/', end - 1);
        return resourceName.substring(start + 1, end);
    }

    /** Raw deserialization target for {@code commands.json}. */
    private static final class LocaleFile {
        private String locale;
        private List<RawEntry> entries;
    }

    /** Raw deserialization target for individual entries. */
    private static final class RawEntry {
        private String target;
        private String path;
        private String value;
    }

}
