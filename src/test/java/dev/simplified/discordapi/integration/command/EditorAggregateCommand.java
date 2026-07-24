package dev.simplified.discordapi.integration.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import dev.simplified.discordapi.response.page.editor.EditorPage;
import dev.simplified.discordapi.response.page.editor.field.AggregateField;
import dev.simplified.discordapi.response.page.editor.field.Choice;
import dev.simplified.discordapi.response.page.editor.field.FieldKind;
import dev.simplified.annotations.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * A global {@code /editaggregate} slash command that replies with an {@link EditorPage.Aggregate} over a
 * mutable {@link Model}, carrying one field of every {@link FieldKind}: a text field, a numeric field, a
 * boolean field, a bounded-choice field (&lt;= 25 options -&gt; select-in-modal), a large-choice field
 * (&gt; 25 options -&gt; in-page escalation), and a nullable text field. Each field's live saver mutates the
 * same {@code Model} instance so the test can read the live model back off the loaded command, and the
 * re-render reflects the new value.
 *
 * <p>
 * The option value {@code agg} seeds the deterministic custom-id namespace, so a field's edit button is
 * {@code editor:agg:<field>} and its modal is {@code editor:agg:<field>:modal} / {@code :input} / {@code :bool}
 * / {@code :choice}, and the escalation rows are {@code editor:agg:<field>:{select|prev|next|cancel}}.
 */
@Structure(
    name = "editaggregate",
    description = "Opens an aggregate editor over a mutable model"
)
public class EditorAggregateCommand extends DiscordCommand<SlashCommandContext> {

    /** The editor option value seeding the custom-id namespace. */
    public static final @NotNull String OPTION_VALUE = "agg";

    /** Field identifier for the text field. */
    public static final @NotNull String FIELD_NAME = "name";
    /** Field identifier for the numeric field. */
    public static final @NotNull String FIELD_LEVEL = "level";
    /** Field identifier for the boolean field. */
    public static final @NotNull String FIELD_ACTIVE = "active";
    /** Field identifier for the bounded-choice field. */
    public static final @NotNull String FIELD_COLOR = "color";
    /** Field identifier for the large (escalating) choice field. */
    public static final @NotNull String FIELD_BIG = "big";
    /** Field identifier for the nullable text field. */
    public static final @NotNull String FIELD_NICKNAME = "nickname";

    /** The live, mutable model the editor edits; read it back to verify live saves. */
    @Getter
    private final @NotNull Model model = new Model();

    public EditorAggregateCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        EditorPage.Aggregate.AggregateBuilder<Model> builder = EditorPage.Aggregate.builder(this.model)
            .withHeader("Edit widget")
            .withDetails("Live-save editor")
            .withField(this.nameField())
            .withField(this.levelField())
            .withField(this.activeField())
            .withField(this.colorField())
            .withField(this.bigField())
            .withField(this.nicknameField())
            .withCancel("Close", context -> Mono.empty())
            .withDelete(model -> {
                model.deleted = true;
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

    private AggregateField<Model, String> nameField() {
        return AggregateField.<Model, String>builder()
            .withIdentifier(FIELD_NAME)
            .withLabel("Name")
            .withKind(new FieldKind.Text(TextInput.Style.SHORT, 0, 100, value -> !value.isBlank(), Optional.of("Enter a name")))
            .withGetter(model -> model.name)
            .withLiveSaver((model, edit) -> {
                model.name = edit.newValue();
                return Mono.just(model);
            })
            .build();
    }

    private AggregateField<Model, Integer> levelField() {
        FieldKind.Numeric<Integer> kind = new FieldKind.Numeric<>(
            Integer.class,
            Optional.empty(),
            Optional.empty(),
            input -> {
                try {
                    return Optional.of(Integer.parseInt(input.trim()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            },
            integer -> Integer.toString(integer)
        );

        return AggregateField.<Model, Integer>builder()
            .withIdentifier(FIELD_LEVEL)
            .withLabel("Level")
            .withKind(kind)
            .withGetter(model -> model.level)
            .withLiveSaver((model, edit) -> {
                model.level = edit.newValue();
                return Mono.just(model);
            })
            .build();
    }

    private AggregateField<Model, Boolean> activeField() {
        return AggregateField.<Model, Boolean>builder()
            .withIdentifier(FIELD_ACTIVE)
            .withLabel("Active")
            .withKind(new FieldKind.Bool())
            .withGetter(model -> model.active)
            .withLiveSaver((model, edit) -> {
                model.active = edit.newValue();
                return Mono.just(model);
            })
            .build();
    }

    private AggregateField<Model, String> colorField() {
        ConcurrentList<Choice<String>> choices = Concurrent.newUnmodifiableList(
            Choice.of("Red", "red"),
            Choice.of("Green", "green"),
            Choice.of("Blue", "blue")
        );

        return AggregateField.<Model, String>builder()
            .withIdentifier(FIELD_COLOR)
            .withLabel("Color")
            .withKind(new FieldKind.Choice<>(choices, false))
            .withGetter(model -> model.color)
            .withLiveSaver((model, edit) -> {
                model.color = edit.newValue();
                return Mono.just(model);
            })
            .build();
    }

    private AggregateField<Model, String> bigField() {
        ConcurrentList<Choice<String>> choices = Concurrent.newList();
        for (int index = 0; index < 30; index++)
            choices.add(Choice.of("Option " + index, "opt" + index));

        return AggregateField.<Model, String>builder()
            .withIdentifier(FIELD_BIG)
            .withLabel("Big")
            .withKind(new FieldKind.Choice<>(choices.toUnmodifiable(), false))
            .withGetter(model -> model.big)
            .withLiveSaver((model, edit) -> {
                model.big = edit.newValue();
                return Mono.just(model);
            })
            .build();
    }

    private AggregateField<Model, String> nicknameField() {
        return AggregateField.<Model, String>builder()
            .withIdentifier(FIELD_NICKNAME)
            .withLabel("Nickname")
            .withKind(new FieldKind.Text(TextInput.Style.SHORT, 0, 100, value -> true, Optional.empty()))
            .withGetter(model -> model.nickname)
            .withLiveSaver((model, edit) -> {
                model.nickname = edit.newValue();
                return Mono.just(model);
            })
            .build();
    }

    /** A mutable domain object the aggregate editor live-saves into. */
    public static final class Model {

        /** The text field value. */
        public String name = "Neo";
        /** The numeric field value. */
        public int level = 5;
        /** The boolean field value. */
        public boolean active = false;
        /** The bounded-choice field value. */
        public String color = "red";
        /** The large-choice field value. */
        public String big = "opt0";
        /** The nullable text field value, seeded {@code null} to exercise the null-field edit path. */
        public String nickname = null;
        /** Whether the delete handler ran. */
        public boolean deleted = false;

    }

}
