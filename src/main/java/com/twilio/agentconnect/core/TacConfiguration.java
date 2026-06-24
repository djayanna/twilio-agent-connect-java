package com.twilio.agentconnect.core;


import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for Twilio Agent Connect.
 */
@Validated
@ConfigurationProperties(prefix = "twilio.agent-connect")
public class TacConfiguration {

    /**
     * Twilio Account SID
     */
    @NotBlank
    private String accountSid;

    /**
     * Twilio Auth Token
     */
    @NotBlank
    private String authToken;

    /**
     * Twilio API Key
     */
    @NotBlank
    private String apiKey;

    /**
     * Twilio API Secret
     */
    @NotBlank
    private String apiSecret;

    /**
     * Conversation Configuration ID
     */
    @NotBlank
    private String conversationConfigurationId;

    /**
     * Twilio phone number
     */
    @NotBlank
    private String phoneNumber;

    /**
     * Public domain for voice webhooks
     */
    private String voicePublicDomain;

    /**
     * Memory configuration
     */
    private MemoryConfig memory = new MemoryConfig();

    /**
     * Idempotency configuration
     */
    private IdempotencyConfig idempotency = new IdempotencyConfig();

    /**
     * Cache configuration
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * Resilience configuration
     */
    private ResilienceConfig resilience = new ResilienceConfig();

    /**
     * Voice configuration
     */
    private VoiceConfig voice = new VoiceConfig();

    /**
     * Channels configuration
     */
    private ChannelsConfig channels = new ChannelsConfig();

    public String getAccountSid() {
        return accountSid;
    }

    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getConversationConfigurationId() {
        return conversationConfigurationId;
    }

