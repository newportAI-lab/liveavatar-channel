package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.model.Message;

/**
 * Message builder utility for creating protocol messages
 */
public class MessageBuilder {

    private MessageBuilder() {
    }

    /**
     * Create session.init message
     */
    public static Message sessionInit(String sessionId, String userId) {
        Message message = new Message(EventType.SESSION_INIT);
        message.setData(new SessionInitData(sessionId, userId));
        return message;
    }

    /**
     * Create session.ready message
     */
    public static Message sessionReady() {
        return new Message(EventType.SESSION_READY);
    }

    /**
     * Create session.state message
     */
    public static Message sessionState(String state, int seq) {
        Message message = new Message(EventType.SESSION_STATE);
        message.setSeq(seq);
        message.setTimestamp(System.currentTimeMillis());
        message.setData(new SessionStateData(state));
        return message;
    }

    /**
     * Create session.closing message
     */
    public static Message sessionClose(String reason) {
        Message message = new Message(EventType.SESSION_CLOSING);
        message.setData(new CloseReasonData(reason));
        return message;
    }

    /**
     * Create input.text message
     */
    public static Message inputText(String requestId, String text) {
        Message message = new Message(EventType.INPUT_TEXT);
        message.setRequestId(requestId);
        message.setData(new TextData(text));
        return message;
    }

    /**
     * Create input.asr.partial message
     */
    public static Message asrPartial(String requestId, int seq, String text) {
        Message message = new Message(EventType.INPUT_ASR_PARTIAL);
        message.setRequestId(requestId);
        message.setSeq(seq);
        message.setData(new TextData(text, false));
        return message;
    }

    /**
     * Create input.asr.final message
     */
    public static Message asrFinal(String requestId, String text) {
        Message message = new Message(EventType.INPUT_ASR_FINAL);
        message.setRequestId(requestId);
        message.setData(new TextData(text));
        return message;
    }

    /**
     * Create response.start message (optional).
     * Send before response.chunk to configure TTS behavior when TTS is provided by the Live Avatar Service.
     * Not needed when TTS is provided by the developer.
     */
    public static Message responseStart(String requestId, String responseId, AudioConfigData audioConfig) {
        Message message = new Message(EventType.RESPONSE_START);
        message.setRequestId(requestId);
        message.setResponseId(responseId);
        message.setData(new ResponseStartData(audioConfig));
        return message;
    }

    /**
     * Create response.chunk message
     */
    public static Message responseChunk(String requestId, String responseId, int seq, String text) {
        Message message = new Message(EventType.RESPONSE_CHUNK);
        message.setRequestId(requestId);
        message.setResponseId(responseId);
        message.setSeq(seq);
        message.setTimestamp(System.currentTimeMillis());
        message.setData(new TextData(text));
        return message;
    }

    /**
     * Create response.done message
     */
    public static Message responseDone(String requestId, String responseId) {
        Message message = new Message(EventType.RESPONSE_DONE);
        message.setRequestId(requestId);
        message.setResponseId(responseId);
        return message;
    }

    /**
     * Create response.cancel message
     */
    public static Message responseCancel(String responseId) {
        Message message = new Message(EventType.RESPONSE_CANCEL);
        message.setResponseId(responseId);
        return message;
    }

    /**
     * Create control.interrupt message
     */
    public static Message controlInterrupt() {
        return new Message(EventType.CONTROL_INTERRUPT);
    }

    /**
     * Create system.prompt message
     */
    public static Message systemPrompt(String text) {
        Message message = new Message(EventType.SYSTEM_PROMPT);
        message.setData(new TextData(text));
        return message;
    }

    /**
     * Create system.idleTrigger message
     */
    public static Message systemIdleTrigger(String reason, long idleTimeMs) {
        Message message = new Message(EventType.SYSTEM_IDLE_TRIGGER);
        message.setData(new IdleTriggerData(reason, idleTimeMs));
        return message;
    }

    /**
     * Create error message
     */
    public static Message error(String requestId, String code, String errorMessage) {
        Message message = new Message(EventType.ERROR);
        message.setRequestId(requestId);
        message.setData(new ErrorData(code, errorMessage));
        return message;
    }

    /**
     * Create input.voice.start message
     */
    public static Message inputVoiceStart(String requestId) {
        Message message = new Message(EventType.INPUT_VOICE_START);
        message.setRequestId(requestId);
        return message;
    }

    /**
     * Create input.voice.finish message
     */
    public static Message inputVoiceFinish(String requestId) {
        Message message = new Message(EventType.INPUT_VOICE_FINISH);
        message.setRequestId(requestId);
        return message;
    }

    /**
     * Create response.audio.start message
     */
    public static Message responseAudioStart(String requestId, String responseId) {
        Message message = new Message(EventType.RESPONSE_AUDIO_START);
        message.setRequestId(requestId);
        message.setResponseId(responseId);
        return message;
    }

    /**
     * Create response.audio.finish message
     */
    public static Message responseAudioFinish(String requestId, String responseId) {
        Message message = new Message(EventType.RESPONSE_AUDIO_FINISH);
        message.setRequestId(requestId);
        message.setResponseId(responseId);
        return message;
    }

    /**
     * Create response.audio.promptStart message
     */
    public static Message responseAudioPromptStart() {
        return new Message(EventType.RESPONSE_AUDIO_PROMPT_START);
    }

    /**
     * Create response.audio.promptFinish message
     */
    public static Message responseAudioPromptFinish() {
        return new Message(EventType.RESPONSE_AUDIO_PROMPT_FINISH);
    }

    /**
     * Serialize message to JSON
     */
    public static String toJson(Message message) throws MessageSerializationException {
        return JsonUtil.toJson(message);
    }
}
