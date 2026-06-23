package com.twilio.agentconnect.channels.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generates TwiML for voice calls.
 */
@Component
public class TwimlGenerator {

    private static final Logger log = LoggerFactory.getLogger(TwimlGenerator.class);

    /**
     * Generate TwiML with Connect verb for Conversation Relay.
     *
     * <p>Optional attributes ({@code conversationConfiguration}, {@code voice},
     * {@code language}, {@code welcomeGreeting}) are only emitted when a
     * non-blank value is provided; otherwise Twilio applies its own defaults.
     *
     * <p>When {@code conversationConfiguration} is set, Conversation
     * Orchestrator captures the call into a conversation and activates the
     * linked Memory Store for identity resolution and context retrieval.
     */
    public String generateConnectTwiml(
            String websocketUrl,
            String conversationConfiguration,
            String action,
            String voice,
            String language,
            String welcomeGreeting,
            Map<String, String> parameters) {

        StringBuilder twiml = new StringBuilder();
        twiml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        twiml.append("<Response>");
        twiml.append("<Connect>");
        twiml.append("<ConversationRelay ");
        twiml.append("url=\"").append(websocketUrl).append("\"");
        // NOTE: attribute is "conversationConfiguration", NOT
        // "conversationConfigurationId" — the wrong name is silently ignored.
        appendAttribute(twiml, "conversationConfiguration", conversationConfiguration);
        // action: Twilio POSTs here when the relay session ends (handoff hook).
        appendAttribute(twiml, "action", action);
        appendAttribute(twiml, "voice", voice);
        appendAttribute(twiml, "language", language);
        appendAttribute(twiml, "welcomeGreeting", welcomeGreeting);
        twiml.append(" />");
        twiml.append("</Connect>");
        twiml.append("</Response>");

        String result = twiml.toString();
        log.debug("Generated TwiML: {}", result);
        return result;
    }

    /**
     * Append {@code name="value"} to the builder when the value is non-blank.
     */
    private void appendAttribute(StringBuilder twiml, String name, String value) {
        if (value != null && !value.isBlank()) {
            twiml.append(" ").append(name).append("=\"").append(escapeXml(value)).append("\"");
        }
    }

    /**
     * Generate simple say TwiML.
     */
    public String generateSayTwiml(String message) {
        return String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<Response><Say>%s</Say></Response>",
            escapeXml(message)
        );
    }

    /**
     * Generate TwiML that dials a human agent (handoff destination).
     *
     * @param agentNumber the E.164 number to dial
     * @param callerId    optional caller ID for the outbound leg (omitted when blank)
     */
    public String generateDialTwiml(String agentNumber, String callerId) {
        StringBuilder twiml = new StringBuilder();
        twiml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        twiml.append("<Response>");
        twiml.append("<Dial");
        appendAttribute(twiml, "callerId", callerId);
        twiml.append(">");
        twiml.append("<Number>").append(escapeXml(agentNumber)).append("</Number>");
        twiml.append("</Dial>");
        twiml.append("</Response>");
        return twiml.toString();
    }

    /**
     * Generate TwiML that hangs up the call.
     */
    public String generateHangupTwiml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    }

    /**
     * Escape XML special characters.
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
