# Live Avatar Channel Server Example

**English** | [中文](./README.zh.md)

This is a **reference implementation of the platform/developer server side** of the Live Avatar Channel Protocol. It supports both connection modes and demonstrates how to build a WebSocket server that processes user interactions with a live avatar.

## What This Example Does

This server plays the **server role** in both connection modes:

1. **Accepts WebSocket connections** at `ws://localhost:8080/avatar/ws`
2. **Exposes a REST session API** at `POST http://localhost:8080/api/session/start` (inbound mode)
3. **Handles protocol messages** for session management, user input, and system events
4. **Processes audio data** and performs ASR (Automatic Speech Recognition)
5. **Sends streaming AI responses** back to the connected client
6. **Handles interrupts** when users interrupt the avatar mid-speech
7. **Responds to idle triggers** to keep conversations engaging

## Connection Modes

### Inbound Mode (platform hosts the server)

The developer AppServer calls the platform REST API first, then connects to the platform WebSocket as a client.
Test with: **`LiveAvatarServiceInboundSimulator`**

```
Developer AppServer (Simulator)      Platform (This Example)
     |                                      |
     |-- POST /api/session/start ---------->|
     |<-- { sessionId,                      |
     |      clientToken,                 |
     |      agentWsUrl?agentToken=... } ----|
     |                                      |
     |-- WebSocket connect to agentWsUrl -->|  (agentToken validated & consumed)
     |-- session.init (sessionId, userId) ->|
     |<-- session.ready --------------------|
     |-- input.text / audio frames -------->|
     |<-- response.chunk / response.done ---|
```

### Outbound Mode (developer hosts the server)

The live avatar service connects directly — no REST call needed.
Test with: **`LiveAvatarServiceOutboundSimulator`**

```
Platform (Simulator)                 Developer Server (This Example)
     (WebSocket Client)                       (WebSocket Server)
     |                                      |
     |-- WebSocket connect to /avatar/ws -->|
     |-- session.init (sessionId, userId) ->|
     |<-- session.ready --------------------|
     |-- input.text / audio frames -------->|
     |<-- response.chunk / response.done ---|
```

## Quick Start

### Prerequisites

- Java 8 or higher
- Maven 3.6+
- Parent project `liveavatar-channel-sdk` installed in local Maven repository

### Install Parent SDK

First, install the parent SDK:

```bash
cd ..
mvn clean install -DskipTests
```

### Run the Server

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

You should see:

```
===========================================
Live Avatar Channel Server started successfully!
WebSocket endpoint : ws://localhost:8080/avatar/ws
Inbound session API: POST http://localhost:8080/api/session/start
===========================================
```

### Test with Simulator

In another terminal, run the appropriate simulator for your mode:

**Inbound mode** (developer client connects to platform):

```bash
cd ..
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceInboundSimulator"
```

**Outbound mode** (live avatar service connects to developer server):

```bash
cd ..
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceOutboundSimulator"
```

## Project Structure

```
liveavatar-channel-server-example/
├── pom.xml
├── README.md
└── src/main/java/com/newportai/liveavatar/channel/server/
    ├── AvatarServerApplication.java          # Spring Boot entry point
    ├── api/
    │   └── StartSessionController.java       # POST /api/session/start (inbound mode)
    ├── config/
    │   └── WebSocketConfig.java              # WebSocket endpoint configuration
    ├── handler/
    │   └── AvatarChannelWebSocketHandler.java # Main message handler
    ├── session/
    │   ├── SessionManager.java               # Thread-safe session management
    │   └── AvatarSession.java                # Session state model
    └── service/
        ├── MessageProcessingService.java     # Business logic processing
        └── AsrService.java                   # ASR recognition (mocked)
```

## Key Features

### 1. Session Management

The `SessionManager` uses `ConcurrentHashMap` to safely manage multiple concurrent WebSocket connections:

```java
sessionManager.initSession(wsSessionId, avatarSessionId, userId);
AvatarSession session = sessionManager.getSessionByWsId(wsSessionId);
```

### 2. Interrupt Handling

