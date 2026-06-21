package com.twilio.agentconnect.channels;

import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.ChannelType;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Interface for communication channel implementations.
 */
public interface Channel {

    /**
     * Get the channel type.
     */
    ChannelType getChannelType();

    /**
     * Process an inbound message from this channel.
     *
     * @param message The inbound message
     * @param webhookParams Additional webhook parameters
     * @return Mono containing the message context
     */
    Mono<MessageContext> processInbound(
        InboundMessage message,
        Map<String, String> webhookParams
    );

    /**
     * Send an outbound message through this channel.
     *
     * @param conversationId The conversation ID
     * @param messageContent The message content
     * @return Mono containing the outbound message
     */
    Mono<OutboundMessage> sendMessage(
        String conversationId,
        String messageContent
    );

    /**
     * Validate Twilio signature for this channel.
     *
     * @param signature The signature from headers
     * @param url The request URL
     * @param params The request parameters
     * @return Mono<Boolean> - true if valid
     */
    Mono<Boolean> validateSignature(
        String signature,
        String url,
        Map<String, String> params
    );
}
