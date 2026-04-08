package com.newportai.liveavatar.channel.model;

import java.nio.ByteBuffer;

/**
 * Audio frame header for WebSocket audio protocol
 *
 * <p>Header structure (9 bytes / 72 bits):
 * <ul>
 *   <li>T (2 bits, pos 70-71): Type, fixed 01 for audio frame</li>
 *   <li>C (1 bit, pos 69): Channel, 0=mono, 1=stereo</li>
 *   <li>K (1 bit, pos 68): Key frame (first frame or Opus resync)</li>
 *   <li>S (12 bits, pos 56-67): Sequence number (0-4095)</li>
 *   <li>TS (20 bits, pos 36-55): Timestamp in milliseconds (0-1048575)</li>
 *   <li>SR (2 bits, pos 34-35): Sample rate, 00=16kHz, 01=24kHz, 10=48kHz</li>
 *   <li>F (12 bits, pos 22-33): Samples per frame (0-4095)</li>
 *   <li>Codec (2 bits, pos 20-21): Audio codec, 00=PCM, 01=Opus</li>
 *   <li>R (4 bits, pos 16-19): Reserved (all 0)</li>
 *   <li>L (16 bits, pos 0-15): Payload length in bytes (0-65535)</li>
 * </ul>
 *
 */
public class AudioHeader {

    /**
     * Header size in bytes
     */
    public static final int HEADER_SIZE = 9;

    /**
     * Audio frame type (fixed value: 0b01)
     */
    public static final byte TYPE_AUDIO = 0b01;

    // Fields
    private byte type;              // 2 bits, fixed 01
    private boolean stereo;         // 1 bit, 0=mono, 1=stereo
    private boolean firstFrame;     // 1 bit
    private int sequence;           // 12 bits (0-4095)
    private int timestamp;          // 20 bits (ms)
    private SampleRate sampleRate;  // 2 bits
    private int frameSize;          // 12 bits (0-4095)
    private Codec codec;            // 2 bits
    private int payloadLength;      // 16 bits (0-65535)

    /**
     * Sample rate enumeration
     */
    public enum SampleRate {
        RATE_16KHZ(0b00, 16000),
        RATE_24KHZ(0b01, 24000),
        RATE_48KHZ(0b10, 48000);

        private final byte code;
        private final int value;

        SampleRate(int code, int value) {
            this.code = (byte) code;
            this.value = value;
        }

        public byte getCode() {
            return code;
        }

        public int getValue() {
            return value;
        }

        public static SampleRate fromCode(byte code) {
            for (SampleRate sr : values()) {
                if (sr.code == code) {
                    return sr;
                }
            }
            return RATE_16KHZ; // default
        }
    }

    /**
     * Audio codec enumeration
     */
    public enum Codec {
        PCM(0b00, "PCM"),
        OPUS(0b01, "Opus");

        private final byte code;
        private final String name;

        Codec(int code, String name) {
            this.code = (byte) code;
            this.name = name;
        }

        public byte getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public static Codec fromCode(byte code) {
            for (Codec c : values()) {
                if (c.code == code) {
                    return c;
                }
            }
            return PCM; // default
        }
    }

    public AudioHeader() {
        this.type = TYPE_AUDIO;
        this.sampleRate = SampleRate.RATE_16KHZ; // Default sample rate
        this.codec = Codec.PCM; // Default codec
    }

