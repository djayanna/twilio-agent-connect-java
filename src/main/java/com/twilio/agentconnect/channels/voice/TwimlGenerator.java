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
        // action is a <Connect> attribute: Twilio POSTs there when the relay
        // session ends (the handoff hook), NOT a <ConversationRelay> attribute.
        twiml.append("<Connect");
        appendAttribute(twiml, "action", action);
        twiml.append(">");
        twiml.append("<ConversationRelay ");
        twiml.append("url=\"").append(websocketUrl).append("\"");
        // NOTE: attribute is "conversationConfiguration", NOT
        // "conversationConfigurationId" — the wrong name is silently ignored.
        appendAttribute(twiml, "conversationConfiguration", conversationConfiguration);
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
     * Generate TwiML that places a leg into a {@code <Conference>}.
     *
     * <p>Two roles are supported:
     * <ul>
     *   <li>{@code CALLER} — the customer waits in the conference. Conference
     *       does not start without an agent ({@code startConferenceOnEnter=false}),
     *       and the conference survives if the caller hangs up first
     *       ({@code endConferenceOnExit=false}). Optional {@code waitUrl}
     *       controls the hold experience.</li>
     *   <li>{@code AGENT} — the human agent joins. Their entry starts the
     *       conference ({@code startConferenceOnEnter=true}) and the conference
     *       ends when they hang up ({@code endConferenceOnExit=true}).</li>
     * </ul>
     */
    public String generateConferenceTwiml(String conferenceName, ConferenceRole role, String waitUrl) {
        boolean isAgent = role == ConferenceRole.AGENT;
        StringBuilder twiml = new StringBuilder();
        twiml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        twiml.append("<Response>");
        twiml.append("<Dial>");
        twiml.append("<Conference");
        twiml.append(" startConferenceOnEnter=\"").append(isAgent).append("\"");
        twiml.append(" endConferenceOnExit=\"").append(isAgent).append("\"");
        if (!isAgent && waitUrl != null && !waitUrl.isBlank()) {
            twiml.append(" waitUrl=\"").append(escapeXml(waitUrl)).append("\"");
        }
        twiml.append(">");
        twiml.append(escapeXml(conferenceName));
        twiml.append("</Conference>");
        twiml.append("</Dial>");
        twiml.append("</Response>");
        return twiml.toString();
    }

    /**
     * Generate TwiML connecting a leg to a ConversationRelay briefing session,
     * passing a context lookup id as a custom {@code <Parameter>} so the
     * WebSocket handler can fetch the briefing payload on setup.
     */
    public String generateBriefingConnectTwiml(
            String websocketUrl,
            String ctxId,
            String action,
            String voice,
            String language,
            String welcomeGreeting) {
        StringBuilder twiml = new StringBuilder();
        twiml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        twiml.append("<Response>");
        // action belongs on <Connect>, not <ConversationRelay>: when AI #2 ends
        // the relay session (bridge_to_caller), Twilio POSTs the action URL with
        // HandoffData so we can return follow-on TwiML that drops the agent into
        // the conference.
        twiml.append("<Connect");
        appendAttribute(twiml, "action", action);
        twiml.append(">");
        twiml.append("<ConversationRelay ");
        twiml.append("url=\"").append(websocketUrl).append("\"");
        appendAttribute(twiml, "voice", voice);
        appendAttribute(twiml, "language", language);
        appendAttribute(twiml, "welcomeGreeting", welcomeGreeting);
        twiml.append(">");
        twiml.append("<Parameter name=\"role\" value=\"briefing\" />");
        twiml.append("<Parameter name=\"ctxId\" value=\"").append(escapeXml(ctxId)).append("\" />");
        twiml.append("</ConversationRelay>");
        twiml.append("</Connect>");
        twiml.append("</Response>");
        return twiml.toString();
    }

    /** Role of a leg joining a handoff conference. */
    public enum ConferenceRole { CALLER, AGENT }

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
