package com.newportai.liveavatar.channel.server.handler;

import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.server.service.AsrService;
import com.newportai.liveavatar.channel.server.service.MessageProcessingService;
import com.newportai.liveavatar.channel.server.session.AvatarSession;
import com.newportai.liveavatar.channel.server.session.SessionManager;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;

/**
 * WebSocket handler for Avatar Channel Protocol
 * <p>
 * This handler processes both text (JSON protocol messages) and binary messages.
 * Binary messages from the live avatar service can be either audio frames (user voice input)
 * or image frames (user multimodal input). The developer server never sends image frames back.
 */
@Component
public class AvatarChannelWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvatarChannelWebSocketHandler.class);

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private MessageProcessingService messageProcessingService;

    @Autowired
    private AsrService asrService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("Received text message: {}", payload);

        try {
            Message msg = JsonUtil.fromJson(payload);

            switch (msg.getEvent()) {
                case EventType.SESSION_INIT:
                    handleSessionInit(session, msg);
                    break;
                case EventType.INPUT_TEXT:
                    handleInputTextWithInterrupt(session, msg);
                    break;
                case EventType.SESSION_STATE:
                    handleSessionState(session, msg);
                    break;
                case EventType.SYSTEM_IDLE_TRIGGER:
                    handleIdleTrigger(session, msg);
                    break;
                case EventType.SESSION_CLOSING:
                    handleSessionClosing(session, msg);
                    break;
                default:
                    logger.warn("Unknown event type: {}", msg.getEvent());
            }
        } catch (Exception e) {
            logger.error("Error handling text message", e);
            sendError(session, null, "MESSAGE_PARSE_ERROR", "Failed to parse message: " + e.getMessage());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] frameBytes = message.getPayload().array();
        logger.debug("Received binary message, size: {} bytes", frameBytes.length);

        if (frameBytes.length == 0) return;

        try {
            // Peek at T field (bits 7-6 of first byte) to determine frame type
            byte type = (byte) ((frameBytes[0] >> 6) & 0b11);
            if (type == AudioHeader.TYPE_AUDIO) {
                AudioFrame frame = AudioFrame.parse(frameBytes);
                logger.debug("Parsed audio frame: seq={}, size={}",
                        frame.getHeader().getSequence(), frame.getPayload().length);
                handleAudioFrameWithInterrupt(session, frame);
            } else if (type == ImageHeader.TYPE_IMAGE) {
                ImageFrame frame = ImageFrame.parse(frameBytes);
                logger.debug("Parsed image frame: id={}, format={}, size={}x{}",
                        frame.getHeader().getImageId(),
                        frame.getHeader().getFormat().getName(),
                        frame.getHeader().getWidth(), frame.getHeader().getHeight());
                handleImageFrame(session, frame);
            } else {
                logger.warn("Unknown binary frame type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling binary message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        sessionManager.removeSession(session.getId());
    }

    /**
     * Handle session.init message
     */
    private void handleSessionInit(WebSocketSession session, Message message) throws IOException, MessageSerializationException {
        SessionInitData data = JsonUtil.convertData(message.getData(), SessionInitData.class);
        logger.info("Session init: sessionId={}, userId={}", data.getSessionId(), data.getUserId());

        // Initialize session
        sessionManager.initSession(session.getId(), data.getSessionId(), data.getUserId());

        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        avatarSession.setWsSession(session);

        // Send session.ready response
        Message ready = MessageBuilder.sessionReady();
        sendMessage(session, ready);

        logger.info("Session ready sent: {}", data.getSessionId());
    }

    /**
     * Handle input.text with interrupt detection
     */
    private void handleInputTextWithInterrupt(WebSocketSession session, Message message) throws IOException, MessageSerializationException {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for input.text");
            return;
        }

        TextData input = JsonUtil.convertData(message.getData(), TextData.class);
        logger.info("Received input.text: {}", input.getText());


        // 1. Cancel current response task if there's an active response
        if (avatarSession.hasActiveResponse()) {
            avatarSession.cancelCurrentResponse();
        }

        // 2. Send interrupt signal
        logger.info("Interrupting current response");
        Message interrupt = MessageBuilder.controlInterrupt();
        sendMessage(session, interrupt);

        // 3. Set current request ID
        avatarSession.setCurrentRequestId(message.getRequestId());

        // 4. Process new input
        messageProcessingService.processTextInput(session, input.getText(), message.getRequestId());
    }

    /**
     * Handle audio frame: manage voice state via VAD and delegate to ASR.
     *
     * <p>Voice state is the sole responsibility of this method:
     * <ul>
     *   <li>silent→speaking: send ONE interrupt (only if response active) + input.voice.start</li>
     *   <li>speaking→silent: send input.voice.finish and trigger ASR finalization</li>
     *   <li>continuing speech: accumulate audio + send ASR partials only</li>
     * </ul>
     */
    private void handleAudioFrameWithInterrupt(WebSocketSession session, AudioFrame frame) throws IOException, MessageSerializationException {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for audio frame");
            return;
        }

        boolean voiceDetected = asrService.detectVoiceActivity(frame);

        if (voiceDetected) {
            if (!avatarSession.isVoiceActive()) {
                // silent → speaking transition: interrupt + voice.start, sent exactly once
                avatarSession.setVoiceActive(true);
                avatarSession.setCurrentRequestId("req_" + System.currentTimeMillis());

                if (avatarSession.hasActiveResponse()) {
                    avatarSession.cancelCurrentResponse();
                    Message interrupt = MessageBuilder.controlInterrupt();
                    sendMessage(session, interrupt);
                    logger.info("Voice started, interrupted active response");
                }

                Message voiceStart = MessageBuilder.inputVoiceStart(avatarSession.getCurrentRequestId());
                sendMessage(session, voiceStart);
                logger.info("Sent input.voice.start: {}", avatarSession.getCurrentRequestId());
            }
            // Continuing speech: accumulate audio + send partials (no interrupt)
            asrService.accumulateAudio(session, frame);

        } else {
            if (avatarSession.isVoiceActive()) {
                // speaking → silent transition: finalize the utterance
                avatarSession.setVoiceActive(false);
                Message voiceFinish = MessageBuilder.inputVoiceFinish(avatarSession.getCurrentRequestId());
                sendMessage(session, voiceFinish);
                logger.info("Sent input.voice.finish: {}", avatarSession.getCurrentRequestId());

                // Trigger final ASR result now that the utterance is complete
                asrService.finalizeAsr(session);
            }
        }
    }

    /**
     * Handle image frame from live avatar service (multimodal input from end user).
     *
     * <p>Implement your image processing logic here, e.g., pass to a vision/OCR service.
     */
    private void handleImageFrame(WebSocketSession session, ImageFrame frame) {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for image frame");
            return;
        }
        // TODO: delegate to your image processing / multimodal service
        logger.info("Received image frame: id={}, format={}, {}x{}, {} bytes",
                frame.getHeader().getImageId(),
                frame.getHeader().getFormat().getName(),
                frame.getHeader().getWidth(), frame.getHeader().getHeight(),
                frame.getPayload().length);
    }

    /**
     * Handle session.state message (optional, for monitoring live avatar state)
     */
    private void handleSessionState(WebSocketSession session, Message message) {
        SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
        logger.debug("Received session.state: state={}, seq={}", data.getState(), message.getSeq());
        // Optional: Store state for business logic
    }

    /**
     * Handle system.idleTrigger message (Scene 3: Idle wake-up)
     */
    private void handleIdleTrigger(WebSocketSession session, Message message) throws IOException, MessageSerializationException {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for idle trigger");
            return;
        }

        IdleTriggerData data = JsonUtil.convertData(message.getData(), IdleTriggerData.class);
        logger.info("Received idle trigger: reason={}, idleTimeMs={}", data.getReason(), data.getIdleTimeMs());

        // Determine if we should send a prompt based on business logic
        String promptText = determinePromptText(data);
        if (promptText != null) {
            // Option A: Text prompt — live avatar uses its own built-in TTS to speak the text.
            Message prompt = MessageBuilder.systemPrompt(promptText);
            sendMessage(session, prompt);
            logger.info("Sent system.prompt: {}", promptText);

            // Option B: Audio prompt — developer pushes audio frames instead of relying on
            // the live avatar's TTS. Use this when you want precise control over the voice.
            // Wrap audio delivery with promptStart / promptFinish:
            //
            //   Message promptStart = MessageBuilder.responseAudioPromptStart();
            //   sendMessage(session, promptStart);
            //   for (AudioFrame frame : ttsService.generateAudio(promptText)) {
            //       sendAudioFrame(session, frame);
            //   }
            //   Message promptFinish = MessageBuilder.responseAudioPromptFinish();
            //   sendMessage(session, promptFinish);
        }
    }

    /**
     * Determine prompt text based on idle trigger data (business logic)
     */
    private String determinePromptText(IdleTriggerData data) {
        long idleMs = data.getIdleTimeMs();

        if (idleMs > 180000) { // 3 minutes
            return "Are you still there? Feel free to ask if you need help.";
        } else if (idleMs > 120000) { // 2 minutes
            return "Is there anything else I can help you with?";
        }

        return null; // Don't send prompt
    }

    /**
     * Handle session.closing message
     */
    private void handleSessionClosing(WebSocketSession session, Message message) {
        CloseReasonData data = JsonUtil.convertData(message.getData(), CloseReasonData.class);
        logger.info("Session closing: reason={}", data.getReason());
    }

    /**
     * Send message to WebSocket session (thread-safe)
     */
    private void sendMessage(WebSocketSession session, Message message) throws IOException, MessageSerializationException {
        String json = MessageBuilder.toJson(message);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    /**
     * Send error message
     */
    private void sendError(WebSocketSession session, String requestId, String code, String errorMessage) {
        try {
            Message error = MessageBuilder.error(requestId, code, errorMessage);
            sendMessage(session, error);
        } catch (Exception e) {
            logger.error("Error sending error message", e);
        }
    }
}
