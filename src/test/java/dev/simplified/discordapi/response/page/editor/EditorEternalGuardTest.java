package dev.simplified.discordapi.response.page.editor;

import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.editor.field.AggregateField;
import dev.simplified.discordapi.response.page.editor.field.FieldKind;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the build-time guard promised by {@link EditorPage}'s javadoc: an eternal (reboot-surviving)
 * {@link Response} may not contain an {@code EditorPage}, because the editor's per-field save handlers cannot
 * be rebuilt from the persisted coordinate. A non-eternal response containing the same editor builds fine.
 */
class EditorEternalGuardTest {

    private record Widget(String name) { }

    private static EditorPage.Aggregate<Widget> editorPage() {
        AggregateField<Widget, String> field = AggregateField.<Widget, String>builder()
            .withIdentifier("name")
            .withLabel("Name")
            .withKind(new FieldKind.Text(TextInput.Style.SHORT, 0, 32, value -> true, Optional.empty()))
            .withGetter(Widget::name)
            .withLiveSaver((widget, edit) -> Mono.just(new Widget(edit.newValue())))
            .build();

        EditorPage.Aggregate.AggregateBuilder<Widget> builder = EditorPage.Aggregate.builder(new Widget("initial"))
            .withHeader("Widget")
            .withField(field);
        builder.withLabel("Widget");
        builder.withValue("widget");
        return builder.build();
    }

    @Test
    void eternal_response_with_editor_page_is_rejected() {
        assertThrows(
            DiscordException.class,
            () -> Response.builder().asEternal("widget-key", "").withPages(editorPage()).build(),
            "an eternal response containing an EditorPage must be rejected at build time"
        );
    }

    @Test
    void non_eternal_response_with_editor_page_builds() {
        assertDoesNotThrow(
            () -> Response.builder().withPages(editorPage()).build(),
            "a plain response may contain an EditorPage"
        );
    }

}
