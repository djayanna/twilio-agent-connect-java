package com.twilio.agentconnect.tools.builtin;


import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.tools.InjectContext;
import com.twilio.agentconnect.tools.InjectSession;
import com.twilio.agentconnect.tools.TacTool;
import com.twilio.agentconnect.tools.TacToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in tool for escalating to human agent via Studio Flow.
 */
@Component
public class HandoffTool {

    private static final Logger log = LoggerFactory.getLogger(HandoffTool.class);

    private final TacConfiguration config;

    public HandoffTool(TacConfiguration config) {
        this.config = config;
    }

    @TacTool(description = "Escalate the conversation to a human agent")
    public Mono<String> handoffToHuman(
            @TacToolParam(description = "Reason for escalation") String reason,
            @InjectSession Session session,
            @InjectContext MessageContext context) {

        log.info("Handoff requested for conversation: {} - Reason: {}",
                context.getConversationId(), reason);

        // Build handoff payload
        Map<String, Object> handoffPayload = new HashMap<>();
        handoffPayload.put("conversationId", context.getConversationId());
        handoffPayload.put("profileId", context.getProfileId());
        handoffPayload.put("reason", reason);
        handoffPayload.put("channel", context.getChannelType().name());

        // Add memory context if available
        if (!context.getMemory().isEmpty()) {
            handoffPayload.put("customerTraits", context.getMemory().getTraits());
        }

        // Store handoff payload in session for voice channel to use
        session.getData().put("handoffPayload", handoffPayload);

        // In production, this would trigger Studio Flow execution
        // For now, we just log it
        log.info("Handoff payload prepared: {}", handoffPayload);

        return Mono.just("Conversation will be escalated to a human agent. Please hold.");
    }
}
