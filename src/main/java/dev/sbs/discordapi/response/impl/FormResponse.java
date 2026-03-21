package dev.sbs.discordapi.response.impl;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.ExceptionUtil;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.media.Attachment;
import dev.sbs.discordapi.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.handler.Subpages;
import dev.sbs.discordapi.response.handler.history.IndexHistoryHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.impl.form.FormPage;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
public final class FormResponse implements Response, Subpages<FormPage> {

    private final long buildTime = System.currentTimeMillis();
    private final @NotNull UUID uniqueId;
    private final @NotNull Optional<Snowflake> referenceId;
    private final @NotNull Scheduler reactorScheduler;
    private final @NotNull AllowedMentions allowedMentions;
    private final int timeToLive;
    private final boolean ephemeral;
    private final @NotNull ConcurrentList<Attachment> attachments;
    private final Function<MessageContext<MessageCreateEvent>, Mono<Void>> createInteraction;
    private final Function<ButtonContext, Mono<Void>> submit;

    private final boolean renderingPagingComponents;
    private final @NotNull IndexHistoryHandler<FormPage, String> historyHandler;
    @Getter(AccessLevel.NONE)
    private ConcurrentList<TopLevelMessageComponent> cachedPageComponents = Concurrent.newUnmodifiableList();

    public static @NotNull FormBuilder builder() {
        return new FormBuilder();
    }

    public static @NotNull FormBuilder from(@NotNull FormResponse response) {
        return new FormBuilder()
            .withUniqueId(response.getUniqueId())
            .withPages(response.getPages())
            .withAttachments(response.getAttachments())
            .withReference(response.getReferenceId())
            .withReactorScheduler(response.getReactorScheduler())
            .withTimeToLive(response.getTimeToLive())
            .isRenderingPagingComponents(response.isRenderingPagingComponents())
            .isEphemeral(response.isEphemeral())
            .withPageIndex(response.getHistoryHandler().getCurrentIndex())
            .onCreate(response.getCreateInteraction())
            .onSubmit(response.getSubmit());
    }

