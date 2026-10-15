package com.newportai.liveavatar.channel.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** EXAU version 1 frame. PCM is interleaved S16LE. */
public final class ExternalAudioFrames {

    private ExternalAudioFrames() {
    }

    public static byte[] encode(long sequence, long timestampUs, byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            throw new IllegalArgumentException("pcm required");
        }
        ByteBuffer buffer = ByteBuffer.allocate(25 + pcm.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 'E').put((byte) 'X').put((byte) 'A').put((byte) 'U');
        buffer.put((byte) 1);
        buffer.putLong(sequence);
        buffer.putLong(timestampUs);
        buffer.putInt(pcm.length);
        buffer.put(pcm);
        return buffer.array();
    }
}
