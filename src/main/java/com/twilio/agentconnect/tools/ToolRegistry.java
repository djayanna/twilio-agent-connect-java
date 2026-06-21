package com.twilio.agentconnect.tools;


import com.twilio.agentconnect.tools.exporter.AnthropicToolExporter;
import com.twilio.agentconnect.tools.exporter.OpenAiToolExporter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for discovering and managing tools.
 */
@Service
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ApplicationContext applicationContext;
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Discover tools on startup.
     */
    @PostConstruct
    public void discoverTools() {
        log.info("Discovering tools...");

        applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class)
            .values()
            .forEach(bean -> {
                Arrays.stream(bean.getClass().getMethods())
                    .filter(method -> method.isAnnotationPresent(TacTool.class))
                    .forEach(method -> registerTool(bean, method));
            });

        log.info("Discovered {} tools", tools.size());
    }

    /**
     * Register a tool from a method.
     */
    private void registerTool(Object bean, Method method) {
        TacTool annotation = method.getAnnotation(TacTool.class);

        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();

        ToolDefinition def = ToolDefinition.builder()
            .name(name)
            .description(annotation.description())
            .parameters(extractParameters(method))
            .method(method)
            .bean(bean)
            .build();

        tools.put(name, def);
        log.info("Registered tool: {}", name);
    }

    /**
     * Extract parameters from method.
     */
    private List<ToolDefinition.ToolParameter> extractParameters(Method method) {
        List<ToolDefinition.ToolParameter> params = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            // Check if parameter is injected
            boolean isInjected = param.isAnnotationPresent(InjectSession.class) ||
                               param.isAnnotationPresent(InjectContext.class);

            if (isInjected) {
                params.add(ToolDefinition.ToolParameter.builder()
                    .name(param.getName())
                    .type(param.getType())
                    .injected(true)
                    .build());
                continue;
            }

            // Regular parameter with @TacToolParam
            TacToolParam paramAnnotation = param.getAnnotation(TacToolParam.class);
            if (paramAnnotation != null) {
                String paramName = paramAnnotation.name().isEmpty() ?
                    param.getName() : paramAnnotation.name();

                params.add(ToolDefinition.ToolParameter.builder()
                    .name(paramName)
                    .description(paramAnnotation.description())
                    .type(param.getType())
                    .required(paramAnnotation.required())
                    .injected(false)
                    .build());
            }
        }

        return params;
    }

    /**
     * Get tool by name.
     */
    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Get all tools.
     */
    public Collection<ToolDefinition> getAllTools() {
        return tools.values();
    }

    /**
     * Export tools in specified format.
     */
    public List<Map<String, Object>> exportTools(ToolFormat format) {
        return switch (format) {
            case OPENAI -> new OpenAiToolExporter().export(tools.values());
            case ANTHROPIC -> new AnthropicToolExporter().export(tools.values());
        };
    }
}
