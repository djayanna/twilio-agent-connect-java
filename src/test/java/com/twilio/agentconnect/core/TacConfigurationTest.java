package com.twilio.agentconnect.core;

import com.twilio.agentconnect.core.TacConfiguration.CacheConfig;
import com.twilio.agentconnect.core.TacConfiguration.ChannelsConfig;
import com.twilio.agentconnect.core.TacConfiguration.IdempotencyConfig;
import com.twilio.agentconnect.core.TacConfiguration.MemoryConfig;
import com.twilio.agentconnect.core.TacConfiguration.ResilienceConfig;
import com.twilio.agentconnect.core.TacConfiguration.ResilienceConfig.CircuitBreakerConfig;
import com.twilio.agentconnect.core.TacConfiguration.ResilienceConfig.RetryConfig;
import com.twilio.agentconnect.core.TacConfiguration.VoiceConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TacConfiguration} and its nested configuration classes.
 */
class TacConfigurationTest {

    @Test
    void hasNonNullNestedDefaultsOnConstruction() {
        TacConfiguration config = new TacConfiguration();

        assertThat(config.getMemory()).isNotNull();
        assertThat(config.getIdempotency()).isNotNull();
        assertThat(config.getCache()).isNotNull();
        assertThat(config.getResilience()).isNotNull();
        assertThat(config.getVoice()).isNotNull();
        assertThat(config.getChannels()).isNotNull();
    }

    @Test
    void topLevelGettersAndSetters() {
        TacConfiguration config = new TacConfiguration();

        config.setAccountSid("AC123");
        config.setAuthToken("token");
        config.setApiKey("SK123");
        config.setApiSecret("secret");
        config.setConversationConfigurationId("conv-config");
        config.setPhoneNumber("+15551230000");
        config.setVoicePublicDomain("voice.example.com");

        assertThat(config.getAccountSid()).isEqualTo("AC123");
        assertThat(config.getAuthToken()).isEqualTo("token");
        assertThat(config.getApiKey()).isEqualTo("SK123");
        assertThat(config.getApiSecret()).isEqualTo("secret");
        assertThat(config.getConversationConfigurationId()).isEqualTo("conv-config");
        assertThat(config.getPhoneNumber()).isEqualTo("+15551230000");
        assertThat(config.getVoicePublicDomain()).isEqualTo("voice.example.com");
    }

    @Test
    void topLevelNestedConfigSetters() {
        TacConfiguration config = new TacConfiguration();

        MemoryConfig memory = new MemoryConfig();
        IdempotencyConfig idempotency = new IdempotencyConfig();
        CacheConfig cache = new CacheConfig();
        ResilienceConfig resilience = new ResilienceConfig();
        VoiceConfig voice = new VoiceConfig();
        ChannelsConfig channels = new ChannelsConfig();

        config.setMemory(memory);
        config.setIdempotency(idempotency);
        config.setCache(cache);
        config.setResilience(resilience);
        config.setVoice(voice);
        config.setChannels(channels);

        assertThat(config.getMemory()).isSameAs(memory);
        assertThat(config.getIdempotency()).isSameAs(idempotency);
        assertThat(config.getCache()).isSameAs(cache);
        assertThat(config.getResilience()).isSameAs(resilience);
        assertThat(config.getVoice()).isSameAs(voice);
        assertThat(config.getChannels()).isSameAs(channels);
    }

    @Test
    void memoryConfigDefaultsAndSetters() {
        MemoryConfig memory = new MemoryConfig();

        // defaults
        assertThat(memory.getMode()).isEqualTo(MemoryMode.ONCE);
        assertThat(memory.getIdentifierType()).isEqualTo("phone");
        assertThat(memory.getObservationsLimit()).isEqualTo(20);
        assertThat(memory.getSummariesLimit()).isEqualTo(5);
        assertThat(memory.getStoreId()).isNull();
        assertThat(memory.getTraitGroups()).isNull();

        // setters
        memory.setStoreId("store-1");
        memory.setTraitGroups(List.of("group-a", "group-b"));
        memory.setMode(MemoryMode.ALWAYS);
        memory.setIdentifierType("email");
        memory.setObservationsLimit(50);
        memory.setSummariesLimit(10);

        assertThat(memory.getStoreId()).isEqualTo("store-1");
        assertThat(memory.getTraitGroups()).containsExactly("group-a", "group-b");
        assertThat(memory.getMode()).isEqualTo(MemoryMode.ALWAYS);
        assertThat(memory.getIdentifierType()).isEqualTo("email");
        assertThat(memory.getObservationsLimit()).isEqualTo(50);
        assertThat(memory.getSummariesLimit()).isEqualTo(10);
    }