    /**
     * Parse header from byte array
     *
     * @param headerBytes header bytes (9 bytes)
     * @return parsed AudioHeader
     * @throws IllegalArgumentException if header size is invalid
     */
    public static AudioHeader parse(byte[] headerBytes) {
        if (headerBytes.length != HEADER_SIZE) {
            throw new IllegalArgumentException("Header must be " + HEADER_SIZE + " bytes");
        }

        AudioHeader header = new AudioHeader();

        // Read first 8 bytes as long (covers bits 71-8 of the 72-bit header)
        // then last byte covers bits 7-0
        long headerLong = ByteBuffer.wrap(headerBytes, 0, 8).getLong();
        int lastByte = headerBytes[8] & 0xFF;

        // Parse fields: bit positions relative to headerLong (long bit 63 = header bit 71)
        // T(2)+C(1)+K(1)+S(12)+TS(20)+SR(2)+F(12)+Codec(2)+R(4)+L[15:8](8) in headerLong
        // L[7:0] in lastByte
        header.type = (byte) ((headerLong >> 62) & 0b11);
        header.stereo = ((headerLong >> 61) & 0b1) == 1;
        header.firstFrame = ((headerLong >> 60) & 0b1) == 1;
        header.sequence = (int) ((headerLong >> 48) & 0xFFF);
        header.timestamp = (int) ((headerLong >> 28) & 0xFFFFF);
        byte srCode = (byte) ((headerLong >> 26) & 0b11);      // 2 bits
        header.sampleRate = SampleRate.fromCode(srCode);
        header.frameSize = (int) ((headerLong >> 14) & 0xFFF); // 12 bits
        byte codecCode = (byte) ((headerLong >> 12) & 0b11);
        header.codec = Codec.fromCode(codecCode);
        // Skip reserved 4 bits (bits 8-11 of headerLong)
        // L high byte at bits 7-0 of headerLong, L low byte in lastByte
        header.payloadLength = (int) (((headerLong & 0xFF) << 8) | lastByte);

        return header;
    }

    /**
     * Encode header to byte array
     *
     * @return header bytes (9 bytes)
     */
    public byte[] encode() {
        // Pack fields into bits 71-8 of the 72-bit header, stored in a long
        // T(2)+C(1)+K(1)+S(12)+TS(20)+SR(2)+F(12)+Codec(2)+R(4)+L[15:8](8)
        long headerLong = 0;
        headerLong |= ((long) (type & 0b11)) << 62;
        headerLong |= ((long) (stereo ? 1 : 0)) << 61;
        headerLong |= ((long) (firstFrame ? 1 : 0)) << 60;
        headerLong |= ((long) (sequence & 0xFFF)) << 48;
        headerLong |= ((long) (timestamp & 0xFFFFF)) << 28;
        headerLong |= ((long) (sampleRate.getCode() & 0b11)) << 26; // 2 bits
        headerLong |= ((long) (frameSize & 0xFFF)) << 14;           // 12 bits
        headerLong |= ((long) (codec.getCode() & 0b11)) << 12;
        // Reserved 4 bits (bits 8-11) are 0
        headerLong |= (long) ((payloadLength >> 8) & 0xFF);         // L high byte

        byte lastByte = (byte) (payloadLength & 0xFF);              // L low byte

        return ByteBuffer.allocate(HEADER_SIZE).putLong(headerLong).put(lastByte).array();
    }

    // Getters and setters

    public byte getType() {
        return type;
    }

    public boolean isStereo() {
        return stereo;
    }

    public void setStereo(boolean stereo) {
        this.stereo = stereo;
    }

    public boolean isFirstFrame() {
        return firstFrame;
    }

    public void setFirstFrame(boolean firstFrame) {
        this.firstFrame = firstFrame;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > 4095) {
            throw new IllegalArgumentException("Sequence must be 0-4095");
        }
        this.sequence = sequence;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        if (timestamp < 0 || timestamp > 0xFFFFF) {
            throw new IllegalArgumentException("Timestamp must be 0-1048575");
        }
        this.timestamp = timestamp;
    }

    public SampleRate getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(SampleRate sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int frameSize) {
        if (frameSize < 0 || frameSize > 4095) {
            throw new IllegalArgumentException("Frame size must be 0-4095");
        }
        this.frameSize = frameSize;
    }

    public Codec getCodec() {
        return codec;
    }

    public void setCodec(Codec codec) {
        this.codec = codec;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public void setPayloadLength(int payloadLength) {
        if (payloadLength < 0 || payloadLength > 65535) {
            throw new IllegalArgumentException("Payload length must be 0-65535");
        }
        this.payloadLength = payloadLength;
    }

    @Override
    public String toString() {
        return "AudioHeader{" +
                "type=" + type +
                ", stereo=" + stereo +
                ", firstFrame=" + firstFrame +
                ", sequence=" + sequence +
                ", timestamp=" + timestamp +
                ", sampleRate=" + sampleRate +
                ", frameSize=" + frameSize +
                ", codec=" + codec +
                ", payloadLength=" + payloadLength +
                '}';
    }
}
