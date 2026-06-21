package com.newportai.liveavatar.channel.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.exception.AvatarChannelException;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level entry point for WebSocket Agent mode.
 *
 * <p>Encapsulates the full session lifecycle: REST call to provision a session,
 * WebSocket connection, protocol handshake, and message routing. The developer
 * only needs to implement {@link AgentListener} with their AI logic.
 *
 * <h3>Quick start (Platform ASR + Platform TTS)</h3>
 * <pre>{@code
 * AvatarAgent agent = AvatarAgent.builder()
 *     .config(AvatarAgentConfig.builder()
 *         .apiKey("lk_live_...")
 *         .avatarId("avatar_...")
 *         .build())
 *     .listener(new AgentListener() {
 *         public void onTextInput(String text, String requestId) {
 *             String reply = callYourAI(text);
 *             agent.sendResponseChunk(requestId, reply, 0);
 *             agent.sendResponseDone(requestId);
 *         }
 *     })
 *     .build();
 *
 * SessionInfo info = agent.start();
 * // Deliver info.getUserToken() + info.getSfuUrl() to your frontend
 * }</pre>
 */
public class AvatarAgent {

    private static final Logger logger = LoggerFactory.getLogger(AvatarAgent.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int START_TIMEOUT_SECONDS = 30;

    private final AvatarAgentConfig config;
    private final AgentListener listener;
    private final OkHttpClient httpClient;
    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

    private AvatarAgent(AvatarAgentConfig config, AgentListener listener, OkHttpClient httpClient) {
        this.config = config;
        this.listener = listener;
        this.httpClient = httpClient;
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private AvatarAgentConfig config;
        private AgentListener listener;
        private OkHttpClient httpClient;

        public Builder config(AvatarAgentConfig config) { this.config = config; return this; }
        public Builder listener(AgentListener listener) { this.listener = listener; return this; }
        public Builder httpClient(OkHttpClient httpClient) { this.httpClient = httpClient; return this; }

        public AvatarAgent build() {
            if (config == null) throw new IllegalStateException("config is required");
            if (listener == null) throw new IllegalStateException("listener is required");
            OkHttpClient client = httpClient != null ? httpClient : defaultHttpClient();
            return new AvatarAgent(config, listener, client);
        }

        private static OkHttpClient defaultHttpClient() {
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .pingInterval(5, TimeUnit.SECONDS)
                    .build();
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public SessionInfo start() throws AvatarChannelException {
        if (state.get().started) throw new IllegalStateException("Already started");

        // 1. Call platform REST API
        SessionInfo info = parseSessionResponse(
                postJson(config.getBaseUrl() + "/v1/session/start", buildStartRequest()));

        // 2. Wrap listener to intercept onSessionInit for the start() latch
        CountDownLatch readyLatch = new CountDownLatch(1);
        AgentListener latched = new LatchedListener(listener, readyLatch);

        // 3. Connect WebSocket
        AvatarWebSocketClient client = new AvatarWebSocketClient(info.getAgentWsUrl(), latched, httpClient);
        if (config.isReconnectEnabled()) client.enableAutoReconnect();
        client.connect();

        // 4. Wait for session.init → session.ready handshake
        try {
            if (!readyLatch.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                client.disableAutoReconnect(); client.disconnect();
                throw new AvatarChannelException("Timed out waiting for session.init after " + START_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            client.disableAutoReconnect(); client.disconnect();
            Thread.currentThread().interrupt();
            throw new AvatarChannelException("Interrupted waiting for session.init", e);
        }

        if (!state.compareAndSet(State.STOPPED, new State(info, client))) {
            client.disableAutoReconnect(); client.disconnect();
            throw new IllegalStateException("Already started");
        }
        return info;
    }

    public void stop() {
        State s = state.getAndSet(State.STOPPED);
        if (!s.started) return;
        try {
            if (s.wsClient.isConnected()) s.wsClient.sendMessage(MessageBuilder.sessionClose("user_stop"));
        } catch (Exception e) {
            logger.debug("Failed to send session.close during stop (connection already gone): {}", e.toString());
        }
        s.wsClient.disableAutoReconnect();
        s.wsClient.disconnect();
    }

    // ── Send: AI Response (Platform TTS) ───────────────────────────────────────

    public void sendResponseStart(String requestId, AudioConfigData audioConfig)
            throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        s.wsClient.sendMessage(MessageBuilder.responseStart(requestId, "res_" + System.currentTimeMillis(), audioConfig));
    }

    public void sendResponseChunk(String requestId, String text, int seq)
            throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        s.wsClient.sendMessage(MessageBuilder.responseChunk(requestId, "res_" + System.currentTimeMillis(), seq, text));
    }

    public void sendResponseDone(String requestId) throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        s.wsClient.sendMessage(MessageBuilder.responseDone(requestId, "res_" + System.currentTimeMillis()));
    }

    public void sendResponseCancel(String responseId) throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        s.wsClient.sendMessage(MessageBuilder.responseCancel(responseId));
    }

    // ── Send: AI Response (Developer TTS) ──────────────────────────────────────

    public void sendResponseAudioStart(String requestId, String responseId)
            throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        s.wsClient.sendMessage(MessageBuilder.responseAudioStart(requestId, responseId));
    }
    public void sendAudioFrame(AudioFrame frame) throws ConnectionException {
        requireStarted().wsClient.sendAudioFrame(frame);
    }
    public void sendResponseAudioFinish(String requestId, String responseId)
            throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        s.wsClient.sendMessage(MessageBuilder.responseAudioFinish(requestId, responseId));
    }

    public void sendPromptAudioStart() throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.responseAudioPromptStart());
    }
    public void sendPromptAudioFinish() throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.responseAudioPromptFinish());
    }

    // ── Send: Developer ASR ────────────────────────────────────────────────────

    public void sendVoiceStart(String requestId) throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.inputVoiceStart(requestId));
    }
    public void sendAsrPartial(String requestId, int seq, String text) throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.asrPartial(requestId, seq, text));
    }
    public void sendVoiceFinish(String requestId) throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.inputVoiceFinish(requestId));
    }
    public void sendAsrFinal(String requestId, String text) throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.asrFinal(requestId, text));
    }

    // ── Send: Control ──────────────────────────────────────────────────────────

    public void sendInterrupt() throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.controlInterrupt());
    }
    public void sendPrompt(String text) throws ConnectionException, MessageSerializationException {
        requireStarted().wsClient.sendMessage(MessageBuilder.systemPrompt(text));
    }

    // ── Send: Custom Event ──────────────────────────────────────────────────────

    /**
     * Send a custom event that is not predefined in the standard protocol.
     * <p>
     * The {@code event} and {@code data} are passed through as-is.
     * Use this for experimental or application-specific events without
     * modifying the SDK.
     *
     * @param requestId optional request identifier
     * @param event     event type name, e.g. {@code "my.custom.event"}
     * @param data      event payload, serialized as JSON (null = omitted)
     */
    public void sendCustomEvent(String requestId, String event, Object data)
            throws ConnectionException, MessageSerializationException {
        State s = requireStarted();
        Message message = new Message(event);
        message.setRequestId(requestId);
        message.setData(data);
        s.wsClient.sendMessage(message);
    }

    // ── State ──────────────────────────────────────────────────────────────────

    public boolean isConnected() { State s = state.get(); return s.started && s.wsClient.isConnected(); }
    public SessionInfo getSessionInfo() { return state.get().sessionInfo; }

    // ── Internal ───────────────────────────────────────────────────────────────

    private State requireStarted() {
        State s = state.get();
        if (!s.started) throw new IllegalStateException("Not started — call start() first");
        return s;
    }

    private String buildStartRequest() {
        StringBuilder sb = new StringBuilder("{\"avatarId\":\"").append(escapeJson(config.getAvatarId())).append('"');
        String vid = config.getVoiceId();
        if (vid != null && !vid.isEmpty()) sb.append(",\"voiceId\":\"").append(escapeJson(vid)).append('"');
        AvatarAgentConfig.VoiceConfig voiceConfig = config.getVoiceConfig();
        if (voiceConfig != null) {
            sb.append(",\"voiceConfig\":{");
            boolean first = true;
            first = appendJsonNumber(sb, "volume", voiceConfig.getVolume(), first);
            first = appendJsonNumber(sb, "speed", voiceConfig.getSpeed(), first);
            first = appendJsonNumber(sb, "stability", voiceConfig.getStability(), first);
            first = appendJsonNumber(sb, "similarityBoost", voiceConfig.getSimilarityBoost(), first);
            first = appendJsonNumber(sb, "style", voiceConfig.getStyle(), first);
            appendJsonNumber(sb, "pitch", voiceConfig.getPitch(), first);
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private static boolean appendJsonNumber(StringBuilder sb, String name, Number value, boolean first) {
        if (value == null) return first;
        if (!first) sb.append(',');
        sb.append('"').append(name).append("\":").append(value);
        return false;
    }

    private String postJson(String url, String json) throws AvatarChannelException {
        Request.Builder rb = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json").post(RequestBody.create(json, JSON));
        if (config.isSandbox()) rb.header("X-Env-Sandbox", "true");
        try (Response r = httpClient.newCall(rb.build()).execute()) {
            if (!r.isSuccessful()) throw new AvatarChannelException("Platform API HTTP " + r.code());
            String body = r.body() != null ? r.body().string() : "{}";
            checkPlatformError(body);
            return body;
        } catch (IOException e) { throw new AvatarChannelException("REST call failed: " + e.getMessage(), e); }
    }

    private static void checkPlatformError(String body) throws AvatarChannelException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(body, Map.class);
            Object code = map.get("code");
            if (code instanceof Number && ((Number) code).intValue() != 0) {
                throw new AvatarChannelException("Platform error: code=" + code + " " + map.get("message"));
            }
        } catch (AvatarChannelException e) { throw e; } catch (Exception e) {
            logger.debug("Response is not a JSON envelope — using raw body: {}", body);
        }
    }

    @SuppressWarnings("unchecked")
    private SessionInfo parseSessionResponse(String json) throws AvatarChannelException {
        try {
            Map<String, Object> map = MAPPER.readValue(json, Map.class);
            Map<String, Object> d = (Map<String, Object>) map.get("data");
            if (d == null) throw new AvatarChannelException("Missing 'data' in platform response");
            String agentWsUrl = String.valueOf(d.get("agentWsUrl"));
            if ("null".equals(agentWsUrl)) throw new AvatarChannelException("Missing agentWsUrl");
            return new SessionInfo(
                    String.valueOf(d.get("sessionId")),
                    String.valueOf(d.getOrDefault("userToken", "")),
                    String.valueOf(d.getOrDefault("sfuUrl", "")),
                    agentWsUrl);
        } catch (AvatarChannelException e) { throw e; }
        catch (Exception e) { throw new AvatarChannelException("Failed to parse response: " + e.getMessage(), e); }
    }

    private static String escapeJson(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // ── Immutable state ──────────────────────────────────────────────────────

    private static class State {
        static final State STOPPED = new State(null, null);

        final boolean started;
        final SessionInfo sessionInfo;
        final AvatarWebSocketClient wsClient;

        State(SessionInfo sessionInfo, AvatarWebSocketClient wsClient) {
            this.started = wsClient != null;
            this.sessionInfo = sessionInfo;
            this.wsClient = wsClient;
        }
    }

    // ── Listener wrapper ─────────────────────────────────────────────────────

    private static class LatchedListener implements AgentListener {
        private final AgentListener delegate;
        private final CountDownLatch latch;

        LatchedListener(AgentListener delegate, CountDownLatch latch) {
            this.delegate = delegate; this.latch = latch;
        }

        @Override public void onTextInput(String text, String requestId) { delegate.onTextInput(text, requestId); }
        @Override public void onAudioFrame(AudioFrame frame) { delegate.onAudioFrame(frame); }
        @Override public void onSessionInit() { latch.countDown(); delegate.onSessionInit(); }
        @Override public void onSceneReady() { delegate.onSceneReady(); }
        @Override public void onSessionState(SessionState state) { delegate.onSessionState(state); }
        @Override public void onIdleTrigger(String reason, long idleMs) { delegate.onIdleTrigger(reason, idleMs); }
        @Override public void onError(String message) { delegate.onError(message); }
        @Override public void onSessionClosing(String reason) { delegate.onSessionClosing(reason); }
        @Override public void onClosed(int code, String reason) { delegate.onClosed(code, reason); }
    }
}
