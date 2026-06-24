package com.twilio.agentconnect.channels.voice;

import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.http.TwilioRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link HumanAgentDialer}. Coverage focuses on the early-out
 * (no agent configured) and failure paths; the success path requires deep
 * Twilio SDK mocking and is exercised by the live smoke test.
 */
@ExtendWith(MockitoExtension.class)
class HumanAgentDialerTest {

    @Mock
    private TwilioRestClient restClient;

    private TacConfiguration config;
    private HumanAgentDialer dialer;

    @BeforeEach
    void setUp() {
        config = new TacConfiguration();
        config.setAccountSid("ACtest");
        config.setAuthToken("authtoken");
        config.setApiKey("SKtest");
        config.setApiSecret("apisecret");
        config.setPhoneNumber("+15559876543");
        dialer = new HumanAgentDialer(config, restClient);
    }

    @Test
    void returnsNullWhenAgentNumberNotConfigured() {
        // handoffAgentNumber is null by default
        String sid = dialer.dialAgent("ctx_1", "https://example.com");
        assertNull(sid);
    }

    @Test
    void returnsNullWhenAgentNumberIsBlank() {
        config.getVoice().setHandoffAgentNumber("   ");
        String sid = dialer.dialAgent("ctx_1", "https://example.com");
        assertNull(sid);
    }

    @Test
    void returnsNullWhenTwilioCallCreationThrows() {
        // restClient is a deep mock that returns null for HTTP calls; the SDK
        // will throw when it tries to parse a non-existent response. The dialer
        // catches that and returns null rather than propagating.
        config.getVoice().setHandoffAgentNumber("+15551234567");
        String sid = dialer.dialAgent("ctx_1", "https://example.com");
        assertNull(sid);
    }
}
