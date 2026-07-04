package dev.simplified.discordapi.response.page.editor.modal;

import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.RadioGroup;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.component.scope.LabelComponent;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.discordapi.response.page.editor.field.Choice;
import dev.simplified.discordapi.response.page.editor.field.EditableField;
import dev.simplified.discordapi.response.page.editor.field.FieldKind;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the {@link Modal} presented when the user clicks Edit on an {@link EditableField}.
 *
 * <p>
 * For {@link FieldKind.Choice} fields with more than 25 options, this factory returns an
 * empty optional - the caller must escalate to an in-page select menu flow because Discord
 * modals cannot paginate options.
 */
public final class FieldModalFactory {

    private FieldModalFactory() {
        throw new UnsupportedOperationException("FieldModalFactory is a static utility");
    }

    /**
     * Produces the modal for editing the given field if one is supported.
     *
     * <p>
     * Every modal and its inner component receive a deterministic custom id derived from
     * {@code customIdPrefix} - the modal itself is {@code <prefix>:modal}, a text/numeric
     * input is {@code <prefix>:input}, a boolean radio group is {@code <prefix>:bool}, and a
     * choice select menu is {@code <prefix>:choice} - so submitted values can be routed back
     * to the field without relying on random identifiers.
     *
     * @param field the field being edited
     * @param currentValue the current field value rendered as pre-fill
     * @param customIdPrefix the custom-id prefix shared by the modal and its inner component
     * @param onSubmit the processor run when the modal is submitted, bound to the field's component
     * @param <T> the domain or seed type
     * @param <V> the field value type
     * @return the modal wrapped in an optional, or empty for Choice fields exceeding 25 options
     */
    public static <T, V> @NotNull Optional<Modal> forField(@NotNull EditableField<T, V> field, @NotNull Optional<V> currentValue, @NotNull String customIdPrefix, @NotNull Function<ModalContext, Mono<Void>> onSubmit) {
        FieldKind<V> kind = field.kind();

        return switch (kind) {
            case FieldKind.Text text -> Optional.of(buildTextModal(field, text, currentValue, customIdPrefix, onSubmit));
            case FieldKind.Numeric<?> numeric -> Optional.of(buildNumericModal(field, numeric, currentValue, customIdPrefix, onSubmit));
            case FieldKind.Bool bool -> Optional.of(buildBoolModal(field, currentValue, customIdPrefix, onSubmit));
            case FieldKind.Choice<?> choice -> buildChoiceModal(field, choice, currentValue, customIdPrefix, onSubmit);
        };
    }

    private static <T, V> @NotNull Modal buildTextModal(@NotNull EditableField<T, V> field, @NotNull FieldKind.Text text, @NotNull Optional<V> currentValue, @NotNull String customIdPrefix, @NotNull Function<ModalContext, Mono<Void>> onSubmit) {
        TextInput.Builder textBuilder = TextInput.builder()
            .withIdentifier(customIdPrefix + ":input")
            .withStyle(text.style())
            .withMinLength(text.minLength())
            .withMaxLength(text.maxLength())
            .withPlaceholder(text.placeholder())
            .withValidator(text.validator())
            .isRequired(field.required());

        currentValue.map(v -> (String) v).ifPresent(textBuilder::withValue);

        return buildModal(field, customIdPrefix, textBuilder.build(), onSubmit);
    }

    private static <T, V, N extends Number & Comparable<N>> @NotNull Modal buildNumericModal(@NotNull EditableField<T, V> field, @NotNull FieldKind.Numeric<N> numeric, @NotNull Optional<V> currentValue, @NotNull String customIdPrefix, @NotNull Function<ModalContext, Mono<Void>> onSubmit) {
        TextInput.Builder textBuilder = TextInput.builder()
            .withIdentifier(customIdPrefix + ":input")
            .withStyle(TextInput.Style.SHORT)
            .isRequired(field.required())
            .withValidator(input -> numeric.parser().apply(input).isPresent());

        // Narrowing cast - the field kind guarantees value type matches N
        @SuppressWarnings("unchecked")
        Optional<N> typedValue = (Optional<N>) currentValue;
        typedValue.map(numeric.formatter()).ifPresent(textBuilder::withValue);

        return buildModal(field, customIdPrefix, textBuilder.build(), onSubmit);
    }

    private static <T, V> @NotNull Modal buildBoolModal(@NotNull EditableField<T, V> field, @NotNull Optional<V> currentValue, @NotNull String customIdPrefix, @NotNull Function<ModalContext, Mono<Void>> onSubmit) {
        RadioGroup.Builder radio = RadioGroup.builder()
            .withIdentifier(customIdPrefix + ":bool")
            .withOptions(
                RadioGroup.Option.builder().withLabel("Yes").withValue("true").build(),
                RadioGroup.Option.builder().withLabel("No").withValue("false").build()
            );

        RadioGroup group = radio.build();
        currentValue.map(v -> ((Boolean) v) ? "true" : "false").ifPresent(group::updateSelected);

        return buildModal(field, customIdPrefix, group, onSubmit);
    }

    private static <T, V, C> @NotNull Optional<Modal> buildChoiceModal(@NotNull EditableField<T, V> field, @NotNull FieldKind.Choice<C> choice, @NotNull Optional<V> currentValue, @NotNull String customIdPrefix, @NotNull Function<ModalContext, Mono<Void>> onSubmit) {
        if (choice.choices().size() > SelectMenu.Option.MAX_ALLOWED)
            return Optional.empty();

        SelectMenu.StringMenu.Builder select = SelectMenu.builder()
            .withIdentifier(customIdPrefix + ":choice")
            .withPlaceholder("Pick a value");

        for (Choice<C> entry : choice.choices()) {
            SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder()
                .withLabel(entry.label())
                .withValue(entry.label());

            entry.description().ifPresent(optionBuilder::withDescription);
            entry.emoji().ifPresent(optionBuilder::withEmoji);
            select = select.withOptions(optionBuilder.build());
        }

        return Optional.of(buildModal(field, customIdPrefix, select.build(), onSubmit));
    }

    private static <T, V> @NotNull Modal buildModal(@NotNull EditableField<T, V> field, @NotNull String customIdPrefix, @NotNull LabelComponent inner, @NotNull Function<ModalContext, Mono<Void>> onSubmit) {
        return Modal.builder()
            .withIdentifier(customIdPrefix + ":modal")
            .withTitle(field.label())
            .withComponents(Label.builder().withTitle(field.label()).withComponent(inner).build())
            .onSubmit(onSubmit)
            .build();
    }

}
