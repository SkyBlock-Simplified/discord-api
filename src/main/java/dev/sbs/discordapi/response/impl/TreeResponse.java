package dev.sbs.discordapi.response.impl;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.search.SearchFunction;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.ExceptionUtil;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.handler.EmojiHandler;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.media.Attachment;
import dev.sbs.discordapi.response.component.media.MediaData;
import dev.sbs.discordapi.response.component.type.TopLevelComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.handler.Subpages;
import dev.sbs.discordapi.response.handler.history.TreeHistoryHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.impl.TreePage;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TreeResponse implements Response, Subpages<TreePage> {

    private final long buildTime = System.currentTimeMillis();
    private final @NotNull UUID uniqueId;
    private final @NotNull Optional<Snowflake> referenceId;
    private final @NotNull Scheduler reactorScheduler;
    private final @NotNull AllowedMentions allowedMentions;
    private final int timeToLive;
    private final boolean ephemeral;
    private final @NotNull ConcurrentList<Attachment> attachments;
    private final Function<MessageContext<MessageCreateEvent>, Mono<Void>> createInteraction;

    private final boolean renderingPagingComponents;
    private final @NotNull TreeHistoryHandler<TreePage, String> historyHandler;
    @Getter(AccessLevel.NONE)
    private ConcurrentList<TopLevelComponent> cachedPageComponents = Concurrent.newUnmodifiableList();

    public static @NotNull TreeBuilder builder() {
        return new TreeBuilder();
    }

    public static @NotNull TreeBuilder from(@NotNull TreeResponse response) {
        return builder()
            .withUniqueId(response.getUniqueId())
            .withPages(response.getPages())
            .withAttachments(response.getAttachments())
            .withReference(response.getReferenceId())
            .withReactorScheduler(response.getReactorScheduler())
            .withTimeToLive(response.getTimeToLive())
            .isRenderingPagingComponents(response.isRenderingPagingComponents())
            .isEphemeral(response.isEphemeral())
            .withPageHistory(response.getHistoryHandler().getIdentifierHistory())
            .withItemPage(response.getHistoryHandler().getCurrentPage().getItemHandler().getCurrentIndex())
            .onCreate(response.getCreateInteraction());
    }

    @Override
    public @NotNull ConcurrentList<TopLevelComponent> getCachedPageComponents() {
        if (this.isRenderingPagingComponents() && this.isCacheUpdateRequired()) {
            ConcurrentList<TopLevelComponent> pageComponents = Concurrent.newList();

            // Page List
            if (this.getPages().size() > 1 && !this.getHistoryHandler().hasPageHistory()) {
                pageComponents.add(ActionRow.of(
                    SelectMenu.builder()
                        .withPageType(SelectMenu.PageType.PAGE_SELECTOR)
                        .withPlaceholder("Select a page.")
                        .withPlaceholderUsesSelectedOption()
                        .withOptions(
                            this.getPages()
                                .stream()
                                .map(Page::getOption)
                                .collect(Concurrent.toList())
                        )
                        .onInteract(SelectMenu.PageType.PAGE_SELECTOR.getInteraction())
                        .build()
                        .updateSelected(this.getHistoryHandler().getIdentifierHistory().get(0))
                ));
            }

            // SubPage List
            if (this.getHistoryHandler().getCurrentPage().getPages().notEmpty() || this.getHistoryHandler().hasPageHistory()) {
                SelectMenu.Builder subPageBuilder = SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE_SELECTOR)
                    .withPlaceholder("Select a subpage.")
                    .withPlaceholderUsesSelectedOption()
                    .onInteract(SelectMenu.PageType.SUBPAGE_SELECTOR.getInteraction());

                if (this.getHistoryHandler().hasPageHistory()) {
                    subPageBuilder.withOptions(
                        SelectMenu.Option.builder()
                            .withValue("BACK")
                            .withLabel("Back")
                            .withEmoji(EmojiHandler.getEmoji("ARROW_LEFT"))
                            .build()
                    );
                }

                TreePage subPage = this.getHistoryHandler().getCurrentPage().getPages().notEmpty() ?
                    this.getHistoryHandler().getCurrentPage() :
                    this.getHistoryHandler().getPreviousPage().orElse(this.getHistoryHandler().getCurrentPage());

                subPageBuilder.withOptions(
                    subPage.getPages()
                        .stream()
                        .map(Page::getOption)
                        .collect(Concurrent.toList())
                );

                pageComponents.add(ActionRow.of(subPageBuilder.build()));
            }

            if (this.getHistoryHandler().getCurrentPage().hasItems()) {
                // Item List
                pageComponents.add(ActionRow.of(
                    Arrays.stream(Button.PageType.values())
                        .filter(pageType -> pageType != Button.PageType.NONE)
                        .map(Button.PageType::build)
                        .collect(Concurrent.toList())
                ));

                // Editor
                if (this.getHistoryHandler().getCurrentPage().getItemHandler().isEditorEnabled()) {
                    // Must Cache First
                    ConcurrentList<FieldItem<?>> cachedFieldItems = this.getHistoryHandler()
                        .getCurrentPage()
                        .getItemHandler()
                        .getCachedFieldItems();

                    pageComponents.add(ActionRow.of(
                        SelectMenu.builder()
                            .withPageType(SelectMenu.PageType.ITEM)
                            .withPlaceholder("Select an item to edit.")
                            .onInteract(SelectMenu.PageType.ITEM.getInteraction())
                            .withOptions(
                                Stream.concat(
                                        this.getHistoryHandler()
                                            .getCurrentPage()
                                            .getItemHandler()
                                            .getCachedStaticItems()
                                            .stream(),
                                        cachedFieldItems.stream()
                                    )
                                    .map(Item::getOption)
                                    .collect(Concurrent.toList())
                            )
                            .build()
                    ));
                }
            }

            this.cachedPageComponents = pageComponents.toUnmodifiableList();

            //this.editPageButton(Button::getPageType, Button.PageType.FIRST, builder -> builder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasPreviousItemPage()));
            this.editPageButton(Button::getPageType, Button.PageType.PREVIOUS, builder -> builder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasPreviousItemPage()));
            this.editPageButton(Button::getPageType, Button.PageType.NEXT, builder -> builder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasNextItemPage()));
            //this.editPageButton(Button::getPageType, Button.PageType.LAST, builder -> builder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasNextItemPage()));
            //this.editPageButton(Button::getPageType, Button.PageType.BACK, builder -> builder.setEnabled(this.getHistoryHandler().getCurrentPage().getHistoryHandler().hasPageHistory()));
            //this.editPageButton(Button::getPageType, Button.PageType.SORT, builder -> builder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().getSortHandler().hasSorters()));
            this.editPageButton(Button::getPageType, Button.PageType.SEARCH, Button.Builder::setEnabled);
            //this.editPageButton(Button::getPageType, Button.PageType.ORDER, Button.Builder::setEnabled);

            // Labels
            this.editPageButton(
                Button::getPageType,
                Button.PageType.INDEX,
                builder -> builder
                    .withLabel(
                        "%s / %s",
                        this.getHistoryHandler()
                            .getCurrentPage()
                            .getItemHandler()
                            .getCurrentIndex(),
                        this.getHistoryHandler()
                            .getCurrentPage()
                            .getItemHandler()
                            .getTotalPages()
                    )
            );
        }

        return this.cachedPageComponents;
    }

    @Override
    public @NotNull ConcurrentList<TreePage> getPages() {
        return this.getHistoryHandler().getItems();
    }

    private @NotNull ConcurrentList<Embed> getCurrentEmbeds() {
        ConcurrentList<Embed> embeds = Concurrent.newList(this.getHistoryHandler().getCurrentPage().getEmbeds());

        // Handle Item List
        if (this.getHistoryHandler().getCurrentPage().hasItems()) {
            // Cache First
            ConcurrentList<Field> renderFields = this.getHistoryHandler()
                .getCurrentPage()
                .getItemHandler()
                .getRenderFields();

            embeds.add(
                Embed.builder()
                    .withItems(
                        this.getHistoryHandler()
                            .getCurrentPage()
                            .getItemHandler()
                            .getCachedStaticItems()
                    )
                    .withFields(renderFields)
                    .build()
            );
        }

        return embeds;
    }

    // D4j Specs

    @Override
    public @NotNull MessageCreateSpec getD4jCreateSpec() {
        return Response.super.getD4jCreateSpec()
            .withContent(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    @Override
    public @NotNull MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return Response.super.getD4jCreateMono(channel)
            .withContent(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    @Override
    public @NotNull MessageEditSpec getD4jEditSpec() {
        return Response.super.getD4jEditSpec()
            .withContentOrNull(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withEmbedsOrNull(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    @Override
    public @NotNull InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return Response.super.getD4jComponentCallbackSpec()
            .withContent(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    @Override
    public @NotNull InteractionFollowupCreateSpec getD4jInteractionFollowupCreateSpec() {
        return Response.super.getD4jInteractionFollowupCreateSpec()
            .withContent(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    @Override
    public @NotNull InteractionReplyEditSpec getD4jInteractionReplyEditSpec() {
        return Response.super.getD4jInteractionReplyEditSpec()
            .withContentOrNull(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withEmbedsOrNull(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    @Override
    public boolean isCacheUpdateRequired() {
        return this.getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().isCacheUpdateRequired();
    }

    @Override
    public void setNoCacheUpdateRequired() {
        this.getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().setCacheUpdateRequired(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull TreeBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TreeBuilder extends Builder<TreePage> {

        // Current Page/Item History
        private Optional<String> defaultPage = Optional.empty();
        private ConcurrentList<String> pageHistory = Concurrent.newList();
        private int currentItemPage = 1;

        /**
         * Recursively disable all interactable components from all {@link Page Pages} in {@link TreeResponse}.
         */
        @Override
        public TreeBuilder disableAllComponents() {
            super.pages.forEach(page -> this.editPage(page.mutate().disableComponents(true).build()));
            return this;
        }

        /**
         * Updates an existing {@link Page}.
         *
         * @param page The page to edit.
         */
        @Override
        public TreeBuilder editPage(@NotNull TreePage page) {
            super.pages.stream()
                .filter(existingPage -> existingPage.getOption().getValue().equals(page.getOption().getValue()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Sets the {@link TreeResponse} should be ephemeral.
         */
        @Override
        public TreeBuilder isEphemeral() {
            return this.isEphemeral(true);
        }

        /**
         * Sets the {@link TreeResponse} should be ephemeral.
         *
         * @param value True if ephemeral.
         */
        @Override
        public TreeBuilder isEphemeral(boolean value) {
            super.ephemeral = value;
            return this;
        }

        /**
         * Sets the {@link TreeResponse} to render paging components.
         */
        @Override
        public TreeBuilder isRenderingPagingComponents() {
            return this.isRenderingPagingComponents(true);
        }

        /**
         * Sets if the {@link TreeResponse} should render paging components.
         *
         * @param value True if rendering page components.
         */
        @Override
        public TreeBuilder isRenderingPagingComponents(boolean value) {
            super.renderingPagingComponents = value;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link TreeResponse} is known to exist.
         *
         * @param interaction The interaction function.
         */
        @Override
        public TreeBuilder onCreate(@Nullable Function<MessageContext<MessageCreateEvent>, Mono<Void>> interaction) {
            return this.onCreate(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link TreeResponse} is known to exist.
         *
         * @param interaction The interaction function.
         */
        @Override
        public TreeBuilder onCreate(@NotNull Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction) {
            super.createInteraction = interaction;
            return this;
        }

        /**
         * Specifies the allowed mentions for the {@link TreeResponse}.
         *
         * @param allowedMentions An {@link AllowedMentions} object that defines which mentions should be allowed in the response.
         */
        @Override
        public TreeBuilder withAllowedMentions(@NotNull AllowedMentions allowedMentions) {
            super.allowedMentions = allowedMentions;
            return this;
        }

        /**
         * Adds an {@link Attachment} to the {@link TreeResponse}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         */
        @Override
        public TreeBuilder withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds an {@link Attachment} to the {@link TreeResponse}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         * @param spoiler True if the attachment should be a spoiler.
         */
        @Override
        public TreeBuilder withAttachment(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
            return this.withAttachments(
                Attachment.builder()
                    .isSpoiler(spoiler)
                    .withName(name)
                    .withStream(inputStream)
                    .build()
            );
        }

        /**
         * Add {@link Attachment Attachments} to the {@link TreeResponse}.
         *
         * @param attachments Variable number of attachments to add.
         */
        @Override
        public TreeBuilder withAttachments(@NotNull Attachment... attachments) {
            Arrays.stream(attachments)
                .filter(attachment -> !super.attachments.contains(SearchFunction.combine(Attachment::getMediaData, MediaData::getName), attachment.getMediaData().getName()))
                .forEach(super.attachments::add);

            return this;
        }

        /**
         * Add {@link Attachment Attachments} to the {@link TreeResponse}.
         *
         * @param attachments Collection of attachments to add.
         */
        @Override
        public TreeBuilder withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(attachment -> {
                if (!super.attachments.contains(SearchFunction.combine(Attachment::getMediaData, MediaData::getName), attachment.getMediaData().getName()))
                    super.attachments.add(attachment);
            });

            return this;
        }

        /**
         * Sets the default page the {@link TreeResponse} should load.
         *
         * @param pageIdentifier The page identifier to load.
         */
        public TreeBuilder withDefaultPage(@Nullable String pageIdentifier) {
            return this.withDefaultPage(Optional.ofNullable(pageIdentifier));
        }

        /**
         * Sets the default page the {@link TreeResponse} should load.
         *
         * @param pageIdentifier The page identifier to load.
         */
        public TreeBuilder withDefaultPage(@NotNull Optional<String> pageIdentifier) {
            this.defaultPage = pageIdentifier;
            return this;
        }

        private TreeBuilder withItemPage(int currentItemPage) {
            this.currentItemPage = currentItemPage;
            return this;
        }

        private TreeBuilder withPageHistory(@NotNull ConcurrentList<String> pageHistory) {
            this.pageHistory = pageHistory;
            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link TreeResponse}.
         *
         * @param throwable The throwable exception stack trace to add.
         */
        @Override
        public TreeBuilder withException(@NotNull Throwable throwable) {
            super.attachments.add(
                Attachment.builder()
                    .withName("stacktrace-%s.log", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("EST", ZoneId.SHORT_IDS)).format(Instant.now()))
                    .withStream(new ByteArrayInputStream(ExceptionUtil.getStackTrace(throwable).getBytes(StandardCharsets.UTF_8)))
                    .build()
            );

            return this;
        }

        /**
         * Add {@link File Files} to the {@link TreeResponse}.
         *
         * @param files Variable number of files to add.
         */
        @Override
        public TreeBuilder withFiles(@NotNull File... files) {
            return this.withFiles(Arrays.asList(files));
        }

        /**
         * Add {@link File Files} to the {@link TreeResponse}.
         *
         * @param files Collection of files to add.
         */
        @Override
        public TreeBuilder withFiles(@NotNull Iterable<File> files) {
            List<File> fileList = List.class.isAssignableFrom(files.getClass()) ? (List<File>) files : StreamSupport.stream(files.spliterator(), false).toList();

            fileList.stream()
                .map(file -> {
                    try {
                        return Attachment.builder()
                            .withName(file.getName())
                            .withStream(new FileInputStream(file))
                            .build();
                    } catch (FileNotFoundException fnfex) {
                        throw new RuntimeException(fnfex);
                    }
                })
                .forEach(super::withAttachments);

            return this;
        }

        /**
         * Add {@link Page Pages} to the {@link TreeResponse}.
         *
         * @param pages Variable number of pages to add.
         */
        @Override
        public TreeBuilder withPages(@NotNull TreePage... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add {@link Page Pages} to the {@link TreeResponse}.
         *
         * @param pages Collection of pages to add.
         */
        @Override
        public TreeBuilder withPages(@NotNull Iterable<TreePage> pages) {
            super.withPages(pages);
            return this;
        }

        @Override
        public TreeBuilder withReactorScheduler(@NotNull Scheduler reactorScheduler) {
            super.reactorScheduler = reactorScheduler;
            return this;
        }

        @Override
        public TreeBuilder withReactorScheduler(@NotNull ExecutorService executorService) {
            super.reactorScheduler = Schedulers.fromExecutorService(executorService);
            return this;
        }

        /**
         * Sets the message the {@link TreeResponse} should reply to.
         *
         * @param messageContext The message to reference.
         */
        @Override
        public TreeBuilder withReference(@NotNull MessageContext<?> messageContext) {
            return this.withReference(messageContext.getMessageId());
        }

        /**
         * Sets the message the {@link TreeResponse} should reply to.
         *
         * @param messageId The message to reference.
         */
        @Override
        public TreeBuilder withReference(@Nullable Snowflake messageId) {
            return this.withReference(Optional.ofNullable(messageId));
        }

        /**
         * Sets the message the {@link TreeResponse} should reply to.
         *
         * @param messageId The message to reference.
         */
        @Override
        public TreeBuilder withReference(@NotNull Mono<Snowflake> messageId) {
            return this.withReference(messageId.blockOptional());
        }

        /**
         * Sets the message the {@link TreeResponse} should reply to.
         *
         * @param messageId The message to reference.
         */
        @Override
        public TreeBuilder withReference(@NotNull Optional<Snowflake> messageId) {
            super.referenceId = messageId;
            return this;
        }

        /**
         * Sets the time in seconds for the {@link TreeResponse} to live in {@link DiscordBot#getResponseHandler()}.
         * <br><br>
         * This value moves whenever the user interacts with the {@link TreeResponse}.
         * <br><br>
         * Maximum 500 seconds and Minimum is 5 seconds.
         *
         * @param timeToLive How long the response should live without interaction in seconds.
         */
        @Override
        public TreeBuilder withTimeToLive(int timeToLive) {
            super.timeToLive = NumberUtil.ensureRange(timeToLive, 5, 300);
            return this;
        }

        /**
         * Used to replace an existing message with a known ID.
         *
         * @param uniqueId Unique ID to assign to {@link TreeResponse}.
         */
        @Override
        public TreeBuilder withUniqueId(@NotNull UUID uniqueId) {
            super.uniqueId = uniqueId;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link TreeResponse}.
         */
        @Override
        public @NotNull TreeResponse build() {
            Reflection.validateFlags(this);

            TreeResponse response = new TreeResponse(
                super.uniqueId,
                super.referenceId,
                super.reactorScheduler,
                super.allowedMentions,
                super.timeToLive,
                super.ephemeral,
                super.attachments,
                super.createInteraction.orElse(__ -> Mono.empty()),
                super.renderingPagingComponents,
                TreeHistoryHandler.<TreePage, String>builder()
                    .withPages(super.pages.toUnmodifiableList())
                    .withMatcher((page, identifier) -> page.getOption().getValue().equals(identifier))
                    .withTransformer(page -> page.getOption().getValue())
                    .build()
            );

            // First Page
            if (this.defaultPage.isPresent() && response.getHistoryHandler().getPage(this.defaultPage.get()).isPresent())
                response.getHistoryHandler().gotoTopLevelPage(this.defaultPage.get());
            else {
                if (!this.pageHistory.isEmpty()) {
                    response.getHistoryHandler().gotoTopLevelPage(this.pageHistory.removeFirst());
                    this.pageHistory.forEach(identifier -> response.getHistoryHandler().gotoSubPage(identifier));
                    response.getHistoryHandler().getCurrentPage().getItemHandler().gotoPage(this.currentItemPage);
                } else
                    response.getHistoryHandler().gotoPage(response.getPages().get(0));
            }

            return response;
        }

    }

}
