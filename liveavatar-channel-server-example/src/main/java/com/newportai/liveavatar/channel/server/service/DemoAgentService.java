package com.newportai.liveavatar.channel.server.service;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.agent.AvatarAgent;
import com.newportai.liveavatar.channel.agent.AvatarAgentConfig;
import com.newportai.liveavatar.channel.agent.SessionInfo;
import com.newportai.liveavatar.channel.exception.AvatarChannelException;
import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates AvatarAgent usage in a Spring Boot application.
 *
 * <p>Manages session lifecycle and provides a simple echo AI.
 * Replace {@link #onTextInput(String, String)} with your own LLM/Agent logic.
 */
@Service
public class DemoAgentService implements AgentListener {

    private static final Logger logger = LoggerFactory.getLogger(DemoAgentService.class);

    @Value("${avatar.api.key}")
    private String apiKey;

    @Value("${avatar.id}")
    private String defaultAvatarId;

    @Value("${avatar.api.base-url:https://facemarket.ai}")
    private String baseUrl;

    @Value("${avatar.sandbox.enabled:false}")
    private boolean sandbox;

    @Value("${avatar.asr.developer-enabled:true}")
    private boolean developerAsr;

    @Value("${avatar.tts.developer-enabled:false}")
    private boolean developerTts;

    private final Map<String, AvatarAgent> sessions = new ConcurrentHashMap<>();

    /**
     * Creates and starts a new agent session.
     *
     * @return SessionInfo containing userToken and sfuUrl for the frontend
     * @throws AvatarChannelException if the REST call or WebSocket connection fails
     */
    public SessionInfo startSession(String avatarId, String voiceId) throws AvatarChannelException {
        String effectiveAvatarId = avatarId != null ? avatarId : defaultAvatarId;

        AvatarAgent agent = AvatarAgent.builder()
                .config(AvatarAgentConfig.builder()
                        .apiKey(apiKey)
                        .avatarId(effectiveAvatarId)
                        .baseUrl(baseUrl)
                        .sandbox(sandbox)
                        .developerAsr(developerAsr)
                        .developerTts(developerTts)
                        .voiceId(voiceId)
                        .build())
                .listener(this)
                .build();

        SessionInfo info = agent.start();
        sessions.put(info.getSessionId(), agent);
        logger.info("Session started: {}", info.getSessionId());
        return info;
    }

    /** Stops an active session. Safe to call with unknown session IDs. */
    public void stopSession(String sessionId) {
        AvatarAgent agent = sessions.remove(sessionId);
        if (agent != null) {
            agent.stop();
            logger.info("Session stopped: {}", sessionId);
        }
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(AvatarAgent::stop);
        sessions.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentListener — replace the logic below with your own AI
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onTextInput(String text, String requestId) {
        logger.info("Received input: {}", text);

        // TODO: Replace with your AI call, e.g.:
        //   String reply = llmService.chat(text);
        //   agent.sendResponseChunk(requestId, reply, 0);
        //   agent.sendResponseDone(requestId);
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
        // Developer ASR mode: raw audio from platform.
        // Run VAD + ASR here, then use agent.sendVoiceStart / sendAsrPartial / etc.
        logger.debug("Audio frame: seq={}, payload={} bytes",
                frame.getHeader().getSequence(), frame.getPayload().length);
    }

    @Override
    public void onSessionInit() {
        logger.info("Session init received — avatar is active");
    }

    @Override
    public void onSessionState(SessionState state) {
        logger.debug("Session state: {}", state.getValue());
    }

    @Override
    public void onIdleTrigger(String reason, long idleMs) {
        logger.info("Idle trigger: reason={}, idleMs={}", reason, idleMs);
    }

    @Override
    public void onError(String message) {
        logger.error("Agent error: {}", message);
    }

    @Override
    public void onClosed(int code, String reason) {
        logger.info("Connection closed: code={}, reason={}", code, reason);
    }
}
