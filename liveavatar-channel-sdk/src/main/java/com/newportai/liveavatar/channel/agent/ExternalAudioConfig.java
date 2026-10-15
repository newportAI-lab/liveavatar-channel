package com.newportai.liveavatar.channel.agent;

/**
 * Optional external-audio format. Zero or null follows the session voice format.
 */
public final class ExternalAudioConfig {

    private final Integer sampleRate;
    private final Integer channels;

    private ExternalAudioConfig(Integer sampleRate, Integer channels) {
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public Integer getChannels() {
        return channels;
    }

    public static final class Builder {
        private Integer sampleRate;
        private Integer channels;

        public Builder sampleRate(Integer sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder channels(Integer channels) {
            this.channels = channels;
            return this;
        }

        public ExternalAudioConfig build() {
            int rate = sampleRate == null ? 0 : sampleRate;
            int channelCount = channels == null ? 0 : channels;
            if (rate < 0 || channelCount < 0 || !allowed(rate)) {
                throw new IllegalArgumentException("externalAudioConfig sampleRate or channels is invalid");
            }
            return new ExternalAudioConfig(sampleRate, channels);
        }

        private static boolean allowed(int rate) {
            return rate == 0 || rate == 16000 || rate == 24000 || rate == 44100 || rate == 48000;
        }
    }
}
