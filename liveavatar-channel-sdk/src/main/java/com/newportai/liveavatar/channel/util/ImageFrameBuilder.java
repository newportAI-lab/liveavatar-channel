package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.model.ImageFrame;
import com.newportai.liveavatar.channel.model.ImageHeader;

/**
 * Builder utility for creating image frames
 *
 * <p>Example usage:
 * <pre>{@code
 * ImageFrame frame = ImageFrameBuilder.create()
 *     .format(ImageHeader.Format.JPG)
 *     .quality(85)
 *     .imageId(1)
 *     .width(1280)
 *     .height(720)
 *     .payload(jpegBytes)
 *     .build();
 *
 * byte[] frameBytes = frame.encode();
 * }</pre>
 *
 */
public class ImageFrameBuilder {

    private final ImageHeader header;
    private byte[] payload;

    private ImageFrameBuilder() {
        this.header = new ImageHeader();
    }

    public static ImageFrameBuilder create() {
        return new ImageFrameBuilder();
    }

    public ImageFrameBuilder format(ImageHeader.Format format) {
        header.setFormat(format);
        return this;
    }

    public ImageFrameBuilder quality(int quality) {
        header.setQuality(quality);
        return this;
    }

    public ImageFrameBuilder imageId(int imageId) {
        header.setImageId(imageId);
        return this;
    }

    public ImageFrameBuilder width(int width) {
        header.setWidth(width);
        return this;
    }

    public ImageFrameBuilder height(int height) {
        header.setHeight(height);
        return this;
    }

    public ImageFrameBuilder payload(byte[] payload) {
        this.payload = payload;
        if (payload != null) {
            header.setPayloadLength(payload.length);
        }
        return this;
    }

    public ImageFrame build() {
        return new ImageFrame(header, payload);
    }

    public byte[] buildAndEncode() {
        return build().encode();
    }
}
