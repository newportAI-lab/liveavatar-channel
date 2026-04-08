package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for session.closing event
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CloseReasonData {

    @JsonProperty("reason")
    private String reason;

    public CloseReasonData() {
    }

    public CloseReasonData(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
