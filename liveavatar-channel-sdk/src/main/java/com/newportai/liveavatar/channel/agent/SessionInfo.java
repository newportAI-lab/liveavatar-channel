package com.newportai.liveavatar.channel.agent;

import java.util.Objects;

/**
 * Immutable value object returned by {@link AvatarAgent#start()}.
 * Contains the credentials needed to connect the frontend to the RTC room.
 */
public final class SessionInfo {

    private final String sessionId;
    private final String userToken;
    private final String sfuUrl;
    private final String agentWsUrl;
    private final String joinUrl;

    SessionInfo(String sessionId, String userToken, String sfuUrl, String agentWsUrl, String joinUrl) {
        this.sessionId = sessionId;
        this.userToken = userToken;
        this.sfuUrl = sfuUrl;
        this.agentWsUrl = agentWsUrl;
        this.joinUrl = joinUrl;
    }

    /** Platform-issued session identifier. */
    public String getSessionId() { return sessionId; }

    /** Token for the end user (frontend) to join the RTC room. */
    public String getUserToken() { return userToken; }

    /** LiveKit SFU endpoint for the frontend JS SDK. */
    public String getSfuUrl() { return sfuUrl; }

    /** WebSocket URL used by the agent to connect to the platform. For debugging only. */
    public String getAgentWsUrl() { return agentWsUrl; }

    /** Frontend join URL returned by the platform. */
    public String getJoinUrl() { return joinUrl; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionInfo)) return false;
        SessionInfo that = (SessionInfo) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return "SessionInfo{sessionId='" + sessionId + "'}";
    }
}
