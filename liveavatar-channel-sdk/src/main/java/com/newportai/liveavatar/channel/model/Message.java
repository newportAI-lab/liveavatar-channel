package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base message model for Live Avatar Channel protocol
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    @JsonProperty("event")
    private String event;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("responseId")
    private String responseId;

    @JsonProperty("seq")
    private Integer seq;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("data")
    private Object data;

    public Message() {
    }

    public Message(String event) {
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
