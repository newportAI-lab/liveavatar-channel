package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.*;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for MessageBuilder
 */
public class MessageBuilderTest {

    @Test
    public void testSessionInit() throws MessageSerializationException {
        Message message = MessageBuilder.sessionInit("sess_123", "user_1");

        assertEquals(EventType.SESSION_INIT, message.getEvent());
        assertNotNull(message.getData());

        SessionInitData data = JsonUtil.convertData(message.getData(), SessionInitData.class);
        assertEquals("sess_123", data.getSessionId());
        assertEquals("user_1", data.getUserId());

        // Verify JSON format
        String json = MessageBuilder.toJson(message);
        assertTrue(json.contains("\"sessionId\":\"sess_123\""));
        assertTrue(json.contains("\"userId\":\"user_1\""));
    }

    @Test
    public void testSessionReady() {
        Message message = MessageBuilder.sessionReady();

        assertEquals(EventType.SESSION_READY, message.getEvent());
        assertNull(message.getSessionId());
    }

    @Test
    public void testSessionState() {
        Message message = MessageBuilder.sessionState(SessionState.SPEAKING.getValue(), 5);

        assertEquals(EventType.SESSION_STATE, message.getEvent());
        assertNull(message.getSessionId());
        assertEquals(Integer.valueOf(5), message.getSeq());

        SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
        assertEquals("SPEAKING", data.getState());
    }

    @Test
    public void testAllSessionStates() {
        for (SessionState state : SessionState.values()) {
            Message message = MessageBuilder.sessionState(state.getValue(), 1);
            SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
            assertEquals(state.getValue(), data.getState());

            // Test fromValue
            SessionState parsedState = SessionState.fromValue(data.getState());
            assertEquals(state, parsedState);
        }
    }

    @Test
    public void testInputText() throws MessageSerializationException {
        Message message = MessageBuilder.inputText("req_1", "Hello");

        assertEquals(EventType.INPUT_TEXT, message.getEvent());
        assertEquals("req_1", message.getRequestId());

        TextData data = JsonUtil.convertData(message.getData(), TextData.class);
        assertEquals("Hello", data.getText());
    }

    @Test
    public void testResponseChunk() {
        Message message = MessageBuilder.responseChunk("req_1", "res_1", 5, "Hello");

        assertEquals(EventType.RESPONSE_CHUNK, message.getEvent());
        assertNull(message.getSessionId());
        assertEquals("req_1", message.getRequestId());
        assertEquals("res_1", message.getResponseId());
        assertEquals(Integer.valueOf(5), message.getSeq());
        assertNotNull(message.getTimestamp());
    }

    @Test
    public void testControlInterrupt() {
        Message message = MessageBuilder.controlInterrupt();

        assertEquals(EventType.CONTROL_INTERRUPT, message.getEvent());
        assertNull(message.getSessionId());
    }

    @Test
    public void testErrorMessage() {
        Message message = MessageBuilder.error("req_1", "ASR_FAIL", "Audio decode error");

        assertEquals(EventType.ERROR, message.getEvent());
        assertNull(message.getSessionId());
        assertEquals("req_1", message.getRequestId());

        ErrorData data = JsonUtil.convertData(message.getData(), ErrorData.class);
        assertEquals("ASR_FAIL", data.getCode());
        assertEquals("Audio decode error", data.getMessage());
    }

    @Test
    public void testJsonSerialization() throws MessageSerializationException {
        Message message = MessageBuilder.inputText("req_1", "test");
        String json = MessageBuilder.toJson(message);

        assertNotNull(json);
        assertTrue(json.contains("input.text"));
        assertTrue(json.contains("test"));

        // Test deserialization
        Message parsed = JsonUtil.fromJson(json);
        assertEquals(message.getEvent(), parsed.getEvent());
        assertEquals(message.getRequestId(), parsed.getRequestId());
    }

