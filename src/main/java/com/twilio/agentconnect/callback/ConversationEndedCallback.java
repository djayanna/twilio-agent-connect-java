package com.twilio.agentconnect.callback;

import com.twilio.agentconnect.session.Session;
import reactor.core.publisher.Mono;

/**
 * Callback interface for handling conversation end events.
 */
@FunctionalInterface
public interface ConversationEndedCallback {

    /**
     * Handle conversation end event.
     *
     * @param session The session that ended
     * @return A Mono signaling completion
     */
    Mono<Void> onConversationEnded(Session session);
}
