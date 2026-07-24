package dev.simplified.discordapi.integration.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.editor.EditorPage;
import dev.simplified.discordapi.response.page.editor.field.BuilderField;
import dev.simplified.discordapi.response.page.editor.field.Choice;
import dev.simplified.discordapi.response.page.editor.field.FieldKind;
import dev.simplified.annotations.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * A global {@code /editbuilder} slash command that replies with an {@link EditorPage.Builder builder-mode}
 * editor over a mutable {@link Model} seed. Field edits accumulate in the seed via each field's applier
 * (rather than saving live), and the page requires a confirmation before {@code onSubmit} materializes the
 * seed. On submit the seed is captured so the test can read it back off the loaded command.
 *
 * <p>
 * The option value {@code bld} seeds the custom-id namespace: a field's edit button is {@code editor:bld:<field>}
 * and the action buttons are {@code editor:bld:submit} / {@code editor:bld:confirm-submit} /
 * {@code editor:bld:cancel-submit}.
 */
@Structure(
    name = "editbuilder",
    description = "Opens a builder-mode editor that submits on confirmation"
)
public class EditorBuilderCommand extends DiscordCommand<SlashCommandContext> {

    /** The editor option value seeding the custom-id namespace. */
    public static final @NotNull String OPTION_VALUE = "bld";

    /** Field identifier for the text field. */
    public static final @NotNull String FIELD_NAME = "name";
    /** Field identifier for the bounded-choice field. */
    public static final @NotNull String FIELD_SIZE = "size";

    private final @NotNull Model seed = new Model();

    /** The seed captured when the editor submitted, or {@code null} if it has not submitted yet. */
    @Getter
    private @Nullable Model submitted = null;

    public EditorBuilderCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        EditorPage.Builder.EditorBuilder<Model> builder = EditorPage.Builder.builder(this.seed)
            .withHeader("Build widget")
            .withField(this.nameField())
            .withField(this.sizeField())
            .confirmSubmit()
            .onSubmit(seed -> {
                this.submitted = seed;
                return Mono.empty();
            });
        builder.withLabel("Widget");
        builder.withValue(OPTION_VALUE);

        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(120)
                .withPages(builder.build())
                .build()
        );
    }

    private BuilderField<Model, String> nameField() {
        return BuilderField.<Model, String>builder()
            .withIdentifier(FIELD_NAME)
            .withLabel("Name")
            .withKind(new FieldKind.Text(TextInput.Style.SHORT, 0, 100, value -> !value.isBlank(), Optional.empty()))
            .withGetter(model -> model.name)
            .withApplier((model, value) -> {
                model.name = value;
                return model;
            })
            .build();
    }

    private BuilderField<Model, String> sizeField() {
        ConcurrentList<Choice<String>> choices = Concurrent.newUnmodifiableList(
            Choice.of("Small", "small"),
            Choice.of("Large", "large")
        );

        return BuilderField.<Model, String>builder()
            .withIdentifier(FIELD_SIZE)
            .withLabel("Size")
            .withKind(new FieldKind.Choice<>(choices, false))
            .withGetter(model -> model.size)
            .withApplier((model, value) -> {
                model.size = value;
                return model;
            })
            .build();
    }

    /** A mutable builder seed the editor accumulates field edits into before submit. */
    public static final class Model {

        /** The text field value. */
        public String name = "unset";
        /** The bounded-choice field value. */
        public String size = "small";

    }

}
