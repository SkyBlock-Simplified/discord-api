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
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.command.message.MessageCommandContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.component.action.Button;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.Paging;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class Response implements Paging {

    protected final ConcurrentList<Page> pageHistory = Concurrent.newList();
    @Getter protected final long buildTime = System.currentTimeMillis();
    @Getter protected final UUID uniqueId;
    @Getter protected final ConcurrentList<LayoutComponent<?>> pageComponents;
    @Getter protected final ConcurrentList<Page> pages;
    @Getter protected final ConcurrentList<Attachment> attachments;
    @Getter protected final Optional<Snowflake> referenceId;
    @Getter protected final Scheduler reactorScheduler;
    @Getter protected final boolean replyMention;
    @Getter protected final int timeToLive;
    @Getter protected final boolean interactable;
    @Getter protected final boolean loader;
    @Getter protected final boolean ephemeral;
    @Getter protected final boolean renderingPagingComponents;
    @Getter protected Button backButton = Button.PageType.BACK.build();

    private Response(
        UUID uniqueId,
        ConcurrentList<Page> pages,
        ConcurrentList<Attachment> attachments,
        Optional<Snowflake> referenceId,
        Scheduler reactorScheduler,
        boolean replyMention,
        int timeToLive,
        boolean interactable,
        boolean loader,
        boolean ephemeral,
        boolean renderingPagingComponents) {
        ConcurrentList<LayoutComponent<?>> pageComponents = Concurrent.newList();

        // Page List
        if (ListUtil.sizeOf(pages) > 1) {
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.PAGE)
                    .withPlaceholder("Select a page.")
                    .withOptions(
                        pages.stream()
                            .filter(page -> !page.isItemSelector() || ListUtil.notEmpty(page.getItems()))
                            .map(Page::getOption)
                            .flatMap(Optional::stream)
                            .collect(Concurrent.toList())
                    )
                    .build()
            ));
        }

        this.uniqueId = uniqueId;
        this.pages = pages;
        this.pageHistory.add(pages.get(0));
        this.pageComponents = Concurrent.newUnmodifiableList(pageComponents);
        this.attachments = attachments;
        this.referenceId = referenceId;
        this.reactorScheduler = reactorScheduler;
        this.replyMention = replyMention;
        this.timeToLive = timeToLive;
        this.interactable = interactable;
        this.loader = loader;
        this.ephemeral = ephemeral;
        this.renderingPagingComponents = renderingPagingComponents;
    }

    /*private static ConcurrentList<SelectMenu.Option> buildPagingSelectMenu(Paging paging, String parentNode, int depth) {
        ConcurrentList<SelectMenu.Option> options = Concurrent.newList();
        String concat = (StringUtil.repeat("", depth) + " ");

        paging.getPages().forEach(subPage -> subPage.getOption().ifPresent(option -> {
            String newParentNode = StringUtil.strip(FormatUtil.format("{0}.{1}", parentNode, option.getValue()), ".");

            options.add(
                option.mutate()
                    .withLabel(FormatUtil.format("{0} {1}", concat, option.getLabel()).trim())
                    .withValue(newParentNode)
                    .build()
            );

            options.addAll(buildPagingSelectMenu(subPage, newParentNode, depth + 1));
        }));

        return options;
    }*/

    public static ResponseBuilder builder() {
        return new ResponseBuilder(UUID.randomUUID());
    }

    public static ResponseBuilder builder(@NotNull EventContext<?> eventContext) {
        return new ResponseBuilder(eventContext.getUniqueId());
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
            .append(this.isEphemeral(), response.isEphemeral())
            .append(this.getUniqueId(), response.getUniqueId())
            .append(this.getPageHistory(), response.getPageHistory())
            .append(this.getPages(), response.getPages())
            .append(this.getAttachments(), response.getAttachments())
            .append(this.getReferenceId(), response.getReferenceId())
            .append(this.getReactorScheduler(), response.getReactorScheduler())
            .append(this.getBackButton(), response.getBackButton())
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
            .isEphemeral(response.isEphemeral());
    }

    public MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .content(this.getCurrentPage().getContent().orElse(""))
            .allowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    public MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withContent(this.getCurrentPage().getContent().orElse(""))
            .withAllowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .withFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .withComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()));
    }

    public MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .contentOrNull(this.getCurrentPage().getContent().orElse(""))
            .addAllFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .addAllComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    public InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return InteractionApplicationCommandCallbackSpec.builder()
            .content(this.getCurrentPage().getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    private ConcurrentList<LayoutComponent<?>> getCurrentComponents() {
        ConcurrentList<LayoutComponent<?>> components = Concurrent.newList();

        if (this.isRenderingPagingComponents()) {
            // Paging Components
            if (!this.hasPageHistory())
                components.addAll(this.getPageComponents());

            // Current Page Paging Components
            components.addAll(this.getCurrentPage().getPageComponents());

            // Paging Back Button
            if (this.hasPageHistory())
                components.add(ActionRow.of(this.getBackButton()));
        }

        // Current Page Components
        components.addAll(this.getCurrentPage().getComponents());

        return components;
    }

    private ConcurrentList<Embed> getCurrentEmbeds() {
        ConcurrentList<Embed> embeds = Concurrent.newList();
        embeds.addAll(this.getCurrentPage().getEmbeds()); // Handle Current Page

        // Handle Item List
        if (this.getCurrentPage().isItemSelector()) {
            embeds.add(
                Embed.builder()
                    .withFields(
                        this.getCurrentPage()
                            .getItems()
                            .stream()
                            .map(pageItem -> Field.of(
                                FormatUtil.format(
                                    "{0}{1}",
                                    pageItem.getOption()
                                        .getEmoji()
                                        .map(Emoji::asSpacedFormat)
                                        .orElse(""),
                                    pageItem.getOption().getLabel()
                                ),
                                pageItem.getOption().getDescription().orElse(""),
                                this.getCurrentPage().isItemsInline()
                            ))
                            .collect(Concurrent.toList())
                    )
                    .build()
            );
        }

        return embeds;
    }

    public final Page getCurrentPage() {
        return this.pageHistory.getLast();
    }

    public final ConcurrentList<String> getPageHistoryIdentifiers() {
        return Concurrent.newUnmodifiableList(
            this.pageHistory.stream()
                .map(Page::getOption)
                .flatMap(Optional::stream)
                .map(SelectMenu.Option::getValue)
                .collect(Concurrent.toList())
        );
    }

    /**
     * Gets a {@link Page} from the {@link Response}.
     *
     * @param identifier the page option value.
     */
    public final Optional<Page> getPage(String identifier) {
        return this.getPages()
            .stream()
            .filter(page -> page.getOption()
                .map(pageOption -> pageOption.getValue().equals(identifier))
                .orElse(false)
            )
            .findFirst();
    }

    public final ConcurrentList<Page> getPageHistory() {
        return Concurrent.newUnmodifiableList(this.pageHistory);
    }

    /**
     * Gets a {@link Page SubPage} from the {@link Page CurrentPage}.
     *
     * @param identifier the subpage option value.
     */
    public final Optional<Page> getSubPage(String identifier) {
        return this.getCurrentPage()
            .getPages()
            .stream()
            .filter(page -> page.getOption()
                .map(pageOption -> pageOption.getValue().equals(identifier))
                .orElse(false)
            )
            .findFirst();
    }

    /**
     * Changes the current {@link Page} to a top-level page in {@link Response} using the given identifier.
     *
     * @param identifier the page option value.
     */
    public final void gotoPage(String identifier) {
        this.pageHistory.clear();
        this.pageHistory.add(
            this.getPage(identifier).orElseThrow(
                () -> SimplifiedException.of(DiscordException.class)
                    .withMessage("Unable to locate page identified by ''{0}''!", identifier)
                    .build()
            )
        );

        this.backButton = this.backButton.mutate().setEnabled(this.hasPageHistory()).build();
    }

    /**
     * Changes the current {@link Page} to a subpage of the current {@link Page} using the given identifier.
     *
     * @param identifier the subpage option value.
     */
    public final void gotoSubPage(String identifier) {
        this.pageHistory.add(this.getSubPage(identifier).orElseThrow(
            () -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate subpage identified by ''{0}''!", identifier)
                .build()
        ));

        this.backButton = this.backButton.mutate().setEnabled(this.hasPageHistory()).build();
    }

    public final void gotoPreviousPage() {
        if (this.hasPageHistory()) {
            this.pageHistory.removeLast();
            this.backButton = this.backButton.mutate().setEnabled(this.hasPageHistory()).build();
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUniqueId())
            .append(this.getPageHistory())
            .append(this.getBuildTime())
            .append(this.getPages())
            .append(this.getAttachments())
            .append(this.getReferenceId())
            .append(this.getReactorScheduler())
            .append(this.isReplyMention())
            .append(this.getTimeToLive())
            .append(this.isInteractable())
            .append(this.isLoader())
            .append(this.isEphemeral())
            .append(this.getBackButton())
            .build();
    }

    public final boolean hasPageHistory() {
        return ListUtil.sizeOf(this.pageHistory) > 1;
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
        private boolean ephemeral = false;
        private boolean renderPagingComponents = true;

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
                .filter(existingPage -> existingPage.getUniqueId().equals(page.getUniqueId()))
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
         * Sets the {@link Response} as a loader to be edited in the future.
         */
        public ResponseBuilder isLoader() {
            return this.isLoader(true);
        }

        /**
         * Sets if the {@link Response} as a loader to be edited in the future.
         *
         * @param value True if loader.
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
         * @param value True if rendering components.
         */
        public ResponseBuilder isRenderingPagingComponents(boolean value) {
            this.renderPagingComponents = value;
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
            return this.withReference(eventContext instanceof MessageCommandContext ? ((MessageCommandContext) eventContext).getMessageId() : null);
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
            this.timeToLive = Math.max(5, Math.min(secondsToLive, 300));
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

            return new Response(
                this.uniqueId,
                Concurrent.newUnmodifiableList(this.pages),
                Concurrent.newUnmodifiableList(this.attachments),
                this.referenceId,
                this.reactorScheduler,
                this.replyMention,
                this.timeToLive,
                this.interactable,
                this.loader,
                this.ephemeral,
                this.renderPagingComponents
            );
        }

    }

}
