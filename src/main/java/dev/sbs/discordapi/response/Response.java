package dev.sbs.discordapi.response;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ExceptionUtil;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.command.message.MessageCommandContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.context.message.interaction.ApplicationInteractionContext;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import dev.sbs.discordapi.response.component.action.Button;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.PageItem;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.AllowedMentions;
import lombok.Getter;
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

public class Response extends Page {

    @Getter protected final ConcurrentList<Page> pageHistory = Concurrent.newList(this);
    @Getter protected final long buildTime = System.currentTimeMillis();
    @Getter protected final Optional<String> content;
    @Getter protected final ConcurrentList<Attachment> attachments;
    @Getter protected final Optional<Snowflake> referenceId;
    @Getter protected final Scheduler reactorScheduler;
    @Getter protected final boolean replyMention;
    @Getter protected final int timeToLive;
    @Getter protected final boolean interactable;
    @Getter protected final boolean ephemeral;
    @Getter protected Button backButton = Button.PageType.BACK.build();

    private Response(
        UUID uniqueId,
        ConcurrentList<LayoutComponent<?>> components,
        ConcurrentList<Emoji> reactions,
        ConcurrentList<Embed> embeds,
        ConcurrentList<Page> pages,
        ConcurrentList<PageItem> items,
        Optional<SelectMenu.Option> option,
        boolean itemsInline,
        int itemsPerPage,
        Optional<String> content,
        ConcurrentList<Attachment> attachments,
        Optional<Snowflake> referenceId,
        Scheduler reactorScheduler,
        boolean replyMention,
        int timeToLive,
        boolean interactable,
        boolean ephemeral) {
        super(uniqueId, components, reactions, embeds, pages, items, option, itemsInline, itemsPerPage);
        this.content = content;
        this.attachments = attachments;
        this.referenceId = referenceId;
        this.reactorScheduler = reactorScheduler;
        this.replyMention = replyMention;
        this.timeToLive = timeToLive;
        this.interactable = interactable;
        this.ephemeral = ephemeral;
    }

    public static ResponseBuilder builder() {
        return new ResponseBuilder(UUID.randomUUID());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Response response = (Response) o;

        return new EqualsBuilder()
            .append(this.getBuildTime(), response.getBuildTime())
            .append(this.isReplyMention(), response.isReplyMention())
            .append(this.getTimeToLive(), response.getTimeToLive())
            .append(this.isInteractable(), response.isInteractable())
            .append(this.getContent(), response.getContent())
            .append(this.getAttachments(), response.getAttachments())
            .append(this.getReferenceId(), response.getReferenceId())
            .append(this.getReactorScheduler(), response.getReactorScheduler())
            .build();
    }

    public static ResponseBuilder from(Response response) {
        return new ResponseBuilder(response.getUniqueId())
            .withComponents(response.getComponents())
            .withReactions(response.getReactions())
            .withEmbeds(response.getEmbeds())
            .withPages(response.getPages())
            .withItems(response.getItems())
            .withOption(response.getOption())
            .withContent(response.getContent())
            .withAttachments(response.getAttachments())
            .withReference(response.getReferenceId())
            .withReactorScheduler(response.getReactorScheduler())
            .replyMention(response.isReplyMention())
            .withTimeToLive(response.getTimeToLive());
    }

