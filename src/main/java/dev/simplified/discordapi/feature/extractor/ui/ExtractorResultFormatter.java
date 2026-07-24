package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Renders an executed pipeline's runtime result into a multi-line string sized to fit inside
 * a Discord {@code Section} or message body. Output is shape-aware: scalars render inline,
 * collections render as bulleted lists, and {@link Map} branches render as
 * {@code key: value} pairs.
 * <p>
 * Excessive output is truncated at {@link #DEFAULT_LIMIT} characters and a {@code ...} marker
 * is appended so callers can show the full result via a followup if needed.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExtractorResultFormatter {

    /** Default character cap for a single rendered result. */
    public static final int DEFAULT_LIMIT = 1500;

    /** Per-item cap when rendering list elements, so a single oversized entry cannot dominate. */
    public static final int PER_ITEM_LIMIT = 200;

    /** Maximum number of list/map entries rendered before further entries are summarised. */
    public static final int MAX_ENTRIES = 25;

    /**
     * Renders {@code value} using {@link #DEFAULT_LIMIT}.
     *
     * @param value the runtime result, possibly {@code null}
     * @return the formatted string
     */
    public static @NotNull String format(@Nullable Object value) {
        return format(value, DEFAULT_LIMIT);
    }

    /**
     * Renders {@code value} truncated to at most {@code limit} characters.
     *
     * @param value the runtime result, possibly {@code null}
     * @param limit the soft character cap
     * @return the formatted string
     */
    public static @NotNull String format(@Nullable Object value, int limit) {
        StringBuilder out = new StringBuilder();
        appendValue(out, value, 0);
        return truncate(out.toString(), limit);
    }

    private static void appendValue(@NotNull StringBuilder out, @Nullable Object value, int depth) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMap(out, map, depth);
            return;
        }
        if (value instanceof Collection<?> col) {
            appendCollection(out, col, depth);
            return;
        }
        if (value.getClass().isArray()) {
            // Boxed object array; primitive arrays handled as toString()
            if (value instanceof Object[] arr) {
                appendCollection(out, java.util.Arrays.asList(arr), depth);
                return;
            }
        }
        out.append(truncate(String.valueOf(value), PER_ITEM_LIMIT));
    }

    private static void appendMap(@NotNull StringBuilder out, @NotNull Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        int seen = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (seen > 0) out.append('\n');
            if (seen >= MAX_ENTRIES) {
                out.append("... (").append(map.size() - seen).append(" more)");
                break;
            }
            out.append("**").append(entry.getKey()).append("**: ");
            appendValue(out, entry.getValue(), depth + 1);
            seen++;
        }
    }

    private static void appendCollection(@NotNull StringBuilder out, @NotNull Collection<?> col, int depth) {
        if (col.isEmpty()) {
            out.append("[]");
            return;
        }
        int seen = 0;
        for (Object item : col) {
            if (seen > 0) out.append('\n');
            if (seen >= MAX_ENTRIES) {
                out.append("... (").append(col.size() - seen).append(" more)");
                break;
            }
            out.append("- ");
            appendValue(out, item, depth + 1);
            seen++;
        }
    }

    private static @NotNull String truncate(@NotNull String s, int limit) {
        if (limit <= 3) return s.length() <= limit ? s : s.substring(0, Math.max(0, limit));
        return s.length() <= limit ? s : s.substring(0, limit - 3) + "...";
    }

}
