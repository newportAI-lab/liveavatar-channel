package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.AudioHeader;

/**
 * Builder utility for creating audio frames
 *
 * <p>Example usage:
 * <pre>{@code
 * // Create an audio frame
 * AudioFrame frame = AudioFrameBuilder.create()
 *     .stereo(true)
 *     .firstFrame(true)
 *     .sequence(0)
 *     .timestamp(System.currentTimeMillis())
 *     .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
 *     .payload(Binary data)
 *     .build();
 *
 * // Encode to bytes for WebSocket transmission
 * byte[] frameBytes = frame.encode();
 * }</pre>
 *
 */
public class AudioFrameBuilder {

    private final AudioHeader header;
    private byte[] payload;

    private AudioFrameBuilder() {
        this.header = new AudioHeader();
    }

    /**
     * Create a new audio frame builder
     *
     * @return new builder instance
     */
    public static AudioFrameBuilder create() {
        return new AudioFrameBuilder();
    }

    /**
     * Set stereo mode
     *
     * @param stereo true for stereo, false for mono
     * @return this builder
     */
    public AudioFrameBuilder stereo(boolean stereo) {
        header.setStereo(stereo);
        return this;
    }

    /**
     * Set mono mode (convenience method)
     *
     * @return this builder
     */
    public AudioFrameBuilder mono() {
        return stereo(false);
    }

    /**
     * Set first frame flag
     *
     * @param firstFrame true if this is the first frame
     * @return this builder
     */
    public AudioFrameBuilder firstFrame(boolean firstFrame) {
        header.setFirstFrame(firstFrame);
        return this;
    }

    /**
     * Set sequence number
     *
     * @param sequence sequence number (0-4095)
     * @return this builder
     */
    public AudioFrameBuilder sequence(int sequence) {
        header.setSequence(sequence);
        return this;
    }

    /**
     * Set timestamp
     *
     * @param timestamp timestamp in milliseconds (0-1048575)
     * @return this builder
     */
    public AudioFrameBuilder timestamp(int timestamp) {
        header.setTimestamp(timestamp);
        return this;
    }

    /**
     * Set current timestamp
     *
     * @return this builder
     */
    public AudioFrameBuilder currentTimestamp() {
        return timestamp((int) (System.currentTimeMillis() & 0xFFFFF));
    }

    /**
     * Set sample rate
     *
     * @param sampleRate sample rate
     * @return this builder
     */
    public AudioFrameBuilder sampleRate(AudioHeader.SampleRate sampleRate) {
        header.setSampleRate(sampleRate);
        return this;
    }

    /**
     * Set frame size
     *
     * @param frameSize frame size (0-4095)
     * @return this builder
     */
    public AudioFrameBuilder frameSize(int frameSize) {
        header.setFrameSize(frameSize);
        return this;
    }

    /**
     * Set audio payload (PCM data)
     *
     * @param payload PCM audio data
     * @return this builder
     */
    public AudioFrameBuilder payload(byte[] payload) {
        this.payload = payload;
        if (payload != null) {
            header.setPayloadLength(payload.length);
        }
        return this;
    }

    /**
     * Build the audio frame
     *
     * @return AudioFrame instance
     */
    public AudioFrame build() {
        return new AudioFrame(header, payload);
    }

    /**
     * Build and encode to byte array
     *
     * @return encoded frame bytes
     */
    public byte[] buildAndEncode() {
        return build().encode();
    }
}
