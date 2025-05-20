package dev.sbs.discordapi.response;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.ExceptionUtil;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.media.Attachment;
import dev.sbs.discordapi.response.component.media.MediaData;
import dev.sbs.discordapi.response.component.type.TopLevelComponent;
import dev.sbs.discordapi.response.handler.history.HistoryHandler;
import dev.sbs.discordapi.response.impl.FormResponse;
import dev.sbs.discordapi.response.impl.TreeResponse;
import dev.sbs.discordapi.response.page.Page;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.component.TopLevelMessageComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
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

public interface Response {

    @NotNull AllowedMentions getAllowedMentions();

    @NotNull ConcurrentList<Attachment> getAttachments();

    long getBuildTime();

    @NotNull ConcurrentList<TopLevelComponent> getCachedPageComponents();

    @NotNull HistoryHandler<Page, String> getHistoryHandler();

    @NotNull Function<MessageContext<MessageCreateEvent>, Mono<Void>> getInteraction();

    @NotNull Scheduler getReactorScheduler();

    @NotNull Optional<Snowflake> getReferenceId();

    int getTimeToLive();

    @NotNull UUID getUniqueId();

    boolean isEphemeral();

    boolean isRenderingPagingComponents();

    // Builders

    static @NotNull FormResponse.FormBuilder form() {
        return FormResponse.builder();
    }

    static @NotNull TreeResponse.TreeResponseBuilder builder() {
        return TreeResponse.builder();
    }

    <P extends Page> @NotNull Builder<P> mutate();

    // Reply Streams

    default @NotNull Stream<Attachment> getPendingAttachments() {
        return Stream.concat(
            this.getAttachments().stream(),
            this.getCurrentComponents()
                .flatMap(Component::flattenComponents)
                .filter(Attachment.class::isInstance)
                .map(Attachment.class::cast)
        ).filter(Attachment::isPendingUpload);
    }

    default @NotNull Stream<TopLevelComponent> getCurrentComponents() {
        return Stream.concat(this.getCachedPageComponents().stream(), this.getHistoryHandler().getCurrentPage().getComponents().stream());

        // Content TextDisplay
        //Optional<String> content = this.getHistoryHandler().getCurrentPage().getContent();

        //if (content.isPresent() && this.isComponentsV2())
        //    components.add(TextDisplay.of(content.get()));
    }

    default boolean isComponentsV2() {
        return this.getCurrentComponents()
            .flatMap(Component::flattenComponents)
            .anyMatch(component -> component.getType().isRequireFlag());
    }

    // Cache

    /**
     * Updates an existing paging {@link Button}.
     *
     * @param buttonBuilder The button to edit.
     */
    default <S> void editPageButton(@NotNull Function<Button, S> function, S value, Function<Button.Builder, Button.Builder> buttonBuilder) {
        this.getCachedPageComponents().forEach(topLevelComponent -> topLevelComponent.flattenComponents()
            .filter(LayoutComponent.class::isInstance)
            .map(LayoutComponent.class::cast)
            .forEach(layoutComponent -> layoutComponent.findComponent(Button.class, function, value)
                .ifPresent(button -> layoutComponent.getComponents().set(
                    layoutComponent.getComponents().indexOf(button),
                    buttonBuilder.apply(button.mutate()).build()
                ))
            )
        );
    }

    default boolean isCacheUpdateRequired() {
        return this.getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().isCacheUpdateRequired();
    }

    default void setNoCacheUpdateRequired() {
        this.getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().setCacheUpdateRequired(false);
    }

    default void updateAttachments(@NotNull Message message) {
        if (this.getAttachments().contains(attachment -> attachment.getMediaData().getState(), MediaData.State.LOADING)) {
            for (int i = 0; this.getAttachments().notEmpty(); i++) {
                Attachment attachment = this.getAttachments().get(i);
                final int index = i;

                // Update Attachment
                message.getAttachments()
                    .stream()
                    .filter(d4jAttachment -> d4jAttachment.getFilename().equals(attachment.getMediaData().getName()))
                    .findFirst()
                    .ifPresent(d4jAttachment -> this.getAttachments().set(index, attachment.mutate(d4jAttachment).build()));

                // Update File
                message.getComponentById(attachment.getMediaData().getComponentId())
                    .filter(discord4j.core.object.component.File.class::isInstance)
                    .map(discord4j.core.object.component.File.class::cast)
                    .ifPresent(d4jFile -> this.getAttachments().set(index, attachment.mutate(d4jFile).build()));
            }
        }
    }

