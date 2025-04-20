package dev.sbs.discordapi.debug.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.handler.cache.ItemHandler;
import dev.sbs.discordapi.response.page.item.AuthorItem;
import dev.sbs.discordapi.response.page.item.DescriptionItem;
import dev.sbs.discordapi.response.page.item.FooterItem;
import dev.sbs.discordapi.response.page.item.ImageUrlItem;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.ThumbnailUrlItem;
import dev.sbs.discordapi.response.page.item.TitleItem;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

@Structure(
    parent = @Structure.Parent(
        name = "debug",
        description = "Debugging Commands"
    ),
    name = "embed",
    guildId = 652148034448261150L,
    description = "Debug Embed Builder"
)
public class EmbedCommand extends DiscordCommand<SlashCommandContext> {

    protected EmbedCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            Response.builder()
                .withTimeToLive(60)
                .isEphemeral()
                .withPages(
                    Page.builder()
                        .withEmbeds(
                            Embed.builder()
                                .withTitle("Embed Management")
                                .withDescription("Manage your servers embeds.")
                                .build()
                        )
                        .withComponents(ActionRow.of(
                            Button.builder()
                                .withDeferEdit()
                                .withStyle(Button.Style.SUCCESS)
                                .withEmoji(Emoji.of(929249819061551115L, "math_plus_math"))
                                .withLabel("Create New")
                                .onInteract(createContext -> createContext.followup(
                                    "create_embed",
                                    Response.builder()
                                        .isEphemeral()
                                        .withPages(
                                            Page.builder()
                                                .withItemHandler(
                                                    ItemHandler.builder(Item.class)
                                                        .withStaticItems(
                                                            AuthorItem.builder().isEditable().build(),
                                                            TitleItem.builder().isEditable().build(),
                                                            ThumbnailUrlItem.builder().isEditable().build(),
                                                            DescriptionItem.builder().isEditable().build(),
                                                            ImageUrlItem.builder().isEditable().build(),
                                                            FooterItem.builder().isEditable().build()
                                                        )
                                                        .isEditorEnabled()
                                                        .build()
                                                )
                                                .build()
                                        )
                                        .build()
                                ))
                                .build()
                        ))
                        .build()
                )
                .build()
            );
    }

}
