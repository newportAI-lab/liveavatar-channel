package com.newportai.liveavatar.channel.model;

/** Immutable response lifecycle event delivered by the platform. */
public final class ResponseStateEvent {
    private final String sessionId;
    private final String requestId;
    private final String responseId;
    private final long seq;
    private final long timestamp;
    private final String state;
    private final String reason;

    public ResponseStateEvent(String sessionId, String requestId, String responseId,
                              long seq, long timestamp, String state, String reason) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.responseId = responseId;
        this.seq = seq;
        this.timestamp = timestamp;
        this.state = state;
        this.reason = reason;
    }

    public String getSessionId() { return sessionId; }
    public String getRequestId() { return requestId; }
    public String getResponseId() { return responseId; }
    public long getSeq() { return seq; }
    public long getTimestamp() { return timestamp; }
    public String getState() { return state; }
    public String getReason() { return reason; }

    public boolean isTerminal() {
        return "REJECTED".equals(state) || "FINISHED".equals(state);
    }
}
