package dev.simplified.discordapi.harness.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.gateway.DispatchFactory;
import dev.simplified.discordapi.harness.gateway.SlashOption;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.InteractionData;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.discordjson.json.gateway.InteractionCreate;
import discord4j.discordjson.json.gateway.MessageCreate;
import discord4j.discordjson.json.gateway.Ready;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies each {@link DispatchFactory} builder fabricates the expected dispatch structure - interaction type,
 * command/component ids, options, resolved targets - so the wire shape matches what Discord4J's pipeline
 * decodes. Framework-free; asserts on the discord-json objects, not on bot behavior.
 */
class DispatchFactoryTest {

    private final HarnessConfig config = HarnessConfig.builder().build();
    private final HarnessEntities entities = new HarnessEntities(this.config);
    private final DispatchFactory factory = new DispatchFactory(this.config, this.entities);
    private final ObjectMapper mapper = this.entities.mapper();

    private InteractionData interaction(Dispatch dispatch) {
        return assertInstanceOf(InteractionCreate.class, dispatch).interaction();
    }

    private String json(InteractionData interaction) throws Exception {
        return this.mapper.writeValueAsString(interaction);
    }

    @Test
    void slash_command_is_a_type_2_chat_input() throws Exception {
        InteractionData interaction = this.interaction(this.factory.slashCommand("ping", SlashOption.text("colour", "red")));
        assertEquals(2, interaction.type());

        String json = this.json(interaction);
        assertTrue(json.contains("\"name\":\"ping\""), json);
        assertTrue(json.contains("\"name\":\"colour\"") && json.contains("\"value\":\"red\""), json);
    }

    @Test
    void subcommand_nests_group_and_sub() throws Exception {
        String json = this.json(this.interaction(this.factory.slashSubCommand("config", "user", "add")));
        assertTrue(json.contains("\"name\":\"config\""), json);
        assertTrue(json.contains("\"name\":\"user\""), json);
        assertTrue(json.contains("\"name\":\"add\""), json);
    }

    @Test
    void button_is_a_type_3_component_on_a_message() throws Exception {
        InteractionData interaction = this.interaction(this.factory.button(555L, "confirm"));
        assertEquals(3, interaction.type());

        String json = this.json(interaction);
        assertTrue(json.contains("\"custom_id\":\"confirm\""), json);
        assertTrue(json.contains("\"component_type\":2"), json);
        assertTrue(json.contains("\"id\":\"555\""), "the interaction should carry its source message; " + json);
    }

    @Test
    void select_menu_carries_values() throws Exception {
        InteractionData interaction = this.interaction(this.factory.selectMenu(555L, "pick", "a", "b"));
        assertEquals(3, interaction.type());

        String json = this.json(interaction);
        assertTrue(json.contains("\"component_type\":3"), json);
        assertTrue(json.contains("\"a\"") && json.contains("\"b\""), json);
    }

    @Test
    void modal_submit_is_a_type_5_with_a_text_input() throws Exception {
        InteractionData interaction = this.interaction(this.factory.modalSubmit(555L, "form", "field", "typed"));
        assertEquals(5, interaction.type());

        String json = this.json(interaction);
        assertTrue(json.contains("\"custom_id\":\"form\""), json);
        assertTrue(json.contains("\"custom_id\":\"field\"") && json.contains("\"value\":\"typed\""), json);
    }

    @Test
    void modal_submit_values_carries_radio_and_checkbox() throws Exception {
        String json = this.json(this.interaction(this.factory.modalSubmitValues(555L, "form", "radio", "one", "check", true)));
        assertTrue(json.contains("\"type\":21"), "radio group component; " + json);
        assertTrue(json.contains("\"type\":23"), "checkbox component; " + json);
        assertTrue(json.contains("\"value\":\"true\""), json);
    }

    @Test
    void user_command_resolves_the_target_user() throws Exception {
        InteractionData interaction = this.interaction(this.factory.userCommand("Report", 4242L));
        assertEquals(2, interaction.type());

        String json = this.json(interaction);
        assertTrue(json.contains("\"users\""), json);
        assertTrue(json.contains("4242"), json);
    }

    @Test
    void message_command_resolves_the_target_message() throws Exception {
        String json = this.json(this.interaction(this.factory.messageCommand("Pin", 7777L)));
        assertTrue(json.contains("\"messages\""), json);
        assertTrue(json.contains("7777"), json);
    }

    @Test
    void message_create_wraps_the_message_id() {
        Dispatch dispatch = this.factory.messageCreate(9090L);
        MessageCreate created = assertInstanceOf(MessageCreate.class, dispatch);
        assertEquals(9090L, created.message().id().asLong());
    }

    @Test
    void handshake_is_ready_then_connected() {
        List<Dispatch> handshake = this.factory.handshake(this.config.getBotId());
        assertEquals(2, handshake.size());
        assertInstanceOf(Ready.class, handshake.getFirst());
    }

}
