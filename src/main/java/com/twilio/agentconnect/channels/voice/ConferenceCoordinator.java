package com.twilio.agentconnect.channels.voice;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for human-handoff briefing context.
 *
 * <p>When AI #1 escalates a call, it stashes a {@link BriefingContext} (the
 * caller's CallSid, conference name, summary of the conversation so far, and
 * caller phone number). The briefing AI session — started on the human agent's
 * outbound leg — looks the context up by {@code ctxId} from the TwiML
 * {@code <Parameter>} on its ConversationRelay verb.
 *
 * <p>Single-instance only; if running multiple replicas, swap for a Redis-backed
 * implementation.
 */
@Component
public class ConferenceCoordinator {

    private final Map<String, BriefingContext> contexts = new ConcurrentHashMap<>();

    /** Store a context and return its lookup id. */
    public String store(BriefingContext ctx) {
        String id = "ctx_" + UUID.randomUUID();
        contexts.put(id, ctx);
        return id;
    }

    /** Look up a context by id (without removing it). */
    public BriefingContext get(String ctxId) {
        return contexts.get(ctxId);
    }

    /** Remove a context, e.g. once the bridge happens or the call ends. */
    public BriefingContext remove(String ctxId) {
        return contexts.remove(ctxId);
    }

    /**
     * Briefing payload kept while the caller is on hold and the human agent is
     * being briefed by AI #2.
     */
    public record BriefingContext(
        String callerCallSid,
        String conferenceName,
        String callerNumber,
        String reason,
        String summary
    ) {}
}
