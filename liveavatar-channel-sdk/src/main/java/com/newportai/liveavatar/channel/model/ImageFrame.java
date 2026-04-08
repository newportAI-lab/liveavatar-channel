package com.newportai.liveavatar.channel.model;

import java.nio.ByteBuffer;

/**
 * Image frame for WebSocket image protocol (multimodal input)
 *
 * <p>Frame structure: [Header (12 bytes)] + [Image Payload]
 *
 * <p>Supported formats: JPG, PNG, WebP, GIF, AVIF.
 *
 */
public class ImageFrame {

    private ImageHeader header;
    private byte[] payload;

    public ImageFrame() {
        this.header = new ImageHeader();
    }

    public ImageFrame(ImageHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload;
        if (payload != null) {
            header.setPayloadLength(payload.length);
        }
    }

    /**
     * Parse image frame from byte array
     *
     * @param frameBytes complete frame bytes
     * @return parsed ImageFrame
     * @throws IllegalArgumentException if frame is too short
     */
    public static ImageFrame parse(byte[] frameBytes) {
        if (frameBytes.length < ImageHeader.HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Frame too short, must be at least " + ImageHeader.HEADER_SIZE + " bytes"
            );
        }

        byte[] headerBytes = new byte[ImageHeader.HEADER_SIZE];
        System.arraycopy(frameBytes, 0, headerBytes, 0, ImageHeader.HEADER_SIZE);
        ImageHeader header = ImageHeader.parse(headerBytes);

        int payloadLength = frameBytes.length - ImageHeader.HEADER_SIZE;
        byte[] payload = new byte[payloadLength];
        System.arraycopy(frameBytes, ImageHeader.HEADER_SIZE, payload, 0, payloadLength);

        return new ImageFrame(header, payload);
    }

    /**
     * Encode image frame to byte array
     *
     * @return complete frame bytes
     */
    public byte[] encode() {
        if (payload == null) {
            payload = new byte[0];
        }

        header.setPayloadLength(payload.length);

        byte[] headerBytes = header.encode();
        ByteBuffer buffer = ByteBuffer.allocate(ImageHeader.HEADER_SIZE + payload.length);
        buffer.put(headerBytes);
        buffer.put(payload);

        return buffer.array();
    }

    /**
     * Get total frame size (header + payload)
     */
    public int getFrameSize() {
        return ImageHeader.HEADER_SIZE + (payload != null ? payload.length : 0);
    }

    // Getters and setters

    public ImageHeader getHeader() {
        return header;
    }

    public void setHeader(ImageHeader header) {
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
        return "ImageFrame{" +
                "header=" + header +
                ", payloadSize=" + (payload != null ? payload.length : 0) + " bytes" +
                '}';
    }
}
