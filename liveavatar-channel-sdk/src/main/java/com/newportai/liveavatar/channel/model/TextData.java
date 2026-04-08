package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for text-based events
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextData {

    @JsonProperty("text")
    private String text;

    @JsonProperty("final")
    private Boolean finalResult;

    public TextData() {
    }

    public TextData(String text) {
        this.text = text;
    }

    public TextData(String text, Boolean finalResult) {
        this.text = text;
        this.finalResult = finalResult;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(Boolean finalResult) {
        this.finalResult = finalResult;
    }
}
