package com.twilio.agentconnect.server.controller;

import com.twilio.agentconnect.channels.voice.VoiceChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TwimlController}.
 */
@ExtendWith(MockitoExtension.class)
class TwimlControllerTest {

    @Mock
    private VoiceChannel voiceChannel;

    private TwimlController controller;

    @BeforeEach
    void setUp() {
        controller = new TwimlController(voiceChannel);
    }

    @Test
    void generateTwimlReturnsXmlBodyFromVoiceChannel() {
        String twiml = "<Response><Connect/></Response>";
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA123");

        when(voiceChannel.generateTwiml(anyMap())).thenReturn(Mono.just(twiml));

        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(twiml, response.getBody());
                assertEquals(MediaType.APPLICATION_XML, response.getHeaders().getContentType());
            })
            .verifyComplete();

        verify(voiceChannel).generateTwiml(params);
    }

    @Test
    void generateTwimlReturns500OnError() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA999");

        when(voiceChannel.generateTwiml(anyMap()))
            .thenReturn(Mono.error(new RuntimeException("generation failed")));

        // onErrorReturn(internalServerError) converts the error into a 500 response.
        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
            .verifyComplete();
    }

    @Test
    void generateTwimlHandlesMissingCallSid() {
        Map<String, String> params = new HashMap<>();
        String twiml = "<Response/>";
        when(voiceChannel.generateTwiml(anyMap())).thenReturn(Mono.just(twiml));

        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(twiml, response.getBody());
            })
            .verifyComplete();
    }
}
