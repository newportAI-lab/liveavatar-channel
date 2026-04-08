package com.newportai.liveavatar.channel.model;

import java.nio.ByteBuffer;

/**
 * Image frame header for WebSocket image protocol (multimodal input)
 *
 * <p>Header structure (12 bytes / 96 bits):
 * <ul>
 *   <li>T (2 bits, pos 94-95): Type, fixed 10 for image frame</li>
 *   <li>V (2 bits, pos 92-93): Protocol version, 00</li>
 *   <li>Format (4 bits, pos 88-91): Image format (0=JPG, 1=PNG, 2=WebP, 3=GIF, 4=AVIF)</li>
 *   <li>Quality (8 bits, pos 80-87): Image quality (0-255)</li>
 *   <li>ID (16 bits, pos 64-79): Unique image identifier (for fragmentation/reassembly)</li>
 *   <li>W (16 bits, pos 48-63): Image width in pixels</li>
 *   <li>H (16 bits, pos 32-47): Image height in pixels</li>
 *   <li>L (32 bits, pos 0-31): Payload length in bytes</li>
 * </ul>
 *
 * <p><b>Note:</b> This protocol is only for WebSocket channel.
 */
public class ImageHeader {

    /**
     * Header size in bytes
     */
    public static final int HEADER_SIZE = 12;

    /**
     * Image frame type (fixed value: 0b10)
     */
    public static final byte TYPE_IMAGE = 0b10;

    /**
     * Protocol version (fixed value: 0b00)
     */
    public static final byte VERSION = 0b00;

    /**
     * Image format enumeration
     */
    public enum Format {
        JPG(0, "JPG"),
        PNG(1, "PNG"),
        WEBP(2, "WebP"),
        GIF(3, "GIF"),
        AVIF(4, "AVIF");

        private final int code;
        private final String name;

        Format(int code, String name) {
            this.code = code;
            this.name = name;
        }

        public int getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public static Format fromCode(int code) {
            for (Format f : values()) {
                if (f.code == code) {
                    return f;
                }
            }
            return JPG; // default
        }
    }

    // Fields
    private byte type;       // 2 bits, fixed 10
    private byte version;    // 2 bits, fixed 00
    private Format format;   // 4 bits
    private int quality;     // 8 bits (0-255)
    private int imageId;     // 16 bits
    private int width;       // 16 bits
    private int height;      // 16 bits
    private long payloadLength; // 32 bits (0-4294967295)

    public ImageHeader() {
        this.type = TYPE_IMAGE;
        this.version = VERSION;
        this.format = Format.JPG;
    }

    /**
     * Parse header from byte array
     *
     * @param headerBytes header bytes (12 bytes)
     * @return parsed ImageHeader
     * @throws IllegalArgumentException if header size is invalid
     */
    public static ImageHeader parse(byte[] headerBytes) {
        if (headerBytes.length != HEADER_SIZE) {
            throw new IllegalArgumentException("Header must be " + HEADER_SIZE + " bytes");
        }

        ImageHeader header = new ImageHeader();
        ByteBuffer buf = ByteBuffer.wrap(headerBytes);

        // Read first 8 bytes as long (bits 95-32)
        long highLong = buf.getLong();
        // Read last 4 bytes as int (bits 31-0) — payload length
        long lengthInt = buf.getInt() & 0xFFFFFFFFL;

        // Parse fields from highLong (bit 95 maps to bit 63 of highLong)
        header.type    = (byte) ((highLong >> 62) & 0b11);
        header.version = (byte) ((highLong >> 60) & 0b11);
        header.format  = Format.fromCode((int) ((highLong >> 56) & 0xF));
        header.quality = (int) ((highLong >> 48) & 0xFF);
        header.imageId = (int) ((highLong >> 32) & 0xFFFF);
        header.width   = (int) ((highLong >> 16) & 0xFFFF);
        header.height  = (int) (highLong & 0xFFFF);
        header.payloadLength = lengthInt;

        return header;
    }

    /**
     * Encode header to byte array
     *
     * @return header bytes (12 bytes)
     */
    public byte[] encode() {
        // Pack bits 95-32 into a long
        long highLong = 0;
        highLong |= ((long) (type & 0b11)) << 62;
        highLong |= ((long) (version & 0b11)) << 60;
        highLong |= ((long) (format.getCode() & 0xF)) << 56;
        highLong |= ((long) (quality & 0xFF)) << 48;
        highLong |= ((long) (imageId & 0xFFFF)) << 32;
        highLong |= ((long) (width & 0xFFFF)) << 16;
        highLong |= (long) (height & 0xFFFF);

        int lengthInt = (int) (payloadLength & 0xFFFFFFFFL);

        return ByteBuffer.allocate(HEADER_SIZE).putLong(highLong).putInt(lengthInt).array();
    }

    // Getters and setters

    public byte getType() {
        return type;
    }

    public byte getVersion() {
        return version;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        if (quality < 0 || quality > 255) {
            throw new IllegalArgumentException("Quality must be 0-255");
        }
        this.quality = quality;
    }

    public int getImageId() {
        return imageId;
    }

    public void setImageId(int imageId) {
        if (imageId < 0 || imageId > 0xFFFF) {
            throw new IllegalArgumentException("ImageId must be 0-65535");
        }
        this.imageId = imageId;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        if (width < 0 || width > 0xFFFF) {
            throw new IllegalArgumentException("Width must be 0-65535");
        }
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        if (height < 0 || height > 0xFFFF) {
            throw new IllegalArgumentException("Height must be 0-65535");
        }
        this.height = height;
    }

    public long getPayloadLength() {
        return payloadLength;
    }

    public void setPayloadLength(long payloadLength) {
        if (payloadLength < 0 || payloadLength > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Payload length must be 0-4294967295");
        }
        this.payloadLength = payloadLength;
    }

    @Override
    public String toString() {
        return "ImageHeader{" +
                "type=" + type +
                ", version=" + version +
                ", format=" + format +
                ", quality=" + quality +
                ", imageId=" + imageId +
                ", width=" + width +
                ", height=" + height +
                ", payloadLength=" + payloadLength +
                '}';
    }
}
