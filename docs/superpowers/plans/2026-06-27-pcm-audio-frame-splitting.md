# PCM Audio Frame Splitting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small SDK utility that splits oversized raw 16-bit PCM payloads into valid `AudioFrame` sequences.

**Architecture:** Keep splitting separate from transport by adding focused utility classes under `com.newportai.liveavatar.channel.util`. Existing `AvatarAgent.sendAudioFrame(AudioFrame)` remains unchanged; callers use the splitter and send the returned frames in order.

**Tech Stack:** Java 8, Maven, JUnit 4.13.2, existing SDK `AudioFrame`, `AudioHeader`, and `AudioFrameBuilder`.

## Global Constraints

- Support PCM splitting only in this version.
- Do not parse, re-packetize, or byte-split Opus/Ogg/WebM payloads.
- Do not change `AvatarAgent.sendAudioFrame` behavior.
- Do not add automatic sending or pacing.
- Do not introduce audio codec dependencies.
- `maxPayloadBytes` default is `64000`.
- `maxPayloadBytes` must be `1-65535`.
- `sampleRate` is required.
- Empty PCM input returns an empty list.
- PCM input must be raw 16-bit signed PCM.
- Mono PCM splits must preserve 2-byte alignment.
- Stereo PCM splits must preserve 4-byte alignment.
- Java source compatibility remains Java 8.

---

## File Structure

- Create `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioSplitOptions.java`
  - Immutable options object with builder defaults and validation.
- Create `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioFrameSplitter.java`
  - Static PCM splitter that returns `List<AudioFrame>`.
- Create `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/util/AudioFrameSplitterTest.java`
  - JUnit 4 tests for splitting, metadata, validation, and encode/parse round trips.
- Modify `README.md`
  - Add a short PCM splitting example and note that Opus payloads are not byte-split by this utility.
- Modify `README.zh.md`
  - Add the same guidance in Chinese.

---

### Task 1: Add PCM Splitter API and Behavior

**Files:**
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioSplitOptions.java`
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioFrameSplitter.java`
- Test: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/util/AudioFrameSplitterTest.java`

**Interfaces:**
- Produces: `AudioSplitOptions.builder()` returning `AudioSplitOptions.Builder`
- Produces: `AudioFrameSplitter.splitPcm(byte[] pcm, AudioSplitOptions options): List<AudioFrame>`
- Consumes: `AudioFrameBuilder.create()`, `AudioHeader.SampleRate`, `AudioFrame.encode()`, `AudioFrame.parse(byte[])`

- [ ] **Step 1: Write failing tests for normal PCM splitting**

Create `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/util/AudioFrameSplitterTest.java`:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail because classes are missing**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=AudioFrameSplitterTest test -Dgpg.skip=true
```

Expected: compilation failure mentioning `AudioSplitOptions` or `AudioFrameSplitter` cannot be found.

- [ ] **Step 3: Implement `AudioSplitOptions`**

Create `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioSplitOptions.java`:

```java
package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.model.AudioHeader;

/**
 * Options for splitting raw PCM audio into protocol-sized audio frames.
 */
public final class AudioSplitOptions {
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 64000;
    public static final int MAX_PAYLOAD_BYTES = 65535;
    public static final int MAX_SEQUENCE = 4095;
    public static final int MAX_TIMESTAMP = 0xFFFFF;

    private final int maxPayloadBytes;
    private final boolean stereo;
    private final AudioHeader.SampleRate sampleRate;
    private final int startSequence;
    private final int startTimestamp;

    private AudioSplitOptions(Builder builder) {
        this.maxPayloadBytes = builder.maxPayloadBytes;
        this.stereo = builder.stereo;
        this.sampleRate = builder.sampleRate;
        this.startSequence = builder.startSequence;
        this.startTimestamp = builder.startTimestamp;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public boolean isStereo() {
        return stereo;
    }

    public AudioHeader.SampleRate getSampleRate() {
        return sampleRate;
    }

    public int getStartSequence() {
        return startSequence;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    private void validate() {
        if (sampleRate == null) {
            throw new IllegalArgumentException("sampleRate is required");
        }
        if (maxPayloadBytes <= 0 || maxPayloadBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("maxPayloadBytes must be 1-65535");
        }
        if (startSequence < 0 || startSequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("startSequence must be 0-4095");
        }
        if (startTimestamp < 0 || startTimestamp > MAX_TIMESTAMP) {
            throw new IllegalArgumentException("startTimestamp must be 0-1048575");
        }
    }

    public static final class Builder {
        private int maxPayloadBytes = DEFAULT_MAX_PAYLOAD_BYTES;
        private boolean stereo;
        private AudioHeader.SampleRate sampleRate;
        private int startSequence;
        private int startTimestamp = (int) (System.currentTimeMillis() & MAX_TIMESTAMP);

        private Builder() {
        }

        public Builder maxPayloadBytes(int maxPayloadBytes) {
            this.maxPayloadBytes = maxPayloadBytes;
            return this;
        }

        public Builder stereo(boolean stereo) {
            this.stereo = stereo;
            return this;
        }

        public Builder sampleRate(AudioHeader.SampleRate sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder startSequence(int startSequence) {
            this.startSequence = startSequence;
            return this;
        }

        public Builder startTimestamp(int startTimestamp) {
            this.startTimestamp = startTimestamp;
            return this;
        }

        public AudioSplitOptions build() {
            return new AudioSplitOptions(this);
        }
    }
}
```

