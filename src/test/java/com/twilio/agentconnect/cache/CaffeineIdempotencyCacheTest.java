package com.twilio.agentconnect.cache;

import com.twilio.agentconnect.core.TacConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

/**
 * Unit tests for {@link CaffeineIdempotencyCache}.
 */
class CaffeineIdempotencyCacheTest {

    private CaffeineIdempotencyCache cache;

    @BeforeEach
    void setUp() {
        // A real configuration so the underlying Caffeine cache initializes properly.
        TacConfiguration config = new TacConfiguration();
        config.getIdempotency().setCapacity(100);
        config.getIdempotency().setTtl(Duration.ofMinutes(10));
        cache = new CaffeineIdempotencyCache(config);
    }

    @Test
    void firstSeenTokenReturnsTrue() {
        StepVerifier.create(cache.checkAndSet("token-1"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void duplicateTokenReturnsFalse() {
        // First call registers the token.
        StepVerifier.create(cache.checkAndSet("token-dup"))
            .expectNext(true)
            .verifyComplete();

        // Second call with the same token is detected as a duplicate.
        StepVerifier.create(cache.checkAndSet("token-dup"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void distinctTokensAreIndependent() {
        StepVerifier.create(cache.checkAndSet("token-a"))
            .expectNext(true)
            .verifyComplete();

        StepVerifier.create(cache.checkAndSet("token-b"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void nullTokenIsTreatedAsNew() {
        StepVerifier.create(cache.checkAndSet(null))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void emptyTokenIsTreatedAsNew() {
        StepVerifier.create(cache.checkAndSet(""))
            .expectNext(true)
            .verifyComplete();

        // Empty tokens are never registered, so a repeat is still "new".
        StepVerifier.create(cache.checkAndSet(""))
            .expectNext(true)
            .verifyComplete();
    }
}
