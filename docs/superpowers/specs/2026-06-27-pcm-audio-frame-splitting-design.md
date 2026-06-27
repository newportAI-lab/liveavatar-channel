# PCM Audio Frame Splitting Design

## Goal

Add a small SDK utility that splits oversized PCM audio payloads into valid
`AudioFrame` instances before sending them over the Live Avatar WebSocket
protocol.

The first version intentionally supports PCM only. Opus is a compressed packet
format and should not be split by raw byte offsets inside this utility.

## Background

`AudioFrame` encodes payload length into the audio header as a 16-bit field.
The current SDK enforces `payloadLength` in the range `0-65535`, so a single
audio frame cannot carry payloads larger than 65535 bytes even if the WebSocket
transport itself allows larger messages.

The SDK already exposes `AvatarAgent.sendAudioFrame(AudioFrame frame)`. The new
utility should prepare a sequence of valid frames; callers remain responsible
for sending those frames.

## Non-Goals

- Do not change `AvatarAgent.sendAudioFrame` behavior.
- Do not add automatic sending or pacing.
- Do not parse, re-packetize, or byte-split Opus/Ogg/WebM payloads.
- Do not introduce audio codec dependencies.

## Proposed API

Add a new utility class:

```java
public final class AudioFrameSplitter {
    public static List<AudioFrame> splitPcm(byte[] pcm, AudioSplitOptions options);
}
```

Add an options class:

```java
public final class AudioSplitOptions {
    public static Builder builder();

    public int getMaxPayloadBytes();
    public boolean isStereo();
    public AudioHeader.SampleRate getSampleRate();
    public int getStartSequence();
    public int getStartTimestamp();
}
```

Defaults:

- `maxPayloadBytes`: `64000`
- `stereo`: `false`
- `startSequence`: `0`
- `startTimestamp`: current low 20 bits of `System.currentTimeMillis()`

Required options:

- `sampleRate`

The implementation should place `AudioFrameSplitter` in the existing SDK util
package. `AudioSplitOptions` should live beside it unless the existing package
layout makes a model package clearly more appropriate.

## PCM Splitting Rules

`splitPcm` accepts raw 16-bit signed PCM bytes.

`maxPayloadBytes` must be no larger than the protocol limit of 65535 bytes.

The split size must be aligned to the PCM sample block:

- Mono 16-bit PCM: 2-byte alignment
- Stereo 16-bit PCM: 4-byte alignment

Each output frame must preserve byte order and contain a contiguous slice of the
input payload.

Frame metadata:

- The first frame has `firstFrame=true`; all following frames have
  `firstFrame=false`.
- `sequence` starts from `options.startSequence` and increments by one per
  frame.
- `sequence` wraps within the existing 12-bit range using `& 0xFFF`.
- `timestamp` starts from `options.startTimestamp`.
- `sampleRate` and `stereo` come from options.
- `payloadLength` is set by the existing `AudioFrame`/`AudioFrameBuilder`
  behavior.

`frameSize` should remain unset unless the protocol requires a specific value.
This keeps the first change focused on fixing oversized payload rejection
without adding ambiguous semantics.

## Opus Handling

The first version does not add an Opus splitting API.

Callers that send Opus should continue constructing `AudioFrame` values
directly and ensure each Opus payload already fits the protocol limit. If an
Opus payload is too large, it should be addressed at the encoder or packetizer
layer rather than by raw byte splitting in this SDK.

Documentation should state this explicitly so users do not assume the PCM
splitter is safe for compressed audio payloads.

## Validation

`splitPcm` should reject invalid input early:

- `pcm == null`
- `options == null`
- `sampleRate == null`
- `maxPayloadBytes <= 0`
- `maxPayloadBytes > 65535`
- `startSequence` outside `0-4095`
- `startTimestamp` outside `0-1048575`
- PCM byte length not aligned to the sample block size
- Effective aligned max payload smaller than one sample block

Empty PCM input should return an empty list.

## Tests

Add focused unit tests for:

- PCM under the limit returns one frame.
- PCM over the limit returns multiple frames.
- Every frame payload length is at or below `maxPayloadBytes`.
- Mono splitting preserves 2-byte alignment.
- Stereo splitting preserves 4-byte alignment.
- Output payloads concatenate back to the original PCM bytes.
- First-frame flag is true only for the first frame.
- Sequence increments and wraps after 4095.
- Invalid option values fail with `IllegalArgumentException`.
- Empty input returns an empty list.
- Each generated frame can be encoded and parsed successfully.

## Documentation

Update SDK documentation with a short example:

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

The documentation should also note that Opus payloads are not byte-split by
this utility.

## Success Criteria

- Oversized PCM payloads can be converted into valid `AudioFrame` sequences.
- No generated frame exceeds the protocol payload limit.
- Existing `AvatarAgent.sendAudioFrame` behavior remains unchanged.
- Opus behavior is documented as unsupported for splitting in this version.
- Unit tests cover splitting, metadata, validation, and round-trip encoding.
