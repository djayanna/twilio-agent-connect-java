package com.twilio.agentconnect.validation;

import com.twilio.agentconnect.core.TacConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TwilioSignatureValidator}.
 *
 * <p>The validator wraps Twilio's {@code RequestValidator}, which computes an
 * HMAC-SHA1 over the URL concatenated with the sorted request parameters and
 * Base64-encodes the result. The tests compute the expected signature with the
 * same library so a correct signature validates true and a tampered one false,
 * without depending on any hard-coded constant.
 */
class TwilioSignatureValidatorTest {

    private static final String AUTH_TOKEN = "test_auth_token_1234567890";
    private static final String URL = "https://example.com/webhooks/sms";

    private TacConfiguration config;
    private TwilioSignatureValidator validator;

    @BeforeEach
    void setUp() {
        config = new TacConfiguration();
        config.setAuthToken(AUTH_TOKEN);
        validator = new TwilioSignatureValidator(config);
    }

    private Map<String, String> params() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("From", "+15551234567");
        params.put("To", "+15557654321");
        params.put("Body", "Hello world");
        return params;
    }

    /**
     * Computes the Twilio signature using the exact algorithm RequestValidator uses
     * (URL + sorted key+value, HMAC-SHA1, Base64).
     */
    private String computeSignature(String url, Map<String, String> params) {
        try {
            StringBuilder builder = new StringBuilder(url);
            java.util.List<String> keys = new java.util.ArrayList<>(params.keySet());
            java.util.Collections.sort(keys);
            for (String key : keys) {
                builder.append(key);
                String value = params.get(key);
                builder.append(value == null ? "" : value);
            }
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                AUTH_TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] raw = mac.doFinal(builder.toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validatesCorrectSignature() {
        Map<String, String> params = params();
        String signature = computeSignature(URL, params);

        assertThat(validator.validate(signature, URL, params)).isTrue();
    }

    @Test
    void rejectsWrongSignature() {
        Map<String, String> params = params();

        assertThat(validator.validate("totally-wrong-signature", URL, params)).isFalse();
    }

    @Test
    void rejectsTamperedParams() {
        Map<String, String> params = params();
        String signature = computeSignature(URL, params);

        // Mutate a parameter after signing; signature should no longer match.
        params.put("Body", "Goodbye world");

        assertThat(validator.validate(signature, URL, params)).isFalse();
    }

    @Test
    void rejectsNullSignature() {
        assertThat(validator.validate(null, URL, params())).isFalse();
    }

    @Test
    void rejectsEmptySignature() {
        assertThat(validator.validate("", URL, params())).isFalse();
    }

    @Test
    void handlesExceptionGracefully() {
        // A null auth token causes RequestValidator construction to throw;
        // the validator must catch it and return false rather than propagate.
        Map<String, String> params = params();
        String signature = computeSignature(URL, params);
        config.setAuthToken(null);

        assertThat(validator.validate(signature, URL, params)).isFalse();
    }
}