    // D4J Specs

    default @NotNull MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .nonce(this.getUniqueId().toString().substring(0, 25))
            .allowedMentions(this.getAllowedMentions())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(MessageReferenceData.builder().messageId(this.getReferenceId().get().asLong()).build()) : Possible.absent())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(
                this.getCurrentComponents()
                    .map(Component::getD4jComponent)
                    .map(TopLevelMessageComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    default @NotNull MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withNonce(this.getUniqueId().toString().substring(0, 25))
            .withAllowedMentions(this.getAllowedMentions())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(MessageReferenceData.builder().messageId(this.getReferenceId().get().asLong()).build()) : Possible.absent())
            .withFiles(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withComponents(
                this.getCurrentComponents()
                    .map(Component::getD4jComponent)
                    .map(TopLevelMessageComponent.class::cast)
                    .collect(Concurrent.toList())
            );
    }

    default @NotNull MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .addAllFiles(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllComponents(
                this.getCurrentComponents()
                    .map(Component::getD4jComponent)
                    .map(TopLevelMessageComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    default @NotNull InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return InteractionApplicationCommandCallbackSpec.builder()
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(
                this.getCurrentComponents()
                    .map(Component::getD4jComponent)
                    .map(TopLevelMessageComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    default @NotNull InteractionFollowupCreateSpec getD4jInteractionFollowupCreateSpec() {
        return InteractionFollowupCreateSpec.builder()
            .ephemeral(this.isEphemeral())
            .allowedMentions(this.getAllowedMentions())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(
                this.getCurrentComponents()
                    .map(Component::getD4jComponent)
                    .map(TopLevelMessageComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    default @NotNull InteractionReplyEditSpec getD4jInteractionReplyEditSpec() {
        return InteractionReplyEditSpec.builder()
            .allowedMentionsOrNull(this.getAllowedMentions())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .componentsOrNull(
                this.getCurrentComponents()
                    .map(Component::getD4jComponent)
                    .map(TopLevelMessageComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    abstract class Builder<P extends Page> implements dev.sbs.api.util.builder.Builder<Response> {

        @BuildFlag(nonNull = true)
        protected UUID uniqueId = UUID.randomUUID();
        @BuildFlag(notEmpty = true)
        protected final ConcurrentList<P> pages = Concurrent.newList();
        protected final ConcurrentList<Attachment> attachments = Concurrent.newList();
        protected Optional<Snowflake> referenceId = Optional.empty();
        @BuildFlag(nonNull = true)
        protected Scheduler reactorScheduler = Schedulers.boundedElastic();
        protected int timeToLive = 10;
        protected boolean renderingPagingComponents = true;
        protected boolean ephemeral = false;
        @BuildFlag(nonNull = true)
        protected AllowedMentions allowedMentions = AllowedMentions.suppressEveryone();
        protected Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction = Optional.empty();

        /**
         * Recursively disable all interactable components from all {@link Page Pages} in {@link Response}.
         */
        public abstract Builder<P> disableAllComponents();

        /**
         * Updates an existing {@link Page}.
         *
         * @param page The page to edit.
         */
        public Builder<P> editPage(@NotNull P page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getOption().getValue().equals(page.getOption().getValue()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         */
        public Builder<P> isEphemeral() {
            return this.isEphemeral(true);
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         *
         * @param value True if ephemeral.
         */
        public Builder<P> isEphemeral(boolean value) {
            this.ephemeral = value;
            return this;
        }

        /**
         * Sets the {@link Response} to render paging components.
         */
        public Builder<P> isRenderingPagingComponents() {
            return this.isRenderingPagingComponents(true);
        }

        /**
         * Sets if the {@link Response} should render paging components.
         *
         * @param value True if rendering page components.
         */
        public Builder<P> isRenderingPagingComponents(boolean value) {
            this.renderingPagingComponents = value;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link Response} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public Builder<P> onCreate(@Nullable Function<MessageContext<MessageCreateEvent>, Mono<Void>> interaction) {
            return this.onCreate(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Response} is known to exist.
         *
         * @param interaction The interaction function.
         */
        public Builder<P> onCreate(@NotNull Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Specifies the allowed mentions for the {@link Response}.
         *
         * @param allowedMentions An {@link AllowedMentions} object that defines which mentions should be allowed in the response.
         */
        public Builder<P> withAllowedMentions(@NotNull AllowedMentions allowedMentions) {
            this.allowedMentions = allowedMentions;
            return this;
        }

        /**
         * Adds an {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         */
        public Builder<P> withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds an {@link Attachment} to the {@link Response}.
         *
         * @param name The name of the attachment.
         * @param inputStream The stream of attachment data.
         * @param spoiler True if the attachment should be a spoiler.
         */
        public Builder<P> withAttachment(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
            return this.withAttachments(
                Attachment.builder()
                    .isSpoiler(spoiler)
                    .withName(name)
                    .withStream(inputStream)
                    .build()
            );
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Variable number of attachments to add.
         */
        public Builder<P> withAttachments(@NotNull Attachment... attachments) {
            Arrays.stream(attachments)
                .filter(attachment -> !this.attachments.contains(current -> current.getMediaData().getName(), attachment.getMediaData().getName()))
                .forEach(this.attachments::add);

            return this;
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments Collection of attachments to add.
         */
        public Builder<P> withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(attachment -> {
                if (!this.attachments.contains(current -> current.getMediaData().getName(), attachment.getMediaData().getName()))
                    this.attachments.add(attachment);
            });

            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link Response}.
         *
         * @param throwable The throwable exception stack trace to add.
         */
        public Builder<P> withException(@NotNull Throwable throwable) {
            this.attachments.add(
                Attachment.builder()
                    .withName("stacktrace-%s.log", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("EST", ZoneId.SHORT_IDS)).format(Instant.now()))
                    .withStream(new ByteArrayInputStream(ExceptionUtil.getStackTrace(throwable).getBytes(StandardCharsets.UTF_8)))
                    .build()
            );

            return this;
        }

        /**
         * Add {@link File Files} to the {@link Response}.
         *
         * @param files Variable number of files to add.
         */
        public Builder<P> withFiles(@NotNull File... files) {
            return this.withFiles(Arrays.asList(files));
        }

        /**
         * Add {@link File Files} to the {@link Response}.
         *
         * @param files Collection of files to add.
         */
        public Builder<P> withFiles(@NotNull Iterable<File> files) {
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
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder<P> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder<P> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        public Builder<P> withReactorScheduler(@NotNull Scheduler reactorScheduler) {
            this.reactorScheduler = reactorScheduler;
            return this;
        }

        public Builder<P> withReactorScheduler(@NotNull ExecutorService executorService) {
            this.reactorScheduler = Schedulers.fromExecutorService(executorService);
            return this;
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageContext The message to reference.
         */
        public Builder<P> withReference(@NotNull MessageContext<?> messageContext) {
            return this.withReference(messageContext.getMessageId());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public Builder<P> withReference(@Nullable Snowflake messageId) {
            return this.withReference(Optional.ofNullable(messageId));
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public Builder<P> withReference(@NotNull Mono<Snowflake> messageId) {
            return this.withReference(messageId.blockOptional());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId The message to reference.
         */
        public Builder<P> withReference(@NotNull Optional<Snowflake> messageId) {
            this.referenceId = messageId;
            return this;
        }

        /**
         * Sets the time in seconds for the {@link Response} to live in {@link DiscordBot#getResponseHandler()}.
         * <br><br>
         * This value moves whenever the user interacts with the {@link Response}.
         * <br><br>
         * Maximum 500 seconds and Minimum is 5 seconds.
         *
         * @param timeToLive How long the response should live without interaction in seconds.
         */
        public Builder<P> withTimeToLive(int timeToLive) {
            this.timeToLive = NumberUtil.ensureRange(timeToLive, 5, 300);
            return this;
        }

        /**
         * Used to replace an existing message with a known ID.
         *
         * @param uniqueId Unique ID to assign to {@link Response}.
         */
        public Builder<P> withUniqueId(@NotNull UUID uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link Response}.
         */
        @Override
        public abstract @NotNull Response build();

    }

}
