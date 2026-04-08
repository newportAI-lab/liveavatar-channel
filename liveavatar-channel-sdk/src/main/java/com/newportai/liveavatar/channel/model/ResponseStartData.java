package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for response.start message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseStartData {

    @JsonProperty("audioConfig")
    private AudioConfigData audioConfig;

    public ResponseStartData() {
    }

    public ResponseStartData(AudioConfigData audioConfig) {
        this.audioConfig = audioConfig;
    }

    public AudioConfigData getAudioConfig() {
        return audioConfig;
    }

    public void setAudioConfig(AudioConfigData audioConfig) {
        this.audioConfig = audioConfig;
    }
}
