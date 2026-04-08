package com.newportai.liveavatar.channel.model;

import java.nio.ByteBuffer;

/**
 * Audio frame for WebSocket audio protocol
 *
 * <p>Frame structure: [Header (9 bytes)] + [Audio Payload]
 *
 */
public class AudioFrame {

    private AudioHeader header;
    private byte[] payload;

    public AudioFrame() {
        this.header = new AudioHeader();
    }

    public AudioFrame(AudioHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload;
        // Auto-update payload length in header
        if (payload != null) {
            header.setPayloadLength(payload.length);
        }
    }

    /**
     * Parse audio frame from byte array
     *
     * @param frameBytes complete frame bytes
     * @return parsed AudioFrame
     * @throws IllegalArgumentException if frame is too short
     */
    public static AudioFrame parse(byte[] frameBytes) {
        if (frameBytes.length < AudioHeader.HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Frame too short, must be at least " + AudioHeader.HEADER_SIZE + " bytes"
            );
        }

        // Parse header
        byte[] headerBytes = new byte[AudioHeader.HEADER_SIZE];
        System.arraycopy(frameBytes, 0, headerBytes, 0, AudioHeader.HEADER_SIZE);
        AudioHeader header = AudioHeader.parse(headerBytes);

        // Extract payload
        int payloadLength = frameBytes.length - AudioHeader.HEADER_SIZE;
        byte[] payload = new byte[payloadLength];
        System.arraycopy(frameBytes, AudioHeader.HEADER_SIZE, payload, 0, payloadLength);

        return new AudioFrame(header, payload);
    }

    /**
     * Encode audio frame to byte array
     *
     * @return complete frame bytes
     */
    public byte[] encode() {
        if (payload == null) {
            payload = new byte[0];
        }

        // Update payload length in header
        header.setPayloadLength(payload.length);

        // Encode header
        byte[] headerBytes = header.encode();

        // Combine header and payload
        ByteBuffer buffer = ByteBuffer.allocate(AudioHeader.HEADER_SIZE + payload.length);
        buffer.put(headerBytes);
        buffer.put(payload);

        return buffer.array();
    }

    /**
     * Get total frame size (header + payload)
     *
     * @return frame size in bytes
     */
    public int getFrameSize() {
        return AudioHeader.HEADER_SIZE + (payload != null ? payload.length : 0);
    }

    // Getters and setters

    public AudioHeader getHeader() {
        return header;
    }

    public void setHeader(AudioHeader header) {
        this.header = header;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
        if (payload != null && header != null) {
            header.setPayloadLength(payload.length);
        }
    }

    @Override
    public String toString() {
        return "AudioFrame{" +
                "header=" + header +
                ", payloadSize=" + (payload != null ? payload.length : 0) + " bytes" +
                '}';
    }
}
