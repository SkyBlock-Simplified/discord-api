package dev.sbs.discordapi.response;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.util.SimplifiedException;
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
import dev.sbs.discordapi.response.page.Paging;
import dev.sbs.discordapi.response.page.handler.HistoryHandler;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.util.base.DiscordHelper;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionFollowupCreateSpec;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Response implements Paging<Page> {

    @Getter private final long buildTime = System.currentTimeMillis();
    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull ConcurrentList<Attachment> attachments;
    @Getter private final @NotNull Optional<Snowflake> referenceId;
    @Getter private final @NotNull Scheduler reactorScheduler;
    @Getter private final boolean replyMention;
    @Getter private final int timeToLive;
    @Getter private final boolean interactable;
    @Getter private final boolean renderingPagingComponents;
    @Getter private final boolean ephemeral;
    @Getter private final @NotNull HistoryHandler<Page, String> historyHandler;
    private ConcurrentList<LayoutComponent<ActionComponent>> cachedPageComponents = Concurrent.newUnmodifiableList();

    public static @NotNull Builder builder() {
        return new Builder();
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
            .append(this.isReplyMention(), response.isReplyMention())
            .append(this.getTimeToLive(), response.getTimeToLive())
            .append(this.isInteractable(), response.isInteractable())
            .append(this.isRenderingPagingComponents(), response.isRenderingPagingComponents())
            .append(this.isEphemeral(), response.isEphemeral())
            .append(this.getUniqueId(), response.getUniqueId())
            .append(this.getHistoryHandler(), response.getHistoryHandler())
            .append(this.getAttachments(), response.getAttachments())
            .append(this.getReferenceId(), response.getReferenceId())
            .append(this.getReactorScheduler(), response.getReactorScheduler())
            .build();
    }

    public static Builder from(@NotNull Response response) {
        return new Builder()
            .withUniqueId(response.getUniqueId())
            .withPages(response.getPages())
            .withAttachments(response.getAttachments())
            .withReference(response.getReferenceId())
            .withReactorScheduler(response.getReactorScheduler())
            .replyMention(response.isReplyMention())
            .withTimeToLive(response.getTimeToLive())
            .isInteractable(response.isInteractable())
            .isRenderingPagingComponents(response.isRenderingPagingComponents())
            .isEphemeral(response.isEphemeral());
    }

    public boolean isCacheUpdateRequired() {
        return this.getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getHistoryHandler().isCacheUpdateRequired();
    }

    public static @NotNull Response loader(@NotNull EventContext<?> context, boolean ephemeral, @Nullable String content, @NotNull Object... objects) {
        return loader(context, ephemeral, FormatUtil.formatNullable(content, objects));
    }

    public static @NotNull Response loader(@NotNull EventContext<?> context, boolean ephemeral, @NotNull Optional<String> content) {
        return builder()
            .isInteractable()
            .isEphemeral(ephemeral)
            .withReference(context)
            .withUniqueId(context.getResponseId())
            .withPages(
                Page.builder()
                    .withContent(
                        content.orElse(FormatUtil.format(
                            "{0}{1} is working...",
                            SimplifiedApi.getRepositoryOf(EmojiModel.class)
                                .findFirst(EmojiModel::getKey, "LOADING_RIPPLE")
                                .flatMap(Emoji::of)
                                .map(Emoji::asSpacedFormat)
                                .orElse(""),
                            context.getDiscordBot().getSelf().getUsername()
                        ))
                    )
                    .build()
            )
            .build();
    }

    public void setNoCacheUpdateRequired() {
        this.getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getHistoryHandler().setCacheUpdateRequired(false);
    }

    public final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> getCachedPageComponents() {
        if (this.isRenderingPagingComponents() && this.isCacheUpdateRequired()) {
            ConcurrentList<LayoutComponent<ActionComponent>> pageComponents = Concurrent.newList();

            // Page List
            if (ListUtil.sizeOf(this.getPages()) > 1 && !this.getHistoryHandler().hasPageHistory()) {
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
                        .build()
                        .updateSelected(this.getHistoryHandler().getHistoryIdentifiers().get(0))
                ));
            }

            // SubPage List
            if (ListUtil.notEmpty(this.getHistoryHandler().getCurrentPage().getPages()) || this.getHistoryHandler().hasPageHistory()) {
                SelectMenu.Builder subPageBuilder = SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE)
                    .withPlaceholder("Select a subpage.")
                    .withPlaceholderUsesSelectedOption();

                if (this.getHistoryHandler().hasPageHistory()) {
                    subPageBuilder.withOptions(
                        SelectMenu.Option.builder()
                            .withValue("BACK")
                            .withLabel("Back")
                            .withEmoji(Button.PageType.BACK.getEmoji())
                            .build()
                    );
                }

                Page subPage = ListUtil.notEmpty(this.getHistoryHandler().getCurrentPage().getPages()) ?
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

            if (this.getHistoryHandler().getCurrentPage().doesHaveItems()) {
                // Item List
                if (this.getHistoryHandler().getCurrentPage().getItemHandler().getTotalItemPages() > 1) {
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
                if (this.getHistoryHandler().getCurrentPage().getItemHandler().isViewerEnabled()) {
                    pageComponents.add(ActionRow.of(
                        SelectMenu.builder()
                            .withPageType(SelectMenu.PageType.ITEM)
                            .withPlaceholder("Select an item to view.")
                            .withOptions(
                                this.getHistoryHandler()
                                    .getCurrentPage()
                                    .getItemHandler()
                                    .getCachedItems()
                                    .stream()
                                    .map(Item::getOption)
                                    .collect(Concurrent.toList())
                            )
                            .build()
                    ));
                }
            }

            this.cachedPageComponents = pageComponents.toUnmodifiableList();
            this.updatePagingComponents();
        }

        return this.cachedPageComponents;
    }

    @Override
    public @NotNull ConcurrentList<Page> getPages() {
        return this.getHistoryHandler().getPages();
    }

    private void updatePagingComponents() {
        // Enabled
        this.editPageButton(Button::getPageType, Button.PageType.FIRST, buttonBuilder -> buttonBuilder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.PREVIOUS, buttonBuilder -> buttonBuilder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.NEXT, buttonBuilder -> buttonBuilder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.LAST, buttonBuilder -> buttonBuilder.setEnabled(this.getHistoryHandler().getCurrentPage().getItemHandler().hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.BACK, buttonBuilder -> buttonBuilder.setEnabled(this.getHistoryHandler().getCurrentPage().getHistoryHandler().hasPageHistory()));
        this.editPageButton(Button::getPageType, Button.PageType.SORT, buttonBuilder -> buttonBuilder.setEnabled(ListUtil.sizeOf(this.getHistoryHandler().getCurrentPage().getItemHandler().getSorters()) > 1));
        this.editPageButton(Button::getPageType, Button.PageType.ORDER, Button.ButtonBuilder::setEnabled);

        // Labels
        this.editPageButton(
            Button::getPageType,
            Button.PageType.INDEX,
            buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format(
                "{0} / {1}",
                this.getHistoryHandler()
                    .getCurrentPage()
                    .getItemHandler()
                    .getCurrentItemPage(),
                this.getHistoryHandler()
                    .getCurrentPage()
                    .getItemHandler()
                    .getTotalItemPages()
            ))
        );

        this.editPageButton(
            Button::getPageType,
            Button.PageType.SORT,
            buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format(
                "Sort: {0}", this.getHistoryHandler()
                    .getCurrentPage()
                    .getItemHandler()
                    .getCurrentSorter()
                    .map(sorter -> sorter.getOption().getLabel())
                    .orElse("N/A")
            ))
        );

        this.editPageButton(
            Button::getPageType,
            Button.PageType.ORDER,
            buttonBuilder -> buttonBuilder
                .withLabel(FormatUtil.format("Order: {0}", this.getHistoryHandler().getCurrentPage().getItemHandler().isReversed() ? "Reversed" : "Normal"))
                .withEmoji(DiscordHelper.getEmoji(FormatUtil.format("SORT_{0}", this.getHistoryHandler().getCurrentPage().getItemHandler().isReversed() ? "ASCENDING" : "DESCENDING")))
        );
    }

    public MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .content(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .allowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withContent(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withAllowedMentions(AllowedMentions.suppressEveryone().mutate().repliedUser(this.isReplyMention()).build())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .withFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    public MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .contentOrNull(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .addAllFiles(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .addAllEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return InteractionApplicationCommandCallbackSpec.builder()
            .content(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionFollowupCreateSpec getD4jInteractionFollowupCreateSpec() {
        return InteractionFollowupCreateSpec.builder()
            .content(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .username("Test")
            .build();
    }

    public InteractionReplyEditSpec getD4jInteractionReplyEditSpec() {
        return InteractionReplyEditSpec.builder()
            .contentOrNull(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .allowedMentionsOrNull(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .componentsOrNull(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embedsOrNull(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    private ConcurrentList<LayoutComponent<ActionComponent>> getCurrentComponents() {
        ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList();
        components.addAll(this.getCachedPageComponents()); // Paging Components
        components.addAll(this.getHistoryHandler().getCurrentPage().getComponents()); // Current Page Components
        return components;
    }

    private ConcurrentList<Embed> getCurrentEmbeds() {
        ConcurrentList<Embed> embeds = Concurrent.newList(this.getHistoryHandler().getCurrentPage().getEmbeds());

        // Handle Item List
        if (this.getHistoryHandler().getCurrentPage().doesHaveItems()) {
            embeds.add(
                Embed.builder()
                    .withFields(
                        this.getHistoryHandler()
                            .getCurrentPage()
                            .getItemHandler()
                            .getStyle()
                            .getPageItems(
                                this.getHistoryHandler()
                                    .getCurrentPage()
                                    .getItemHandler()
                                    .getColumnNames(),
                                this.getHistoryHandler()
                                    .getCurrentPage()
                                    .getItemHandler()
                                    .getCachedItems()
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
            .append(this.getHistoryHandler())
            .append(this.getPages())
            .append(this.getAttachments())
            .append(this.getReferenceId())
            .append(this.getReactorScheduler())
            .append(this.isReplyMention())
            .append(this.getTimeToLive())
            .append(this.isInteractable())
            .append(this.isRenderingPagingComponents())
            .append(this.isEphemeral())
            .build();
    }

    public Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Response> {

        private UUID uniqueId = UUID.randomUUID();
        private final ConcurrentList<Page> pages = Concurrent.newList();
        private final ConcurrentList<Attachment> attachments = Concurrent.newList();
        private boolean inlineItems;
        private Optional<Snowflake> referenceId = Optional.empty();
        private Scheduler reactorScheduler = Schedulers.boundedElastic();
        private boolean replyMention;
        private int timeToLive = 10;
        private boolean interactable = true;
        private boolean renderingPagingComponents = true;
        private boolean ephemeral = false;
        private Optional<String> defaultPage = Optional.empty();

        /**
         * Recursively clear all but preservable components from all {@link Page Pages} in {@link Response}.
         */
        public Builder clearAllComponents() {
            return this.clearAllComponents(true);
        }

        /**
         * Recursively clear all components from all {@link Page Pages} in {@link Response}.
         *
         * @param enforcePreserve True to leave preservable components.
         */
        public Builder clearAllComponents(boolean enforcePreserve) {
            this.pages.forEach(page -> this.editPage(page.mutate().clearComponents(true, enforcePreserve).build()));
            return this;
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        public Builder editPage(@NotNull Function<Page.Builder, Page.Builder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param index The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public Builder editPage(int index, @NotNull Function<Page.Builder, Page.Builder> pageBuilder) {
            if (index < this.pages.size())
                this.pages.set(index, pageBuilder.apply(this.pages.get(index).mutate()).build());

            return this;
        }

        /**
         * Updates an existing {@link Page}.
         *
         * @param page The page to edit.
         */
        public Builder editPage(@NotNull Page page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getOption().getValue().equals(page.getOption().getValue()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         * <br><br>
         * Only applies to slash commands.
         */
        public Builder isEphemeral() {
            return this.isEphemeral(true);
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         * <br><br>
         * Only applies to slash commands.
         *
         * @param value True if interactable.
         */
        public Builder isEphemeral(boolean value) {
            this.ephemeral = value;
            return this;
        }

        /**
         * Sets the {@link Response} as cacheable for interaction.
         */
        public Builder isInteractable() {
            return this.isInteractable(true);
        }

        /**
         * Sets if the {@link Response} is cached for interaction.
         *
         * @param value True if interactable.
         */
        public Builder isInteractable(boolean value) {
            this.interactable = value;
            return this;
        }

        /**
         * Sets the {@link Response} to render paging components.
         */
        public Builder isRenderingPagingComponents() {
            return this.isRenderingPagingComponents(true);
        }

        /**
         * Sets if the {@link Response} should render paging components.
         *
         * @param value True if rendering page components.
         */
        public Builder isRenderingPagingComponents(boolean value) {
            this.renderingPagingComponents = value;
            return this;
        }

        /**
         * Sets if the {@link Response} should mention the author of the specified {@link #withReference}.
         */
        public Builder replyMention() {
            return this.replyMention(true);
        }

        /**
         * Sets if the {@link Response} should mention the author of the specified {@link #withReference}.
         *
         * @param mention True to mention the user in the response.
         */
        public Builder replyMention(boolean mention) {
            this.replyMention = mention;
            return this;
        }

        /**
         * Adds a {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         */
        public Builder withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds a {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         * @param spoiler True if the attachment should be a spoiler.
         */
        public Builder withAttachment(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
            return this.withAttachments(Attachment.of(name, inputStream, spoiler));
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Variable number of attachments to add.
         */
        public Builder withAttachments(@NotNull Attachment... attachments) {
            return this.withAttachments(Arrays.asList(attachments));
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Collection of attachments to add.
         */
        public Builder withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(this.attachments::add);
            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link Response}.
         *
         * @param throwable The throwable exception stack trace to add.
         */
        public Builder withException(@NotNull Throwable throwable) {
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
        public Builder withDefaultPage(@Nullable String pageIdentifier) {
            return this.withDefaultPage(Optional.ofNullable(pageIdentifier));
        }

        /**
         * Sets the default page the {@link Response} should load.
         *
         * @param pageIdentifier The page identifier to load.
         */
        public Builder withDefaultPage(@NotNull Optional<String> pageIdentifier) {
            this.defaultPage = pageIdentifier;
            return this;
        }

        /**
         * Add {@link File Files} to the {@link Response}.
         *
         * @param files Variable number of files to add.
         */
        public Builder withFiles(@NotNull File... files) {
            return this.withFiles(Arrays.asList(files));
        }

        /**
         * Add {@link File Files} to the {@link Response}.
         *
         * @param files Collection of files to add.
         */
        public Builder withFiles(@NotNull Iterable<File> files) {
            List<File> fileList = List.class.isAssignableFrom(files.getClass()) ? (List<File>) files : StreamSupport.stream(files.spliterator(), false).toList();

            fileList.stream()
                .map(file -> {
                    try {
                        return Attachment.of(file.getName(), new FileInputStream(file));
                    } catch (FileNotFoundException fnfex) {
                        throw SimplifiedException.wrapNative(fnfex).build();
                    }
                })
                .forEach(this.attachments::add);

            return this;
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder withPages(@NotNull Page... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder withPages(@NotNull Iterable<Page> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        public Builder withReactorScheduler(@NotNull Scheduler reactorScheduler) {
            this.reactorScheduler = reactorScheduler;
            return this;
        }

        public Builder withReactorScheduler(@NotNull ExecutorService executorService) {
            this.reactorScheduler = Schedulers.fromExecutorService(executorService);
            return this;
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param eventContext The message to reference.
         */
        public Builder withReference(@NotNull EventContext<?> eventContext) {
            return this.withReference(eventContext instanceof TextCommandContext ? ((TextCommandContext) eventContext).getMessageId() : null);
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageContext The message to reference.
         */
        public Builder withReference(@NotNull MessageContext<?> messageContext) {
            return this.withReference(messageContext.getMessageId());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public Builder withReference(@Nullable Snowflake messageId) {
            return this.withReference(Optional.ofNullable(messageId));
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public Builder withReference(@NotNull Mono<Snowflake> messageId) {
            return this.withReference(messageId.blockOptional());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public Builder withReference(@NotNull Optional<Snowflake> messageId) {
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
        public Builder withTimeToLive(int secondsToLive) {
            this.timeToLive = NumberUtil.ensureRange(secondsToLive, 5, 300);
            return this;
        }

        /**
         * Used to replace an existing message with a known ID.
         *
         * @param uniqueId Unique ID to assign to {@link Response}.
         */
        public Builder withUniqueId(@NotNull UUID uniqueId) {
            this.uniqueId = uniqueId;
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
                this.attachments.toUnmodifiableList(),
                this.referenceId,
                this.reactorScheduler,
                this.replyMention,
                this.timeToLive,
                this.interactable,
                this.renderingPagingComponents,
                this.ephemeral,
                HistoryHandler.<Page, String>builder()
                    .withPages(this.pages.toUnmodifiableList())
                    .withHistoryMatcher((page, identifier) -> page.getOption().getValue().equals(identifier))
                    .withHistoryTransformer(page -> page.getOption().getValue())
                    .build()
            );

            // First Page
            if (this.defaultPage.isPresent() && response.getHistoryHandler().getPage(this.defaultPage.get()).isPresent())
                response.getHistoryHandler().gotoPage(this.defaultPage.get());
            else
                response.getHistoryHandler().gotoPage(response.getPages().get(0));

            return response;
        }

    }

}
