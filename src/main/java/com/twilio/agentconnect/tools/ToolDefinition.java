package com.twilio.agentconnect.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Definition of a tool including metadata and execution method.
 */
public class ToolDefinition {

    private String name;
    private String description;
    private List<ToolParameter> parameters;
    private Method method;
    private Object bean;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ToolParameter> getParameters() { return parameters; }
    public void setParameters(List<ToolParameter> parameters) { this.parameters = parameters; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public Object getBean() { return bean; }
    public void setBean(Object bean) { this.bean = bean; }

    // Builder
    public static ToolDefinitionBuilder builder() {
        return new ToolDefinitionBuilder();
    }

    public static class ToolDefinitionBuilder {
        private String name;
        private String description;
        private List<ToolParameter> parameters = new ArrayList<>();
        private Method method;
        private Object bean;

        public ToolDefinitionBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ToolDefinitionBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ToolDefinitionBuilder parameters(List<ToolParameter> parameters) {
            this.parameters = parameters;
            return this;
        }

        public ToolDefinitionBuilder method(Method method) {
            this.method = method;
            return this;
        }

        public ToolDefinitionBuilder bean(Object bean) {
            this.bean = bean;
            return this;
        }

        public ToolDefinition build() {
            ToolDefinition def = new ToolDefinition();
            def.name = this.name;
            def.description = this.description;
            def.parameters = this.parameters;
            def.method = this.method;
            def.bean = this.bean;
            return def;
        }
    }

    /**
     * Parameter definition
     */
    public static class ToolParameter {
        private String name;
        private String description;
        private Class<?> type;
        private boolean required;
        private boolean injected; // True for @InjectSession, @InjectContext

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Class<?> getType() { return type; }
        public void setType(Class<?> type) { this.type = type; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public boolean isInjected() { return injected; }
        public void setInjected(boolean injected) { this.injected = injected; }

        // Builder
        public static ToolParameterBuilder builder() {
            return new ToolParameterBuilder();
        }

        public static class ToolParameterBuilder {
            private String name;
            private String description;
            private Class<?> type;
            private boolean required = true;
            private boolean injected;

            public ToolParameterBuilder name(String name) {
                this.name = name;
                return this;
            }

            public ToolParameterBuilder description(String description) {
                this.description = description;
                return this;
            }

            public ToolParameterBuilder type(Class<?> type) {
                this.type = type;
                return this;
            }

            public ToolParameterBuilder required(boolean required) {
                this.required = required;
                return this;
            }

            public ToolParameterBuilder injected(boolean injected) {
                this.injected = injected;
                return this;
            }

            public ToolParameter build() {
                ToolParameter param = new ToolParameter();
                param.name = this.name;
                param.description = this.description;
                param.type = this.type;
                param.required = this.required;
                param.injected = this.injected;
                return param;
            }
        }
    }
}
