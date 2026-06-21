package com.twilio.agentconnect.channels.messaging;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.channels.AbstractChannel;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Abstract base for messaging channels (SMS, WhatsApp, RCS, Chat).
 * Provides idempotency handling.
 */
public abstract class MessagingChannel extends AbstractChannel {

    protected static final Logger log = LoggerFactory.getLogger(MessagingChannel.class);

    protected final IdempotencyCache idempotencyCache;

    public MessagingChannel(
            TwilioAgentConnect tac,
            TwilioSignatureValidator signatureValidator,
            IdempotencyCache idempotencyCache) {
        super(tac, signatureValidator);
        this.idempotencyCache = idempotencyCache;
    }

    @Override
    public Mono<MessageContext> processInbound(
            InboundMessage message,
            Map<String, String> webhookParams) {

        String idempotencyToken = webhookParams.get("i-twilio-idempotency-token");

        return idempotencyCache.checkAndSet(idempotencyToken)
            .filter(isNew -> isNew)
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Duplicate message detected, skipping processing");
                return Mono.empty();
            }))
            .flatMap(isNew -> tac.processInboundMessage(
                getChannelType(),
                message,
                webhookParams
            ));
    }

    @Override
    public Mono<OutboundMessage> sendMessage(String conversationId, String messageContent) {
        return tac.sendMessage(conversationId, messageContent, getChannelType());
    }
}