- [ ] **Step 4: Implement `AudioFrameSplitter`**

Create `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioFrameSplitter.java`:

```java
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
```

- [ ] **Step 5: Run splitter tests**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=AudioFrameSplitterTest test -Dgpg.skip=true
```

Expected: all `AudioFrameSplitterTest` tests pass.

- [ ] **Step 6: Add validation tests**

Append these tests to `AudioFrameSplitterTest` before the helper methods:

```java
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
```

- [ ] **Step 7: Run validation tests and full SDK tests**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=AudioFrameSplitterTest test -Dgpg.skip=true
```

Expected: all splitter tests pass.

Then run:

```bash
mvn -q -pl liveavatar-channel-sdk test -Dgpg.skip=true
```

Expected: all SDK tests pass.

- [ ] **Step 8: Commit Task 1**

Run:

```bash
git add liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioSplitOptions.java liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/AudioFrameSplitter.java liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/util/AudioFrameSplitterTest.java
git commit -m "feat: add pcm audio frame splitter"
```

Expected: commit succeeds.

---

### Task 2: Document PCM Splitting and Opus Boundary

**Files:**
- Modify: `README.md`
- Modify: `README.zh.md`

**Interfaces:**
- Consumes: `AudioFrameSplitter.splitPcm(byte[] pcm, AudioSplitOptions options): List<AudioFrame>`
- Consumes: `AudioSplitOptions.builder()`
- Produces: README examples showing how callers split PCM and send frames.

- [ ] **Step 1: Find the best README location**

Run:

```bash
rg -n "AudioFrame|sendAudioFrame|Developer TTS|TTS|audio" README.md README.zh.md
```

Expected: output identifies the existing audio or Developer TTS section where the example belongs.

- [ ] **Step 2: Update English README**

Add this concise example near the existing audio-frame or Developer TTS documentation in `README.md`:

````markdown
### Splitting large PCM audio payloads

Raw PCM audio must fit the SDK audio-frame header limit. For large 16-bit PCM
payloads, split the payload before sending:

```java
List<AudioFrame> frames = AudioFrameSplitter.splitPcm(
    pcmBytes,
    AudioSplitOptions.builder()
        .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
        .stereo(false)
        .build()
);

for (AudioFrame frame : frames) {
    agent.sendAudioFrame(frame);
}
```

`AudioFrameSplitter` is for raw PCM only. Do not use it to byte-split Opus,
Ogg, or WebM payloads; keep compressed audio packetization at the encoder or
container layer.
````

- [ ] **Step 3: Update Chinese README**

Add this concise example near the corresponding audio-frame or Developer TTS documentation in `README.zh.md`:

````markdown
### 拆分较大的 PCM 音频负载

原始 PCM 音频需要符合 SDK 音频帧头的长度限制。对于较大的 16-bit PCM
负载，可以先拆分再发送：

```java
List<AudioFrame> frames = AudioFrameSplitter.splitPcm(
    pcmBytes,
    AudioSplitOptions.builder()
        .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
        .stereo(false)
        .build()
);

for (AudioFrame frame : frames) {
    agent.sendAudioFrame(frame);
}
```

`AudioFrameSplitter` 只用于原始 PCM。不要用它按字节拆分 Opus、Ogg 或 WebM
负载；压缩音频的分包应在编码器或容器层完成。
````

- [ ] **Step 4: Run focused tests after docs**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=AudioFrameSplitterTest test -Dgpg.skip=true
```

Expected: all splitter tests still pass.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
git add README.md README.zh.md
git commit -m "docs: document pcm audio frame splitting"
```

Expected: commit succeeds.

---

### Task 3: Final Verification

**Files:**
- No new files.
- Verify all files changed by Tasks 1-2.

**Interfaces:**
- Consumes: all Task 1 and Task 2 deliverables.
- Produces: verified working tree ready for review.

- [ ] **Step 1: Run full Maven test suite with signing skipped**

Run:

```bash
mvn test -Dgpg.skip=true
```

Expected: all modules build and tests pass.

- [ ] **Step 2: Inspect final diff**

Run:

```bash
git status --short
git log --oneline -3
```

Expected: working tree is clean after the task commits; the latest commits include the splitter implementation and docs commits.

- [ ] **Step 3: Report results**

Summarize:

- New utility classes added.
- PCM behavior covered by tests.
- Opus explicitly documented as not byte-split.
- Exact Maven command results.
