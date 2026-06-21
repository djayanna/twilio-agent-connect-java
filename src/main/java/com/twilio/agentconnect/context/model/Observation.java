package com.twilio.agentconnect.context.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An observation from Conversation Memory (unstructured insight).
 *
 * <p>Field names mirror the Memory Recall API response
 * ({@code POST /v1/Stores/{storeId}/Profiles/{profileId}/Recall}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Observation {

    /**
     * Observation ID (Twilio Type ID format)
     */
    private String id;

    /**
     * Observation content
     */
    private String content;

    /**
     * Relevance score (higher is more relevant). Omitted when results are
     * returned in most-recent order rather than ranked by relevance.
     */
    private Double score;

    /**
     * The system that generated this observation
     */
    private String source;

    /**
     * When the observation was created
     */
    private Instant createdAt;

    /**
     * When the observation originally occurred
     */
    private Instant occurredAt;

    /**
     * Conversation IDs associated with this observation
     */
    private List<String> conversationIds = new ArrayList<>();

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

    public List<String> getConversationIds() {
        return conversationIds;
    }

    public void setConversationIds(List<String> conversationIds) {
        this.conversationIds = conversationIds;
    }
}
