package dev.sbs.discordapi.debug.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.CommandId;
import dev.sbs.discordapi.command.impl.SlashCommand;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.ResponseContext;
import dev.sbs.discordapi.context.interaction.deferrable.application.slash.SlashCommandContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.util.exception.DiscordException;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@CommandId("75f1762a-4672-48db-83d8-86d953645d08")
public class DebugCommand extends SlashCommand {

    protected DebugCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Discord API Debugging";
    }

    @Override
    public long getGuildId() {
        return 652148034448261150L;
    }

    @Override
    public long getId() {
        return 0;
    }

    @NotNull
    @Override
    public String getName() {
        return "debug";
    }

    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            Response.builder()
                .replyMention()
                .withTimeToLive(30)
                .withPages(
                    Page.builder()
                        .withContent("test command")
                        .withReactions(Emoji.of("\uD83D\uDC80", reactionContext -> reactionContext.removeUserReaction()
                            .then(this.editPage(reactionContext, pageBuilder -> pageBuilder.withContent("reaction: " + reactionContext.getEmoji().asFormat()))))
                        )
                        .withEmbeds(
                            Embed.builder()
                                .withDescription("[Is this google?](https://google.com/)")
                                .build()
                        )
                        .withComponents(
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.PRIMARY)
                                    .withEmoji(Emoji.of(480079705077186560L, "chiefhmm"))
                                    .withLabel("Create Followup")
                                    .onInteract(context -> context.followup(
                                        "followuptest",
                                        Response.builder()
                                            .withPages(
                                                Page.builder()
                                                    .withEmbeds(
                                                        Embed.builder()
                                                            .withDescription("Hmm")
                                                            .withFooter("Herro")
                                                            .build()
                                                    )
                                                    .withComponents(ActionRow.of(
                                                        Button.builder()
                                                            .withStyle(Button.Style.SECONDARY)
                                                            .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                                            .withLabel("Edit Followup2")
                                                            .onInteract(context2 -> context2.editFollowup(
                                                                Response.builder()
                                                                    .withPages(
                                                                        Page.builder()
                                                                            .withEmbeds(
                                                                                Embed.builder()
                                                                                    .withDescription("Hmm")
                                                                                    .withFooter("Herro edit2")
                                                                                    .build()
                                                                            )
                                                                            .build()
                                                                    )
                                                                    .build()
                                                            ))
                                                            .build(),
                                                        Button.builder()
                                                            .withStyle(Button.Style.DANGER)
                                                            .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                                            .withLabel("Delete Followup2")
                                                            .onInteract(ResponseContext::deleteFollowup)
                                                            .build()
                                                    ))
                                                    .build()
                                            )
                                            .build()
                                    ))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.SECONDARY)
                                    .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                    .withLabel("Edit Followup")
                                    .onInteract(context -> context.editFollowup(
                                        "followuptest",
                                        Response.builder()
                                            .withPages(
                                                Page.builder()
                                                    .withEmbeds(
                                                        Embed.builder()
                                                            .withDescription("Hmm")
                                                            .withFooter("Herro edit")
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .build()
                                    ))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.DANGER)
                                    .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                    .withLabel("Delete Followup")
                                    .onInteract(context -> context.deleteFollowup("followuptest"))
                                    .build()
                            ),
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.PRIMARY)
                                    .withEmoji(Emoji.of("\uD83C\uDF85"))
                                    .withLabel("Santa")
                                    .withDeferEdit()
                                    .onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent("santa!")))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.SECONDARY)
                                    .withEmoji(Emoji.of("\uD83D\uDC31"))
                                    .withLabel("Cat")
                                    .onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent("cat!")))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.DANGER)
                                    .withLabel("Open Modal!")
                                    .onInteract(buttonContext -> buttonContext.presentModal(
                                        Modal.builder()
                                            .withTitle("Test title")
                                            .withComponents(
                                                ActionRow.of(
                                                    TextInput.builder()
                                                        .withStyle(TextInput.Style.SHORT)
                                                        .withIdentifier("something")
                                                        .withLabel("label")
                                                        .withPlaceholder("placeholder")
                                                        .withValue("value")
                                                        .isRequired(false)
                                                        .build()
                                                )
                                            )
                                            .onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent("modal: " + context.getComponent()
                                                .findComponent(TextInput.class, "something")
                                                .flatMap(TextInput::getValue)
                                                .orElse("hurdur i'm a failure")
                                            )))
                                            .build()
                                    ))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.SUCCESS)
                                    .withLabel("Success!")
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.LINK)
                                    .withUrl("https://google.com/")
                                    .withLabel("Google")
                                    .isPreserved()
                                    .build()
                            ),
                            ActionRow.of(
                                SelectMenu.builder()
                                    .withPlaceholder("Derpy menu")
                                    .withPlaceholderUsesSelectedOption()
                                    .withOptions(
                                        SelectMenu.Option.builder()
                                            .withLabel("Neigh")
                                            .withValue("value 1")
                                            .withEmoji(getEmoji("SKYBLOCK_ICON_HORSE"))
                                            .onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent(context.getOption().getValue())))
                                            .build(),
                                        SelectMenu.Option.builder()
                                            .withLabel("Buni")
                                            .withValue("value 2")
                                            .withDescription("Looking for ores!")
                                            .withEmoji(Emoji.of(769279331875946506L, "Buni", true))
                                            .onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent(context.getOption().getValue())))
                                            .build(),
                                        SelectMenu.Option.builder()
                                            .withLabel("Yes sir!")
                                            .withValue("value 3")
                                            .withEmoji(Emoji.of(837805777187241985L, "linasalute"))
                                            .onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent(context.getOption().getValue())))
                                            .build(),
                                        SelectMenu.Option.builder()
                                            .withLabel("I do nothing :)")
                                            .withValue("value 4")
                                            .withEmoji(Emoji.of(851662312925954068L, "goosewalk", true))
                                            .build()
                                    )
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
            );
    }

    @Override
    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder("test2", "test parameter", Parameter.Type.WORD)
                .build()
        );
    }

    private Mono<Void> editPage(ResponseContext<?> responseContext, Function<Page.Builder, Page.Builder> currentPage) {
        return responseContext.withResponseCacheEntry(entry -> entry.updateResponse(
            responseContext.getResponse()
                .mutate()
                .editPage(
                    currentPage.apply(
                        responseContext.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                    ).build()
                )
                .build()
        ));
    }

}
