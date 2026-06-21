package com.twilio.agentconnect.tools.exporter;

import com.twilio.agentconnect.tools.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AnthropicToolExporter}.
 */
class AnthropicToolExporterTest {

    private final AnthropicToolExporter exporter = new AnthropicToolExporter();

    @SuppressWarnings("unchecked")
    @Test
    void exportsToolInAnthropicInputSchemaShape() {
        ToolDefinition.ToolParameter query = ToolDefinition.ToolParameter.builder()
            .name("query")
            .description("Search query")
            .type(String.class)
            .required(true)
            .injected(false)
            .build();

        ToolDefinition tool = ToolDefinition.builder()
            .name("searchKnowledge")
            .description("Search the knowledge base")
            .parameters(List.of(query))
            .build();

        List<Map<String, Object>> exported = exporter.export(List.of(tool));

        assertEquals(1, exported.size());
        Map<String, Object> entry = exported.get(0);

        // Anthropic format has top-level name/description and an "input_schema".
        assertEquals("searchKnowledge", entry.get("name"));
        assertEquals("Search the knowledge base", entry.get("description"));
        // No OpenAI-style wrappers.
        assertFalse(entry.containsKey("type"));
        assertFalse(entry.containsKey("function"));

        Map<String, Object> inputSchema = (Map<String, Object>) entry.get("input_schema");
        assertEquals("object", inputSchema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        Map<String, Object> querySchema = (Map<String, Object>) properties.get("query");
        assertEquals("string", querySchema.get("type"));
        assertEquals("Search query", querySchema.get("description"));

        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void injectedParametersAreExcludedFromInputSchema() {
        ToolDefinition.ToolParameter visible = ToolDefinition.ToolParameter.builder()
            .name("reason")
            .description("Reason")
            .type(String.class)
            .required(true)
            .injected(false)
            .build();

        ToolDefinition.ToolParameter injectedContext = ToolDefinition.ToolParameter.builder()
            .name("context")
            .type(Object.class)
            .injected(true)
            .build();

        ToolDefinition tool = ToolDefinition.builder()
            .name("handoff")
            .description("Escalate")
            .parameters(List.of(visible, injectedContext))
            .build();

        Map<String, Object> inputSchema =
            (Map<String, Object>) exporter.export(List.of(tool)).get(0).get("input_schema");
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

        assertTrue(properties.containsKey("reason"));
        assertFalse(properties.containsKey("context"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void optionalParameterOmittedFromRequiredList() {
        ToolDefinition.ToolParameter optional = ToolDefinition.ToolParameter.builder()
            .name("limit")
            .description("Max results")
            .type(Integer.class)
            .required(false)
            .injected(false)
            .build();

        ToolDefinition tool = ToolDefinition.builder()
            .name("search")
            .description("Search")
            .parameters(List.of(optional))
            .build();

        Map<String, Object> inputSchema =
            (Map<String, Object>) exporter.export(List.of(tool)).get(0).get("input_schema");

        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        Map<String, Object> limitSchema = (Map<String, Object>) properties.get("limit");
        assertEquals("integer", limitSchema.get("type"));

        assertFalse(inputSchema.containsKey("required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void jsonTypeMappingCoversPrimitivesCollectionsAndObjects() {
        ToolDefinition tool = ToolDefinition.builder()
            .name("typed")
            .description("Typed params")
            .parameters(List.of(
                param("aString", String.class),
                param("aInt", int.class),
                param("aFloat", float.class),
                param("aBooleanBoxed", Boolean.class),
                param("anArray", String[].class),
                param("anObject", ToolDefinition.class)
            ))
            .build();

        Map<String, Object> inputSchema =
            (Map<String, Object>) exporter.export(List.of(tool)).get(0).get("input_schema");
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

        assertEquals("string", ((Map<String, Object>) properties.get("aString")).get("type"));
        assertEquals("integer", ((Map<String, Object>) properties.get("aInt")).get("type"));
        assertEquals("number", ((Map<String, Object>) properties.get("aFloat")).get("type"));
        assertEquals("boolean", ((Map<String, Object>) properties.get("aBooleanBoxed")).get("type"));
        assertEquals("array", ((Map<String, Object>) properties.get("anArray")).get("type"));
        assertEquals("object", ((Map<String, Object>) properties.get("anObject")).get("type"));
    }

    @Test
    void emptyToolCollectionProducesEmptyList() {
        assertTrue(exporter.export(List.of()).isEmpty());
    }

    private static ToolDefinition.ToolParameter param(String name, Class<?> type) {
        return ToolDefinition.ToolParameter.builder()
            .name(name)
            .description(name)
            .type(type)
            .required(true)
            .injected(false)
            .build();
    }
}
