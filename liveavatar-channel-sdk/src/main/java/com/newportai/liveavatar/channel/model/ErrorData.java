package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for error event
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorData {

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    public ErrorData() {
    }

    public ErrorData(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
