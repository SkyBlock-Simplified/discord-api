package dev.sbs.discordapi.response;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.ExceptionUtil;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.Paging;
import dev.sbs.discordapi.response.page.handler.cache.HistoryHandler;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.Id;
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Response implements Paging<Page> {

    private static Function<MessageContext<MessageCreateEvent>, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
    private final long buildTime = System.currentTimeMillis();
    private final @NotNull UUID uniqueId;
    private final @NotNull Optional<Snowflake> referenceId;
    private final @NotNull Scheduler reactorScheduler;
    private final int timeToLive;
    private final boolean renderingPagingComponents;
    private final boolean ephemeral;
    private final @NotNull HistoryHandler<Page, String> historyHandler;
    private final @NotNull ConcurrentList<Attachment> attachments;
    private final Function<MessageContext<MessageCreateEvent>, Mono<Void>> interaction;
    @Getter(AccessLevel.NONE)
    private ConcurrentList<LayoutComponent<ActionComponent>> cachedPageComponents = Concurrent.newUnmodifiableList();

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Updates an existing paging {@link Button}.
     *
     * @param buttonBuilder The button to edit.
     */
    private <S> void editPageButton(@NotNull Function<Button, S> function, S value, Function<Button.Builder, Button.Builder> buttonBuilder) {
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
            .append(this.getTimeToLive(), response.getTimeToLive())
            .append(this.getAttachments(), response.getAttachments())
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
            .withTimeToLive(response.getTimeToLive())
            .isRenderingPagingComponents(response.isRenderingPagingComponents())
            .isEphemeral(response.isEphemeral())
            .withPageHistory(response.getHistoryHandler().getHistoryIdentifiers())
            .withItemPage(response.getHistoryHandler().getCurrentPage().getItemHandler().getCurrentItemPage());
    }

    public boolean isCacheUpdateRequired() {
        return this.getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().getSortHandler().isCacheUpdateRequired();
    }

    public void setNoCacheUpdateRequired() {
        this.getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().getSortHandler().setCacheUpdateRequired(false);
    }

    public final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> getCachedPageComponents() {
        if (this.isRenderingPagingComponents() && this.isCacheUpdateRequired()) {
            ConcurrentList<LayoutComponent<ActionComponent>> pageComponents = Concurrent.newList();

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
                        .updateSelected(this.getHistoryHandler().getHistoryIdentifiers().get(0))
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
                            .withEmoji(DiscordReference.getEmoji("ARROW_LEFT"))
                            .build()
                    );
                }

                Page subPage = this.getHistoryHandler().getCurrentPage().getPages().notEmpty() ?
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
            this.updatePagingComponents();
        }

        return this.cachedPageComponents;
    }

    @Override
    public @NotNull ConcurrentList<Page> getPages() {
        return this.getHistoryHandler().getPages();
    }

    public void updateAttachments(@NotNull Message message) {
        if (this.attachments.contains(Attachment::notUploaded, true)) {
            message.getAttachments().forEach(d4jAttachment -> this.attachments.stream()
                .filter(Attachment::notUploaded)
                .filter(attachment -> attachment.getName().equals(d4jAttachment.getFilename()))
                .findFirst()
                .ifPresent(attachment -> this.attachments.set(
                    this.attachments.indexOf(attachment),
                    Attachment.of(d4jAttachment)
                )));
        }
    }

    private void updatePagingComponents() {
        // Enabled
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
                        .getCurrentItemPage(),
                    this.getHistoryHandler()
                        .getCurrentPage()
                        .getItemHandler()
                        .getTotalItemPages()
                )
        );

        /*this.editPageButton(
            Button::getPageType,
            Button.PageType.SORT,
            builder -> builder
                .withLabel(
                    "Sort: %s",
                    this.getHistoryHandler()
                        .getCurrentPage()
                        .getItemHandler()
                        .getSortHandler()
                        .getCurrent()
                        .map(sorter -> sorter.getOption().getLabel())
                        .orElse("N/A")
                )
        );

        this.editPageButton(
            Button::getPageType,
            Button.PageType.ORDER,
            builder -> builder.setEnabled()
                .withLabel(
                    "Order: %s",
                    this.getHistoryHandler()
                        .getCurrentPage()
                        .getItemHandler()
                        .getSortHandler()
                        .isReversed() ? "Reversed" : "Normal"
                )
                .withEmoji(
                    DiscordReference.getEmoji(
                        String.format("SORT_%s", this.getHistoryHandler()
                            .getCurrentPage()
                            .getItemHandler()
                            .getSortHandler()
                            .isReversed() ? "ASCENDING" : "DESCENDING")
                    )
                )
        );*/
    }

    public MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .nonce(this.getUniqueId().toString().substring(0, 25))
            .content(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .allowedMentions(AllowedMentions.suppressEveryone())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .files(this.getAttachments().stream().filter(Attachment::notUploaded).map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withNonce(this.getUniqueId().toString().substring(0, 25))
            .withContent(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .withAllowedMentions(AllowedMentions.suppressEveryone())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(this.getReferenceId().get()) : Possible.absent())
            .withFiles(this.getAttachments().stream().filter(Attachment::notUploaded).map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()));
    }

    public MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .contentOrNull(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .addAllFiles(this.getAttachments().stream().filter(Attachment::notUploaded).map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllComponents(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .addAllEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return InteractionApplicationCommandCallbackSpec.builder()
            .content(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().filter(Attachment::notUploaded).map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionFollowupCreateSpec getD4jInteractionFollowupCreateSpec() {
        return InteractionFollowupCreateSpec.builder()
            .content(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().filter(Attachment::notUploaded).map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(this.getCurrentComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .build();
    }

    public InteractionReplyEditSpec getD4jInteractionReplyEditSpec() {
        return InteractionReplyEditSpec.builder()
            .contentOrNull(this.getHistoryHandler().getCurrentPage().getContent().orElse(""))
            .allowedMentionsOrNull(AllowedMentions.suppressEveryone())
            .files(this.getAttachments().stream().filter(Attachment::notUploaded).map(Attachment::getD4jFile).collect(Concurrent.toList()))
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
        if (this.getHistoryHandler().getCurrentPage().hasItems()) {
            // Must Cache First
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

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUniqueId())
            .append(this.getHistoryHandler())
            .append(this.getPages())
            .append(this.getAttachments())
            .append(this.getReferenceId())
            .append(this.getReactorScheduler())
            .append(this.getTimeToLive())
            .append(this.isRenderingPagingComponents())
            .append(this.isEphemeral())
            .build();
    }

    public Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Response> {

        @BuildFlag(nonNull = true)
        private UUID uniqueId = UUID.randomUUID();
        @BuildFlag(nonNull = true)
        private final ConcurrentList<Page> pages = Concurrent.newList();
        private final ConcurrentList<Attachment> attachments = Concurrent.newList();
        private boolean inlineItems;
        private Optional<Snowflake> referenceId = Optional.empty();
        @BuildFlag(nonNull = true)
        private Scheduler reactorScheduler = Schedulers.boundedElastic();
        private int timeToLive = 10;
        private boolean renderingPagingComponents = true;
        private boolean ephemeral = false;
        private Optional<String> defaultPage = Optional.empty();
        private Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction = Optional.empty();

        // Current Page/Item History
        private ConcurrentList<String> pageHistory = Concurrent.newList();
        private int currentItemPage = 1;

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
         * Updates the current {@link Page}.
         *
         * @param builder The current page builder.
         */
        public Builder editCurrentPage(@NotNull Function<Page.Builder, Page.Builder> builder) {
            if (this.pageHistory.getLast().isEmpty())
                return this;

            return this.editPage(
                builder.apply(
                        this.pages.findFirst(page -> page.getOption().getValue(), this.pageHistory.getLast().get())
                            .orElseThrow()
                            .mutate()
                    )
                    .build()
            );
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
         * Sets the interaction to execute when the {@link Response} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public Builder onCreate(@Nullable Function<MessageContext<MessageCreateEvent>, Mono<Void>> interaction) {
            return this.onCreate(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Response} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public Builder onCreate(@NotNull Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Adds an {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         */
        public Builder withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds an {@link Attachment} to the {@link Response}.
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
            Arrays.stream(attachments)
                .filter(attachment -> !this.attachments.contains(Attachment::getName, attachment.getName()))
                .forEach(this.attachments::add);

            return this;
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Collection of attachments to add.
         */
        public Builder withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(attachment -> {
                if (!this.attachments.contains(Attachment::getName, attachment.getName()))
                    this.attachments.add(attachment);
            });

            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link Response}.
         *
         * @param throwable The throwable exception stack trace to add.
         */
        public Builder withException(@NotNull Throwable throwable) {
            this.attachments.add(Attachment.of(
                String.format("stacktrace-%s.log", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("EST", ZoneId.SHORT_IDS)).format(Instant.now())),
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
                .forEach(this::withAttachments);

            return this;
        }

        private Builder withItemPage(int currentItemPage) {
            this.currentItemPage = currentItemPage;
            return this;
        }

        private Builder withPageHistory(@NotNull ConcurrentList<String> pageHistory) {
            this.pageHistory = pageHistory;
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
        public @NotNull Response build() {
            Reflection.validateFlags(this);

            Response response = new Response(
                this.uniqueId,
                this.referenceId,
                this.reactorScheduler,
                this.timeToLive,
                this.renderingPagingComponents,
                this.ephemeral,
                HistoryHandler.<Page, String>builder()
                    .withPages(this.pages.toUnmodifiableList())
                    .withHistoryMatcher((page, identifier) -> page.getOption().getValue().equals(identifier))
                    .withHistoryTransformer(page -> page.getOption().getValue())
                    .build(),
                this.attachments,
                this.interaction.orElse(NOOP_HANDLER)
            );

            // First Page
            if (this.defaultPage.isPresent() && response.getHistoryHandler().getPage(this.defaultPage.get()).isPresent())
                response.getHistoryHandler().gotoPage(this.defaultPage.get());
            else {
                if (!this.pageHistory.isEmpty()) {
                    response.getHistoryHandler().gotoPage(this.pageHistory.removeFirst());
                    this.pageHistory.forEach(identifier -> response.getHistoryHandler().gotoSubPage(identifier));
                    response.getHistoryHandler().getCurrentPage().getItemHandler().gotoItemPage(this.currentItemPage);
                } else
                    response.getHistoryHandler().gotoPage(response.getPages().get(0));
            }

            return response;
        }

    }

    public final static class Cache extends ConcurrentList<Cache.Entry> {

        /**
         * Adds a {@link Response} and it's assigned {@link MessageChannel}, User ID and Message ID.
         *
         * @param channelId the discord channel id of the response
         * @param userId the user interacting with the response
         * @param messageId the discord response id of the response
         * @param response the response to cache
         */
        public Entry createAndGet(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            Entry entry = new Entry(channelId, userId, messageId, response);
            this.add(entry);
            return entry;
        }

        @Getter
        public static final class Entry extends BaseEntry {

            private final @NotNull ConcurrentList<Followup> followups = Concurrent.newList();
            private final @NotNull ConcurrentMap<Snowflake, Modal> activeModals = Concurrent.newMap();
            private long lastInteract = System.currentTimeMillis();
            private boolean busy;
            private boolean deferred;

            public Entry(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
                super(channelId, userId, messageId, response, response);
                this.busy = true;
                this.deferred = false;
            }

            public Mono<Followup> addFollowup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
                Followup followup = new Followup(identifier, channelId, userId, messageId, response);
                this.followups.add(followup);
                return Mono.just(followup);
            }

            public boolean containsFollowup(@NotNull String identifier) {
                return this.getFollowups()
                    .stream()
                    .anyMatch(followup -> followup.getIdentifier().equals(identifier));
            }

            public boolean containsFollowup(@NotNull Snowflake messageId) {
                return this.getFollowups()
                    .stream()
                    .anyMatch(followup -> followup.getMessageId().equals(messageId));
            }

            public Optional<Followup> findFollowup(@NotNull String identifier) {
                return this.getFollowups().findFirst(Followup::getIdentifier, identifier);
            }

            public Optional<Followup> findFollowup(@NotNull Snowflake messageId) {
                return this.getFollowups().findFirst(Followup::getMessageId, messageId);
            }

            public void removeFollowup(@NotNull String identifier) {
                this.followups.removeIf(followup -> followup.getIdentifier().equals(identifier));
            }

            public void clearModal(@NotNull User user) {
                this.activeModals.remove(user.getId());
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                if (!super.equals(o)) return false;

                Entry entry = (Entry) o;

                return new EqualsBuilder()
                    .append(this.getLastInteract(), entry.getLastInteract())
                    .append(this.isBusy(), entry.isBusy())
                    .append(this.isDeferred(), entry.isDeferred())
                    .append(this.getFollowups(), entry.getFollowups())
                    .append(this.getActiveModals(), entry.getActiveModals())
                    .build();
            }

            public @NotNull Optional<Modal> getUserModal(@NotNull User user) {
                return Optional.ofNullable(this.activeModals.getOrDefault(user.getId(), null));
            }

            @Override
            public int hashCode() {
                return new HashCodeBuilder()
                    .appendSuper(super.hashCode())
                    .append(this.getFollowups())
                    .append(this.getActiveModals())
                    .append(this.getLastInteract())
                    .append(this.isBusy())
                    .append(this.isDeferred())
                    .build();
            }

            public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Snowflake userId) {
                return this.getUserId().equals(userId) && (this.getMessageId().equals(messageId) || this.containsFollowup(messageId));
            }

            public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Id userId) {
                return this.getUserId().asLong() == userId.asLong() && (this.getMessageId().equals(messageId) || this.containsFollowup(messageId));
            }

            /**
             * Checks if this response is busy or not expired.
             * <br><br>
             * See {@link #getLastInteract()} and {@link Response#getTimeToLive()}.
             *
             * @return True if busy or not expired.
             */
            public boolean isActive() {
                return this.isBusy() || System.currentTimeMillis() < this.getLastInteract() + (this.getResponse().getTimeToLive() * 1000L);
            }

            @Override
            public boolean isFollowup() {
                return true;
            }

            public boolean notActive() {
                return !this.isActive();
            }

            /**
             * Sets this response as busy, preventing it from being removed from the {@link DiscordBot#getResponseCache()}.
             */
            public void setBusy() {
                this.busy = true;
            }

            public void setDeferred() {
                this.deferred = true;
            }

            public void setUserModal(@NotNull User user, @NotNull Modal modal) {
                this.activeModals.put(user.getId(), modal);
            }

            /**
             * Updates this response as not busy, allowing it to be later removed from the {@link DiscordBot#getResponseCache()}.
             */
            public Mono<Entry> updateLastInteract() {
                return Mono.fromRunnable(() -> {
                    super.processLastInteract();
                    this.lastInteract = System.currentTimeMillis();
                    this.busy = false;
                    this.deferred = false;
                });
            }

            public Mono<Entry> updateResponse(@NotNull Response response) {
                super.setUpdatedResponse(response);
                return Mono.just(this);
            }

        }

        @Getter
        public static class Followup extends BaseEntry {

            private final @NotNull String identifier;

            private Followup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
                super(channelId, userId, messageId, response, response);
                this.identifier = identifier;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                if (!super.equals(o)) return false;

                Followup followup = (Followup) o;

                return new EqualsBuilder()
                    .append(this.getIdentifier(), followup.getIdentifier())
                    .build();
            }

            @Override
            public int hashCode() {
                return new HashCodeBuilder()
                    .appendSuper(super.hashCode())
                    .append(this.getIdentifier())
                    .build();
            }

            @Override
            public boolean isFollowup() {
                return true;
            }

            public Mono<Followup> updateResponse(@NotNull Response response) {
                super.setUpdatedResponse(response);
                return Mono.just(this);
            }

        }

        @Getter
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static abstract class BaseEntry {

            private final @NotNull Snowflake channelId;
            private final @NotNull Snowflake userId;
            private final @NotNull Snowflake messageId;
            private @NotNull Response response;
            @Getter(AccessLevel.PROTECTED)
            private @NotNull Response currentResponse;

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                BaseEntry baseEntry = (BaseEntry) o;

                return new EqualsBuilder()
                    .append(this.getChannelId(), baseEntry.getChannelId())
                    .append(this.getUserId(), baseEntry.getUserId())
                    .append(this.getMessageId(), baseEntry.getMessageId())
                    .append(this.getResponse(), baseEntry.getResponse())
                    .append(this.getCurrentResponse(), baseEntry.getCurrentResponse())
                    .build();
            }

            @Override
            public int hashCode() {
                return new HashCodeBuilder()
                    .append(this.getChannelId())
                    .append(this.getUserId())
                    .append(this.getMessageId())
                    .append(this.getResponse())
                    .append(this.getCurrentResponse())
                    .build();
            }

            public abstract boolean isFollowup();

            public boolean isModified() {
                return !this.getCurrentResponse().equals(this.getResponse()) || this.getResponse().isCacheUpdateRequired();
            }

            protected void processLastInteract() {
                this.currentResponse = this.response;
                this.response.setNoCacheUpdateRequired();
            }

            protected void setUpdatedResponse(@NotNull Response response) {
                this.response = response;
            }

            public Mono<Entry> updateAttachments(@NotNull Message message) {
                return Mono.fromRunnable(() -> this.getResponse().updateAttachments(message));
            }

            public Mono<BaseEntry> updateReactions(@NotNull Message message) {
                return Mono.just(message)
                    .checkpoint("ResponseCache#updateReactions Processing")
                    .flatMap(msg -> {
                        // Update Reactions
                        ConcurrentList<Emoji> newReactions = this.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .getReactions();

                        // Current Reactions
                        ConcurrentList<Emoji> currentReactions = msg.getReactions()
                            .stream()
                            .filter(Reaction::selfReacted)
                            .map(Reaction::getEmoji)
                            .map(Emoji::of)
                            .collect(Concurrent.toList());

                        Mono<Void> mono = Mono.empty();

                        // Remove Existing Reactions
                        if (currentReactions.stream().anyMatch(messageEmoji -> !newReactions.contains(messageEmoji)))
                            mono = msg.removeAllReactions();

                        return mono.then(Mono.when(
                            newReactions.stream()
                                .map(emoji -> msg.addReaction(emoji.getD4jReaction()))
                                .collect(Concurrent.toList())
                        ));
                    })
                    .thenReturn(this);
            }

        }

    }
    
}
