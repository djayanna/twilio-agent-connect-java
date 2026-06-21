package com.twilio.agentconnect.validation;


import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.security.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates Twilio webhook signatures.
 */
@Component
public class TwilioSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(TwilioSignatureValidator.class);

    private final TacConfiguration config;

    public TwilioSignatureValidator(TacConfiguration config) {
        this.config = config;
    }

    /**
     * Validate Twilio signature for a webhook request.
     *
     * @param signature The X-Twilio-Signature header value
     * @param url The full URL of the request
     * @param params The request parameters
     * @return true if signature is valid
     */
    public boolean validate(String signature, String url, Map<String, String> params) {
        if (signature == null || signature.isEmpty()) {
            log.warn("No Twilio signature provided");
            return false;
        }

        try {
            RequestValidator validator = new RequestValidator(config.getAuthToken());
            boolean isValid = validator.validate(url, params, signature);

            if (!isValid) {
                log.warn("Invalid Twilio signature for URL: {}", url);
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error validating Twilio signature", e);
            return false;
        }
    }
}
