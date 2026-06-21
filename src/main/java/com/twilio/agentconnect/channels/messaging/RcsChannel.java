package com.twilio.agentconnect.channels.messaging;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RCS channel implementation.
 */
@Component
@ConditionalOnProperty(prefix = "twilio.agent-connect.channels", name = "rcs", havingValue = "true")
public class RcsChannel extends MessagingChannel {

    public RcsChannel(
            TwilioAgentConnect tac,
            TwilioSignatureValidator signatureValidator,
            IdempotencyCache idempotencyCache) {
        super(tac, signatureValidator, idempotencyCache);
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.RCS;
    }
}
