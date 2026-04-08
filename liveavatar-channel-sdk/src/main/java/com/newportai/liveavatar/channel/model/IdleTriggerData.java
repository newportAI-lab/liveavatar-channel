package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model for system.idleTrigger event
 * Sent by live avatar service when idle timeout is detected
 */
public class IdleTriggerData {

    private final String reason;
    private final long idleTimeMs;

    @JsonCreator
    public IdleTriggerData(
            @JsonProperty("reason") String reason,
            @JsonProperty("idleTimeMs") long idleTimeMs) {
        this.reason = reason;
        this.idleTimeMs = idleTimeMs;
    }

    public String getReason() {
        return reason;
    }

    public long getIdleTimeMs() {
        return idleTimeMs;
    }

    @Override
    public String toString() {
        return "IdleTriggerData{" +
                "reason='" + reason + '\'' +
                ", idleTimeMs=" + idleTimeMs +
                '}';
    }
}
