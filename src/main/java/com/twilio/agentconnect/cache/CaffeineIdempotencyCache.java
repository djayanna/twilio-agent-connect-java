package com.twilio.agentconnect.cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.twilio.agentconnect.core.TacConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Caffeine-based idempotency cache implementation.
 * Suitable for single-instance deployments.
 */
@Service
@ConditionalOnProperty(prefix = "twilio.agent-connect.cache", name = "provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineIdempotencyCache implements IdempotencyCache {

    private static final Logger log = LoggerFactory.getLogger(CaffeineIdempotencyCache.class);

    private final Cache<String, Boolean> cache;

    public CaffeineIdempotencyCache(TacConfiguration config) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(config.getIdempotency().getCapacity())
            .expireAfterWrite(config.getIdempotency().getTtl())
            .build();

        log.info("Initialized Caffeine idempotency cache with capacity: {}",
                config.getIdempotency().getCapacity());
    }

    @Override
    public Mono<Boolean> checkAndSet(String token) {
        return Mono.fromCallable(() -> {
            if (token == null || token.isEmpty()) {
                log.warn("Received null or empty idempotency token");
                return true; // Treat as new to allow processing
            }

            if (cache.getIfPresent(token) != null) {
                log.debug("Duplicate idempotency token detected: {}", token);
                return false; // Already processed
            }

            cache.put(token, Boolean.TRUE);
            log.debug("Registered new idempotency token: {}", token);
            return true; // New token
        });
    }
}
