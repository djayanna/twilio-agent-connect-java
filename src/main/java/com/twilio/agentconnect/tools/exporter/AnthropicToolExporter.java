package com.twilio.agentconnect.tools.exporter;

import com.twilio.agentconnect.tools.ToolDefinition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports tools in Anthropic Claude tool use format.
 */
public class AnthropicToolExporter {

    public List<Map<String, Object>> export(Collection<ToolDefinition> tools) {
        return tools.stream()
            .map(this::exportTool)
            .collect(Collectors.toList());
    }

    private Map<String, Object> exportTool(ToolDefinition tool) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", tool.getName());
        result.put("description", tool.getDescription());

        // Build input schema
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        tool.getParameters().stream()
            .filter(p -> !p.isInjected())
            .forEach(param -> {
                Map<String, Object> paramSchema = new HashMap<>();
                paramSchema.put("type", getJsonType(param.getType()));
                paramSchema.put("description", param.getDescription());

                properties.put(param.getName(), paramSchema);

                if (param.isRequired()) {
                    required.add(param.getName());
                }
            });

        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }

        result.put("input_schema", inputSchema);

        return result;
    }

    private String getJsonType(Class<?> javaType) {
        if (javaType == String.class) {
            return "string";
        } else if (javaType == Integer.class || javaType == int.class ||
                   javaType == Long.class || javaType == long.class) {
            return "integer";
        } else if (javaType == Double.class || javaType == double.class ||
                   javaType == Float.class || javaType == float.class) {
            return "number";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        } else if (javaType.isArray() || Collection.class.isAssignableFrom(javaType)) {
            return "array";
        } else {
            return "object";
        }
    }
}
