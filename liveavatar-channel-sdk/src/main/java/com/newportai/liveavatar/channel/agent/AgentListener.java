package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.SessionState;

/**
 * Simplified callback interface for WebSocket Agent mode.
 *
 * <p>All methods have default no-op implementations — only override what you need.
 * The platform always sends {@code input.text} to the agent, whether the user typed
 * a message or used platform ASR. Raw ASR/voice events are never forwarded to the
 * developer; they are either platform-internal or sent <em>by</em> the developer
 * in Developer ASR mode via {@link AvatarAgent} send methods.
 *
 * <h3>Quick start (Platform ASR + Platform TTS)</h3>
 * <pre>{@code
 * AvatarAgent agent = AvatarAgent.builder()
 *     .apiKey("...").avatarId("...")
 *     .listener(new AgentListener() {
 *         @Override
 *         public void onTextInput(String text, String requestId) {
 *             String reply = callYourAI(text);
 *             agent.sendResponseChunk(requestId, reply, 0);
 *             agent.sendResponseDone(requestId);
 *         }
 *     })
 *     .build();
 * SessionInfo info = agent.start();
 * }</pre>
 */
public interface AgentListener {

    /**
     * Called when the platform sends user text input.
     * This is the primary callback — it fires for both typed text and platform-ASR results.
     *
     * @param text      the user's message
     * @param requestId platform-issued request identifier (use with response send methods)
     */
    default void onTextInput(String text, String requestId) {}

    /**
     * Called when the platform forwards a raw audio frame (Developer ASR mode only).
     * The developer must run VAD and ASR internally, then send
     * {@code input.voice.*} / {@code input.asr.*} events back via AvatarAgent.
     */
    default void onAudioFrame(AudioFrame frame) {}

    /**
     * Called when the platform sends {@code session.init} — the session handshake is complete.
     * The SDK automatically replies with {@code session.ready}.
     */
    default void onSessionInit() {}

    /**
     * Called when the platform sends {@code scene.ready} — the avatar scene is rendered
     * and the conversation can begin.
     */
    default void onSceneReady() {}

    /**
     * Called when the platform reports a session state change.
     * @param state the new session state (IDLE, LISTENING, THINKING, SPEAKING, etc.)
     */
    default void onSessionState(SessionState state) {}

    /**
     * Called when the platform detects the avatar has been idle for a configured duration.
     * The developer may respond via {@link AvatarAgent#sendPrompt(String)}.
     */
    default void onIdleTrigger(String reason, long idleMs) {}

    /** Called on protocol or transport errors. */
    default void onError(String message) {}

    /**
     * Called when the platform sends {@code session.closing} — the session is
     * about to end (e.g. timeout). No further messages should be sent.
     */
    default void onSessionClosing(String reason) {}

    /** Called when the WebSocket connection is closed. */
    default void onClosed(int code, String reason) {}
}