When the user sends new input while the avatar is speaking:

```java
// Detect interrupt condition
if (avatarSession.hasActiveResponse()) {
    // Cancel current response task
    avatarSession.cancelCurrentResponse();

    // Send control.interrupt to live avatar service
    Message interrupt = MessageBuilder.controlInterrupt();
    sendMessage(session, interrupt);
}
```

### 3. ASR Processing — Scenario 2B (Developer ASR / Omni)

This server example implements **Scenario 2B**: the platform continuously forwards all session audio as raw Binary Frames (no start/finish signaling, no platform-side VAD), and the developer handles VAD and ASR entirely internally.

The `AsrService` demonstrates:

- Receiving raw audio frames (continuously forwarded by the platform)
- Performing VAD (Voice Activity Detection) to detect speech boundaries
- Sending `input.voice.start` / `input.voice.finish` to the platform (so its state machine transitions correctly)
- Sending streaming `input.asr.partial` results to the platform (for real-time subtitle display)
- Sending the final `input.asr.final` result to the platform (advances state machine; logged in conversation history)
- Triggering the downstream AI response pipeline

**Key difference from Scenario 2A:** In 2A the platform originates these events and sends them to the developer. In 2B the developer originates the same events and sends them back to the platform — same events, reversed direction.

**⚠️ Production Note**: Replace the mock VAD and ASR with real services:

```java
// TODO: Integrate real ASR service (e.g. Alibaba Cloud, OpenAI Whisper)
```

### 4. Idle Trigger Handling

When the live avatar service detects user inactivity:

```java
@Override
private void handleIdleTrigger(WebSocketSession session, Message message) {
    IdleTriggerData data = JsonUtil.convertData(message.getData(), IdleTriggerData.class);

    // Business logic: decide whether to prompt user
    String promptText = determinePromptText(data);
    if (promptText != null) {
        Message prompt = MessageBuilder.systemPrompt(promptText);
        sendMessage(session, prompt);
    }
}
```

### 5. Streaming Responses

Send AI responses in chunks for natural conversation flow:

```java
String[] sentences = splitBySentence(aiResponse);
for (int i = 0; i < sentences.length; i++) {
    Message chunk = MessageBuilder.responseChunk(requestId, responseId, i, sentences[i]);
    sendMessage(session, chunk);
}

Message done = MessageBuilder.responseDone(requestId, responseId);
sendMessage(session, done);
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8080  # Change WebSocket port

logging:
  level:
    com.newportai.liveavatar.channel.server: DEBUG  # Adjust log level
```

## Integrating Real Services

### ASR Integration

Replace the mock ASR in `AsrService.java`:

```java
private AsrResult callRealTimeAsrService(List<byte[]> audioBuffer) {
    // Example: Alibaba Cloud Real-time ASR
    // 1. Initialize ASR client
    // 2. Send audio data
    // 3. Receive recognition results
    // 4. Return AsrResult
}
```

### AI Integration

Replace the mock AI in `MessageProcessingService.java`:

```java
private String callAIService(String text) {
    // Example: OpenAI GPT API
    // ChatCompletion completion = openai.createChatCompletion(request);
    // return completion.getChoices().get(0).getMessage().getContent();
}
```

## Protocol Messages Reference

### Messages Received from Live Avatar Service (Platform → Developer)

| Event                 | Description                               | When Sent                    |
|-----------------------|-------------------------------------------|------------------------------|
| `session.init`        | Initialize session                        | On connection start          |
| `input.text`          | User text input                           | User types message           |
| `audio frames`        | Raw user voice (binary, Scenario 2B)      | Continuously while session is active |
| `input.asr.partial`   | Streaming ASR result (Scenario 2A only)   | Platform ASR in progress     |
| `input.asr.final`     | Final ASR result (Scenario 2A only)       | Platform ASR complete        |
| `input.voice.start`   | VAD speech start (Scenario 2A only)       | Platform VAD triggered       |
| `input.voice.finish`  | VAD speech end (Scenario 2A only)         | Platform VAD triggered       |
| `image frames`        | User video input (binary)                 | Camera input                 |
| `session.state`       | Avatar state update                       | State changes                |
| `system.idleTrigger`  | Idle timeout detected                     | After inactivity             |
| `session.closing`     | Connection closing                        | Before disconnect            |

