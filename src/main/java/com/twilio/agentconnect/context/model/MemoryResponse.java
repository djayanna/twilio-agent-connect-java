package com.twilio.agentconnect.context.model;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response from Conversation Memory API containing profile and memory data.
 */
public class MemoryResponse {

    /**
     * Profile ID
     */
    private String profileId;

    /**
     * Customer traits (factual data)
     */
    private Map<String, Object> traits = new HashMap<>();

    /**
     * Observations (conversational memory)
     */
    private List<Observation> observations = new ArrayList<>();

    /**
     * Conversation summaries
     */
    private List<ConversationSummary> summaries = new ArrayList<>();

    /**
     * Communication history
     */
    private List<CommunicationHistory> communicationHistory = new ArrayList<>();

    /**
     * Create an empty memory response (for fallback scenarios)
     */
    public static MemoryResponse empty() {
        return MemoryResponse.builder().build();
    }

    /**
     * Check if this memory response is empty
     */
    public boolean isEmpty() {
        return (profileId == null || profileId.isEmpty()) &&
               traits.isEmpty() &&
               observations.isEmpty() &&
               summaries.isEmpty() &&
               communicationHistory.isEmpty();
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public Map<String, Object> getTraits() {
        return traits;
    }

    public void setTraits(Map<String, Object> traits) {
        this.traits = traits;
    }

    public List<Observation> getObservations() {
        return observations;
    }

    public void setObservations(List<Observation> observations) {
        this.observations = observations;
    }

    public List<ConversationSummary> getSummaries() {
        return summaries;
    }

    public void setSummaries(List<ConversationSummary> summaries) {
        this.summaries = summaries;
    }

    public List<CommunicationHistory> getCommunicationHistory() {
        return communicationHistory;
    }

    public void setCommunicationHistory(List<CommunicationHistory> communicationHistory) {
        this.communicationHistory = communicationHistory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String profileId;
        private Map<String, Object> traits = new HashMap<>();
        private List<Observation> observations = new ArrayList<>();
        private List<ConversationSummary> summaries = new ArrayList<>();
        private List<CommunicationHistory> communicationHistory = new ArrayList<>();

        public Builder profileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public Builder traits(Map<String, Object> traits) {
            this.traits = traits;
            return this;
        }

        public Builder observations(List<Observation> observations) {
            this.observations = observations;
            return this;
        }

        public Builder summaries(List<ConversationSummary> summaries) {
            this.summaries = summaries;
            return this;
        }

        public Builder communicationHistory(List<CommunicationHistory> communicationHistory) {
            this.communicationHistory = communicationHistory;
            return this;
        }

        public MemoryResponse build() {
            MemoryResponse response = new MemoryResponse();
            response.profileId = this.profileId;
            response.traits = this.traits;
            response.observations = this.observations;
            response.summaries = this.summaries;
            response.communicationHistory = this.communicationHistory;
            return response;
        }
    }
}
