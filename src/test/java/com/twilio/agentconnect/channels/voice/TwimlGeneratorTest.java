package com.twilio.agentconnect.channels.voice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TwimlGenerator}.
 */
class TwimlGeneratorTest {

    private TwimlGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TwimlGenerator();
    }

    @Test
    void generateConnectTwimlAlwaysIncludesUrlAndStructure() {
        String xml = generator.generateConnectTwiml(
            "wss://example.com/ws/voice", null, null, null, null, Map.of());

        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<Response>"));
        assertTrue(xml.contains("<Connect>"));
        assertTrue(xml.contains("<ConversationRelay "));
        assertTrue(xml.contains("url=\"wss://example.com/ws/voice\""));
        assertTrue(xml.contains("</Connect>"));
        assertTrue(xml.contains("</Response>"));
    }

    @Test
    void generateConnectTwimlOmitsBlankOptionalAttributes() {
        String xml = generator.generateConnectTwiml(
            "wss://example.com/ws/voice", "", "  ", null, "", Map.of());

        assertFalse(xml.contains("conversationConfiguration="));
        assertFalse(xml.contains("voice="));
        assertFalse(xml.contains("language="));
        assertFalse(xml.contains("welcomeGreeting="));
    }

    @Test
    void generateConnectTwimlEmitsAllOptionalAttributesWhenPresent() {
        String xml = generator.generateConnectTwiml(
            "wss://example.com/ws/voice",
            "config_123",
            "en-US-Journey-O",
            "en-US",
            "Hello there",
            Map.of());

        assertTrue(xml.contains("conversationConfiguration=\"config_123\""));
        assertTrue(xml.contains("voice=\"en-US-Journey-O\""));
        assertTrue(xml.contains("language=\"en-US\""));
        assertTrue(xml.contains("welcomeGreeting=\"Hello there\""));
    }

    @Test
    void generateConnectTwimlEscapesXmlInAttributes() {
        String xml = generator.generateConnectTwiml(
            "wss://example.com/ws/voice",
            null,
            null,
            null,
            "Hi <b> & \"you\"",
            Map.of());

        assertTrue(xml.contains("welcomeGreeting=\"Hi &lt;b&gt; &amp; &quot;you&quot;\""));
        // Raw unescaped characters must not appear inside the greeting value.
        assertFalse(xml.contains("<b>"));
    }

    @Test
    void generateSayTwimlEscapesXml() {
        String xml = generator.generateSayTwiml("Tom & Jerry <said> \"hi\" 'bye'");

        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<Response><Say>"));
        assertTrue(xml.contains("Tom &amp; Jerry &lt;said&gt; &quot;hi&quot; &apos;bye&apos;"));
        assertTrue(xml.contains("</Say></Response>"));
    }
}
