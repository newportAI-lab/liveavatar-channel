package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TTS audio configuration for response.start message.
 * Only applicable when TTS is provided by the Live Avatar Service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioConfigData {

    /** Speech speed. 1.0 = normal, 0.5 = very slow, 2.0 = very fast. */
    @JsonProperty("speed")
    private Double speed;

    /** Volume level. 0.0 = muted, 1.0 = standard, 1.5 = max. */
    @JsonProperty("volume")
    private Double volume;

    /**
     * Emotional tone. Supported values: neutral, happy, sad, angry, excited, calm, serious.
     * The list is extensible.
     */
    @JsonProperty("mood")
    private String mood;

    public AudioConfigData() {
    }

    public AudioConfigData(Double speed, Double volume, String mood) {
        this.speed = speed;
        this.volume = volume;
        this.mood = mood;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }
}
