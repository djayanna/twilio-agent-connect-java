package com.twilio.agentconnect.channels.messaging;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.springframework.stereotype.Component;

/**
 * WhatsApp channel implementation.
 */
@Component
public class WhatsAppChannel extends MessagingChannel {

    public WhatsAppChannel(
            TwilioAgentConnect tac,
            TwilioSignatureValidator signatureValidator,
            IdempotencyCache idempotencyCache) {
        super(tac, signatureValidator, idempotencyCache);
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WHATSAPP;
    }
}
