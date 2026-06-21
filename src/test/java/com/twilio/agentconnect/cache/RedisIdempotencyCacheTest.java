package com.twilio.agentconnect.cache;

import com.twilio.agentconnect.core.TacConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisIdempotencyCache}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisIdempotencyCacheTest {

    private static final String KEY_PREFIX = "tac:idempotency:";

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private RedisIdempotencyCache cache;

    @BeforeEach
    void setUp() {
        TacConfiguration config = new TacConfiguration();
        config.getIdempotency().setTtl(Duration.ofHours(1));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        cache = new RedisIdempotencyCache(redisTemplate, config);
    }

    @Test
    void newTokenReturnsTrueWhenSetIfAbsentSucceeds() {
        when(valueOps.setIfAbsent(eq(KEY_PREFIX + "abc"), eq("1"), any(Duration.class)))
            .thenReturn(Mono.just(true));

        StepVerifier.create(cache.checkAndSet("abc"))
            .expectNext(true)
            .verifyComplete();

        verify(valueOps).setIfAbsent(eq(KEY_PREFIX + "abc"), eq("1"), any(Duration.class));
    }

    @Test
    void duplicateTokenReturnsFalseWhenSetIfAbsentFails() {
        when(valueOps.setIfAbsent(eq(KEY_PREFIX + "abc"), eq("1"), any(Duration.class)))
            .thenReturn(Mono.just(false));

        StepVerifier.create(cache.checkAndSet("abc"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void nullTokenReturnsTrueWithoutTouchingRedis() {
        StepVerifier.create(cache.checkAndSet(null))
            .expectNext(true)
            .verifyComplete();

        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void emptyTokenReturnsTrueWithoutTouchingRedis() {
        StepVerifier.create(cache.checkAndSet(""))
            .expectNext(true)
            .verifyComplete();

        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }
}
