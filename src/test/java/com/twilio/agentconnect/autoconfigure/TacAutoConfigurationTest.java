package com.twilio.agentconnect.autoconfigure;

import com.twilio.agentconnect.channels.Channel;
import com.twilio.agentconnect.channels.messaging.ChatChannel;
import com.twilio.agentconnect.channels.messaging.SmsChannel;
import com.twilio.agentconnect.channels.messaging.WhatsAppChannel;
import com.twilio.agentconnect.channels.voice.VoiceChannel;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TacConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TacAutoConfiguration} bean factory methods.
 */
@ExtendWith(MockitoExtension.class)
class TacAutoConfigurationTest {

    @Mock
    private SmsChannel smsChannel;

    @Mock
    private WhatsAppChannel whatsAppChannel;

    @Mock
    private ChatChannel chatChannel;

    @Mock
    private VoiceChannel voiceChannel;

    private TacAutoConfiguration autoConfiguration;

    @BeforeEach
    void setUp() {
        autoConfiguration = new TacAutoConfiguration();
    }

    @Test
    void cacheManagerIsAsyncCaffeineManager() {
        CacheManager cacheManager = autoConfiguration.cacheManager();

        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        // Async mode must be on so reactive @Cacheable methods work.
        // getCache lazily creates a cache; with async mode enabled it succeeds.
        assertThat(cacheManager.getCache("memory")).isNotNull();
    }

    @Test
    void circuitBreakerUsesConfiguredThresholds() {
        TacConfiguration config = new TacConfiguration();
        config.getResilience().getCircuitBreaker().setFailureRateThreshold(42);
        config.getResilience().getCircuitBreaker()
            .setWaitDurationInOpenState(Duration.ofSeconds(30));

        CircuitBreaker circuitBreaker = autoConfiguration.tacCircuitBreaker(config);

        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getName()).isEqualTo("tac");
        assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold())
            .isEqualTo(42.0f);
        // A freshly-created breaker starts CLOSED.
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void channelMapRegistersMessagingChannelsWithoutVoice() {
        Map<ChannelType, Channel> channels =
            autoConfiguration.channelMap(smsChannel, whatsAppChannel, chatChannel, null);

        assertThat(channels).hasSize(3);
        assertThat(channels).containsOnlyKeys(
            ChannelType.SMS, ChannelType.WHATSAPP, ChannelType.CHAT);
        assertThat(channels.get(ChannelType.SMS)).isSameAs(smsChannel);
        assertThat(channels.get(ChannelType.WHATSAPP)).isSameAs(whatsAppChannel);
        assertThat(channels.get(ChannelType.CHAT)).isSameAs(chatChannel);
        assertThat(channels).doesNotContainKey(ChannelType.VOICE);
    }

    @Test
    void channelMapIncludesVoiceWhenPresent() {
        Map<ChannelType, Channel> channels =
            autoConfiguration.channelMap(smsChannel, whatsAppChannel, chatChannel, voiceChannel);

        assertThat(channels).hasSize(4);
        assertThat(channels).containsKey(ChannelType.VOICE);
        assertThat(channels.get(ChannelType.VOICE)).isSameAs(voiceChannel);
    }
}
