package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.model.AudioHeader;

/**
 * Options for splitting raw PCM audio into protocol-sized audio frames.
 */
public final class AudioSplitOptions {
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 64000;
    public static final int MAX_PAYLOAD_BYTES = 65535;
    public static final int MAX_SEQUENCE = 4095;
    public static final int MAX_TIMESTAMP = 0xFFFFF;

    private final int maxPayloadBytes;
    private final boolean stereo;
    private final AudioHeader.SampleRate sampleRate;
    private final int startSequence;
    private final int startTimestamp;

    private AudioSplitOptions(Builder builder) {
        this.maxPayloadBytes = builder.maxPayloadBytes;
        this.stereo = builder.stereo;
        this.sampleRate = builder.sampleRate;
        this.startSequence = builder.startSequence;
        this.startTimestamp = builder.startTimestamp;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public boolean isStereo() {
        return stereo;
    }

    public AudioHeader.SampleRate getSampleRate() {
        return sampleRate;
    }

    public int getStartSequence() {
        return startSequence;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    private void validate() {
        if (sampleRate == null) {
            throw new IllegalArgumentException("sampleRate is required");
        }
        if (maxPayloadBytes <= 0 || maxPayloadBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("maxPayloadBytes must be 1-65535");
        }
        if (startSequence < 0 || startSequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("startSequence must be 0-4095");
        }
        if (startTimestamp < 0 || startTimestamp > MAX_TIMESTAMP) {
            throw new IllegalArgumentException("startTimestamp must be 0-1048575");
        }
    }

    public static final class Builder {
        private int maxPayloadBytes = DEFAULT_MAX_PAYLOAD_BYTES;
        private boolean stereo;
        private AudioHeader.SampleRate sampleRate;
        private int startSequence;
        private int startTimestamp = (int) (System.currentTimeMillis() & MAX_TIMESTAMP);

        private Builder() {
        }

        public Builder maxPayloadBytes(int maxPayloadBytes) {
            this.maxPayloadBytes = maxPayloadBytes;
            return this;
        }

        public Builder stereo(boolean stereo) {
            this.stereo = stereo;
            return this;
        }

        public Builder sampleRate(AudioHeader.SampleRate sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder startSequence(int startSequence) {
            this.startSequence = startSequence;
            return this;
        }

        public Builder startTimestamp(int startTimestamp) {
            this.startTimestamp = startTimestamp;
            return this;
        }

        public AudioSplitOptions build() {
            return new AudioSplitOptions(this);
        }
    }
}