> **Scenario 2A vs 2B:** In Platform ASR (2A), the platform runs ASR/VAD and sends `input.asr.*` / `input.voice.*` **to** the developer. In Developer ASR / Omni (2B), the platform continuously forwards raw audio Binary Frames; the developer runs VAD + ASR and sends the same events **back to** the platform (reversed direction) to keep the state machine in sync.

### Messages Sent to Live Avatar Service (Developer → Platform)

| Event                         | Description                | When Sent                                 |
|-------------------------------|----------------------------|-------------------------------------------|
| `session.ready`               | Session initialized        | After `session.init`                      |
| `response.start`              | TTS config (optional)      | Before first chunk when using platform TTS |
| `response.chunk`              | AI response chunk          | Streaming response                        |
| `response.done`               | Response complete          | After all chunks                          |
| `response.audio.start`        | TTS output started         | Before pushing TTS audio frames           |
| `response.audio.finish`       | TTS output finished        | After pushing TTS audio frames            |
| `response.audio.promptStart`  | Idle prompt audio started  | Before pushing prompt audio frames        |
| `response.audio.promptFinish` | Idle prompt audio finished | After pushing prompt audio frames         |
| `control.interrupt`           | Interrupt avatar           | New input while avatar is speaking        |
| `system.prompt`               | Idle prompt text           | Response to `system.idleTrigger`          |
| `error`                       | Error occurred             | On errors                                 |

## Testing

### Manual Testing Checklist

- [ ] Server starts successfully on port 8080
- [ ] WebSocket connection established
- [ ] Session init/ready handshake works
- [ ] Text input processed correctly
- [ ] Streaming responses work
- [ ] Interrupt mechanism works
- [ ] Audio frames received and parsed
- [ ] ASR results sent correctly
- [ ] Idle trigger handled correctly
- [ ] Ping/pong heartbeat works
- [ ] Multiple concurrent sessions work
- [ ] Clean disconnection and resource cleanup

### Running Tests

```bash
mvn test
```

## Troubleshooting

### Port Already in Use

If port 8080 is already in use:

```yaml
# application.yml
server:
  port: 8081  # Change to different port
```

### WebSocket Connection Failed

Check:

1. Server is running (`mvn spring-boot:run`)
2. Correct URL: `ws://localhost:8080/avatar/ws`
3. No firewall blocking the connection

### Session Not Found Errors

Ensure the platform sends `session.init` after the WebSocket connection is established, and that the developer server has replied with `session.ready` before any other messages are exchanged.

## Production Considerations

### Security

1. **CORS**: Configure allowed origins in `WebSocketConfig.java`:

   ```java
   registry.addHandler(handler, "/avatar/ws")
       .setAllowedOrigins("https://facemarket.ai");
   ```

2. **Authentication**: Add auth token validation(optional):

   ```java
   @Override
   public void afterConnectionEstablished(WebSocketSession session) {
       String token = session.getHandshakeHeaders().getFirst("Authorization");
       if (!validateToken(token)) {
           session.close(CloseStatus.NOT_ACCEPTABLE);
       }
   }
   ```

### Performance

1. **Thread Pool**: Adjust thread pool size in `MessageProcessingService`:

   ```java
   executor.setCorePoolSize(50);
   executor.setMaxPoolSize(200);
   ```

2. **Audio Buffer**: Implement buffer size limits to prevent memory issues

3. **Connection Limits**: Add max connection limits per user/IP

### Monitoring

Add metrics and monitoring:

- WebSocket connection count
- Active session count
- Message processing latency
- ASR recognition success rate
- Error rates

## License

This example is part of the Avatar Channel SDK project.

## Support

For issues and questions:

- GitHub Issues: <https://github.com/newportAI-lab/liveavatar-channel/issues>
- Documentation: See parent project README
