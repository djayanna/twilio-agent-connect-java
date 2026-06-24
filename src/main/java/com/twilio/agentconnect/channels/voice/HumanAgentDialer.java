package com.twilio.agentconnect.channels.voice;

import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Places the outbound briefing call to a human agent during a handoff.
 *
 * <p>The agent's leg points at {@code /twiml/briefing?ctx=...}, which returns a
 * fresh {@code <ConversationRelay>} session — the briefing AI introduces the
 * caller, summarizes context, and bridges to the conference when the human is
 * ready.
 */
@Component
public class HumanAgentDialer {

    private static final Logger log = LoggerFactory.getLogger(HumanAgentDialer.class);

    private final TacConfiguration config;
    private final TwilioRestClient restClient;

    @Autowired
    public HumanAgentDialer(TacConfiguration config,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            TwilioRestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    /**
     * Dial the human agent and connect them to the briefing session.
     *
     * @param ctxId       briefing context id passed through the TwiML URL
     * @param baseUrl     scheme-qualified public base (e.g. https://x.ngrok.dev)
     * @return the created Call SID, or null if dialing failed
     */
    public String dialAgent(String ctxId, String baseUrl) {
        TacConfiguration.VoiceConfig voice = config.getVoice();
        String agentNumber = voice.getHandoffAgentNumber();
        if (agentNumber == null || agentNumber.isBlank()) {
            log.warn("Cannot dial human agent: handoffAgentNumber is not configured");
            return null;
        }

        String briefingUrl = baseUrl + "/twiml/briefing?ctx=" + ctxId;
        String statusCallback = baseUrl + "/twiml/agent-status?ctx=" + ctxId;

        try {
            TwilioRestClient client = restClient != null ? restClient : buildRestClient();
            Call call = Call.creator(
                    new PhoneNumber(agentNumber),
                    new PhoneNumber(config.getPhoneNumber()),
                    URI.create(briefingUrl))
                .setTimeout(voice.getAgentReachTimeoutSeconds())
                .setStatusCallback(URI.create(statusCallback))
                .setStatusCallbackEvent(java.util.List.of("completed", "no-answer", "busy", "failed"))
                .create(client);
            log.info("Dialed human agent {} for ctx {} (callSid={})",
                     agentNumber, ctxId, call.getSid());
            return call.getSid();
        } catch (Exception e) {
            log.error("Failed to dial human agent for ctx {}: {}", ctxId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build a dedicated {@link TwilioRestClient} from the current configuration
     * — avoids depending on the global {@code Twilio.init} singleton, which can
     * be initialized elsewhere in the app with a different credential set.
     *
     * <p>Preference order:
     * <ol>
     *   <li>Account SID + Auth Token (always paired, most reliable).</li>
     *   <li>API Key + API Secret + Account SID (fall-through if Auth Token is
     *       missing).</li>
     * </ol>
     */
    private TwilioRestClient buildRestClient() {
        String accountSid = config.getAccountSid();
        String authToken = config.getAuthToken();
        if (authToken != null && !authToken.isBlank()) {
            return new TwilioRestClient.Builder(accountSid, authToken).build();
        }
        String apiKey = config.getApiKey();
        String apiSecret = config.getApiSecret();
        if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
            return new TwilioRestClient.Builder(apiKey, apiSecret)
                .accountSid(accountSid)
                .build();
        }
        throw new IllegalStateException(
            "No Twilio credentials configured (need TWILIO_AUTH_TOKEN or TWILIO_API_KEY + TWILIO_API_SECRET)");
    }
}
