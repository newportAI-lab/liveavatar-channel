package com.newportai.liveavatar.channel.server.service;

import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.AudioConfigData;
import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.server.session.AvatarSession;
import com.newportai.liveavatar.channel.server.session.SessionManager;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Message processing service for handling business logic.
 *
 * <p><b>TTS ownership</b> is controlled by {@code avatar.tts.developer-enabled}:
 * <ul>
 *   <li><b>true (Developer TTS):</b> This server owns TTS. After streaming text chunks it
 *       pushes TTS audio Binary Frames to the platform, wrapped with
 *       {@code response.audio.start} / {@code response.audio.finish}. {@code response.start}
 *       is NOT sent.</li>
 *   <li><b>false (Platform TTS):</b> The platform owns TTS. This server sends
 *       {@code response.start} (with audio config) so the platform knows how to synthesise
 *       speech, then sends text chunks only. The platform will send
 *       {@code response.audio.start} / {@code response.audio.finish} back when audio
 *       playback starts and ends.</li>
 * </ul>
 */
@Service
public class MessageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessingService.class);

    /** Whether this server (developer) provides TTS. */
    @Value("${avatar.tts.developer-enabled:true}")
    private boolean developerTtsEnabled;

    @Autowired
    private SessionManager sessionManager;

    private ThreadPoolTaskExecutor executor;

    @PostConstruct
    public void init() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("msg-processor-");
        executor.initialize();
    }

    /**
     * Process text input (from input.text or ASR recognition result)
     */
    public void processTextInput(WebSocketSession session, String text, String requestId) {
        AvatarSession avatarSession = sessionManager.getSessionByWsId(session.getId());
        if (avatarSession == null) {
            logger.warn("Session not found for text input processing");
            return;
        }

        logger.info("Processing text input: {}", text);

        // Submit async task for response generation
        Future<?> task = executor.submit(() -> {
            try {
                generateStreamingResponse(session, avatarSession, text, requestId);
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    logger.info("Response task cancelled due to interrupt");
                } else {
                    logger.error("Error generating response", e);
                }
            }
        });

        // Track the response task (for cancellation on interrupt)
        String responseId = "res_" + System.currentTimeMillis();
        avatarSession.startResponse(responseId, task);
    }

    /**
     * Generate streaming response.
     *
     * <p>TTS ownership determines which signals are sent:
     * <ul>
     *   <li><b>Platform TTS</b>: send {@code response.start} (audio config) so the platform
     *       knows how to synthesise speech, then stream text chunks + {@code response.done}.
     *       The platform will send {@code response.audio.start/finish} back when it plays
     *       audio on the avatar.</li>
     *   <li><b>Developer TTS</b>: stream text chunks + {@code response.done}, then push TTS
     *       audio Binary Frames wrapped with {@code response.audio.start} /
     *       {@code response.audio.finish}. Do NOT send {@code response.start}.</li>
     * </ul>
     */
    private void generateStreamingResponse(WebSocketSession session, AvatarSession avatarSession,
                                             String text, String requestId) throws IOException, InterruptedException, MessageSerializationException {
        // 1. Call AI service (mocked)
        String aiResponse = callAIService(text);

        // 2. Split by sentence (not by character)
        String[] sentences = splitBySentence(aiResponse);

        String responseId = avatarSession.getActiveResponseId();

        if (!developerTtsEnabled) {
            // Platform TTS: send audio config so the platform knows how to synthesise speech.
            // The platform will drive audio playback and send response.audio.start/finish back.
            AudioConfigData audioConfig = new AudioConfigData(1.0, 1.0, "neutral");
            Message responseStart = MessageBuilder.responseStart(requestId, responseId, audioConfig);
            sendMessage(session, responseStart);
            logger.debug("Sent response.start (platform TTS audio config)");
        }

        // 3. Stream text chunks (transcript)
        for (int i = 0; i < sentences.length; i++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Response streaming interrupted");
                return;
            }
            Message chunk = MessageBuilder.responseChunk(requestId, responseId, i, sentences[i]);
            sendMessage(session, chunk);
            Thread.sleep(100);
        }

        // 4. Signal text response complete
        Message done = MessageBuilder.responseDone(requestId, responseId);
        sendMessage(session, done);

        if (developerTtsEnabled) {
            // Developer TTS: this server owns audio synthesis. Push TTS audio Binary Frames
            // to the platform, wrapped with response.audio.start / response.audio.finish.
            Message audioStart = MessageBuilder.responseAudioStart(requestId, responseId);
            sendMessage(session, audioStart);
            logger.debug("Sent response.audio.start (developer TTS)");

            // TODO: Push TTS audio frames as binary messages here, e.g.:
            //   for (AudioFrame frame : ttsService.generateAudio(aiResponse)) {
            //       sendAudioFrame(session, frame);
            //   }

            Message audioFinish = MessageBuilder.responseAudioFinish(requestId, responseId);
            sendMessage(session, audioFinish);
            logger.debug("Sent response.audio.finish (developer TTS)");
        }

        // Mark response as complete
        avatarSession.completeResponse();
        logger.info("Response completed: {}", responseId);
    }

    /**
     * Mock AI service call (replace with real AI integration)
     */
    private String callAIService(String text) {
        // TODO: Integrate with real AI services:
        // - OpenAI GPT
        // - Anthropic Claude
        // - Local LLM models

        return "This is a mock AI response to your question: \"" + text + "\". " +
                "I understand you want to learn more about this topic. " +
                "Let me provide you with a detailed explanation. " +
                "First, this question involves several aspects. " +
                "Second, we need to consider the practical application scenarios. " +
                "Finally, I hope this information is helpful to you.";
    }

    /**
     * Split text by sentence (fix the character splitting issue)
     */
    private String[] splitBySentence(String text) {
        // Split by Chinese and English punctuation
        return text.split("(?<=[。！？.!?])");
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
     * Send audio frame to WebSocket session as binary message (thread-safe)
     */
    @SuppressWarnings("unused")
    private void sendAudioFrame(WebSocketSession session, AudioFrame frame) throws IOException {
        byte[] frameBytes = frame.encode();
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new BinaryMessage(frameBytes));
            }
        }
    }
}
