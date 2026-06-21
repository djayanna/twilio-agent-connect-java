package com.twilio.agentconnect.autoconfigure;

import com.twilio.agentconnect.channels.Channel;
import com.twilio.agentconnect.channels.messaging.ChatChannel;
import com.twilio.agentconnect.channels.messaging.RcsChannel;
import com.twilio.agentconnect.channels.messaging.SmsChannel;
import com.twilio.agentconnect.channels.messaging.WhatsAppChannel;
import com.twilio.agentconnect.channels.voice.VoiceChannel;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TacConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for Twilio Agent Connect SDK.
 */
@AutoConfiguration
@EnableCaching
@EnableConfigurationProperties(TacConfiguration.class)
@ComponentScan(basePackages = "com.twilio.agentconnect")
public class TacAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TacAutoConfiguration.class);

    /**
     * Caffeine-backed cache manager in async mode.
     *
     * <p>Reactive {@code @Cacheable} methods (those returning {@code Mono}/{@code Flux},
     * such as {@code MemoryClient.retrieveMemory}) require the async cache; the default
     * Spring Boot Caffeine cache manager runs in sync mode and throws at runtime.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setAsyncCacheMode(true);
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24)));
        log.info("Initialized async Caffeine cache manager");
        return cacheManager;
    }

    /**
     * Create circuit breaker for API calls.
     */
    @Bean
    public CircuitBreaker tacCircuitBreaker(TacConfiguration config) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(config.getResilience().getCircuitBreaker().getFailureRateThreshold())
            .waitDurationInOpenState(config.getResilience().getCircuitBreaker().getWaitDurationInOpenState())
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("tac");

        log.info("Initialized circuit breaker with failure rate threshold: {}%",
                config.getResilience().getCircuitBreaker().getFailureRateThreshold());

        return circuitBreaker;
    }

    /**
     * Create channel map for easy lookup.
     */
    @Bean
    public Map<ChannelType, Channel> channelMap(
            SmsChannel smsChannel,
            WhatsAppChannel whatsAppChannel,
            ChatChannel chatChannel,
            @Autowired(required = false) VoiceChannel voiceChannel) {

        Map<ChannelType, Channel> channels = new HashMap<>();
        channels.put(ChannelType.SMS, smsChannel);
        channels.put(ChannelType.WHATSAPP, whatsAppChannel);
        channels.put(ChannelType.CHAT, chatChannel);

        if (voiceChannel != null) {
            channels.put(ChannelType.VOICE, voiceChannel);
            log.info("Voice channel enabled");
        }

        log.info("Registered {} channels", channels.size());

        return channels;
    }
}
