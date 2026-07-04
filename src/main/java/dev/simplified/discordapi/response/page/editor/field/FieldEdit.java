package dev.simplified.discordapi.response.page.editor.field;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A value transition recorded when a field's edit is applied.
 *
 * <p>
 * Passed to a live-save handler along with the current domain value, so the handler
 * may distinguish which field changed and what the previous value was.
 *
 * @param fieldId the identifier of the edited {@link EditableField}
 * @param oldValue the prior value before the edit, or {@code null} when the field had no value
 * @param newValue the value submitted by the user
 * @param <V> the field value type
 */
public record FieldEdit<V>(
    @NotNull String fieldId,
    @Nullable V oldValue,
    @NotNull V newValue
) { }
