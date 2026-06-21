package com.twilio.agentconnect.context.client;

import com.twilio.agentconnect.core.TacConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract base client for Twilio API interactions.
 * Provides common functionality for circuit breaking, retry, and error handling.
 */
public abstract class AbstractContextClient {

    protected static final Logger log = LoggerFactory.getLogger(AbstractContextClient.class);

    protected final WebClient webClient;
    protected final TacConfiguration config;
    protected final CircuitBreaker circuitBreaker;

    protected AbstractContextClient(WebClient webClient,
                                   TacConfiguration config,
                                   CircuitBreaker circuitBreaker) {
        this.webClient = webClient;
        this.config = config;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Exposes the configuration for SpEL cache conditions (e.g. {@code @Cacheable}),
     * which can only read public properties on the proxied target.
     */
    public TacConfiguration getConfig() {
        return config;
    }

    /**
     * Execute a request with circuit breaker and fallback.
     *
     * @param requestBuilder Function to build and execute the request
     * @param fallbackSupplier Supplier for fallback value on error
     * @return Mono with the response or fallback
     */
    protected <T> Mono<T> executeRequest(
            Function<WebClient, Mono<T>> requestBuilder,
            Supplier<T> fallbackSupplier) {

        return Mono.defer(() -> requestBuilder.apply(webClient))
            .transform(CircuitBreakerOperator.of(circuitBreaker))
            .retry(config.getResilience().getRetry().getMaxAttempts())
            .onErrorResume(ex -> {
                log.warn("API call failed, using fallback: {}", ex.getMessage());
                return Mono.justOrEmpty(fallbackSupplier.get());
            });
    }

    /**
     * Build basic auth header value.
     */
    protected String buildBasicAuth() {
        String credentials = config.getApiKey() + ":" + config.getApiSecret();
        return "Basic " + java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes());
    }

    /**
     * Build user agent string.
     */
    protected String buildUserAgent() {
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        return String.format("twilio-agent-connect-java/0.1.0 java/%s os/%s",
                           javaVersion, osName);
    }
}
