package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.PipelineContext;
import dev.simplified.dataflow.stage.Stage;
import dev.simplified.dataflow.stage.StageRegistry;
import dev.simplified.dataflow.stage.meta.StageReflection;
import dev.simplified.dataflow.stage.meta.StageSpec;
import dev.simplified.dataflow.stage.source.EmbedSource;
import dev.simplified.dataflow.stage.terminal.collect.MapCollect;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.discordapi.context.component.OptionContext;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.discordapi.feature.extractor.ExtractorResolver;
import dev.simplified.discordapi.feature.extractor.ExtractorStore;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Live builder Response composer: takes a {@link PipelineBuilderSession} plus the bot/store
 * needed for embedded-pipeline resolution, and returns a {@link Response} whose buttons,
 * select menus, and modals close over the session.
 * <p>
 * Page modes:
 * <ul>
 *     <li><b>Main</b> - title, stages with Edit accessories, preview, footer with
 *     {@code Add Stage}, {@code Save as...}, {@code Reset}, {@code Close}.</li>
 *     <li><b>Category picker</b> - main layout but the footer is replaced with a
 *     {@link StageAddSelectMenu#categories()} menu plus a Cancel button.</li>
 *     <li><b>Kind picker</b> - main layout with {@link StageAddSelectMenu#kindsIn} for the
 *     chosen category plus a Cancel button.</li>
 *     <li><b>Embed picker</b> - main layout with {@link EmbedPipelinePicker} listing
 *     visible saved extractors plus a Cancel button.</li>
 * </ul>
 * Modals (stage config, save) are presented via {@link ButtonContext#presentModal}; on
 * submit each modal's interaction handler mutates the session and re-renders the main page.
 */
public final class PipelineBuilderResponse {

    /** Time-to-live for the live builder Response, in seconds. Discord caps at 300. */
    public static final int TTL_SECONDS = 300;

    /** Component identifier for the Cancel button shown on every picker page. */
    public static final @NotNull String ID_CANCEL_PICKER = "extractor.builder.picker.cancel";

    private final @NotNull PipelineBuilderSession session;
    private final @NotNull DiscordBot bot;
    private final long callerUserId;
    private final @Nullable Long callerGuildId;

    private PipelineBuilderResponse(
        @NotNull PipelineBuilderSession session,
        @NotNull DiscordBot bot,
        long callerUserId,
        @Nullable Long callerGuildId
    ) {
        this.session = session;
        this.bot = bot;
        this.callerUserId = callerUserId;
        this.callerGuildId = callerGuildId;
    }

    /**
     * Builds a Response for a fresh, unsaved pipeline.
     *
     * @param bot the bot instance
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return a live, ephemeral Response
     */
    public static @NotNull Response startNew(@NotNull DiscordBot bot, long callerUserId, @Nullable Long callerGuildId) {
        return new PipelineBuilderResponse(PipelineBuilderSession.startNew(), bot, callerUserId, callerGuildId).build();
    }

    /**
     * Builds a Response that resumes editing the given saved {@link Extractor}.
     *
     * @param bot the bot instance
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @param row the extractor to resume editing
     * @return a live, ephemeral Response
     */
    public static @NotNull Response forEditing(@NotNull DiscordBot bot, long callerUserId, @Nullable Long callerGuildId, @NotNull Extractor row) {
        return new PipelineBuilderResponse(
            PipelineBuilderSession.resume(PipelineBuilderState.forEditing(row)),
            bot, callerUserId, callerGuildId
        ).build();
    }

    private @NotNull Response build() {
        return Response.builder()
            .withTimeToLive(TTL_SECONDS)
            .isEphemeral()
            .withPages(this.renderMainPage())
            .build();
    }

    /* ============================  page builders  ============================ */

    private @NotNull Page renderMainPage() {
        return PipelineBuilderPage.of(this.session.state(), new PipelineBuilderPage.Handlers(
            this::onAddStageClicked,
            this::onEditStageClicked,
            this::onRunClicked,
            this::onSaveClicked,
            this::onResetClicked,
            this::onCloseClicked
        ));
    }

    private @NotNull Page renderCategoryPickerPage() {
        SelectMenu.StringMenu base = StageAddSelectMenu.categories();
        SelectMenu.StringMenu menu = SelectMenu.StringMenu.from(base)
            .withOptions(base.getOptions().stream()
                .map(opt -> SelectMenu.Option.from(opt).onInteract(this::onCategorySelected).build())
                .toList())
            .build();
        return PipelineBuilderPage.withCustomFooter(this.session.state(), null, this::onRunClicked,
            ActionRow.of(menu),
            cancelRow());
    }

    private @NotNull Page renderKindPickerPage(@NotNull StageSpec.Category category) {
        SelectMenu.StringMenu base = StageAddSelectMenu.kindsIn(category);
        SelectMenu.StringMenu menu = SelectMenu.StringMenu.from(base)
            .withOptions(base.getOptions().stream()
                .map(opt -> SelectMenu.Option.from(opt).onInteract(this::onKindSelected).build())
                .toList())
            .build();
        return PipelineBuilderPage.withCustomFooter(this.session.state(), null, this::onRunClicked,
            ActionRow.of(menu),
            cancelRow());
    }

    private @NotNull Page renderEmbedPickerPage(@NotNull SelectMenu.StringMenu pickerWithHandlers) {
        return PipelineBuilderPage.withCustomFooter(this.session.state(), null, this::onRunClicked,
            ActionRow.of(pickerWithHandlers),
            cancelRow());
    }

    private @NotNull ActionRow cancelRow() {
        return ActionRow.of(Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withLabel("Cancel")
            .withIdentifier(ID_CANCEL_PICKER)
            .onInteract(this::onCancelPicker)
            .build());
    }

    /* ============================  main-page handlers  ============================ */

    private @NotNull Mono<Void> onRunClicked(@NotNull ButtonContext ctx) {
        this.session.runPipeline(buildPipelineContext());
        return rerender(ctx);
    }

    private @NotNull Mono<Void> onResetClicked(@NotNull ButtonContext ctx) {
        this.session.reset();
        return rerender(ctx);
    }

    private @NotNull Mono<Void> onCloseClicked(@NotNull ButtonContext ctx) {
        return ctx.edit(response -> response.mutate().disableAllComponents().build());
    }

    private @NotNull Mono<Void> onAddStageClicked(@NotNull ButtonContext ctx) {
        return ctx.edit(response -> response.mutate().withPages(renderCategoryPickerPage()).build());
    }

    private @NotNull Mono<Void> onCancelPicker(@NotNull ButtonContext ctx) {
        return rerender(ctx);
    }

    /* ============================  add-stage cascade  ============================ */

    private @NotNull Mono<Void> onCategorySelected(@NotNull OptionContext ctx) {
        StageSpec.Category category = StageSpec.Category.valueOf(ctx.getOption().getValue());
        return ctx.edit(response -> response.mutate().withPages(renderKindPickerPage(category)).build());
    }

    private @NotNull Mono<Void> onKindSelected(@NotNull OptionContext ctx) {
        Class<? extends Stage<?, ?>> stageClass = StageRegistry.byId(ctx.getOption().getValue());
        if (stageClass.equals(EmbedSource.class)) return presentEmbedPicker(ctx);
        if (!requiresModal(stageClass)) {
            this.session.appendStage(stageClass, java.util.Map.of());
            return rerender(ctx);
        }
        return ctx.presentModal(StageEditModal.forAdd(stageClass).mutate()
            .onSubmit(modalCtx -> onAddModalSubmitted(modalCtx, stageClass))
            .build());
    }

    private @NotNull Mono<Void> presentEmbedPicker(@NotNull OptionContext ctx) {
        return EmbedPipelinePicker.of(this.bot.getExtractorStore(), this.callerUserId, this.callerGuildId)
            .map(menu -> SelectMenu.StringMenu.from(menu)
                .withOptions(menu.getOptions().stream()
                    .map(opt -> SelectMenu.Option.from(opt).onInteract(this::onEmbedSelected).build())
                    .toList())
                .build())
            .flatMap(menu -> ctx.edit(response -> response.mutate().withPages(renderEmbedPickerPage(menu)).build()))
            .switchIfEmpty(Mono.defer(() -> {
                this.session.banner("No saved extractors visible to embed");
                return rerender(ctx);
            }));
    }

    private @NotNull Mono<Void> onEmbedSelected(@NotNull OptionContext ctx) {
        UUID id = UUID.fromString(ctx.getOption().getValue());
        return this.bot.getExtractorStore().findById(id, this.callerUserId, this.callerGuildId)
            .doOnNext(this.session::appendEmbedStage)
            .then(rerender(ctx));
    }

    private @NotNull Mono<Void> onAddModalSubmitted(@NotNull ModalContext ctx, @NotNull Class<? extends Stage<?, ?>> stageClass) {
        this.session.appendStage(stageClass, StageConfigParser.readValues(ctx.getComponent()));
        return rerender(ctx);
    }

    /* ============================  edit-stage flow  ============================ */

    private @NotNull Mono<Void> onEditStageClicked(int stageIndex, @NotNull ButtonContext ctx) {
        var stages = this.session.state().pipeline().stages();
        if (stageIndex < 0 || stageIndex >= stages.size()) {
            this.session.banner("No such stage #" + stageIndex);
            return rerender(ctx);
        }
        Stage<?, ?> stage = stages.get(stageIndex);
        @SuppressWarnings("unchecked")
        Class<? extends Stage<?, ?>> stageClass = (Class<? extends Stage<?, ?>>) stage.getClass();
        if (stageClass.equals(EmbedSource.class)) {
            this.session.banner("Embedded pipelines cannot be edited; remove and re-add");
            return rerender(ctx);
        }
        if (stageClass.equals(MapCollect.class)) {
            this.session.banner("Branch sub-chain editing is not yet supported in this UI");
            return rerender(ctx);
        }
        if (!requiresModal(stageClass)) {
            this.session.banner("Stage has no editable fields");
            return rerender(ctx);
        }
        return ctx.presentModal(StageEditModal.forEdit(stageIndex, stageClass, stage.config()).mutate()
            .onSubmit(modalCtx -> onEditModalSubmitted(modalCtx, stageIndex, stageClass))
            .build());
    }

    private @NotNull Mono<Void> onEditModalSubmitted(@NotNull ModalContext ctx, int stageIndex, @NotNull Class<? extends Stage<?, ?>> stageClass) {
        this.session.replaceStage(stageIndex, stageClass, StageConfigParser.readValues(ctx.getComponent()));
        return rerender(ctx);
    }

    private static boolean requiresModal(@NotNull Class<? extends Stage<?, ?>> stageClass) {
        return StageReflection.of(stageClass).schema().stream().anyMatch(spec -> switch (spec.type()) {
            case SUB_PIPELINE, SUB_PIPELINES_MAP, TYPED_SUB_PIPELINES_MAP -> false;
            default -> true;
        });
    }

    /* ============================  save flow  ============================ */

    private @NotNull Mono<Void> onSaveClicked(@NotNull ButtonContext ctx) {
        return ctx.presentModal(SaveExtractorModal.of(this.session.state()).mutate()
            .onSubmit(this::onSaveModalSubmitted)
            .build());
    }

    private @NotNull Mono<Void> onSaveModalSubmitted(@NotNull ModalContext ctx) {
        PipelineBuilderSession.SaveValidation v = this.session.validateAndStoreBanner(
            StageConfigParser.readValues(ctx.getComponent())
        );
        if (!v.ok()) return rerender(ctx);

        Extractor row = buildExtractorFromState(v);
        return this.bot.getExtractorStore().save(row)
            .doOnSuccess(__ -> this.session.recordSaved(row))
            .then(rerender(ctx));
    }

    private @NotNull Extractor buildExtractorFromState(@NotNull PipelineBuilderSession.SaveValidation v) {
        Extractor backing = this.session.state().backingRow();
        Extractor row = new Extractor();
        if (backing != null) {
            row.setId(backing.getId());
            row.setCreatedAt(backing.getCreatedAt());
        } else {
            row.setId(UUID.randomUUID());
        }
        row.setOwnerUserId(this.callerUserId);
        row.setLabel(v.label());
        row.setShortId(v.shortId());
        row.setVisibility(v.visibility());
        row.setGuildId(v.visibility() == Extractor.Visibility.GUILD ? this.callerGuildId : null);
        row.setPipeline(this.session.state().pipeline());
        return row;
    }

    /* ============================  rerender helpers  ============================ */

    private @NotNull Mono<Void> rerender(@NotNull ButtonContext ctx) {
        return ctx.edit(response -> response.mutate().withPages(renderMainPage()).build());
    }

    private @NotNull Mono<Void> rerender(@NotNull ModalContext ctx) {
        return ctx.edit(response -> response.mutate().withPages(renderMainPage()).build());
    }

    private @NotNull Mono<Void> rerender(@NotNull OptionContext ctx) {
        return ctx.edit(response -> response.mutate().withPages(renderMainPage()).build());
    }

    private @NotNull PipelineContext buildPipelineContext() {
        ExtractorStore store = this.bot.getExtractorStore();
        return PipelineContext.builder()
            .withResolver(ExtractorResolver.of(store, PipelineContext.defaults()))
            .withBagEntry(ExtractorResolver.BAG_CALLER_USER_ID, this.callerUserId)
            .withBagEntry(ExtractorResolver.BAG_CALLER_GUILD_ID,
                this.callerGuildId == null ? -1L : this.callerGuildId)
            .build();
    }

}
