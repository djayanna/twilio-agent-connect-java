package com.twilio.agentconnect.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ToolDefinition} and its nested builders.
 */
class ToolDefinitionTest {

    // A real method used to exercise the method/bean fields.
    public String sampleMethod(String input) {
        return input;
    }

    @Test
    void builderPopulatesAllFields() throws NoSuchMethodException {
        Method method = ToolDefinitionTest.class.getMethod("sampleMethod", String.class);
        Object bean = this;

        ToolDefinition.ToolParameter param = ToolDefinition.ToolParameter.builder()
            .name("input")
            .description("an input value")
            .type(String.class)
            .required(true)
            .injected(false)
            .build();

        ToolDefinition def = ToolDefinition.builder()
            .name("sampleTool")
            .description("does something")
            .parameters(List.of(param))
            .method(method)
            .bean(bean)
            .build();

        assertEquals("sampleTool", def.getName());
        assertEquals("does something", def.getDescription());
        assertSame(method, def.getMethod());
        assertSame(bean, def.getBean());
        assertEquals(1, def.getParameters().size());
        assertSame(param, def.getParameters().get(0));
    }

    @Test
    void builderDefaultsParametersToEmptyList() {
        ToolDefinition def = ToolDefinition.builder()
            .name("noParams")
            .description("desc")
            .build();

        assertNotNull(def.getParameters());
        assertTrue(def.getParameters().isEmpty());
    }

    @Test
    void settersUpdateFields() {
        ToolDefinition def = new ToolDefinition();
        def.setName("renamed");
        def.setDescription("new description");

        assertEquals("renamed", def.getName());
        assertEquals("new description", def.getDescription());
    }

    @Test
    void toolParameterBuilderDefaultsRequiredToTrue() {
        ToolDefinition.ToolParameter param = ToolDefinition.ToolParameter.builder()
            .name("p")
            .type(Integer.class)
            .build();

        assertEquals("p", param.getName());
        assertSame(Integer.class, param.getType());
        assertTrue(param.isRequired());
        assertFalse(param.isInjected());
    }

    @Test
    void toolParameterSettersUpdateFields() {
        ToolDefinition.ToolParameter param = new ToolDefinition.ToolParameter();
        param.setName("session");
        param.setDescription("injected session");
        param.setType(Object.class);
        param.setRequired(false);
        param.setInjected(true);

        assertEquals("session", param.getName());
        assertEquals("injected session", param.getDescription());
        assertSame(Object.class, param.getType());
        assertFalse(param.isRequired());
        assertTrue(param.isInjected());
    }
}
