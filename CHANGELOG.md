# Changelog

Notable changes to this SDK are documented here. The date is the release day.

## [1.3.2] - 2026-10-22

1. Added: `sendExternalPlayback(requestId, responseId, frames)` plays developer PCM as the voice turn while platform TTS is enabled. It sends `response.audio.start` and `response.audio.finish` with `data.purpose=external`, then the existing binary audio frames. The SDK does not resample. This is not `sendExternalAudio`, which remains accompaniment and does not replace speech. If a frame fails after start, `response.audio.finish` is still sent. The platform Dispatcher must already accept `purpose=external`.

## [1.3.1] - 2026-10-15

1. Added: `AvatarAgentConfig` adds the optional parameter `externalAudioConfig`. `sampleRate`: `0` or omitted follows the session voice format; otherwise 16000, 24000, 44100, or 48000. `channels`: `0` or omitted follows the session voice format; otherwise 1 or 2. Any other value is rejected. On session start it is sent as `externalAudioConfig`.
2. Added: `sendExternalAudio(byte[] pcm, long timestampUs)` is a new method. `pcm` is 16-bit little-endian PCM. `timestampUs` of `0` lets the platform choose the presentation time. The audio does not drive lip sync. Do not use `response.audio.*` or `sendAudioFrame`. Its sample rate and channel count must match `externalAudioConfig` when that parameter is set. When it is not set, they must match the TTS audio format used for this session. One frame including its header must not exceed 65536 bytes. Send later packets before the queued audio finishes, and make the first packet longer than the later ones without exceeding that limit.
3. Added: `sendInterrupt` adds the parameter `InterruptScope scope`. `null` and `VOICE_ONLY` stop voice playback only. `BGM_ONLY` clears queued external audio only. `VOICE_AND_ACCOMPANIMENT` stops voice playback and clears queued external audio. Calling `sendInterrupt()` without the parameter uses `VOICE_ONLY`. The value is sent as `data.interruptScope` on `control.interrupt`. Base-video audio is not cleared.
4. Changed: session start fields `onceVideoIds` and `loopVideoIds`, and `scene.switchVideo` fields `onceVideos`, `loopVideos`, and `videoChoreographyGroups`, used to accept only resource IDs. Each item may now also be an alias of that avatar's base video. An alias is 3 to 32 characters, starts with a letter, uses only lowercase letters, digits, and underscores, and does not end with an underscore. The SDK passes the string through. The platform replaces an alias with its resource ID before playback.

## [1.3.0] - 2026-09-28

### Added

- Response state callbacks and a streaming system-prompt API.

## [1.1.10] - 2026-08-14

### Added

- The `session.close` event.

## [1.1.9] - 2026-08-06

### Added

- Resource transition messages.

## [1.1.5] - 2026-06-21

### Fixed

- The WebSocket client no longer reconnects indefinitely.

## [1.1.4] - 2026-06-21

### Added

- Voice settings on the agent configuration.

## [1.1.3] - 2026-06-21

### Added

- `scene.ready` on `AgentListener`.

## [1.1.1] - 2026-05-17

### Changed

- Event payloads accept the additional fields introduced in this release.

## [1.1.0] - 2026-05-15

### Changed

- The public protocol and the agent listener API were revised.

## [1.0.0] - 2026-04-08

### Added

- Initial SDK release.
