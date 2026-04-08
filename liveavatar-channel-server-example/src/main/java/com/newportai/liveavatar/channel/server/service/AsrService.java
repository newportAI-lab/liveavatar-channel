package com.newportai.liveavatar.channel.server.service;

import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.server.session.AvatarSession;
import com.newportai.liveavatar.channel.server.session.SessionManager;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

/**
 * ASR (Automatic Speech Recognition) Service
 *
 * This is a MOCK implementation for demonstration purposes.
 * In production, you should integrate with real ASR services such as:
 * - Alibaba Cloud Real-time ASR: https://help.aliyun.com/document_detail/84428.html
 * - Tencent Cloud ASR: https://cloud.tencent.com/document/product/1093
 * - Baidu ASR: https://ai.baidu.com/tech/speech/asr
 *
 * <p><b>Voice state is NOT managed here.</b> Voice lifecycle (voiceActive flag,
 * voice.start/finish signals, interrupt logic) is entirely owned by
 * {@code AvatarChannelWebSocketHandler}. This service only:
 * <ol>
 *   <li>Detects voice activity (VAD) on demand</li>
 *   <li>Accumulates audio and sends incremental partial results</li>
 *   <li>Returns the final recognition result when told the utterance is done</li>
 * </ol>
 */
@Service
public class AsrService {

    private static final Logger logger = LoggerFactory.getLogger(AsrService.class);
    private static final double VOICE_ACTIVITY_THRESHOLD = 1000000;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private MessageProcessingService messageProcessingService;

    /**
     * VAD Detection: returns true if the frame contains user speech.
     *
     * <p>This is a simple energy-based detection. In production, use:
     * WebRTC VAD, a deep-learning VAD model, or the ASR service's built-in VAD.
     */
    public boolean detectVoiceActivity(AudioFrame frame) {
        double energy = calculateAudioEnergy(frame.getPayload());
        return energy > VOICE_ACTIVITY_THRESHOLD;
    }

    /**
     * Accumulate audio and send an incremental partial ASR result.
     *
     * <p>Call once per audio frame while the user is speaking. Does NOT touch voice state.
     */
    public void accumulateAudio(WebSocketSession session, AudioFrame frame) {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for audio accumulation");
            return;
        }

        avatarSession.addAudioBuffer(frame.getPayload());

        // Send incremental partial result (real-time subtitles)
        int buffered = avatarSession.getAudioBuffer().size();
        try {
            Message partialMsg = MessageBuilder.asrPartial(
                    avatarSession.getCurrentRequestId(),
                    buffered,
                    "Recognizing... (" + buffered + " frames)"
            );
            sendMessage(session, partialMsg);
        } catch (IOException | MessageSerializationException e) {
            logger.error("Error sending ASR partial result", e);
        }
    }

    /**
     * Finalize ASR for the current utterance.
     *
     * <p>Called by the handler when VAD detects end-of-speech (after input.voice.finish
     * has been sent). Sends the final recognition result, clears the audio buffer,
     * and triggers downstream AI processing.
     *
     * <p>Does NOT touch voice state — that is the handler's responsibility.
     */
    public void finalizeAsr(WebSocketSession session) {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for ASR finalization");
            return;
        }

        List<byte[]> buffer = avatarSession.getAudioBuffer();
        String finalText = recognizeSpeech(buffer);
        logger.info("ASR final: {} frames → \"{}\"", buffer.size(), finalText);

        try {
            Message finalMsg = MessageBuilder.asrFinal(
                    avatarSession.getCurrentRequestId(),
                    finalText
            );
            sendMessage(session, finalMsg);
        } catch (IOException | MessageSerializationException e) {
            logger.error("Error sending ASR final result", e);
            return;
        }

        avatarSession.clearAudioBuffer();

        messageProcessingService.processTextInput(
                session,
                finalText,
                avatarSession.getCurrentRequestId()
        );
    }

    /**
     * Mock final speech recognition (replace with real ASR service in production).
     */
    private String recognizeSpeech(List<byte[]> audioBuffer) {
        // TODO: Call real ASR service with the full audio buffer
        // e.g. Alibaba Cloud: https://help.aliyun.com/document_detail/84428.html
        if (audioBuffer.isEmpty()) {
            return "";
        }
        return "Hello, this is a test ASR recognition result";
    }

    /**
     * Calculate audio energy (for VAD detection).
     */
    private double calculateAudioEnergy(byte[] pcmData) {
        double sum = 0;
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i + 1] << 8) | (pcmData[i] & 0xFF));
            sum += sample * sample;
        }
        return sum / (pcmData.length / 2);
    }

    /**
     * Send message to WebSocket session.
     */
    private void sendMessage(WebSocketSession session, Message message) throws IOException, MessageSerializationException {
        String json = MessageBuilder.toJson(message);
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }
}
