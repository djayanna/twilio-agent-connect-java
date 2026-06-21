package com.twilio.agentconnect.tools;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ToolRegistry}.
 *
 * <p>Discovery is exercised by feeding a real (small) {@link AnnotationConfigApplicationContext}
 * containing a {@link Component @Component} bean with {@link TacTool @TacTool} methods, which is
 * exactly what the registry scans on startup.
 */
class ToolRegistryTest {

    /**
     * Test bean exposing a couple of tools. {@code @Component} so the registry's
     * {@code getBeansWithAnnotation(Component.class)} scan picks it up.
     */
    @Component
    static class SampleToolBean {

        @TacTool(description = "Greet a person by name")
        public String greet(@TacToolParam(description = "Name to greet") String name) {
            return "hello " + name;
        }

        @TacTool(name = "customNamed", description = "Tool with an explicit name")
        public String namedTool(
                @TacToolParam(description = "A required value") String value,
                @TacToolParam(description = "An optional count", required = false) Integer count,
                @InjectSession Session session,
                @InjectContext MessageContext context) {
            return value;
        }

        // No @TacTool annotation -> must NOT be registered.
        public String notATool() {
            return "ignored";
        }
    }

    private AnnotationConfigApplicationContext applicationContext;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(SampleToolBean.class);
        applicationContext.refresh();

        registry = new ToolRegistry(applicationContext);
        registry.discoverTools();
    }

    @AfterEach
    void tearDown() {
        applicationContext.close();
    }

    @Test
    void discoversAnnotatedToolsAndIgnoresPlainMethods() {
        // greet (defaults to method name) and customNamed (explicit name).
        assertTrue(registry.getTool("greet").isPresent());
        assertTrue(registry.getTool("customNamed").isPresent());
        // Plain method without @TacTool is not registered.
        assertFalse(registry.getTool("notATool").isPresent());
        assertFalse(registry.getTool("namedTool").isPresent());
    }

    @Test
    void toolDefinitionCapturesDescriptionMethodAndBean() {
        Optional<ToolDefinition> greet = registry.getTool("greet");
        assertTrue(greet.isPresent());

        ToolDefinition def = greet.get();
        assertEquals("greet", def.getName());
        assertEquals("Greet a person by name", def.getDescription());
        assertNotNull(def.getMethod());
        assertEquals("greet", def.getMethod().getName());
        assertNotNull(def.getBean());
        assertTrue(def.getBean() instanceof SampleToolBean);
    }

    @Test
    void extractsParametersAndMarksInjectedOnes() {
        ToolDefinition def = registry.getTool("customNamed").orElseThrow();

        // Two @TacToolParam params + two injected params = four total.
        assertEquals(4, def.getParameters().size());

        ToolDefinition.ToolParameter value = findParam(def, "value");
        assertTrue(value.isRequired());
        assertFalse(value.isInjected());
        assertEquals("A required value", value.getDescription());

        ToolDefinition.ToolParameter count = findParam(def, "count");
        assertFalse(count.isRequired());
        assertFalse(count.isInjected());

        // Injected params keep their reflected parameter name and are flagged injected.
        long injectedCount = def.getParameters().stream()
            .filter(ToolDefinition.ToolParameter::isInjected)
            .count();
        assertEquals(2, injectedCount);
    }

    @Test
    void getAllToolsReturnsEveryRegisteredTool() {
        assertEquals(2, registry.getAllTools().size());
    }

    @Test
    void exportToolsOpenAiFormatProducesFunctionEntries() {
        List<Map<String, Object>> exported = registry.exportTools(ToolFormat.OPENAI);

        assertEquals(2, exported.size());
        for (Map<String, Object> entry : exported) {
            assertEquals("function", entry.get("type"));
            assertTrue(entry.containsKey("function"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void exportToolsAnthropicFormatExcludesInjectedParameters() {
        List<Map<String, Object>> exported = registry.exportTools(ToolFormat.ANTHROPIC);

        Map<String, Object> customNamed = exported.stream()
            .filter(e -> "customNamed".equals(e.get("name")))
            .findFirst()
            .orElseThrow();

        assertTrue(customNamed.containsKey("input_schema"));
        Map<String, Object> inputSchema = (Map<String, Object>) customNamed.get("input_schema");
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

        // Visible @TacToolParam params present; injected ones absent.
        assertTrue(properties.containsKey("value"));
        assertTrue(properties.containsKey("count"));
        assertFalse(properties.containsKey("session"));
        assertFalse(properties.containsKey("context"));
    }

    private static ToolDefinition.ToolParameter findParam(ToolDefinition def, String name) {
        return def.getParameters().stream()
            .filter(p -> name.equals(p.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Parameter not found: " + name));
    }
}
