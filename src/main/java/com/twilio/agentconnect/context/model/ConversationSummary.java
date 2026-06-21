package com.twilio.agentconnect.context.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * A conversation summary from Conversation Memory.
 *
 * <p>Field names mirror the Memory Recall API response
 * ({@code POST /v1/Stores/{storeId}/Profiles/{profileId}/Recall}), where the
 * summary text is returned under {@code content}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationSummary {

    /**
     * Summary ID (Twilio Type ID format)
     */
    private String id;

    /**
     * Summary text
     */
    private String content;

    /**
     * Relevance score (higher is more relevant). Omitted when results are
     * returned in most-recent order rather than ranked by relevance.
     */
    private Double score;

    /**
     * The system that generated this summary
     */
    private String source;

    /**
     * Source conversation ID
     */
    private String conversationId;

    /**
     * When the summary was created
     */
    private Instant createdAt;

    /**
     * When the summary originally occurred
     */
    private Instant occurredAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Convenience alias for {@link #getContent()} — the summary text.
     */
    public String getSummary() {
        return content;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
