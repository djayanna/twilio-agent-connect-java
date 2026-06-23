package com.twilio.agentconnect.channels.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ConversationRelayProtocol}.
 */
class ConversationRelayProtocolTest {

    private ObjectMapper objectMapper;
    private ConversationRelayProtocol protocol;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        protocol = new ConversationRelayProtocol(objectMapper);
    }

    @Test
    void parseSetupMessageExtractsMetadata() {
        String json = "{\"type\":\"setup\",\"callSid\":\"CA123\",\"from\":\"+1111\",\"to\":\"+2222\"}";

        StepVerifier.create(protocol.parseMessage(json))
            .expectNextMatches(msg ->
                msg.getType() == ConversationRelayMessage.MessageType.SETUP &&
                "CA123".equals(msg.getCallSid()) &&
                "+1111".equals(msg.getFrom()) &&
                "+2222".equals(msg.getTo()))
            .verifyComplete();
    }

    @Test
    void parsePromptMessageReadsVoicePromptAsText() {
        String json = "{\"type\":\"prompt\",\"voicePrompt\":\"hello there\"}";

        StepVerifier.create(protocol.parseMessage(json))
            .expectNextMatches(msg ->
                msg.getType() == ConversationRelayMessage.MessageType.PROMPT &&
                "hello there".equals(msg.getText()))
            .verifyComplete();
    }

    @Test
    void parseInterruptMessage() {
        String json = "{\"type\":\"interrupt\"}";

        StepVerifier.create(protocol.parseMessage(json))
            .expectNextMatches(msg ->
                msg.getType() == ConversationRelayMessage.MessageType.INTERRUPT)
            .verifyComplete();
    }

    @Test
    void parseMarkMessageReadsMarkName() {
        String json = "{\"type\":\"mark\",\"mark\":\"label-1\"}";

        StepVerifier.create(protocol.parseMessage(json))
            .expectNextMatches(msg ->
                msg.getType() == ConversationRelayMessage.MessageType.MARK &&
                "label-1".equals(msg.getMarkName()))
            .verifyComplete();
    }

    @Test
    void parseUnsupportedTypeReturnsEmptyMono() {
        String json = "{\"type\":\"dtmf\",\"digit\":\"5\"}";

        StepVerifier.create(protocol.parseMessage(json))
            .verifyComplete();
    }

    @Test
    void parseMissingTypeReturnsEmptyMono() {
        String json = "{\"callSid\":\"CA123\"}";

        StepVerifier.create(protocol.parseMessage(json))
            .verifyComplete();
    }

    @Test
    void parseInvalidJsonErrors() {
        String json = "this is not json";

        StepVerifier.create(protocol.parseMessage(json))
            .expectError()
            .verify();
    }

    @Test
    void buildResponseMessageProducesTextTokenLast() throws Exception {
        String result = protocol.buildResponseMessage("hi");

        var node = objectMapper.readTree(result);
        assertEquals("text", node.path("type").asText());
        assertEquals("hi", node.path("token").asText());
        assertEquals(true, node.path("last").asBoolean());
    }

    @Test
    void buildClearMessageProducesInterrupt() throws Exception {
        String result = protocol.buildClearMessage();

        var node = objectMapper.readTree(result);
        assertEquals("interrupt", node.path("type").asText());
        // No token/last on a clear message.
        assertNull(node.get("token"));
    }

    @Test
    void buildEndMessageIncludesHandoffData() throws Exception {
        String handoff = "{\"reasonCode\":\"live-agent-handoff\"}";
        String result = protocol.buildEndMessage(handoff);

        var node = objectMapper.readTree(result);
        assertEquals("end", node.path("type").asText());
        // handoffData is carried as a JSON *string* per the relay spec.
        assertEquals(handoff, node.path("handoffData").asText());
    }

    @Test
    void buildEndMessageOmitsHandoffDataWhenNull() throws Exception {
        String result = protocol.buildEndMessage(null);

        var node = objectMapper.readTree(result);
        assertEquals("end", node.path("type").asText());
        assertNull(node.get("handoffData"));
    }
}
