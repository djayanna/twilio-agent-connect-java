package com.twilio.agentconnect.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tool parameters.
 * Marks parameters that should be included in tool schema.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TacToolParam {

    /**
     * Parameter name (defaults to parameter name)
     */
    String name() default "";

    /**
     * Parameter description (required)
     */
    String description();

    /**
     * Whether parameter is required
     */
    boolean required() default true;
}
