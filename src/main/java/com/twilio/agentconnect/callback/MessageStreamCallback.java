package com.twilio.agentconnect.callback;

import com.twilio.agentconnect.context.model.MessageContext;
import reactor.core.publisher.Mono;

/**
 * Callback interface for handling inbound messages with a pushed, streamed response.
 *
 * <p>This mirrors the Python push pattern: the handler returns no value of its own
 * and instead pushes a token stream to the channel, e.g.
 *
 * <pre>{@code
 * tac.onMessageStream(context -> {
 *     Flux<String> tokens = streamFromLlm(context);            // token-by-token
 *     return voiceChannel.sendResponse(context.getConversationId(), tokens);
 * });
 * }</pre>
 *
 * <p>For the Voice channel each token is forwarded to ConversationRelay as it
 * arrives ({@code last:false}), so Twilio can begin speaking before the LLM has
 * finished generating. The returned {@link Mono} completes when the response has
 * been fully pushed.
 *
 * <p>When a stream callback is registered it takes precedence over a
 * {@link MessageReadyCallback} on streaming-capable channels (currently Voice).
 */
@FunctionalInterface
public interface MessageStreamCallback {

    /**
     * Process an inbound message and push a streamed response.
     *
     * @param context The message context containing message, memory, and session
     * @return A Mono that completes when the response has been pushed
     */
    Mono<Void> onMessageStream(MessageContext context);
}
