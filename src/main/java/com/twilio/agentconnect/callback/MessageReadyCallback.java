package com.twilio.agentconnect.callback;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import reactor.core.publisher.Mono;

/**
 * Callback interface for handling inbound messages.
 * Implement this to process messages and generate responses from your LLM.
 */
@FunctionalInterface
public interface MessageReadyCallback {

    /**
     * Process an inbound message and return a response.
     *
     * @param context The message context containing message, memory, and session
     * @return A Mono containing the outbound message response
     */
    Mono<OutboundMessage> onMessageReady(MessageContext context);
}