    @Test
    void idempotencyConfigDefaultsAndSetters() {
        IdempotencyConfig idempotency = new IdempotencyConfig();

        assertThat(idempotency.getCapacity()).isEqualTo(10000);
        assertThat(idempotency.getTtl()).isEqualTo(Duration.ofHours(24));

        idempotency.setCapacity(500);
        idempotency.setTtl(Duration.ofMinutes(30));

        assertThat(idempotency.getCapacity()).isEqualTo(500);
        assertThat(idempotency.getTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void cacheConfigDefaultAndSetter() {
        CacheConfig cache = new CacheConfig();

        assertThat(cache.getProvider()).isEqualTo("caffeine");

        cache.setProvider("redis");
        assertThat(cache.getProvider()).isEqualTo("redis");
    }

    @Test
    void resilienceConfigDefaultsAndSetters() {
        ResilienceConfig resilience = new ResilienceConfig();

        assertThat(resilience.getCircuitBreaker()).isNotNull();
        assertThat(resilience.getRetry()).isNotNull();

        CircuitBreakerConfig newCb = new CircuitBreakerConfig();
        RetryConfig newRetry = new RetryConfig();
        resilience.setCircuitBreaker(newCb);
        resilience.setRetry(newRetry);

        assertThat(resilience.getCircuitBreaker()).isSameAs(newCb);
        assertThat(resilience.getRetry()).isSameAs(newRetry);
    }

    @Test
    void circuitBreakerConfigDefaultsAndSetters() {
        CircuitBreakerConfig cb = new CircuitBreakerConfig();

        assertThat(cb.getFailureRateThreshold()).isEqualTo(50);
        assertThat(cb.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(60));

        cb.setFailureRateThreshold(75);
        cb.setWaitDurationInOpenState(Duration.ofSeconds(10));

        assertThat(cb.getFailureRateThreshold()).isEqualTo(75);
        assertThat(cb.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void retryConfigDefaultAndSetter() {
        RetryConfig retry = new RetryConfig();

        assertThat(retry.getMaxAttempts()).isEqualTo(3);

        retry.setMaxAttempts(5);
        assertThat(retry.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    void voiceConfigDefaultsAndSetters() {
        VoiceConfig voice = new VoiceConfig();

        assertThat(voice.isEnabled()).isTrue();
        assertThat(voice.getVoice()).isNull();
        assertThat(voice.getLanguage()).isNull();
        assertThat(voice.getWelcomeGreeting()).isNull();

        voice.setEnabled(false);
        voice.setVoice("en-US-Journey-O");
        voice.setLanguage("en-US");
        voice.setWelcomeGreeting("Hello there");

        assertThat(voice.isEnabled()).isFalse();
        assertThat(voice.getVoice()).isEqualTo("en-US-Journey-O");
        assertThat(voice.getLanguage()).isEqualTo("en-US");
        assertThat(voice.getWelcomeGreeting()).isEqualTo("Hello there");
    }

    @Test
    void channelsConfigDefaultsAndSetters() {
        ChannelsConfig channels = new ChannelsConfig();

        assertThat(channels.isSms()).isTrue();
        assertThat(channels.isWhatsapp()).isTrue();
        assertThat(channels.isRcs()).isFalse();
        assertThat(channels.isChat()).isTrue();

        channels.setSms(false);
        channels.setWhatsapp(false);
        channels.setRcs(true);
        channels.setChat(false);

        assertThat(channels.isSms()).isFalse();
        assertThat(channels.isWhatsapp()).isFalse();
        assertThat(channels.isRcs()).isTrue();
        assertThat(channels.isChat()).isFalse();
    }

    @Test
    void fromEnvironmentReadsValuesWithoutThrowing() {
        // We do not control the real environment; assert the factory returns a
        // fully-formed object with a non-null memory config and that present env
        // vars (if any) are propagated. Values may be null when env is unset.
        TacConfiguration config = TacConfiguration.fromEnvironment();

        assertThat(config).isNotNull();
        assertThat(config.getMemory()).isNotNull();
        assertThat(config.getAccountSid()).isEqualTo(System.getenv("TWILIO_ACCOUNT_SID"));
        assertThat(config.getAuthToken()).isEqualTo(System.getenv("TWILIO_AUTH_TOKEN"));
        assertThat(config.getPhoneNumber()).isEqualTo(System.getenv("TWILIO_PHONE_NUMBER"));
    }
}
