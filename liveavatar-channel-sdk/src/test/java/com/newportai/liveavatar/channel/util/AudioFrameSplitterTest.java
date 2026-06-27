package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.AudioHeader;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.*;

public class AudioFrameSplitterTest {

    @Test
    public void splitPcmReturnsOneFrameWhenUnderLimit() {
        byte[] pcm = sequentialBytes(100);
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .maxPayloadBytes(64000)
                .startSequence(7)
                .startTimestamp(12345)
                .build();

        List<AudioFrame> frames = AudioFrameSplitter.splitPcm(pcm, options);

        assertEquals(1, frames.size());
        AudioFrame frame = frames.get(0);
        assertArrayEquals(pcm, frame.getPayload());
        assertTrue(frame.getHeader().isFirstFrame());
        assertFalse(frame.getHeader().isStereo());
        assertEquals(7, frame.getHeader().getSequence());
        assertEquals(12345, frame.getHeader().getTimestamp());
        assertEquals(AudioHeader.SampleRate.RATE_24KHZ, frame.getHeader().getSampleRate());
        assertEquals(AudioHeader.Codec.PCM, frame.getHeader().getCodec());
    }

    @Test
    public void splitPcmSplitsLargeMonoPayloadAndPreservesBytes() {
        byte[] pcm = sequentialBytes(130000);
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .maxPayloadBytes(64000)
                .startSequence(0)
                .startTimestamp(222)
                .build();

        List<AudioFrame> frames = AudioFrameSplitter.splitPcm(pcm, options);

        assertEquals(3, frames.size());
        assertEquals(64000, frames.get(0).getPayload().length);
        assertEquals(64000, frames.get(1).getPayload().length);
        assertEquals(2000, frames.get(2).getPayload().length);
        assertArrayEquals(pcm, concatenate(frames));
        assertTrue(frames.get(0).getHeader().isFirstFrame());
        assertFalse(frames.get(1).getHeader().isFirstFrame());
        assertFalse(frames.get(2).getHeader().isFirstFrame());
    }

    @Test
    public void splitPcmAlignsStereoPayloadsToFourBytes() {
        byte[] pcm = sequentialBytes(20);
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_48KHZ)
                .stereo(true)
                .maxPayloadBytes(10)
                .build();

        List<AudioFrame> frames = AudioFrameSplitter.splitPcm(pcm, options);

        assertEquals(3, frames.size());
        assertEquals(8, frames.get(0).getPayload().length);
        assertEquals(8, frames.get(1).getPayload().length);
        assertEquals(4, frames.get(2).getPayload().length);
        assertTrue(frames.get(0).getHeader().isStereo());
        assertArrayEquals(pcm, concatenate(frames));
    }

    @Test
    public void splitPcmWrapsSequenceAtTwelveBits() {
        byte[] pcm = sequentialBytes(12);
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_16KHZ)
                .maxPayloadBytes(2)
                .startSequence(4094)
                .build();

        List<AudioFrame> frames = AudioFrameSplitter.splitPcm(pcm, options);

        assertEquals(6, frames.size());
        assertEquals(4094, frames.get(0).getHeader().getSequence());
        assertEquals(4095, frames.get(1).getHeader().getSequence());
        assertEquals(0, frames.get(2).getHeader().getSequence());
        assertEquals(1, frames.get(3).getHeader().getSequence());
    }

    @Test
    public void splitPcmReturnsEmptyListForEmptyInput() {
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .build();

        List<AudioFrame> frames = AudioFrameSplitter.splitPcm(new byte[0], options);

        assertTrue(frames.isEmpty());
    }

    @Test
    public void generatedFramesRoundTripThroughEncodeAndParse() {
        byte[] pcm = sequentialBytes(130000);
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .maxPayloadBytes(64000)
                .startTimestamp(321)
                .build();

        List<AudioFrame> frames = AudioFrameSplitter.splitPcm(pcm, options);

        for (AudioFrame frame : frames) {
            AudioFrame parsed = AudioFrame.parse(frame.encode());
            assertArrayEquals(frame.getPayload(), parsed.getPayload());
            assertEquals(frame.getHeader().getPayloadLength(), parsed.getHeader().getPayloadLength());
            assertEquals(frame.getHeader().getSequence(), parsed.getHeader().getSequence());
            assertEquals(frame.getHeader().getSampleRate(), parsed.getHeader().getSampleRate());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void splitPcmRejectsNullPcm() {
        AudioFrameSplitter.splitPcm(null, validOptions());
    }

    @Test(expected = IllegalArgumentException.class)
    public void splitPcmRejectsNullOptions() {
        AudioFrameSplitter.splitPcm(new byte[2], null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionsRequireSampleRate() {
        AudioSplitOptions.builder().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionsRejectMaxPayloadAboveProtocolLimit() {
        AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .maxPayloadBytes(65536)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionsRejectInvalidStartSequence() {
        AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .startSequence(4096)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionsRejectInvalidStartTimestamp() {
        AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .startTimestamp(0x100000)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void splitPcmRejectsUnalignedMonoPayload() {
        AudioFrameSplitter.splitPcm(new byte[3], validOptions());
    }

    @Test(expected = IllegalArgumentException.class)
    public void splitPcmRejectsMaxPayloadSmallerThanAlignedSampleBlock() {
        AudioSplitOptions options = AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .maxPayloadBytes(1)
                .build();

        AudioFrameSplitter.splitPcm(new byte[2], options);
    }

    private static AudioSplitOptions validOptions() {
        return AudioSplitOptions.builder()
                .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                .build();
    }

    private static byte[] sequentialBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i & 0xFF);
        }
        return bytes;
    }

    private static byte[] concatenate(List<AudioFrame> frames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (AudioFrame frame : frames) {
            byte[] payload = frame.getPayload();
            out.write(payload, 0, payload.length);
        }
        return out.toByteArray();
    }
}