    public MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .content(this.getContent().orElse(""))
            .allowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    public MessageCreateMono getD4jCreateMono(MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withContent(this.getContent().orElse(""))
            .withAllowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .withFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .withComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()));
    }

    public MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .contentOrNull(this.getContent().orElse(""))
            .addAllFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .addAllComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    public InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return this.getD4jComponentCallbackSpec(Optional.empty());
    }

    public InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec(ApplicationInteractionContext<?> interactionContext) {
        return this.getD4jComponentCallbackSpec(Optional.ofNullable(interactionContext));
    }

    private InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec(Optional<ApplicationInteractionContext<?>> interactionContext) {
        String mention = this.isReplyMention() ? Mono.justOrEmpty(interactionContext)
            .flatMap(EventContext::getInteractUser)
            .map(user -> FormatUtil.format("{0} ", user.getMention()))
            .blockOptional()
            .orElse("") : "";

        return InteractionApplicationCommandCallbackSpec.builder()
            .content(mention + this.getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    private ConcurrentList<LayoutComponent<?>> getCurrentComponents() {
        ConcurrentList<LayoutComponent<?>> components = Concurrent.newList();

        // Page Components
        components.addAll(this.getCurrentPage().getPageComponents());

        // Back Button
        if (ListUtil.notEmpty(this.getPages()))
            components.add(ActionRow.of(this.getBackButton()));

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
                                FormatUtil.format("{0}{1}", pageItem.getOption().getEmoji().map(Emoji::asSpacedFormat).orElse(""), pageItem.getOption().getLabel()),
                                pageItem.getOption().getDescription().orElse(""),
                                this.isItemsInline()
                            ))
                            .collect(Concurrent.toList())
                    )
                    .build()
            );
        }

        return embeds;
    }

    public final Page getCurrentPage() {
        return this.pageHistory.get(this.pageHistory.size() - 1);
    }

    public final ConcurrentList<String> getPageHistoryIdentifiers() {
        return Concurrent.newUnmodifiableList(this.pageHistory.stream().map(Page::getOption).flatMap(Optional::stream).map(SelectMenu.Option::getValue).collect(Concurrent.toList()));
    }

    public final Optional<Page> getPage(String identifier) {
        return this.getCurrentPage().getPages().stream().filter(page -> page.getOption().map(pageOption -> pageOption.getValue().equals(identifier)).orElse(false)).findFirst();
    }

    public final void gotoPage(String identifier) {
        this.pageHistory.add(this.getPage(identifier).orElseThrow(() -> SimplifiedException.of(DiscordException.class).withMessage("Unable to locate page identified by ''{0}''!", identifier).build()));
        this.backButton = this.backButton.mutate().setDisabled(false).build();
    }

    public final void gotoPreviousPage() {
        if (this.hasPageHistory()) {
            this.pageHistory.remove(this.pageHistory.size() - 1);
            this.backButton = this.backButton.mutate().setEnabled(this.hasPageHistory()).build();
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getBuildTime())
            .append(this.getContent())
            .append(this.getAttachments())
            .append(this.getReferenceId())
            .append(this.getReactorScheduler())
            .append(this.isReplyMention())
            .append(this.getTimeToLive())
            .append(this.isInteractable())
            .build();
    }

    public final boolean hasPageHistory() {
        return this.pageHistory.size() > 1;
    }

    public Response.ResponseBuilder mutate() {
        return from(this);
    }

    public static class ResponseBuilder extends ContentBuilder<Response> {

        private boolean inlineItems;
        private Optional<String> content = Optional.empty();
        private Optional<Snowflake> referenceId = Optional.empty();
        private final ConcurrentList<Attachment> attachments = Concurrent.newList();
        private Scheduler reactorScheduler = Schedulers.boundedElastic();
        private boolean replyMention;
        private int timeToLive = 10;
        private boolean interactable = true;
        private boolean ephemeral = false;

        protected ResponseBuilder(UUID uniqueId) {
            super(uniqueId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder clearComponents() {
            return this.clearComponents(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder clearComponents(boolean enforcePreserve) {
            super.clearComponents(enforcePreserve);
            return this;
        }

        /**
         * Clear all pages from {@link Response}.
         */
        @Override
        public ResponseBuilder clearPages() {
            super.clearPages();
            return this;
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        @Override
        public ResponseBuilder editPage(Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param index The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        @Override
        public ResponseBuilder editPage(int index, Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            super.editPage(index, pageBuilder);
            return this;
        }

        /**
         * Updates an existing {@link ActionComponent}.
         *
         * @param actionComponent The component to edit.
         */
        @Override
        public ResponseBuilder editComponent(@NotNull ActionComponent<?, ?> actionComponent) {
            super.editComponent(actionComponent);
            return this;
        }

        /**
         * Edits an existing {@link Embed}.
         *
         * @param uniqueId The unique id of the embed to search for.
         * @param embedBuilder The embed builder to edit with.
         */
        @Override
        public ResponseBuilder editEmbed(UUID uniqueId, Function<Embed.EmbedBuilder, Embed.EmbedBuilder> embedBuilder) {
            super.editEmbed(uniqueId, embedBuilder);
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
         * Add {@link LayoutComponent LayoutComponents} to the main {@link Page}.
         *
         * @param components Variable number of layout components to add.
         */
        @Override
        public ResponseBuilder withComponents(@NotNull LayoutComponent<?>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the main {@link Page}.
         *
         * @param components Collection of layout components to add.
         */
        @Override
        public ResponseBuilder withComponents(@NotNull Iterable<LayoutComponent<?>> components) {
            super.withComponents(components);
            return this;
        }

        /**
         * Sets the content text to add to the {@link Response}.
         *
         * @param content The text to add to the response.
         */
        public ResponseBuilder withContent(@Nullable String content) {
            return this.withContent(Optional.ofNullable(content));
        }

        /**
         * Sets the content text to add to the {@link Response}.
         *
         * @param content The text to add to the response.
         */
        public ResponseBuilder withContent(@NotNull Optional<String> content) {
            this.content = content;
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withEmbeds(@NotNull Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withEmbeds(@NotNull Iterable<Embed> embeds) {
            super.withEmbeds(embeds);
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
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withInlineItems() {
            return this.withInlineItems(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withInlineItems(boolean inlineItems) {
            super.withInlineItems(inlineItems);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withItems(@NotNull PageItem... pageItems) {
            return this.withItems(Arrays.asList(pageItems));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withItems(@NotNull Iterable<PageItem> pageItems) {
            super.withItems(pageItems);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withItemsPerPage(int itemsPerPage) {
            super.withItemsPerPage(itemsPerPage);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withOption(@Nullable SelectMenu.Option option) {
            return this.withOption(Optional.ofNullable(option));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withOption(@NotNull Optional<SelectMenu.Option> option) {
            super.withOption(option);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withPages(@NotNull Page... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withPages(@NotNull Iterable<Page> pages) {
            super.withPages(pages);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ResponseBuilder withReactions(@NotNull Iterable<Emoji> reactions) {
            super.withReactions(reactions);
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
         * @param commandContext The message to reference.
         */
        public ResponseBuilder withReference(@NotNull CommandContext<?> commandContext) {
            return this.withReference(commandContext instanceof MessageCommandContext ? ((MessageCommandContext) commandContext).getMessageId() : null);
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
            return new Response(
                this.uniqueId,
                this.components,
                Concurrent.newUnmodifiableList(this.reactions),
                Concurrent.newUnmodifiableList(this.embeds),
                Concurrent.newUnmodifiableList(this.pages),
                Concurrent.newUnmodifiableList(this.items),
                this.option,
                this.inlineItems,
                this.itemsPerPage,
                this.content,
                Concurrent.newUnmodifiableList(this.attachments),
                this.referenceId,
                this.reactorScheduler,
                this.replyMention,
                this.timeToLive,
                this.interactable,
                this.ephemeral
            );
        }

    }

}
