# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Live Avatar Channel SDK is a Java SDK for live avatar WebSocket protocol supporting text and audio communication. It is a multi-module Maven project:

- **liveavatar-channel-sdk**: Core SDK library (WebSocket client + protocol implementation)
- **liveavatar-channel-server-example**: Reference Spring Boot server implementation

**Java 1.8**, **Maven 3.6+**

## Build & Run Commands

```bash
# Build and install all modules
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Run tests
mvn test

# Run the reference server (port 8080, ws://localhost:8080/avatar/ws)
cd liveavatar-channel-server-example && mvn spring-boot:run

# Run the client simulator (in a second terminal)
cd liveavatar-channel-sdk && mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceSimulator"
```

There is no linting configured. Tests live in `src/test/java/com/newportai/liveavatar/channel/example/` and are manual/integration-style (no JUnit runner invocations in the POMs beyond the default surefire).

## Architecture

### Two Usage Modes

1. **SDK Client Mode** — live avatar connects to a developer server:
   - Entry point: `AvatarWebSocketClient` (OkHttp3 WebSocket)
   - Use `AvatarChannelListenerAdapter` to override only needed callbacks
   - Build messages with `MessageBuilder` / `AudioFrameBuilder`

2. **Reference Server Mode** — developer server accepts connections:
   - Entry point: `AvatarServerApplication` (Spring Boot)
   - WebSocket endpoint configured in `WebSocketConfig` → `/avatar/ws`
   - `AvatarChannelWebSocketHandler` dispatches incoming messages to `MessageProcessingService`
   - `SessionManager` holds `ConcurrentHashMap` session state

### Core SDK Layers

```
Application (AvatarChannelListener callbacks)
    ↓
Protocol (Message / AudioFrame models + EventType constants)
    ↓
Transport (AvatarWebSocketClient via OkHttp3)
```

Key classes:

| Class | Purpose |
|---|---|
| `AvatarWebSocketClient` | Manages lifecycle, sends JSON text and binary audio, dispatches events |
| `AvatarChannelListener` | 18-method callback interface for all protocol events |
| `AvatarChannelListenerAdapter` | No-op adapter — extend only methods you need |
| `StreamingResponseHandler` | TreeMap-based in-order delivery of out-of-order `response.chunk` frames |
| `MessageBuilder` | Fluent factory for all protocol messages |
| `AudioFrameBuilder` | Fluent builder for binary audio frames |
| `ExponentialBackoffStrategy` | Optional auto-reconnect (1 s → 60 s max); disabled by default |
| `SessionManager` | Server-side thread-safe session registry |

### Protocol

**10 event types** grouped as: `session.*`, `input.*`, `response.*`, `control.*`, `system.*`, `error`.

**8 session states**: `IDLE → LISTENING → THINKING → STAGING → SPEAKING` plus `PROMPT_THINKING / PROMPT_STAGING / PROMPT_SPEAKING` for idle-wakeup flows.

**Audio frames** are binary (not JSON). Each frame = 9-byte header + payload. Header encodes type, channel, sequence (12-bit), timestamp (20-bit), sample rate, frame size, and payload length. Recommended settings: 16 kHz mono PCM, 640-sample frames (40 ms).

**Heartbeat**: Native WebSocket ping/pong (RFC 6455) handled automatically by OkHttp3 / Spring. No application-layer ping messages.

### Key Design Decisions

- Auto-reconnect is **opt-in** (`client.enableAutoReconnect()`).
- `StreamingResponseHandler` buffers chunks so the listener always receives them in sequence order.
- Server session cleanup (cancel tasks, clear audio buffer, remove mappings) happens in `SessionManager.removeSession()`.
- No LiveKit / SDK integration on the Java side — WebSocket only.
