package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for session.state event
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionStateData {

    @JsonProperty("state")
    private String state;

    public SessionStateData() {
    }

    public SessionStateData(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
