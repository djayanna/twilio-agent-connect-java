package com.twilio.agentconnect.tools;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as TAC tools.
 * Tools are automatically discovered and registered by the ToolRegistry.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface TacTool {

    /**
     * Tool name (defaults to method name)
     */
    String name() default "";

    /**
     * Tool description (required)
     */
    String description();
}
