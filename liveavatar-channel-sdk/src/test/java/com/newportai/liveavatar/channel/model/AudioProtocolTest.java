package com.newportai.liveavatar.channel.model;

import com.newportai.liveavatar.channel.util.AudioFrameBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for audio protocol (AudioHeader and AudioFrame)
 */
public class AudioProtocolTest {

    @Test
    public void testAudioHeaderEncodeAndParse() {
        // Create audio header
        AudioHeader header = new AudioHeader();
        header.setStereo(true);
        header.setFirstFrame(true);
        header.setSequence(123);
        header.setTimestamp(1000);
        header.setSampleRate(AudioHeader.SampleRate.RATE_24KHZ);
        header.setFrameSize(4095);  // new max (12 bits)
        header.setPayloadLength(960);

        // Encode
        byte[] bytes = header.encode();
        assertEquals(AudioHeader.HEADER_SIZE, bytes.length);

        // Parse
        AudioHeader parsed = AudioHeader.parse(bytes);
        assertEquals(AudioHeader.TYPE_AUDIO, parsed.getType());
        assertTrue(parsed.isStereo());
        assertTrue(parsed.isFirstFrame());
        assertEquals(123, parsed.getSequence());
        assertEquals(1000, parsed.getTimestamp());
        assertEquals(AudioHeader.SampleRate.RATE_24KHZ, parsed.getSampleRate());
        assertEquals(4095, parsed.getFrameSize());
        assertEquals(960, parsed.getPayloadLength());
    }

    @Test
    public void testAudioHeaderMonoMode() {
        AudioHeader header = new AudioHeader();
        header.setStereo(false);

        byte[] bytes = header.encode();
        AudioHeader parsed = AudioHeader.parse(bytes);

        assertFalse(parsed.isStereo());
    }

    @Test
    public void testSampleRateValues() {
        assertEquals(16000, AudioHeader.SampleRate.RATE_16KHZ.getValue());
        assertEquals(24000, AudioHeader.SampleRate.RATE_24KHZ.getValue());
        assertEquals(48000, AudioHeader.SampleRate.RATE_48KHZ.getValue());
    }

    @Test
    public void testSampleRateFromCode() {
        assertEquals(AudioHeader.SampleRate.RATE_16KHZ,
                AudioHeader.SampleRate.fromCode((byte) 0b00));
        assertEquals(AudioHeader.SampleRate.RATE_24KHZ,
                AudioHeader.SampleRate.fromCode((byte) 0b01));
        assertEquals(AudioHeader.SampleRate.RATE_48KHZ,
                AudioHeader.SampleRate.fromCode((byte) 0b10));
    }

    @Test
    public void testAudioFrameEncodeAndParse() {
        // Create audio frame
        byte[] payload = new byte[]{1, 2, 3, 4, 5};

        AudioHeader header = new AudioHeader();
        header.setSequence(0);
        header.setTimestamp(0);
        header.setSampleRate(AudioHeader.SampleRate.RATE_16KHZ);
        header.setPayloadLength(payload.length);

        AudioFrame frame = new AudioFrame(header, payload);

        // Encode
        byte[] bytes = frame.encode();
        assertEquals(AudioHeader.HEADER_SIZE + payload.length, bytes.length);

        // Parse
        AudioFrame parsed = AudioFrame.parse(bytes);
        assertEquals(header.getSequence(), parsed.getHeader().getSequence());
        assertEquals(payload.length, parsed.getPayload().length);
        assertArrayEquals(payload, parsed.getPayload());
    }

    @Test
    public void testAudioFrameBuilder() {
        byte[] payload = new byte[480 * 2]; // 960 samples, 16-bit PCM

        AudioFrame frame = AudioFrameBuilder.create()
                .stereo(true)
                .firstFrame(true)
                .sequence(0)
                .timestamp(1000)
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .frameSize(960)
                .payload(payload)
                .build();

        assertNotNull(frame);
        assertTrue(frame.getHeader().isStereo());
        assertTrue(frame.getHeader().isFirstFrame());
        assertEquals(0, frame.getHeader().getSequence());
        assertEquals(payload.length, frame.getHeader().getPayloadLength());
        assertArrayEquals(payload, frame.getPayload());
    }

    @Test
    public void testSequenceRange() {
        AudioHeader header = new AudioHeader();

        // Valid range: 0-4095
        header.setSequence(0);
        assertEquals(0, header.getSequence());

        header.setSequence(4095);
        assertEquals(4095, header.getSequence());

        // Invalid range
        try {
            header.setSequence(-1);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            header.setSequence(4096);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testTimestampRange() {
        AudioHeader header = new AudioHeader();

        // Valid range: 0-1048575 (20 bits)
        header.setTimestamp(0);
        assertEquals(0, header.getTimestamp());

        header.setTimestamp(1048575);
        assertEquals(1048575, header.getTimestamp());

        // Invalid range
        try {
            header.setTimestamp(-1);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            header.setTimestamp(1048576);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testAutoUpdatePayloadLength() {
        byte[] payload = new byte[3000]; // > old 2047 limit — tests wider 16-bit L field
        AudioFrame frame = new AudioFrame();

        // Setting payload should auto-update header
        frame.setPayload(payload);

        assertEquals(3000, frame.getHeader().getPayloadLength());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTooShortHeader() {
        byte[] shortBytes = new byte[4]; // Less than 9 bytes
        AudioHeader.parse(shortBytes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTooShortFrame() {
        byte[] shortBytes = new byte[4]; // Less than 9 bytes
        AudioFrame.parse(shortBytes);
    }

    @Test
    public void testFrameSizeRange() {
        AudioHeader header = new AudioHeader();

        header.setFrameSize(0);
        assertEquals(0, header.getFrameSize());

        header.setFrameSize(4095);
        assertEquals(4095, header.getFrameSize());

        try {
            header.setFrameSize(4096);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testPayloadLengthRange() {
        AudioHeader header = new AudioHeader();

        header.setPayloadLength(0);
        assertEquals(0, header.getPayloadLength());

        header.setPayloadLength(65535);
        assertEquals(65535, header.getPayloadLength());

        try {
            header.setPayloadLength(65536);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void test48kHzRoundTrip() {
        AudioHeader header = new AudioHeader();
        header.setSampleRate(AudioHeader.SampleRate.RATE_48KHZ);
        header.setSequence(1);
        header.setTimestamp(500);

        byte[] bytes = header.encode();
        AudioHeader parsed = AudioHeader.parse(bytes);

        assertEquals(AudioHeader.SampleRate.RATE_48KHZ, parsed.getSampleRate());
    }
}
