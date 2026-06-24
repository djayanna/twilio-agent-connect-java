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
            "wss://example.com/ws/voice", null, null, null, null, null, Map.of());

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
            "wss://example.com/ws/voice", "", "", "  ", null, "", Map.of());

        assertFalse(xml.contains("conversationConfiguration="));
        assertFalse(xml.contains("action="));
        assertFalse(xml.contains("voice="));
        assertFalse(xml.contains("language="));
        assertFalse(xml.contains("welcomeGreeting="));
    }

    @Test
    void generateConnectTwimlEmitsAllOptionalAttributesWhenPresent() {
        String xml = generator.generateConnectTwiml(
            "wss://example.com/ws/voice",
            "config_123",
            "https://example.com/twiml/handoff",
            "en-US-Journey-O",
            "en-US",
            "Hello there",
            Map.of());

        assertTrue(xml.contains("conversationConfiguration=\"config_123\""));
        assertTrue(xml.contains("voice=\"en-US-Journey-O\""));
        assertTrue(xml.contains("language=\"en-US\""));
        assertTrue(xml.contains("welcomeGreeting=\"Hello there\""));
        // action is a <Connect> attribute, not a <ConversationRelay> attribute.
        assertTrue(xml.contains("<Connect action=\"https://example.com/twiml/handoff\">"));
    }

    @Test
    void generateConnectTwimlEscapesXmlInAttributes() {
        String xml = generator.generateConnectTwiml(
            "wss://example.com/ws/voice",
            null,
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

    @Test
    void generateDialTwimlDialsNumberWithCallerId() {
        String xml = generator.generateDialTwiml("+15551234567", "+15559876543");

        assertTrue(xml.contains("<Dial callerId=\"+15559876543\">"));
        assertTrue(xml.contains("<Number>+15551234567</Number>"));
        assertTrue(xml.contains("</Dial>"));
    }

    @Test
    void generateDialTwimlOmitsBlankCallerId() {
        String xml = generator.generateDialTwiml("+15551234567", null);

        assertTrue(xml.contains("<Dial>"));
        assertFalse(xml.contains("callerId="));
        assertTrue(xml.contains("<Number>+15551234567</Number>"));
    }

    @Test
    void generateConferenceTwimlForCallerLocksConferenceUntilAgentJoins() {
        String xml = generator.generateConferenceTwiml(
            "conf-CA1", TwimlGenerator.ConferenceRole.CALLER, null);

        assertTrue(xml.contains("<Conference"));
        assertTrue(xml.contains("startConferenceOnEnter=\"false\""));
        assertTrue(xml.contains("endConferenceOnExit=\"false\""));
        assertTrue(xml.contains(">conf-CA1</Conference>"));
        assertFalse(xml.contains("waitUrl="));
    }

    @Test
    void generateConferenceTwimlForCallerEmitsWaitUrlWhenProvided() {
        String xml = generator.generateConferenceTwiml(
            "conf-CA1",
            TwimlGenerator.ConferenceRole.CALLER,
            "https://example.com/hold.mp3");

        assertTrue(xml.contains("waitUrl=\"https://example.com/hold.mp3\""));
    }

    @Test
    void generateConferenceTwimlForAgentStartsAndEndsConference() {
        String xml = generator.generateConferenceTwiml(
            "conf-CA1", TwimlGenerator.ConferenceRole.AGENT, null);

        assertTrue(xml.contains("startConferenceOnEnter=\"true\""));
        assertTrue(xml.contains("endConferenceOnExit=\"true\""));
    }

    @Test
    void generateBriefingConnectTwimlPassesCtxIdAndActionAsParameter() {
        String xml = generator.generateBriefingConnectTwiml(
            "wss://example.com/ws/voice",
            "ctx_abc",
            "https://example.com/twiml/handoff",
            "en-US-Journey-O",
            "en-US",
            "Hi there");

        assertTrue(xml.contains("<ConversationRelay "));
        assertTrue(xml.contains("url=\"wss://example.com/ws/voice\""));
        assertTrue(xml.contains("voice=\"en-US-Journey-O\""));
        assertTrue(xml.contains("language=\"en-US\""));
        assertTrue(xml.contains("welcomeGreeting=\"Hi there\""));
        assertTrue(xml.contains("name=\"role\" value=\"briefing\""));
        assertTrue(xml.contains("name=\"ctxId\" value=\"ctx_abc\""));
        // action belongs on <Connect>, not <ConversationRelay>.
        assertTrue(xml.contains("<Connect action=\"https://example.com/twiml/handoff\">"));
    }

    @Test
    void generateBriefingConnectTwimlOmitsBlankOptionalAttributes() {
        String xml = generator.generateBriefingConnectTwiml(
            "wss://example.com/ws/voice", "ctx_abc", null, null, null, null);

        assertFalse(xml.contains("voice="));
        assertFalse(xml.contains("language="));
        assertFalse(xml.contains("welcomeGreeting="));
        assertFalse(xml.contains(" action="));
    }

    @Test
    void generateHangupTwimlReturnsHangup() {
        String xml = generator.generateHangupTwiml();

        assertTrue(xml.contains("<Response><Hangup/></Response>"));
    }
}
