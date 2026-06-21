package com.twilio.agentconnect.tools.exporter;

import com.twilio.agentconnect.tools.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OpenAiToolExporter}.
 */
class OpenAiToolExporterTest {

    private final OpenAiToolExporter exporter = new OpenAiToolExporter();

    @SuppressWarnings("unchecked")
    @Test
    void exportsToolInOpenAiFunctionShape() {
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

        // OpenAI wraps everything under "type":"function" + "function" object.
        assertEquals("function", entry.get("type"));

        Map<String, Object> function = (Map<String, Object>) entry.get("function");
        assertEquals("searchKnowledge", function.get("name"));
        assertEquals("Search the knowledge base", function.get("description"));

        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
        assertEquals("object", parameters.get("type"));

        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        assertTrue(properties.containsKey("query"));

        Map<String, Object> querySchema = (Map<String, Object>) properties.get("query");
        assertEquals("string", querySchema.get("type"));
        assertEquals("Search query", querySchema.get("description"));

        List<String> required = (List<String>) parameters.get("required");
        assertTrue(required.contains("query"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void injectedParametersAreExcludedFromSchema() {
        ToolDefinition.ToolParameter visible = ToolDefinition.ToolParameter.builder()
            .name("reason")
            .description("Reason")
            .type(String.class)
            .required(true)
            .injected(false)
            .build();

        ToolDefinition.ToolParameter injectedSession = ToolDefinition.ToolParameter.builder()
            .name("session")
            .type(Object.class)
            .injected(true)
            .build();

        ToolDefinition tool = ToolDefinition.builder()
            .name("handoff")
            .description("Escalate")
            .parameters(List.of(visible, injectedSession))
            .build();

        Map<String, Object> function =
            (Map<String, Object>) exporter.export(List.of(tool)).get(0).get("function");
        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");

        assertTrue(properties.containsKey("reason"));
        assertFalse(properties.containsKey("session"));
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

        Map<String, Object> function =
            (Map<String, Object>) exporter.export(List.of(tool)).get(0).get("function");
        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");

        // Integer maps to JSON "integer".
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        Map<String, Object> limitSchema = (Map<String, Object>) properties.get("limit");
        assertEquals("integer", limitSchema.get("type"));

        // No required entries means the "required" key is omitted entirely.
        assertFalse(parameters.containsKey("required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void jsonTypeMappingCoversPrimitivesCollectionsAndObjects() {
        ToolDefinition tool = ToolDefinition.builder()
            .name("typed")
            .description("Typed params")
            .parameters(List.of(
                param("aString", String.class),
                param("aBoolean", boolean.class),
                param("aDouble", double.class),
                param("aLong", Long.class),
                param("aList", List.class),
                param("anObject", ToolDefinition.class)
            ))
            .build();

        Map<String, Object> function =
            (Map<String, Object>) exporter.export(List.of(tool)).get(0).get("function");
        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");

        assertEquals("string", ((Map<String, Object>) properties.get("aString")).get("type"));
        assertEquals("boolean", ((Map<String, Object>) properties.get("aBoolean")).get("type"));
        assertEquals("number", ((Map<String, Object>) properties.get("aDouble")).get("type"));
        assertEquals("integer", ((Map<String, Object>) properties.get("aLong")).get("type"));
        assertEquals("array", ((Map<String, Object>) properties.get("aList")).get("type"));
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