    public void setConversationConfigurationId(String conversationConfigurationId) {
        this.conversationConfigurationId = conversationConfigurationId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getVoicePublicDomain() {
        return voicePublicDomain;
    }

    public void setVoicePublicDomain(String voicePublicDomain) {
        this.voicePublicDomain = voicePublicDomain;
    }

    public MemoryConfig getMemory() {
        return memory;
    }

    public void setMemory(MemoryConfig memory) {
        this.memory = memory;
    }

    public IdempotencyConfig getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(IdempotencyConfig idempotency) {
        this.idempotency = idempotency;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public ResilienceConfig getResilience() {
        return resilience;
    }

    public void setResilience(ResilienceConfig resilience) {
        this.resilience = resilience;
    }

    public VoiceConfig getVoice() {
        return voice;
    }

    public void setVoice(VoiceConfig voice) {
        this.voice = voice;
    }

    public ChannelsConfig getChannels() {
        return channels;
    }

    public void setChannels(ChannelsConfig channels) {
        this.channels = channels;
    }

    /**
     * Memory configuration
     */
    public static class MemoryConfig {
        private String storeId;
        private List<String> traitGroups;
        private MemoryMode mode = MemoryMode.ONCE;

        /**
         * Identifier type used to look up a profile by the caller's address
         * (e.g. "phone", "email"), as configured in the Memory Store's Identity
         * Resolution Settings.
         */
        private String identifierType = "phone";

        /**
         * Maximum number of observations to retrieve from the Recall API.
         */
        private int observationsLimit = 20;

        /**
         * Maximum number of summaries to retrieve from the Recall API.
         */
        private int summariesLimit = 5;

        public String getStoreId() {
            return storeId;
        }

        public void setStoreId(String storeId) {
            this.storeId = storeId;
        }

        public List<String> getTraitGroups() {
            return traitGroups;
        }

        public void setTraitGroups(List<String> traitGroups) {
            this.traitGroups = traitGroups;
        }

        public MemoryMode getMode() {
            return mode;
        }

        public void setMode(MemoryMode mode) {
            this.mode = mode;
        }

        public String getIdentifierType() {
            return identifierType;
        }

        public void setIdentifierType(String identifierType) {
            this.identifierType = identifierType;
        }

        public int getObservationsLimit() {
            return observationsLimit;
        }

        public void setObservationsLimit(int observationsLimit) {
            this.observationsLimit = observationsLimit;
        }

        public int getSummariesLimit() {
            return summariesLimit;
        }

        public void setSummariesLimit(int summariesLimit) {
            this.summariesLimit = summariesLimit;
        }
    }

    /**
     * Idempotency configuration
     */
    public static class IdempotencyConfig {
        private int capacity = 10000;
        private Duration ttl = Duration.ofHours(24);

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    /**
     * Cache configuration
     */
    public static class CacheConfig {
        private String provider = "caffeine"; // caffeine or redis

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    /**
     * Resilience configuration
     */
    public static class ResilienceConfig {
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
        private RetryConfig retry = new RetryConfig();

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public RetryConfig getRetry() {
            return retry;
        }

        public void setRetry(RetryConfig retry) {
            this.retry = retry;
        }

        public static class CircuitBreakerConfig {
            private int failureRateThreshold = 50;
            private Duration waitDurationInOpenState = Duration.ofSeconds(60);

            public int getFailureRateThreshold() {
                return failureRateThreshold;
            }

            public void setFailureRateThreshold(int failureRateThreshold) {
                this.failureRateThreshold = failureRateThreshold;
            }

            public Duration getWaitDurationInOpenState() {
                return waitDurationInOpenState;
            }

            public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
                this.waitDurationInOpenState = waitDurationInOpenState;
            }
        }

        public static class RetryConfig {
            private int maxAttempts = 3;

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }
        }
    }

    /**
     * Voice configuration
     */
    public static class VoiceConfig {
        private boolean enabled = true;

        /**
         * ConversationRelay TTS voice (e.g. "en-US-Journey-O", "Polly.Joanna").
         * Optional - omitted from TwiML when blank so Twilio uses its default.
         */
        private String voice;

        /**
         * ConversationRelay language code (e.g. "en-US").
         * Optional - omitted from TwiML when blank.
         */
        private String language;

        /**
         * Greeting spoken to the caller when the call connects.
         * Optional - omitted from TwiML when blank.
         */
        private String welcomeGreeting;

        /**
         * E.164 phone number a human-agent handoff dials into (e.g. a support
         * line). Used by the handoff action endpoint's {@code <Dial><Number>}.
         * Optional - handoff is unavailable when blank.
         */
        private String handoffAgentNumber;

        /**
         * URL Twilio plays to the caller while they wait in the handoff
         * conference. When blank, Twilio's default hold music is used.
         */
        private String conferenceWaitUrl;

        /**
         * How long to wait for the human agent to answer the briefing call
         * before giving up; default 30s.
         */
        private int agentReachTimeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getWelcomeGreeting() {
            return welcomeGreeting;
        }

        public void setWelcomeGreeting(String welcomeGreeting) {
            this.welcomeGreeting = welcomeGreeting;
        }

        public String getHandoffAgentNumber() {
            return handoffAgentNumber;
        }

        public void setHandoffAgentNumber(String handoffAgentNumber) {
            this.handoffAgentNumber = handoffAgentNumber;
        }

        public String getConferenceWaitUrl() {
            return conferenceWaitUrl;
        }

        public void setConferenceWaitUrl(String conferenceWaitUrl) {
            this.conferenceWaitUrl = conferenceWaitUrl;
        }

        public int getAgentReachTimeoutSeconds() {
            return agentReachTimeoutSeconds;
        }

        public void setAgentReachTimeoutSeconds(int agentReachTimeoutSeconds) {
            this.agentReachTimeoutSeconds = agentReachTimeoutSeconds;
        }
    }

    /**
     * Channels configuration
     */
    public static class ChannelsConfig {
        private boolean sms = true;
        private boolean whatsapp = true;
        private boolean rcs = false;
        private boolean chat = true;

        public boolean isSms() {
            return sms;
        }

        public void setSms(boolean sms) {
            this.sms = sms;
        }

        public boolean isWhatsapp() {
            return whatsapp;
        }

        public void setWhatsapp(boolean whatsapp) {
            this.whatsapp = whatsapp;
        }

        public boolean isRcs() {
            return rcs;
        }

        public void setRcs(boolean rcs) {
            this.rcs = rcs;
        }

        public boolean isChat() {
            return chat;
        }

        public void setChat(boolean chat) {
            this.chat = chat;
        }
    }

    /**
     * Create configuration from environment variables.
     * Primarily used for non-Spring contexts.
     */
    public static TacConfiguration fromEnvironment() {
        TacConfiguration config = new TacConfiguration();
        config.setAccountSid(System.getenv("TWILIO_ACCOUNT_SID"));
        config.setAuthToken(System.getenv("TWILIO_AUTH_TOKEN"));
        config.setApiKey(System.getenv("TWILIO_API_KEY"));
        config.setApiSecret(System.getenv("TWILIO_API_SECRET"));
        config.setConversationConfigurationId(System.getenv("TWILIO_CONVERSATION_CONFIGURATION_ID"));
        config.setPhoneNumber(System.getenv("TWILIO_PHONE_NUMBER"));
        config.setVoicePublicDomain(System.getenv("TWILIO_VOICE_PUBLIC_DOMAIN"));

        MemoryConfig memoryConfig = new MemoryConfig();
        memoryConfig.setStoreId(System.getenv("TWILIO_MEMORY_STORE_ID"));
        String traitGroups = System.getenv("TWILIO_TRAIT_GROUPS");
        if (traitGroups != null && !traitGroups.isEmpty()) {
            memoryConfig.setTraitGroups(List.of(traitGroups.split(",")));
        }
        config.setMemory(memoryConfig);

        return config;
    }
}