    @Test
    public void testNewAudioEvents() throws MessageSerializationException {
        // input.voice.start / finish
        Message voiceStart = MessageBuilder.inputVoiceStart("req_1");
        assertEquals(EventType.INPUT_VOICE_START, voiceStart.getEvent());
        assertEquals("req_1", voiceStart.getRequestId());

        Message voiceFinish = MessageBuilder.inputVoiceFinish("req_1");
        assertEquals(EventType.INPUT_VOICE_FINISH, voiceFinish.getEvent());

        // response.audio.start / finish
        Message audioStart = MessageBuilder.responseAudioStart("req_1", "res_1");
        assertEquals(EventType.RESPONSE_AUDIO_START, audioStart.getEvent());
        assertEquals("res_1", audioStart.getResponseId());

        Message audioFinish = MessageBuilder.responseAudioFinish("req_1", "res_1");
        assertEquals(EventType.RESPONSE_AUDIO_FINISH, audioFinish.getEvent());

        // response.audio.promptStart / promptFinish
        Message promptStart = MessageBuilder.responseAudioPromptStart();
        assertEquals(EventType.RESPONSE_AUDIO_PROMPT_START, promptStart.getEvent());
        assertNull(promptStart.getRequestId());

        Message promptFinish = MessageBuilder.responseAudioPromptFinish();
        assertEquals(EventType.RESPONSE_AUDIO_PROMPT_FINISH, promptFinish.getEvent());
    }

    @Test
    public void testBusinessExchangeMetadataIsMergedIntoData() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("interviewId", "iv_001");
        metadata.put("questionId", "q_001");
        metadata.put("exchangeId", "ex_002");
        metadata.put("answerId", "ans_002");

        Message voiceStart = MessageBuilder.inputVoiceStart("req_a2", metadata);
        Map<String, Object> voiceData = JsonUtil.convertData(voiceStart.getData(), Map.class);
        assertEquals("iv_001", voiceData.get("interviewId"));
        assertEquals("q_001", voiceData.get("questionId"));
        assertEquals("ex_002", voiceData.get("exchangeId"));
        assertEquals("ans_002", voiceData.get("answerId"));

        Message asrFinal = MessageBuilder.asrFinal("req_a2", "I built payment systems.", metadata);
        Map<String, Object> asrData = JsonUtil.convertData(asrFinal.getData(), Map.class);
        assertEquals("I built payment systems.", asrData.get("text"));
        assertEquals("ex_002", asrData.get("exchangeId"));

        Message prompt = MessageBuilder.systemPrompt("Tell me about your last project.", metadata);
        Map<String, Object> promptData = JsonUtil.convertData(prompt.getData(), Map.class);
        assertEquals("Tell me about your last project.", promptData.get("text"));
        assertEquals("iv_001", promptData.get("interviewId"));
    }

    @Test
    public void testMetadataCannotOverrideProtocolFields() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("text", "metadata text");
        metadata.put("final", true);
        metadata.put("event", "evil.event");
        metadata.put("requestId", "evil_req");
        metadata.put("seq", 99);
        metadata.put("exchangeId", "ex_002");

        Message partial = MessageBuilder.asrPartial("req_a2", 3, "real text", metadata);
        Map<String, Object> data = JsonUtil.convertData(partial.getData(), Map.class);

        assertEquals(EventType.INPUT_ASR_PARTIAL, partial.getEvent());
        assertEquals("req_a2", partial.getRequestId());
        assertEquals(Integer.valueOf(3), partial.getSeq());
        assertEquals("real text", data.get("text"));
        assertEquals(false, data.get("final"));
        assertFalse(data.containsKey("event"));
        assertFalse(data.containsKey("requestId"));
        assertFalse(data.containsKey("seq"));
        assertEquals("ex_002", data.get("exchangeId"));
    }

    @Test
    public void testSuggestedMetadataEvents() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("interviewId", "iv_001");
        metadata.put("questionId", "q_001");
        metadata.put("reason", "skip_question");

        Message inputText = MessageBuilder.inputText("req_t1", "typed answer", metadata);
        Map<String, Object> inputData = JsonUtil.convertData(inputText.getData(), Map.class);
        assertEquals("typed answer", inputData.get("text"));
        assertEquals("iv_001", inputData.get("interviewId"));

        Message responseDone = MessageBuilder.responseDone("req_r1", "res_1", metadata);
        Map<String, Object> responseData = JsonUtil.convertData(responseDone.getData(), Map.class);
        assertEquals("q_001", responseData.get("questionId"));

        Message interrupt = MessageBuilder.controlInterrupt("req_i1", metadata);
        Map<String, Object> interruptData = JsonUtil.convertData(interrupt.getData(), Map.class);
        assertEquals("req_i1", interrupt.getRequestId());
        assertEquals("skip_question", interruptData.get("reason"));
        assertEquals("iv_001", interruptData.get("interviewId"));
    }
}
