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
    public SessionInfo startSession(String avatarId, String voiceId, String rtcProvider) throws AvatarChannelException {
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
                        .rtcProvider(rtcProvider)
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
        //   ResponseStream response = agent.beginResponse(requestId);
        //   response.sendChunk(reply);
        //   response.done();
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

    /*
     * Call examples. Nothing in this comment runs.
     *
     * AvatarAgentConfig.Builder: apiKey, avatarId, baseUrl, sandbox, developerAsr,
     * developerTts, reconnectEnabled, voiceId, userId, rtcProvider, voiceConfig.
     * externalAudioConfig is optional. sampleRate 0 follows the session voice format;
     * otherwise use 16000, 24000, 44100, or 48000. channels 0 follows voice; otherwise 1 or 2.
     * The PCM passed to sendExternalAudio must use that same rate and channel count.
     * If externalAudioConfig is omitted, match the session TTS audio format instead.
     *
     * AvatarAgent agent = AvatarAgent.builder()
     *     .config(AvatarAgentConfig.builder()
     *         .apiKey(apiKey).avatarId(avatarId).baseUrl(baseUrl)
     *         .externalAudioConfig(ExternalAudioConfig.builder().sampleRate(48000).channels(2).build())
     *         .build())
     *     .listener(this).build();
     * SessionInfo info = agent.start();
     * agent.isConnected();
     * agent.getSessionInfo();
     *
     * ResponseStream response = agent.beginResponse(requestId);
     * response.start(audioConfig); // platform TTS settings before the first chunk
     * response.sendChunk("Hello");
     * response.done();
     * response.cancel();
     * // Deprecated. Prefer beginResponse:
     * agent.sendResponseStart(requestId, audioConfig);
     * agent.sendResponseChunk(requestId, "Hello", 1);
     * agent.sendResponseDone(requestId);
     * agent.sendResponseCancel(responseId);
     *
     * agent.sendResponseAudioStart(requestId, responseId);
     * agent.sendAudioFrame(frame); // 9-byte voice frame. This drives lip sync.
     * agent.sendResponseAudioFinish(requestId, responseId);
     * agent.sendPromptAudioStart();
     * agent.sendPromptAudioFinish();
     *
     * agent.sendVoiceStart(requestId);
     * agent.sendAsrPartial(requestId, 1, "partial text");
     * agent.sendVoiceFinish(requestId);
     * agent.sendAsrFinal(requestId, "final text");
     *
     * agent.sendPrompt("Are you still there?");
     * PromptStream prompt = agent.beginPrompt(requestId);
     * prompt.sendChunk("Hello");
     * prompt.done();
     * PromptStream named = agent.beginPrompt(requestId, responseId);
     *
     * byte[] pcm = new byte[38400]; // 200 ms, 48000 Hz, stereo, 16-bit
     * agent.sendExternalAudio(pcm); // timestamp 0 lets the platform choose the time
     * agent.sendExternalAudio(pcm, 0L);
     * // One frame including its header must stay within 65536 bytes.
     * // Send the next packet before the previous audio finishes playing.
     * // The first packet may be longer than later packets, but not over that limit.
     *
     * agent.sendInterrupt(); // same as VOICE_ONLY
     * agent.sendInterrupt(InterruptScope.VOICE_ONLY); // stop voice only
     * agent.sendInterrupt(InterruptScope.BGM_ONLY); // clear queued external audio only
     * agent.sendInterrupt(InterruptScope.VOICE_AND_ACCOMPANIMENT); // stop both
     *
     * agent.sendCustomEvent(requestId, "scene.switchVideo", Map.of(
     *     "onceVideos", java.util.List.of("fist_bump"), // resourceId or alias
     *     "loopVideos", java.util.List.of("01KTJNMD7NZJF4KMGCCFSTJN4X")));
     *
     * agent.stop();
     *
     * Listener methods you can override: onTextInput, onAudioFrame, onSessionInit,
     * onSceneReady, onSessionState, onResponseState, onResourceTransition,
     * onIdleTrigger, onSessionClosing, onError, onClosed.
     */
}