    @Override
    public @NotNull ConcurrentList<TopLevelMessageComponent> getCachedPageComponents() {
        if (this.isRenderingPagingComponents() && this.isCacheUpdateRequired()) {
            ConcurrentList<TopLevelMessageComponent> pageComponents = Concurrent.newList();

            // Page List
            if (this.getPages().size() > 1 && !this.getHistoryHandler().hasPageHistory()) {
                pageComponents.add(ActionRow.of(
                    SelectMenu.builder()
                        .withPageType(SelectMenu.PageType.PAGE)
                        .withPlaceholder("Select a page.")
                        .withPlaceholderUsesSelectedOption()
                        .withOptions(
                            this.getPages()
                                .stream()
                                .map(Page::getOption)
                                .collect(Concurrent.toList())
                        )
                        .onInteract(SelectMenu.PageType.PAGE.getInteraction())
                        .build()
                        .updateSelected(this.getHistoryHandler().getIdentifierHistory().get(0))
                ));
            }

            // SubPage List
            if (this.getHistoryHandler().getCurrentPage().getPages().notEmpty() || this.getHistoryHandler().hasPageHistory()) {
                SelectMenu.Builder subPageBuilder = SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE)
                    .withPlaceholder("Select a subpage.")
                    .withPlaceholderUsesSelectedOption()
                    .onInteract(SelectMenu.PageType.SUBPAGE.getInteraction());

                if (this.getHistoryHandler().hasPageHistory()) {
                    subPageBuilder.withOptions(
                        SelectMenu.Option.builder()
                            .withValue("BACK")
                            .withLabel("Back")
                            .withEmoji(this.getEmoji("ARROW_LEFT"))
                            .build()
                    );
                }

                FormPage subPage = this.getHistoryHandler().getCurrentPage().getPages().notEmpty() ?
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
    public @NotNull ConcurrentList<FormPage> getPages() {
        return this.getHistoryHandler().getItems();
    }

    @Override
    public boolean isCacheUpdateRequired() {
        return this.getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().isCacheUpdateRequired();
    }

    @Override
    public void setNoCacheUpdateRequired() {
        this.getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().setCacheUpdateRequired(false);
    }
    @SuppressWarnings("unchecked")
    public @NotNull FormBuilder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FormBuilder extends Builder<FormPage> {

        protected Optional<Function<ButtonContext, Mono<Void>>> submitInteraction = Optional.empty();

        // Current Page/Item History
        private int currentPageIndex = 0;

        /**
         * Recursively disable all interactable components from all {@link Page Pages} in {@link FormResponse}.
         */
        public FormBuilder disableAllComponents() {
            this.pages.forEach(page -> this.editPage(page.mutate().disableComponents(true).build()));
            return this;
        }

        /**
         * Updates an existing {@link Page}.
         *
         * @param page The page to edit.
         */
        public FormBuilder editPage(@NotNull FormPage page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getOption().getValue().equals(page.getOption().getValue()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Sets the {@link FormResponse} should be ephemeral.
         */
        public FormBuilder isEphemeral() {
            return this.isEphemeral(true);
        }

        /**
         * Sets the {@link FormResponse} should be ephemeral.
         *
         * @param value True if ephemeral.
         */
        public FormBuilder isEphemeral(boolean value) {
            this.ephemeral = value;
            return this;
        }

        /**
         * Sets the {@link FormResponse} to render paging components.
         */
        public FormBuilder isRenderingPagingComponents() {
            return this.isRenderingPagingComponents(true);
        }

        /**
         * Sets if the {@link FormResponse} should render paging components.
         *
         * @param value True if rendering page components.
         */
        public FormBuilder isRenderingPagingComponents(boolean value) {
            this.renderingPagingComponents = value;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link FormResponse} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public FormBuilder onCreate(@Nullable Function<MessageContext<MessageCreateEvent>, Mono<Void>> interaction) {
            return this.onCreate(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link FormResponse} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public FormBuilder onCreate(@NotNull Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction) {
            super.createInteraction = interaction;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link FormResponse} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public FormBuilder onSubmit(@Nullable Function<ButtonContext, Mono<Void>> interaction) {
            return this.onSubmit(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link FormResponse} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public FormBuilder onSubmit(@NotNull Optional<Function<ButtonContext, Mono<Void>>> interaction) {
            this.submitInteraction = interaction;
            return this;
        }

        /**
         * Specifies the allowed mentions for the {@link FormResponse}.
         *
         * @param allowedMentions An {@link AllowedMentions} object that defines which mentions should be allowed in the response.
         */
        public FormBuilder withAllowedMentions(@NotNull AllowedMentions allowedMentions) {
            super.allowedMentions = allowedMentions;
            return this;
        }

        /**
         * Adds an {@link Attachment} to the {@link FormResponse}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         */
        public FormBuilder withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds an {@link Attachment} to the {@link FormResponse}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         * @param spoiler True if the attachment should be a spoiler.
         */
        public FormBuilder withAttachment(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
            return this.withAttachments(
                Attachment.builder()
                    .isSpoiler(spoiler)
                    .withName(name)
                    .withStream(inputStream)
                    .build()
            );
        }

        /**
         * Add {@link Attachment Attachments} to the {@link FormResponse}.
         *
         * @param attachments Variable number of attachments to add.
         */
        public FormBuilder withAttachments(@NotNull Attachment... attachments) {
            Arrays.stream(attachments)
                .filter(attachment -> !super.attachments.contains(current -> current.getMediaData().getName(), attachment.getMediaData().getName()))
                .forEach(super.attachments::add);

            return this;
        }

        /**
         * Add {@link Attachment Attachments} to the {@link FormResponse}.
         *
         * @param attachments Collection of attachments to add.
         */
        public FormBuilder withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(attachment -> {
                if (!super.attachments.contains(current -> current.getMediaData().getName(), attachment.getMediaData().getName()))
                    super.attachments.add(attachment);
            });

            return this;
        }

        private FormBuilder withPageIndex(int currentPageIndex) {
            this.currentPageIndex = currentPageIndex;
            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link FormResponse}.
         *
         * @param throwable The throwable exception stack trace to add.
         */
        public FormBuilder withException(@NotNull Throwable throwable) {
            super.attachments.add(
                Attachment.builder()
                    .withName("stacktrace-%s.log", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("EST", ZoneId.SHORT_IDS)).format(Instant.now()))
                    .withStream(new ByteArrayInputStream(ExceptionUtil.getStackTrace(throwable).getBytes(StandardCharsets.UTF_8)))
                    .build()
            );

            return this;
        }

        /**
         * Add {@link File Files} to the {@link FormResponse}.
         *
         * @param files Variable number of files to add.
         */
        public FormBuilder withFiles(@NotNull File... files) {
            return this.withFiles(Arrays.asList(files));
        }

        /**
         * Add {@link File Files} to the {@link FormResponse}.
         *
         * @param files Collection of files to add.
         */
        public FormBuilder withFiles(@NotNull Iterable<File> files) {
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
                .forEach(this::withAttachments);

            return this;
        }

        /**
         * Add {@link Page Pages} to the {@link FormResponse}.
         *
         * @param pages Variable number of pages to add.
         */
        public FormBuilder withPages(@NotNull FormPage... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add {@link Page Pages} to the {@link FormResponse}.
         *
         * @param pages Collection of pages to add.
         */
        public FormBuilder withPages(@NotNull Iterable<FormPage> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        public FormBuilder withReactorScheduler(@NotNull Scheduler reactorScheduler) {
            this.reactorScheduler = reactorScheduler;
            return this;
        }

        public FormBuilder withReactorScheduler(@NotNull ExecutorService executorService) {
            this.reactorScheduler = Schedulers.fromExecutorService(executorService);
            return this;
        }

        /**
         * Sets the message the {@link FormResponse} should reply to.
         *
         * @param messageContext The message to reference.
         */
        public FormBuilder withReference(@NotNull MessageContext<?> messageContext) {
            return this.withReference(messageContext.getMessageId());
        }

        /**
         * Sets the message the {@link FormResponse} should reply to.
         *
         * @param messageId The message to reference.
         */
        public FormBuilder withReference(@Nullable Snowflake messageId) {
            return this.withReference(Optional.ofNullable(messageId));
        }

        /**
         * Sets the message the {@link FormResponse} should reply to.
         *
         * @param messageId The message to reference.
         */
        public FormBuilder withReference(@NotNull Mono<Snowflake> messageId) {
            return this.withReference(messageId.blockOptional());
        }

        /**
         * Sets the message the {@link FormResponse} should reply to.
         *
         * @param messageId The message to reference.
         */
        public FormBuilder withReference(@NotNull Optional<Snowflake> messageId) {
            super.referenceId = messageId;
            return this;
        }

        /**
         * Sets the time in seconds for the {@link FormResponse} to live in {@link DiscordBot#getResponseHandler()}.
         * <br><br>
         * This value moves whenever the user interacts with the {@link FormResponse}.
         * <br><br>
         * Maximum 500 seconds and Minimum is 5 seconds.
         *
         * @param timeToLive How long the response should live without interaction in seconds.
         */
        public FormBuilder withTimeToLive(int timeToLive) {
            super.timeToLive = NumberUtil.ensureRange(timeToLive, 5, 300);
            return this;
        }

        /**
         * Used to replace an existing message with a known ID.
         *
         * @param uniqueId Unique ID to assign to {@link FormResponse}.
         */
        public FormBuilder withUniqueId(@NotNull UUID uniqueId) {
            super.uniqueId = uniqueId;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link FormResponse}.
         */
        @Override
        public @NotNull FormResponse build() {
            Reflection.validateFlags(this);

            FormResponse response = new FormResponse(
                super.uniqueId,
                super.referenceId,
                super.reactorScheduler,
                super.allowedMentions,
                super.timeToLive,
                super.ephemeral,
                super.attachments,
                super.createInteraction.orElse(__ -> Mono.empty()),
                this.submitInteraction.orElse(__ -> Mono.empty()),
                super.renderingPagingComponents,
                IndexHistoryHandler.<FormPage, String>builder()
                    .withPages(super.pages.toUnmodifiableList())
                    .withMatcher((page, identifier) -> page.getOption().getValue().equals(identifier))
                    .withTransformer(page -> page.getOption().getValue())
                    .build()
            );

            // Current Page
            response.getHistoryHandler().gotoPage(this.currentPageIndex);

            return response;
        }

    }

}
