package com.newportai.liveavatar.channel.server.session;

import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Represents an avatar session with all its state
 */
public class AvatarSession {

    private final String sessionId;          // Protocol sessionId
    private final String userId;
    private final String wsSessionId;        // Spring WebSocketSession ID
    private WebSocketSession wsSession;      // WebSocket session reference
    private String currentRequestId;         // Current request ID (for ASR scenario)
    private final List<byte[]> audioBuffer;  // Audio buffer (for ASR)

    // Response task management
    private String activeResponseId;         // Current active response ID
    private volatile boolean responseActive; // Whether there's an active response
    private Future<?> currentResponseTask;   // Current response task (for cancellation)

    // VAD state tracking
    private volatile boolean voiceActive;    // Whether user is currently speaking

    public AvatarSession(String sessionId, String userId, String wsSessionId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.wsSessionId = wsSessionId;
        this.audioBuffer = new ArrayList<>();
        this.responseActive = false;
    }

    // Response management methods
    public boolean hasActiveResponse() {
        return responseActive;
    }

    public void startResponse(String responseId, Future<?> task) {
        this.activeResponseId = responseId;
        this.currentResponseTask = task;
        this.responseActive = true;
    }

    public void cancelCurrentResponse() {
        if (currentResponseTask != null && !currentResponseTask.isDone()) {
            currentResponseTask.cancel(true);
        }
        this.responseActive = false;
        this.activeResponseId = null;
    }

    public void completeResponse() {
        this.responseActive = false;
        this.activeResponseId = null;
        this.currentResponseTask = null;
    }

    public boolean isVoiceActive() {
        return voiceActive;
    }

    public void setVoiceActive(boolean voiceActive) {
        this.voiceActive = voiceActive;
    }

    // Audio buffer management
    public void addAudioBuffer(byte[] pcmData) {
        this.audioBuffer.add(pcmData);
    }

    public List<byte[]> getAudioBuffer() {
        return audioBuffer;
    }

    public void clearAudioBuffer() {
        this.audioBuffer.clear();
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getWsSessionId() {
        return wsSessionId;
    }

    public WebSocketSession getWsSession() {
        return wsSession;
    }

    public void setWsSession(WebSocketSession wsSession) {
        this.wsSession = wsSession;
    }

    public String getCurrentRequestId() {
        return currentRequestId;
    }

    public void setCurrentRequestId(String currentRequestId) {
        this.currentRequestId = currentRequestId;
    }

    public String getActiveResponseId() {
        return activeResponseId;
    }

    @Override
    public String toString() {
        return "AvatarSession{" +
                "sessionId='" + sessionId + '\'' +
                ", userId='" + userId + '\'' +
                ", wsSessionId='" + wsSessionId + '\'' +
                ", responseActive=" + responseActive +
                '}';
    }
}
