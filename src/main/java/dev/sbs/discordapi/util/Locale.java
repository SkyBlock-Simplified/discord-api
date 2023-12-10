package dev.sbs.discordapi.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public enum Locale {

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

    private final @NotNull String shortName;
    private final @NotNull String nativeName;

}
