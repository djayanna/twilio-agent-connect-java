package com.twilio.agentconnect.cache;


import com.twilio.agentconnect.core.TacConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Redis-based idempotency cache for distributed deployments.
 */
@Service
@ConditionalOnProperty(prefix = "twilio.agent-connect.cache", name = "provider", havingValue = "redis")
public class RedisIdempotencyCache implements IdempotencyCache {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyCache.class);
    private static final String KEY_PREFIX = "tac:idempotency:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final TacConfiguration config;

    public RedisIdempotencyCache(ReactiveRedisTemplate<String, String> redisTemplate,
                                 TacConfiguration config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    @Override
    public Mono<Boolean> checkAndSet(String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Received null or empty idempotency token");
            return Mono.just(true); // Treat as new to allow processing
        }

        String key = KEY_PREFIX + token;

        return redisTemplate.opsForValue()
            .setIfAbsent(key, "1", config.getIdempotency().getTtl())
            .map(wasAbsent -> {
                if (Boolean.TRUE.equals(wasAbsent)) {
                    log.debug("Registered new idempotency token: {}", token);
                    return true; // New token
                } else {
                    log.debug("Duplicate idempotency token detected: {}", token);
                    return false; // Already processed
                }
            });
    }
}
