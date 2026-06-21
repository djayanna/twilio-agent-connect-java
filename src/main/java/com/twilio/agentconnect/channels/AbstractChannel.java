package com.twilio.agentconnect.channels;

import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Abstract base implementation for channels.
 * Provides common functionality for message processing.
 */
public abstract class AbstractChannel implements Channel {

    protected final TwilioAgentConnect tac;
    protected final TwilioSignatureValidator signatureValidator;

    protected AbstractChannel(TwilioAgentConnect tac,
                             TwilioSignatureValidator signatureValidator) {
        this.tac = tac;
        this.signatureValidator = signatureValidator;
    }

    @Override
    public Mono<Boolean> validateSignature(
            String signature,
            String url,
            Map<String, String> params) {

        return Mono.fromCallable(() ->
            signatureValidator.validate(signature, url, params)
        );
    }

    /**
     * Build a message context from components.
     */
    protected MessageContext buildMessageContext(
            InboundMessage message,
            MemoryResponse memory,
            Session session) {

        return MessageContext.builder()
            .message(message)
            .memory(memory)
            .session(session)
            .conversationId(message.getConversationId())
            .profileId(session.getProfileId())
            .channelType(getChannelType())
            .build();
    }
}
