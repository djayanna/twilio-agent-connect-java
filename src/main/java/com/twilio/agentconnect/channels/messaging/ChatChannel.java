package com.twilio.agentconnect.channels.messaging;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.springframework.stereotype.Component;

/**
 * Chat channel implementation.
 */
@Component
public class ChatChannel extends MessagingChannel {

    public ChatChannel(
            TwilioAgentConnect tac,
            TwilioSignatureValidator signatureValidator,
            IdempotencyCache idempotencyCache) {
        super(tac, signatureValidator, idempotencyCache);
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.CHAT;
    }
}
