package dev.sbs.discordapi.response;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ExceptionUtil;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.context.message.text.TextCommandContext;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.handler.PageHandler;
import dev.sbs.discordapi.response.page.handler.Paging;
import dev.sbs.discordapi.response.page.item.PageItem;
import dev.sbs.discordapi.util.base.DiscordHelper;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Response implements Paging<Page> {

    @Getter private final long buildTime = System.currentTimeMillis();
    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull ConcurrentList<Page> pages;
    @Getter private final @NotNull ConcurrentList<Attachment> attachments;
    @Getter private final @NotNull Optional<Snowflake> referenceId;
    @Getter private final @NotNull Scheduler reactorScheduler;
    @Getter private final boolean replyMention;
    @Getter private final int timeToLive;
    @Getter private final boolean interactable;
    @Getter private final boolean loader;
    @Getter private final boolean renderingPagingComponents;
    @Getter private final boolean ephemeral;
    @Getter private final PageHandler<Page, String> handler;
    private ConcurrentList<LayoutComponent<ActionComponent>> cachedPageComponents = Concurrent.newUnmodifiableList();

    public static ResponseBuilder builder() {
        return new ResponseBuilder(UUID.randomUUID());
    }

    public static ResponseBuilder builder(@NotNull EventContext<?> eventContext) {
        return new ResponseBuilder(eventContext.getUniqueId());
    }

    /**
     * Updates an existing paging {@link Button}.
     *
     * @param buttonBuilder The button to edit.
     */
    private <S> void editPageButton(@NotNull Function<Button, S> function, S value, Function<Button.ButtonBuilder, Button.ButtonBuilder> buttonBuilder) {
        this.cachedPageComponents.forEach(layoutComponent -> layoutComponent.getComponents()
            .stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> Objects.equals(function.apply(button), value))
            .findFirst()
            .ifPresent(button -> layoutComponent.getComponents().set(
                layoutComponent.getComponents().indexOf(button),
                buttonBuilder.apply(button.mutate()).build()
            ))
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Response response = (Response) o;

        return new EqualsBuilder()
            .append(this.getBuildTime(), response.getBuildTime())
            .append(this.isReplyMention(), response.isReplyMention())
            .append(this.getTimeToLive(), response.getTimeToLive())
            .append(this.isInteractable(), response.isInteractable())
            .append(this.isLoader(), response.isLoader())
            .append(this.isRenderingPagingComponents(), response.isRenderingPagingComponents())
            .append(this.isEphemeral(), response.isEphemeral())
            .append(this.getUniqueId(), response.getUniqueId())
            .append(this.getHandler(), response.getHandler())
            .append(this.getPages(), response.getPages())
            .append(this.getAttachments(), response.getAttachments())
            .append(this.getReferenceId(), response.getReferenceId())
            .append(this.getReactorScheduler(), response.getReactorScheduler())
            .build();
    }

    public static ResponseBuilder from(@NotNull Response response) {
        return new ResponseBuilder(response.getUniqueId())
            .withPages(response.getPages())
            .withAttachments(response.getAttachments())
            .withReference(response.getReferenceId())
            .withReactorScheduler(response.getReactorScheduler())
            .replyMention(response.isReplyMention())
            .withTimeToLive(response.getTimeToLive())
            .isInteractable(response.isInteractable())
            .isLoader(response.isLoader())
            .isRenderingPagingComponents(response.isRenderingPagingComponents())
            .isEphemeral(response.isEphemeral());
    }

    public final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> getCachedPageComponents() {
        if (this.isRenderingPagingComponents()) {
            boolean cacheUpdateRequired = this.getHandler().isCacheUpdateRequired() || this.getHandler().getCurrentPage().getItemData().isCacheUpdateRequired();

            if (this.getHandler().isCacheUpdateRequired()) {
                ConcurrentList<LayoutComponent<ActionComponent>> pageComponents = Concurrent.newList();
                this.getHandler().setCacheUpdateRequired(false);

                // Page List
                if (ListUtil.sizeOf(this.getPages()) > 1) {
                    pageComponents.add(ActionRow.of(
                        SelectMenu.builder()
                            .withPageType(SelectMenu.PageType.PAGE)
                            .withPlaceholder("Select a page.")
                            .withPlaceholderUsesSelectedOption()
                            .withOptions(
                                this.getPages()
                                    .stream()
                                    .map(Page::getOption)
                                    .flatMap(Optional::stream)
                                    .collect(Concurrent.toList())
                            )
                            .build()
                            .initPlaceholder(this.getHandler().getHistoryIdentifiers().get(0))
                    ));
                }

                // SubPage List
                if (ListUtil.notEmpty(this.getHandler().getCurrentPage().getPages()) || this.getHandler().getPreviousPage().isPresent()) {
                    SelectMenu.Builder subPageBuilder = SelectMenu.builder()
                        .withPageType(SelectMenu.PageType.SUBPAGE)
                        .withPlaceholder("Select a subpage.")
                        .withPlaceholderUsesSelectedOption();

                    if (this.getHandler().getPreviousPage().isPresent()) {
                        subPageBuilder.withOptions(
                            SelectMenu.Option.builder()
                                .withValue("BACK")
                                .withLabel("Back")
                                .withEmoji(Button.PageType.BACK.getEmoji())
                                .build()
                        );
                    }

                    Page subPage = ListUtil.notEmpty(
                        this.getHandler().getCurrentPage().getPages()) ?
                        this.getHandler().getCurrentPage() :
                        this.getHandler().getPreviousPage().orElse(this.getHandler().getCurrentPage());

                    subPageBuilder.withOptions(
                        subPage.getPages()
                            .stream()
                            .map(Page::getOption)
                            .flatMap(Optional::stream)
                            .collect(Concurrent.toList())
                    );

                    pageComponents.add(ActionRow.of(subPageBuilder.build()));
                }

                if (this.getHandler().getCurrentPage().doesHaveItems()) {
                    // Item List
                    if (this.getHandler().getCurrentPage().getItemData().getTotalItemPages() > 1) {
                        for (int i = 1; i <= Button.PageType.getNumberOfRows(); i++) {
                            int row = i;

                            pageComponents.add(ActionRow.of(
                                Arrays.stream(Button.PageType.values())
                                    .filter(pageType -> pageType.getRow() == row)
                                    .map(Button.PageType::build)
                                    .collect(Concurrent.toList())
                            ));
                        }
                    }

                    // Viewer/Editor
                    if (this.getHandler().getCurrentPage().getItemData().isViewerEnabled()) {
                        pageComponents.add(ActionRow.of(
                            SelectMenu.builder()
                                .withPageType(SelectMenu.PageType.ITEM)
                                .withPlaceholder("Select an item to view.")
                                .withOptions(
                                    this.getHandler()
                                        .getCurrentPage()
                                        .getItemData()
                                        .getCachedPageItems()
                                        .stream()
                                        .map(PageItem::getOption)
                                        .flatMap(Optional::stream)
                                        .collect(Concurrent.toList())
                                )
                                .build()
                        ));
                    }
                }

                this.cachedPageComponents = pageComponents.toUnmodifiableList();
            }

            if (cacheUpdateRequired)
                this.updatePagingComponents();
        }

        return this.cachedPageComponents;
    }

    private void updatePagingComponents() {
        // Enabled
        this.editPageButton(Button::getPageType, Button.PageType.FIRST, buttonBuilder -> buttonBuilder.setEnabled(this.getHandler().getCurrentPage().getItemData().hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.PREVIOUS, buttonBuilder -> buttonBuilder.setEnabled(this.getHandler().getCurrentPage().getItemData().hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.NEXT, buttonBuilder -> buttonBuilder.setEnabled(this.getHandler().getCurrentPage().getItemData().hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.LAST, buttonBuilder -> buttonBuilder.setEnabled(this.getHandler().getCurrentPage().getItemData().hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.BACK, Button.ButtonBuilder::setDisabled); // TODO: Item Paging?
        this.editPageButton(Button::getPageType, Button.PageType.SORT, buttonBuilder -> buttonBuilder.setEnabled(ListUtil.sizeOf(this.getHandler().getCurrentPage().getItemData().getSorters()) > 1));
        this.editPageButton(Button::getPageType, Button.PageType.ORDER, Button.ButtonBuilder::setEnabled);

        // Labels
        this.editPageButton(
            Button::getPageType,
            Button.PageType.INDEX,
            buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format(
                "{0} / {1}",
                this.getHandler()
                    .getCurrentPage()
                    .getItemData()
                    .getCurrentItemPage(),
                this.getHandler()
                    .getCurrentPage()
                    .getItemData()
                    .getTotalItemPages()
            ))
        );

        this.editPageButton(
            Button::getPageType,
            Button.PageType.SORT,
            buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format(
                "Sort: {0}", this.getHandler()
                    .getCurrentPage()
                    .getItemData()
                    .getCurrentSorter()
                    .map(sorter -> sorter.getOption().getLabel())
                    .orElse("N/A")
            ))
        );

        this.editPageButton(
            Button::getPageType,
            Button.PageType.ORDER,
            buttonBuilder -> buttonBuilder
                .withLabel(FormatUtil.format("Order: {0}", this.getHandler().getCurrentPage().getItemData().isReversed() ? "Reversed" : "Normal"))
                .withEmoji(DiscordHelper.getEmoji(FormatUtil.format("SORT_{0}", this.getHandler().getCurrentPage().getItemData().isReversed() ? "ASCENDING" : "DESCENDING")))
        );
    }

    public MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .content(this.getHandler().getCurrentPage().getContent().orElse(""))
            .allowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withContent(this.getHandler().getCurrentPage().getContent().orElse(""))
            .withAllowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .withFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    public MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .contentOrNull(this.getHandler().getCurrentPage().getContent().orElse(""))
            .addAllFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .addAllEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return InteractionApplicationCommandCallbackSpec.builder()
            .content(this.getHandler().getCurrentPage().getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionReplyEditSpec getD4jInteractionEditSpec() {
        return InteractionReplyEditSpec.builder()
            .contentOrNull(this.getHandler().getCurrentPage().getContent().orElse(""))
            .allowedMentionsOrNull(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .componentsOrNull(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embedsOrNull(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    private ConcurrentList<LayoutComponent<ActionComponent>> getCurrentComponents() {
        ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList();
        components.addAll(this.getCachedPageComponents()); // Paging Components
        components.addAll(this.getHandler().getCurrentPage().getComponents()); // Current Page Components
        return components;
    }

    private ConcurrentList<Embed> getCurrentEmbeds() {
        ConcurrentList<Embed> embeds = Concurrent.newList(this.getHandler().getCurrentPage().getEmbeds());

        // Handle Item List
        if (this.getHandler().getCurrentPage().doesHaveItems()) {
            embeds.add(
                Embed.builder()
                    .withFields(
                        this.getHandler()
                            .getCurrentPage()
                            .getItemData()
                            .getStyle()
                            .getPageItems(
                                this.getHandler()
                                    .getCurrentPage()
                                    .getItemData()
                                    .getColumnNames(),
                                this.getHandler()
                                    .getCurrentPage()
                                    .getItemData()
                                    .getCachedPageItems()
                            )
                    )
                    .build()
            );
        }

        return embeds;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUniqueId())
            .append(this.getHandler())
            .append(this.getBuildTime())
            .append(this.getPages())
            .append(this.getAttachments())
            .append(this.getReferenceId())
            .append(this.getReactorScheduler())
            .append(this.isReplyMention())
            .append(this.getTimeToLive())
            .append(this.isInteractable())
            .append(this.isLoader())
            .append(this.isRenderingPagingComponents())
            .append(this.isEphemeral())
            .build();
    }

    public Response.ResponseBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ResponseBuilder implements Builder<Response> {

        private final UUID uniqueId;
        private final ConcurrentList<Page> pages = Concurrent.newList();
        private final ConcurrentList<Attachment> attachments = Concurrent.newList();
        private boolean inlineItems;
        private Optional<Snowflake> referenceId = Optional.empty();
        private Scheduler reactorScheduler = Schedulers.boundedElastic();
        private boolean replyMention;
        private int timeToLive = 10;
        private boolean interactable = true;
        private boolean loader = false;
        private boolean renderingPagingComponents = true;
        private boolean ephemeral = false;
        private Optional<String> defaultPage = Optional.empty();

        /**
         * Recursively clear all but preservable components from all {@link Page Pages} in {@link Response}.
         */
        public ResponseBuilder clearAllComponents() {
            return this.clearAllComponents(true);
        }

        /**
         * Recursively clear all components from all {@link Page Pages} in {@link Response}.
         *
         * @param enforcePreserve True to leave preservable components.
         */
        public ResponseBuilder clearAllComponents(boolean enforcePreserve) {
            this.pages.forEach(page -> this.editPage(page.mutate().clearComponents(true, enforcePreserve).build()));
            return this;
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        public ResponseBuilder editPage(@NotNull Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param index The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public ResponseBuilder editPage(int index, @NotNull Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            if (index < this.pages.size())
                this.pages.set(index, pageBuilder.apply(this.pages.get(index).mutate()).build());

            return this;
        }

        /**
         * Updates an existing {@link Page}.
         *
         * @param page The page to edit.
         */
        public ResponseBuilder editPage(@NotNull Page page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getIdentifier().equals(page.getIdentifier()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         * <br><br>
         * Only applies to slash commands.
         */
        public ResponseBuilder isEphemeral() {
            return this.isEphemeral(true);
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         * <br><br>
         * Only applies to slash commands.
         *
         * @param value True if interactable.
         */
        public ResponseBuilder isEphemeral(boolean value) {
            this.ephemeral = value;
            return this;
        }

        /**
         * Sets the {@link Response} as cacheable for interaction.
         */
        public ResponseBuilder isInteractable() {
            return this.isInteractable(true);
        }

        /**
         * Sets if the {@link Response} is cached for interaction.
         *
         * @param value True if interactable.
         */
        public ResponseBuilder isInteractable(boolean value) {
            this.interactable = value;
            return this;
        }

        /**
         * Sets the {@link Response} as a loader.
         */
        public ResponseBuilder isLoader() {
            return this.isLoader(true);
        }

        /**
         * Sets if the {@link Response} should be a loader.
         *
         * @param value True if rendering page components.
         */
        public ResponseBuilder isLoader(boolean value) {
            this.loader = value;
            return this;
        }

        /**
         * Sets the {@link Response} to render paging components.
         */
        public ResponseBuilder isRenderingPagingComponents() {
            return this.isRenderingPagingComponents(true);
        }

        /**
         * Sets if the {@link Response} should render paging components.
         *
         * @param value True if rendering page components.
         */
        public ResponseBuilder isRenderingPagingComponents(boolean value) {
            this.renderingPagingComponents = value;
            return this;
        }

        /**
         * Sets if the {@link Response} should mention the author of the specified {@link #withReference}.
         */
        public ResponseBuilder replyMention() {
            return this.replyMention(true);
        }

        /**
         * Sets if the {@link Response} should mention the author of the specified {@link #withReference}.
         *
         * @param mention True to mention the user in the response.
         */
        public ResponseBuilder replyMention(boolean mention) {
            this.replyMention = mention;
            return this;
        }

        /**
         * Adds a {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         */
        public ResponseBuilder withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds a {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         * @param spoiler True if the attachment should be a spoiler.
         */
        public ResponseBuilder withAttachment(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
            return this.withAttachments(Attachment.of(name, inputStream, spoiler));
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Variable number of attachments to add.
         */
        public ResponseBuilder withAttachments(@NotNull Attachment... attachments) {
            return this.withAttachments(Arrays.asList(attachments));
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Collection of attachments to add.
         */
        public ResponseBuilder withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(this.attachments::add);
            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link Response}.
         *
         * @param throwable The throwable exception stack trace to add.
         */
        public ResponseBuilder withException(@NotNull Throwable throwable) {
            this.attachments.add(Attachment.of(
                FormatUtil.format("stacktrace-{0}.log", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("EST", ZoneId.SHORT_IDS)).format(Instant.now())),
                new ByteArrayInputStream(ExceptionUtil.getStackTrace(throwable).getBytes(StandardCharsets.UTF_8)))
            );

            return this;
        }

        /**
         * Sets the default page the {@link Response} should load.
         *
         * @param pageIdentifier The page identifier to load.
         */
        public ResponseBuilder withDefaultPage(@Nullable String pageIdentifier) {
            return this.withDefaultPage(Optional.ofNullable(pageIdentifier));
        }

        /**
         * Sets the default page the {@link Response} should load.
         *
         * @param pageIdentifier The page identifier to load.
         */
        public ResponseBuilder withDefaultPage(@NotNull Optional<String> pageIdentifier) {
            this.defaultPage = pageIdentifier;
            return this;
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages Variable number of pages to add.
         */
        public ResponseBuilder withPages(@NotNull Page... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages Collection of pages to add.
         */
        public ResponseBuilder withPages(@NotNull Iterable<Page> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        public ResponseBuilder withReactorScheduler(@NotNull Scheduler reactorScheduler) {
            this.reactorScheduler = reactorScheduler;
            return this;
        }

        public ResponseBuilder withReactorScheduler(@NotNull ExecutorService executorService) {
            this.reactorScheduler = Schedulers.fromExecutorService(executorService);
            return this;
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param eventContext The message to reference.
         */
        public ResponseBuilder withReference(@NotNull EventContext<?> eventContext) {
            return this.withReference(eventContext instanceof TextCommandContext ? ((TextCommandContext) eventContext).getMessageId() : null);
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageContext The message to reference.
         */
        public ResponseBuilder withReference(@NotNull MessageContext<?> messageContext) {
            return this.withReference(messageContext.getMessageId());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public ResponseBuilder withReference(@Nullable Snowflake messageId) {
            return this.withReference(Optional.ofNullable(messageId));
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public ResponseBuilder withReference(@NotNull Mono<Snowflake> messageId) {
            return this.withReference(messageId.blockOptional());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public ResponseBuilder withReference(@NotNull Optional<Snowflake> messageId) {
            this.referenceId = messageId;
            return this;
        }

        /**
         * Sets the time in seconds for the {@link Response} to live in {@link DiscordBot#getResponseCache()}.
         * <br><br>
         * This value moves whenever the user interacts with the {@link Response}.
         * <br><br>
         * Maximum 500 seconds and Minimum is 5 seconds.
         *
         * @param secondsToLive How long the response should live without interaction in seconds.
         */
        public ResponseBuilder withTimeToLive(int secondsToLive) {
            this.timeToLive = NumberUtil.ensureRange(secondsToLive, 5, 300);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link Response}.
         */
        @Override
        public Response build() {
            if (ListUtil.isEmpty(this.pages))
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("A response must have at least one page!")
                    .build();

            Response response = new Response(
                this.uniqueId,
                this.pages.toUnmodifiableList(),
                this.attachments.toUnmodifiableList(),
                this.referenceId,
                this.reactorScheduler,
                this.replyMention,
                this.timeToLive,
                this.interactable,
                this.loader,
                this.renderingPagingComponents,
                this.ephemeral,
                PageHandler.<Page, String>builder()
                    .withPages(this.pages)
                    .withHistoryMatcher((page, identifier) -> page.getOption()
                        .map(pageOption -> pageOption.getValue().equals(identifier))
                        .orElse(false)
                    )
                    .withHistoryTransformer(page -> page.getOption().map(SelectMenu.Option::getValue).orElse(null))
                    .build()
            );

            // First Page
            if (this.defaultPage.isPresent() && response.getHandler().getPage(this.defaultPage.get()).isPresent())
                response.getHandler().gotoPage(this.defaultPage.get());
            else
                response.getHandler().gotoPage(response.getPages().get(0));



            return response;
        }

    }

}
