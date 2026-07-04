package dev.simplified.discordapi.harness.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.common.JacksonResources;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A structured view over the JSON body of an outbound message request (a reply, edit, or interaction
 * callback captured as a {@link RecordedRequest}).
 *
 * <p>
 * The REST mock is stateless and always returns a canned message, so tests cannot GET the rendered
 * message back - they assert against the body the bot sent. This parser walks the (possibly Components V2)
 * component tree once and exposes structured lookups by custom id, which is what makes chained assertions
 * precise and lets a test discover the random-UUID pagination/navigation ids from a render before clicking
 * them.
 */
public final class RenderedMessage {

    private static final @NotNull ObjectMapper MAPPER = JacksonResources.create().getObjectMapper();

    private final @NotNull JsonNode root;
    private final @NotNull List<JsonNode> components;

    private RenderedMessage(@NotNull JsonNode root) {
        this.root = root;
        this.components = new ArrayList<>();
        collectComponents(root, this.components);
    }

    /**
     * Parses the body of the given recorded request into a structured message view.
     *
     * <p>
     * An interaction callback (a {@code POST .../callback}, e.g. a type-7 message update from a component or
     * modal submit) nests the message under a {@code data} envelope, whereas a webhook reply edit
     * ({@code PATCH .../messages/@original}) carries the message fields at the top level. This unwraps the
     * {@code data} envelope when present so both shapes read the same.
     *
     * @param request the recorded outbound request
     * @return the parsed message
     * @throws IllegalArgumentException if the body is not valid JSON
     */
    public static @NotNull RenderedMessage of(@NotNull RecordedRequest request) {
        try {
            JsonNode parsed = MAPPER.readTree(request.body());
            JsonNode message = parsed.path("data").isObject() ? parsed.get("data") : parsed;
            return new RenderedMessage(message);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Request body is not JSON: " + request.body(), exception);
        }
    }

    /**
     * Whether a component with the given custom id is present anywhere in the tree.
     *
     * @param customId the component custom id
     * @return {@code true} if present
     */
    public boolean hasComponent(@NotNull String customId) {
        return this.find(customId).isPresent();
    }

    /**
     * Returns the component node with the given custom id.
     *
     * @param customId the component custom id
     * @return the component node
     * @throws AssertionError if no such component is present
     */
    public @NotNull JsonNode component(@NotNull String customId) {
        return this.find(customId).orElseThrow(() -> new AssertionError("no component with custom_id '" + customId + "' in " + this.root));
    }

    /**
     * Whether the component with the given custom id renders as disabled.
     *
     * @param customId the component custom id
     * @return {@code true} if the component's {@code disabled} flag is set
     */
    public boolean isDisabled(@NotNull String customId) {
        return this.find(customId).map(node -> node.path("disabled").asBoolean(false)).orElse(false);
    }

    /**
     * The label rendered on the component with the given custom id, if any.
     *
     * @param customId the component custom id
     * @return the label text, if present
     */
    public @NotNull Optional<String> label(@NotNull String customId) {
        return this.find(customId).map(node -> node.path("label")).filter(JsonNode::isTextual).map(JsonNode::asText);
    }

    /**
     * The placeholder rendered on the component with the given custom id, if any.
     *
     * @param customId the component custom id
     * @return the placeholder text, if present
     */
    public @NotNull Optional<String> placeholder(@NotNull String customId) {
        return this.find(customId).map(node -> node.path("placeholder")).filter(JsonNode::isTextual).map(JsonNode::asText);
    }

    /**
     * The selected values carried by the component with the given custom id (a select menu's defaults).
     *
     * @param customId the component custom id
     * @return the selected values, or an empty list
     */
    public @NotNull List<String> values(@NotNull String customId) {
        List<String> out = new ArrayList<>();
        this.find(customId)
            .map(node -> node.path("values"))
            .filter(JsonNode::isArray)
            .ifPresent(array -> array.forEach(value -> out.add(value.asText())));
        return out;
    }

    /**
     * The custom id of the first component rendering the given label. Useful for discovering the
     * random-UUID pagination buttons (Previous/Sort/Filter/Next) of a rendered response.
     *
     * @param label the exact button label
     * @return the matching custom id, if present
     */
    public @NotNull Optional<String> customIdByLabel(@NotNull String label) {
        return this.components.stream()
            .filter(node -> node.hasNonNull("custom_id") && node.path("label").isTextual() && node.get("label").asText().equals(label))
            .map(node -> node.get("custom_id").asText())
            .findFirst();
    }

    /**
     * The custom id of the first component rendering the given placeholder. Useful for discovering the
     * random-UUID page/subpage select menus of a rendered response.
     *
     * @param placeholder the exact placeholder text
     * @return the matching custom id, if present
     */
    public @NotNull Optional<String> customIdByPlaceholder(@NotNull String placeholder) {
        return this.components.stream()
            .filter(node -> node.hasNonNull("custom_id") && node.path("placeholder").isTextual() && node.get("placeholder").asText().equals(placeholder))
            .map(node -> node.get("custom_id").asText())
            .findFirst();
    }

    /**
     * The top-level custom id of the parsed body - for an interaction callback presenting a modal, this is the
     * modal's custom id (needed to target a modal submit).
     *
     * @return the root custom id, if present
     */
    public @NotNull Optional<String> rootCustomId() {
        return this.root.path("custom_id").isTextual() ? Optional.of(this.root.get("custom_id").asText()) : Optional.empty();
    }

    /**
     * The custom ids of every component of the given Discord component type, in tree order. Useful for
     * discovering the random-UUID pagination/navigation ids of a rendered response.
     *
     * @param type the Discord component type integer
     * @return the matching custom ids
     */
    public @NotNull List<String> customIdsByType(int type) {
        List<String> out = new ArrayList<>();

        for (JsonNode node : this.components) {
            if (node.path("type").asInt(-1) == type && node.hasNonNull("custom_id"))
                out.add(node.get("custom_id").asText());
        }

        return out;
    }

    /**
     * Whether the rendered message text (top-level content, plus every text display and component label)
     * contains the given substring.
     *
     * @param needle the substring to search for
     * @return {@code true} if the rendered text contains it
     */
    public boolean textContains(@NotNull String needle) {
        return this.collectText().contains(needle);
    }

    /** The raw parsed root node of the request body. */
    public @NotNull JsonNode root() {
        return this.root;
    }

    private @NotNull Optional<JsonNode> find(@NotNull String customId) {
        return this.components.stream()
            .filter(node -> node.hasNonNull("custom_id") && node.get("custom_id").asText().equals(customId))
            .findFirst();
    }

    private @NotNull String collectText() {
        StringBuilder builder = new StringBuilder();

        if (this.root.hasNonNull("content"))
            builder.append(this.root.get("content").asText()).append('\n');

        for (JsonNode node : this.components) {
            if (node.hasNonNull("content"))
                builder.append(node.get("content").asText()).append('\n');

            if (node.path("label").isTextual())
                builder.append(node.get("label").asText()).append('\n');
        }

        return builder.toString();
    }

    private static void collectComponents(JsonNode node, @NotNull List<JsonNode> out) {
        if (node == null || node.isMissingNode() || node.isNull())
            return;

        if (node.isArray()) {
            node.forEach(child -> collectComponents(child, out));
            return;
        }

        if (node.isObject()) {
            if (node.has("type"))
                out.add(node);

            collectComponents(node.get("components"), out);
            collectComponents(node.get("component"), out);
            collectComponents(node.get("accessory"), out);
        }
    }

}
