package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.model.AudioFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods for splitting raw PCM audio into protocol-sized audio frames.
 */
public final class AudioFrameSplitter {
    private AudioFrameSplitter() {
    }

    public static List<AudioFrame> splitPcm(byte[] pcm, AudioSplitOptions options) {
        if (pcm == null) {
            throw new IllegalArgumentException("pcm is required");
        }
        if (options == null) {
            throw new IllegalArgumentException("options is required");
        }
        if (pcm.length == 0) {
            return Collections.emptyList();
        }

        int sampleBlockBytes = options.isStereo() ? 4 : 2;
        if (pcm.length % sampleBlockBytes != 0) {
            throw new IllegalArgumentException("pcm length must align to the sample block size");
        }

        int chunkSize = options.getMaxPayloadBytes() - (options.getMaxPayloadBytes() % sampleBlockBytes);
        if (chunkSize < sampleBlockBytes) {
            throw new IllegalArgumentException("maxPayloadBytes is smaller than one sample block after alignment");
        }

        List<AudioFrame> frames = new ArrayList<AudioFrame>((pcm.length + chunkSize - 1) / chunkSize);
        int offset = 0;
        int index = 0;
        while (offset < pcm.length) {
            int length = Math.min(chunkSize, pcm.length - offset);
            byte[] payload = Arrays.copyOfRange(pcm, offset, offset + length);
            frames.add(AudioFrameBuilder.create()
                    .stereo(options.isStereo())
                    .firstFrame(index == 0)
                    .sequence((options.getStartSequence() + index) & AudioSplitOptions.MAX_SEQUENCE)
                    .timestamp(options.getStartTimestamp())
                    .sampleRate(options.getSampleRate())
                    .payload(payload)
                    .build());
            offset += length;
            index++;
        }
        return frames;
    }
}
