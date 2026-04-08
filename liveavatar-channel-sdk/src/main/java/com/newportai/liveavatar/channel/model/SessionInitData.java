package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for session.init event
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionInitData {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("userId")
    private String userId;

    public SessionInitData() {
    }

    public SessionInitData(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
