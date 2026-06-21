package com.twilio.agentconnect.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.twilio.agentconnect.core.ChannelType;
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
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisSessionManager}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisSessionManagerTest {

    private static final String KEY_PREFIX = "tac:session:";

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private RedisSessionManager manager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        manager = new RedisSessionManager(redisTemplate, objectMapper);
    }

    private String serialize(Session session) throws Exception {
        return objectMapper.writeValueAsString(session);
    }

    @Test
    void getOrCreateReturnsExistingSessionWhenPresent() throws Exception {
        Session existing = Session.builder()
            .id("conv-1")
            .conversationId("conv-1")
            .channelType(ChannelType.SMS)
            .createdAt(Instant.parse("2026-06-20T10:00:00Z"))
            .lastActivityAt(Instant.parse("2026-06-20T10:00:00Z"))
            .build();

        when(valueOps.get(KEY_PREFIX + "conv-1")).thenReturn(Mono.just(serialize(existing)));

        StepVerifier.create(manager.getOrCreate("conv-1", ChannelType.SMS))
            .assertNext(session -> {
                assertEquals("conv-1", session.getId());
                assertEquals(ChannelType.SMS, session.getChannelType());
            })
            .verifyComplete();

        // Should not write a new session when one already exists.
        verify(valueOps, org.mockito.Mockito.never())
            .set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getOrCreateCreatesAndSavesSessionWhenAbsent() {
        when(valueOps.get(KEY_PREFIX + "conv-2")).thenReturn(Mono.empty());
        when(valueOps.set(eq(KEY_PREFIX + "conv-2"), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));

        StepVerifier.create(manager.getOrCreate("conv-2", ChannelType.VOICE))
            .assertNext(session -> {
                assertEquals("conv-2", session.getId());
                assertEquals("conv-2", session.getConversationId());
                assertEquals(ChannelType.VOICE, session.getChannelType());
                assertNotNull(session.getCreatedAt());
                assertNotNull(session.getLastActivityAt());
            })
            .verifyComplete();

        verify(valueOps).set(eq(KEY_PREFIX + "conv-2"), anyString(), any(Duration.class));
    }

    @Test
    void getReturnsDeserializedSession() throws Exception {
        Session existing = Session.builder()
            .id("conv-3")
            .conversationId("conv-3")
            .channelType(ChannelType.CHAT)
            .profileId("p-3")
            .createdAt(Instant.parse("2026-06-20T11:00:00Z"))
            .lastActivityAt(Instant.parse("2026-06-20T11:30:00Z"))
            .build();

        when(valueOps.get(KEY_PREFIX + "conv-3")).thenReturn(Mono.just(serialize(existing)));

        StepVerifier.create(manager.get("conv-3"))
            .assertNext(session -> {
                assertEquals("conv-3", session.getId());
                assertEquals("p-3", session.getProfileId());
                assertEquals(ChannelType.CHAT, session.getChannelType());
            })
            .verifyComplete();
    }

    @Test
    void getReturnsEmptyWhenKeyMissing() {
        when(valueOps.get(KEY_PREFIX + "missing")).thenReturn(Mono.empty());

        StepVerifier.create(manager.get("missing"))
            .verifyComplete();
    }

    @Test
    void getReturnsEmptyOnDeserializationError() {
        when(valueOps.get(KEY_PREFIX + "bad")).thenReturn(Mono.just("not-valid-json"));

        StepVerifier.create(manager.get("bad"))
            .verifyComplete();
    }

    @Test
    void updateTouchesAndSavesSession() {
        Session session = Session.builder()
            .id("conv-4")
            .conversationId("conv-4")
            .channelType(ChannelType.SMS)
            .createdAt(Instant.parse("2026-06-20T09:00:00Z"))
            .lastActivityAt(Instant.parse("2026-06-20T09:00:00Z"))
            .build();

        when(valueOps.set(eq(KEY_PREFIX + "conv-4"), anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));

        StepVerifier.create(manager.update(session))
            .verifyComplete();

        // touch() should have advanced the last-activity timestamp past the original.
        org.junit.jupiter.api.Assertions.assertTrue(
            session.getLastActivityAt().isAfter(Instant.parse("2026-06-20T09:00:00Z")));
        verify(valueOps).set(eq(KEY_PREFIX + "conv-4"), anyString(), any(Duration.class));
    }

    @Test
    void deleteRemovesKey() {
        when(redisTemplate.delete(KEY_PREFIX + "conv-5")).thenReturn(Mono.just(1L));

        StepVerifier.create(manager.delete("conv-5"))
            .verifyComplete();

        verify(redisTemplate).delete(KEY_PREFIX + "conv-5");
    }

    @Test
    void existsDelegatesToHasKey() {
        when(redisTemplate.hasKey(KEY_PREFIX + "conv-6")).thenReturn(Mono.just(true));

        StepVerifier.create(manager.exists("conv-6"))
            .expectNext(true)
            .verifyComplete();

        verify(redisTemplate).hasKey(KEY_PREFIX + "conv-6");
    }
}
