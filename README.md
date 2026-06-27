# Live Avatar Channel SDK

**English** | [中文](./README.zh.md)

Java SDK for the Live Avatar WebSocket Agent protocol.

## Quick Start

```java
AvatarAgent agent = AvatarAgent.builder()
    .config(AvatarAgentConfig.builder()
        .apiKey("lk_live_...")
        .avatarId("avatar_...")
        .build())
    .listener(new AgentListener() {
        // Voice input (default: Developer ASR) — receive raw audio, run ASR+LLM locally
        public void onAudioFrame(AudioFrame frame) {
            String text = yourAsr.recognize(frame);
            if (text != null) {
                String reply = yourLLM.chat(text);
                agent.sendResponseChunk("req_" + System.currentTimeMillis(), reply, 0);
                agent.sendResponseDone("req_" + System.currentTimeMillis());
            }
        }

        // Text input (Platform ASR mode) — user typed or platform transcribed
        public void onTextInput(String text, String requestId) {
            String reply = yourLLM.chat(text);
            agent.sendResponseChunk(requestId, reply, 0);
            agent.sendResponseDone(requestId);
        }
    })
    .build();

SessionInfo info = agent.start();
// → info.getUserToken() + info.getSfuUrl() to your frontend
```

`agent.start()` does the REST call, WebSocket connection, and protocol handshake in one call.

## Features

- **One-call start** — REST + WS + handshake in `agent.start()`
- **Simple listener** — 9 callbacks, only `onTextInput` required
- **Developer ASR + Platform TTS** by default — receive raw audio, run ASR+LLM locally, send text back
- **Developer ASR / TTS** available — full send API for all 4 mode combos
- **Auto-reconnect** — exponential backoff, enabled by default
- **Native ping/pong** — RFC 6455, handled by OkHttp

## Maven

```xml
<dependency>
    <groupId>io.github.newportai-lab</groupId>
    <artifactId>liveavatar-channel-sdk</artifactId>
    <version>1.1.5</version>
</dependency>
```

## Architecture

```
AgentListener (your AI — onTextInput)
    ↓
AvatarAgent (lifecycle + 17 send methods)
    ↓
AvatarWebSocketClient (OkHttp3 transport, auto-handshake)
```

## Core API

### AvatarAgent

| Method | Description |
|--------|-------------|
| `start()` | Provisions session, connects WS, waits for handshake → `SessionInfo` |
| `stop()` | Sends `session.close`, disconnects (idempotent) |
| `sendResponseChunk(reqId, text, seq)` | Stream a text chunk (Platform TTS) |
| `sendResponseDone(reqId)` | End text response |
| `sendResponseAudioStart(reqId, resId)` | Begin audio response (Developer TTS) |
| `sendAudioFrame(frame)` | Send an audio frame |
| `sendResponseAudioFinish(reqId, resId)` | End audio response |
| `sendVoiceStart(reqId)` | Notify platform of speech start (Developer ASR) |
| `sendAsrPartial(reqId, seq, text)` | Stream partial ASR result |
| `sendAsrFinal(reqId, text)` | Send final ASR result |
| `sendInterrupt()` | Interrupt current avatar speech |
| `sendPrompt(text)` | Send idle-wake prompt text |
| `sendPromptAudioStart()` / `sendPromptAudioFinish()` | Idle-wake audio (Developer TTS) |
| `isConnected()` / `getSessionInfo()` | State queries |

### AgentListener

| Callback | When |
|----------|------|
| `onTextInput(text, requestId)` | User sent text or platform ASR result |
| `onSessionInit()` | Handshake complete |
| `onSessionState(state)` | Avatar state changed |
| `onIdleTrigger(reason, idleMs)` | User inactive — optionally reply with `sendPrompt()` |
| `onSessionClosing(reason)` | Platform about to close session |
| `onAudioFrame(frame)` | Raw audio (Developer ASR mode only) |
| `onError(message)` | Protocol or transport error |
| `onClosed(code, reason)` | Connection closed |

All methods have default no-op bodies — only override what you need.

### AvatarAgentConfig

```java
AvatarAgentConfig.builder()
    .apiKey("lk_live_...")          // required
    .avatarId("avatar_...")         // required
    .baseUrl("https://facemarket.ai")  // default
    .sandbox(false)                 // true = X-Env-Sandbox header
    .developerAsr(true)             // true = Developer ASR / Omni (default)
    .developerTts(false)            // true = Developer TTS
    .reconnectEnabled(true)
    .voiceId("voice_...")           // optional voice override
    .userId("user_...")             // optional
    .build();
```

### SessionInfo

| Getter | Description |
|--------|-------------|
| `getSessionId()` | Platform session identifier |
| `getUserToken()` | Frontend RTC join token |
| `getSfuUrl()` | LiveKit SFU endpoint for frontend |

## Mode Combinations

| Config | Receive via AgentListener | Send via AvatarAgent |
|--------|--------------------------|---------------------|
| **Developer ASR + Platform TTS** (default) | `onAudioFrame` | `sendVoiceStart`, `sendAsrPartial`, `sendAsrFinal` → `sendResponseChunk`, `sendResponseDone` |
| Platform ASR + Developer TTS | `onTextInput` | `sendResponseAudioStart`, `sendAudioFrame`, `sendResponseAudioFinish` |
| Platform ASR + Platform TTS | `onTextInput` | `sendResponseChunk`, `sendResponseDone` |
| Developer ASR + Developer TTS | `onAudioFrame` | ASR events → `sendResponseAudioStart`, `sendAudioFrame`, `sendResponseAudioFinish` |

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

## Server Example

Reference Spring Boot app in [`liveavatar-channel-server-example/`](./liveavatar-channel-server-example/).

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

### REST API

```bash
# Start a session
curl -X POST http://localhost:8080/api/session/start \
  -H "Content-Type: application/json" \
  -d '{"avatarId": "avatar_xxx"}'
# → {"sessionId":"...", "userToken":"...", "sfuUrl":"...", "agentWsUrl":"..."}

# Stop a session
curl -X POST http://localhost:8080/api/session/stop \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "sess_xxx"}'
```

### Configuration (`application.yml`)

| Key | Default | Description |
|-----|---------|-------------|
| `avatar.api.key` | (example) | API Key from console |
| `avatar.id` | (example) | Default avatar ID |
| `avatar.api.base-url` | `https://facemarket.ai` | Platform dispatcher URL |
| `avatar.sandbox.enabled` | `false` | Route to sandbox (30 free min/month) |
| `avatar.asr.developer-enabled` | `true` | `false` = Platform ASR, `true` = Developer ASR (default) |
| `avatar.tts.developer-enabled` | `false` | `true` = Developer TTS |

### Customize

Edit `DemoAgentService.onTextInput()` — replace the echo AI with your own:

```java
@Override
public void onTextInput(String text, String requestId) {
    String reply = yourLLM.chat(text);
    agent.sendResponseChunk(requestId, reply, 0);
    agent.sendResponseDone(requestId);
}
```

## Build

```bash
mvn clean install -DskipTests -Dgpg.skip=true
```

## Protocol

See [PROTOCOL.md](./PROTOCOL.md) for the full WebSocket protocol definition.

## License

[LICENSE](./LICENSE)
