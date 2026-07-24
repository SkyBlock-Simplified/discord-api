package dev.simplified.discordapi.handler;

import dev.simplified.util.StringUtil;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Enumeration of all locales supported by the Discord API for
 * internationalization of command names, descriptions, option names,
 * option descriptions, and choice names.
 *
 * <p>
 * Each constant maps a BCP 47 language tag ({@link #getShortName()})
 * to its native display name ({@link #getNativeName()}). The short
 * name is the value Discord expects as a key in the
 * {@code name_localizations} and {@code description_localizations}
 * maps on application command payloads.
 *
 * @see <a href="https://discord.com/developers/docs/reference#locales">Discord Reference - Locales</a>
 * @see <a href="https://discord.com/developers/docs/interactions/application-commands#localization">Application Commands - Localization</a>
 */
@Getter
@RequiredArgsConstructor
public enum DiscordLocale {

    INDONESIAN("id", "Bahasa Indonesia"),
    DANISH("da", "Dansk"),
    GERMAN("de", "Deutsch"),
    ENGLISH("en-US", "English"),
    SPANISH("es-ES", "Español"),
    FRENCH("fr", "Français"),
    CROATIAN("hr", "Hrvatski"),
    ITALIAN("it", "Italiano"),
    LITHUANIAN("lt", "Lietuviškai"),
    HUNGARIAN("hu", "Magyar"),
    DUTCH("nl", "Nederlands"),
    NORWEGIAN("no", "Norsk"),
    POLISH("pl", "Polski"),
    PORTUGUESE("pt-BR", "Português do Brasil"),
    ROMANIAN("ro", "Română"),
    FINNISH("fi", "Suomi"),
    SWEDISH("sv-SE", "Svenska"),
    VIETNAMESE("vi", "Tiếng Việt"),
    TURKISH("tr", "Türkçe"),
    CZECH("cs", "Čeština"),
    GREEK("el", "Ελληνικά"),
    BULGARIAN("bg", "български"),
    RUSSIAN("ru", "Pусский"),
    UKRANIAN("uk", "Українська"),
    HINDI("hi", "हिन्दी"),
    THAI("th", "ไทย"),
    CHINESE("zh-CN", "中文"),
    JAPANESE("ja", "日本語"),
    TAIWANESE("zh-TW", "繁體中文"),
    KOREAN("ko", "한국어");

    /** BCP 47 language tag recognized by the Discord API. */
    private final @NotNull String shortName;

    /** Display name of the locale written in its own language. */
    private final @NotNull String nativeName;

    /**
     * Returns the fully capitalized English language name derived from
     * this constant's enum name.
     *
     * @return the English language name with proper capitalization
     */
    public @NotNull String getLanguageName() {
        return StringUtil.capitalizeFully(this.name().replace("_", " "));
    }

    /**
     * Resolves a {@link DiscordLocale} from its BCP 47 short name, matched
     * case-insensitively.
     *
     * @param shortName the BCP 47 language tag to resolve
     * @return the matching locale, or empty if no constant matches
     */
    public static @NotNull Optional<DiscordLocale> byShortName(@NotNull String shortName) {
        for (DiscordLocale locale : values()) {
            if (locale.shortName.equalsIgnoreCase(shortName))
                return Optional.of(locale);
        }
        return Optional.empty();
    }

}
