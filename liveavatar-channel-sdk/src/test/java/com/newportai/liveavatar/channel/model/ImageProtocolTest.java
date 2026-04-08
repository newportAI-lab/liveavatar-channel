package com.newportai.liveavatar.channel.model;

import com.newportai.liveavatar.channel.util.ImageFrameBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for image protocol (ImageHeader and ImageFrame)
 */
public class ImageProtocolTest {

    @Test
    public void testImageHeaderEncodeAndParse() {
        ImageHeader header = new ImageHeader();
        header.setFormat(ImageHeader.Format.PNG);
        header.setQuality(90);
        header.setImageId(42);
        header.setWidth(1920);
        header.setHeight(1080);
        header.setPayloadLength(50000);

        byte[] bytes = header.encode();
        assertEquals(ImageHeader.HEADER_SIZE, bytes.length);

        ImageHeader parsed = ImageHeader.parse(bytes);
        assertEquals(ImageHeader.TYPE_IMAGE, parsed.getType());
        assertEquals(ImageHeader.VERSION, parsed.getVersion());
        assertEquals(ImageHeader.Format.PNG, parsed.getFormat());
        assertEquals(90, parsed.getQuality());
        assertEquals(42, parsed.getImageId());
        assertEquals(1920, parsed.getWidth());
        assertEquals(1080, parsed.getHeight());
        assertEquals(50000, parsed.getPayloadLength());
    }

    @Test
    public void testImageFormatValues() {
        assertEquals(0, ImageHeader.Format.JPG.getCode());
        assertEquals(1, ImageHeader.Format.PNG.getCode());
        assertEquals(2, ImageHeader.Format.WEBP.getCode());
        assertEquals(3, ImageHeader.Format.GIF.getCode());
        assertEquals(4, ImageHeader.Format.AVIF.getCode());
    }

    @Test
    public void testImageFormatFromCode() {
        assertEquals(ImageHeader.Format.JPG, ImageHeader.Format.fromCode(0));
        assertEquals(ImageHeader.Format.PNG, ImageHeader.Format.fromCode(1));
        assertEquals(ImageHeader.Format.WEBP, ImageHeader.Format.fromCode(2));
        assertEquals(ImageHeader.Format.GIF, ImageHeader.Format.fromCode(3));
        assertEquals(ImageHeader.Format.AVIF, ImageHeader.Format.fromCode(4));
    }

    @Test
    public void testImageFrameEncodeAndParse() {
        byte[] payload = new byte[]{10, 20, 30, 40, 50};

        ImageHeader header = new ImageHeader();
        header.setFormat(ImageHeader.Format.JPG);
        header.setWidth(640);
        header.setHeight(480);
        header.setPayloadLength(payload.length);

        ImageFrame frame = new ImageFrame(header, payload);

        byte[] bytes = frame.encode();
        assertEquals(ImageHeader.HEADER_SIZE + payload.length, bytes.length);

        ImageFrame parsed = ImageFrame.parse(bytes);
        assertEquals(640, parsed.getHeader().getWidth());
        assertEquals(480, parsed.getHeader().getHeight());
        assertEquals(payload.length, parsed.getPayload().length);
        assertArrayEquals(payload, parsed.getPayload());
    }

    @Test
    public void testImageFrameBuilder() {
        byte[] payload = new byte[1024];

        ImageFrame frame = ImageFrameBuilder.create()
                .format(ImageHeader.Format.WEBP)
                .quality(85)
                .imageId(7)
                .width(800)
                .height(600)
                .payload(payload)
                .build();

        assertNotNull(frame);
        assertEquals(ImageHeader.Format.WEBP, frame.getHeader().getFormat());
        assertEquals(85, frame.getHeader().getQuality());
        assertEquals(7, frame.getHeader().getImageId());
        assertEquals(800, frame.getHeader().getWidth());
        assertEquals(600, frame.getHeader().getHeight());
        assertEquals(payload.length, frame.getHeader().getPayloadLength());
        assertArrayEquals(payload, frame.getPayload());
    }

    @Test
    public void testLargePayloadLength() {
        // Test 32-bit L field (> 65535 bytes)
        ImageHeader header = new ImageHeader();
        header.setPayloadLength(100000L);
        assertEquals(100000L, header.getPayloadLength());

        byte[] bytes = header.encode();
        ImageHeader parsed = ImageHeader.parse(bytes);
        assertEquals(100000L, parsed.getPayloadLength());
    }

    @Test
    public void testAutoUpdatePayloadLength() {
        byte[] payload = new byte[200];
        ImageFrame frame = new ImageFrame();
        frame.setPayload(payload);
        assertEquals(200, frame.getHeader().getPayloadLength());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTooShortHeader() {
        byte[] shortBytes = new byte[8]; // Less than 12 bytes
        ImageHeader.parse(shortBytes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTooShortFrame() {
        byte[] shortBytes = new byte[8]; // Less than 12 bytes
        ImageFrame.parse(shortBytes);
    }

    @Test
    public void testTypeField() {
        // Verify TYPE_IMAGE is embedded correctly and can be used for dispatch
        ImageHeader header = new ImageHeader();
        header.setWidth(100);
        header.setHeight(100);

        byte[] bytes = header.encode();
        // Type bits are in the top 2 bits of first byte
        byte type = (byte) ((bytes[0] >> 6) & 0b11);
        assertEquals(ImageHeader.TYPE_IMAGE, type);
    }
}
