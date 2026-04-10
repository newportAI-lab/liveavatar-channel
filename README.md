# Live Avatar Channel SDK

**English** | [中文](./README.zh.md)

A Java SDK for the Live Avatar WebSocket Protocol (Text + Audio/Image)

## Features

- 🚀 WebSocket Bidirectional Communication (Text Messages + Audio/Image Frames)
- 📨 Comprehensive Support for Text Message Types (session, input, response, control, system, error)
- 🔊 Support for Binary Audio/Image Frame Protocol (Header + Payload)
- 🔄 Streamed Data Transmission with Sequence Number-based Out-of-Order Handling
- 💪 Type-Safe Message and Audio/Image Frame Builders
- 🎯 Event-Driven Listener Pattern
- 💓 Native WebSocket Ping/Pong Automatic Heartbeat (5-second interval)
- 🔌 Automatic Reconnection Mechanism (Exponential Backoff Strategy)
- 📦 Out-of-the-Box Readiness; Easy to Integrate
- 🏗️ Multi-Module Maven Project Structure

## Architecture Overview

This SDK implements the live avatar protocol based on **WebSocket**, supporting the following:

### ✅ Supported Features

- **Text Messages:** JSON-formatted text protocol (session, input, response, control, etc.)
- **Binary Data:** Binary audio/image frames (Header + Payload)
- **Keep-Alive Heartbeat:** Native WebSocket Ping/Pong control frames (handled automatically at 5-second intervals)
- **Streaming:** Supports streaming of both text and audio/image data

### ⚠️ Technical Stack Notes

**WebSocket Mode:**
- ✅ Supports both Inbound and Outbound connection modes
- ✅ Supports Text Protocol (JSON)
- ✅ Supports Audio/Image Protocol (Binary)
- ✅ Fully supported by this SDK

**Build the entire project:**
```bash
mvn clean install
```

This will build the SDK and the server example in sequence, eliminating the need to manually install the SDK to your local repository.

## Quick Start

### Connection Modes

The Live Avatar Channel protocol supports two WebSocket connection modes:

#### Inbound Mode (platform hosts the server)

The Live Avatar platform provides the WebSocket server. The developer:
1. Calls `POST /api/session/start` on the platform REST API → receives `sessionId` + `wsUrl`
2. Connects to `wsUrl` using the SDK
3. Sends `session.init` with the returned `sessionId`

```java
// Step 1: call REST API to get sessionId + wsUrl (see LiveAvatarServiceInboundSimulator)
// Step 2: connect using the returned wsUrl
AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
    new AvatarChannelListenerAdapter() {
        @Override
        public void onConnected() {
            // Step 3: send session.init with the platform-issued sessionId
            client.sendMessage(MessageBuilder.sessionInit(sessionId, userId));
        }
    }
);
```

#### Outbound Mode (developer hosts the server)

The developer implements a WebSocket server; the Live Avatar Service connects to it as a client.

**Complete server example**: [`liveavatar-channel-server-example/`](./liveavatar-channel-server-example/)

This is a Spring Boot-based reference implementation demonstrating:
- How to accept connections from the Live Avatar service
- How to handle text and audio input
- How to perform ASR (Automatic Speech Recognition)
- How to send streaming AI responses
- How to handle interruptions and idle wake-ups

See: [Server Example README](./liveavatar-channel-server-example/README.md)

### Maven Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
<groupId>com.newportai.liveavatar.channel</groupId>
<artifactId>avatar-channel-sdk</artifactId>
<version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.util.MessageBuilder;

// Create the client
AvatarWebSocketClient client = new AvatarWebSocketClient(
"ws://your-server.com/ws",
new AvatarChannelListenerAdapter() {

@Override
public void onConnected() {
System.out.println("Connected!");

// Send session.init
try {
Message msg = MessageBuilder.sessionInit("sess_123", "user_1");
client.sendMessage(msg);
} catch (Exception e) {
e.printStackTrace();
}
}

@Override
public void onResponseChunk(Message message) {
// Handle streaming responses
System.out.println("Chunk: " + message.getData());
}
}
);

// Connect
client.connect();

// Send a message
Message textMessage = MessageBuilder.inputText("req_1", "Hello");
client.sendMessage(textMessage);

// Disconnect
client.disconnect();
```

## Protocol Support

### Message Types

The SDK fully supports all message types defined in the protocol:

#### Session-related
- `session.init` - Session Initialization
- `session.ready` - Session ready
- `session.state` - Session state change
- `session.closing` - Session about to close

#### Input-related
- `input.text` - Text input
- `input.asr.partial` - ASR partial result
- `input.asr.final` - ASR final result
- `input.voice.start` - User voice start
- `input.voice.finish` - User voice finish

#### Response-related
- `response.start` - Response start with optional TTS audio config (speed / volume / mood); sent before first chunk when TTS is managed by the Live Avatar Service
- `response.chunk` - Streaming response chunk
- `response.done` - Response complete
- `response.cancel` - Response cancelled
- `response.voice.start` - TTS response start
- `response.voice.finish` - TTS response finish
- `finishresponse.audio.promptStart` - TTS prompt response start
- `finishresponse.audio.promptFinish` - TTS prompt response finish

#### Control-related
- `control.interrupt` - Interruption control

#### System-related
- `system.prompt` - System prompt (triggered by response idle state)
- `system.idleTrigger` - Idle trigger (detected no user interaction)

#### Error
- `error` - Error message

### Heartbeat Mechanism

This SDK utilizes **native WebSocket ping/pong control frames** to maintain the heartbeat and keep the connection alive:

- **Client:** OkHttp automatically sends native WebSocket ping frames (opcode 0x9) every 5 seconds.
- **Server:** Spring WebSocket automatically responds with pong frames (opcode 0xA).
- **Features:**
- Handled at the transport layer; transparent to the application layer.
- No JSON serialization required, resulting in higher efficiency.
- Automatic timeout detection and reconnection.
- Compliant with the RFC 6455 standard.

**Configuration Example:**
```java
OkHttpClient client = new OkHttpClient.Builder()
.pingInterval(5, TimeUnit.SECONDS)  // Enable native ping
.build();
```

> **Note:** Application-layer ping/pong messages in JSON format are no longer used.

### Audio Protocol

The SDK supports the transmission of binary audio data via WebSocket. #### Audio Frame Structure

```
[Header (9 bytes)] + [Audio Payload]
```

#### Header Field Definitions (72-bit)

| Field | Bits | Description |
|------|------|-----|
| T | 2    | Type; fixed at 01 (Audio Frame) |
| C | 1    | Channel (0=mono, 1=stereo) |
| K | 1    | Is First Frame? |
| S | 12   | Sequence Number (0-4095) |
| TS | 20   | Timestamp (ms) |
| SR | 2    | Sample Rate (00=16kHz, 01=24kHz, 10=48kHz) |
| F | 12   | Frame Size |
| Codec| 2 | Codec |
| R | 4    | Reserved |
| L | 16   | Payload Length |

#### Usage Example

```java
// Create an audio frame
AudioFrame frame = AudioFrameBuilder.create()
.mono()  // or .stereo(true)
.firstFrame(true)
.sequence(0)
.currentTimestamp()
.sampleRate(AudioHeader.SampleRate.RATE_48KHZ)
.frameSize(480)
.codec(Codec.PCM)
.payload(pcmData)
.build();

// Send audio frame
client.sendAudioFrame(frame);

// Receive audio frame
@Override
public void onAudioFrame(AudioFrame frame) {
byte[] pcmData = frame.getPayload();
int sampleRate = frame.getHeader().getSampleRate().getValue();
// Process PCM audio data...
}
```

> **Note:** The audio protocol is supported only on the WebSocket channel.

## Core Components

### AvatarWebSocketClient

A WebSocket client implementation providing the following functionality:

```java
// Create client
AvatarWebSocketClient client = new AvatarWebSocketClient(url, listener);

// Connect
client.connect();

// Send message
client.sendMessage(message);

// Check connection status
boolean connected = client.isConnected();

// Disconnect
client.disconnect();
```

### AvatarChannelListener

An event listener interface for monitoring all protocol events:

```java
public interface AvatarChannelListener {
void onConnected();
void onClosed(int code, String reason);
void onError(Throwable error);
void onSessionInit(Message message);
void onSessionReady(Message message);
void onInputText(Message message);
void onResponseChunk(Message message);
void onResponseDone(Message message);
// ... more methods
}
```

It is recommended to extend `AvatarChannelListenerAdapter` to implement only the methods you require. ### MessageBuilder

A message builder providing convenient methods for creating messages:

```java
// Session messages
Message msg = MessageBuilder.sessionInit(sessionId, userId);
Message msg = MessageBuilder.sessionReady(sessionId);
Message msg = MessageBuilder.sessionClose(sessionId, reason);

// Input messages
Message msg = MessageBuilder.inputText(requestId, text);
Message msg = MessageBuilder.asrFinal(sessionId, requestId, text);

// Response messages
Message msg = MessageBuilder.responseStart(requestId, responseId, audioConfig);
Message msg = MessageBuilder.responseChunk(sessionId, requestId, responseId, seq, text);
Message msg = MessageBuilder.responseDone(sessionId, requestId, responseId);

// Control messages
Message msg = MessageBuilder.controlInterrupt(sessionId);

// System messages
Message msg = MessageBuilder.systemPrompt(sessionId, text);

// Error messages
Message msg = MessageBuilder.error(sessionId, requestId, code, errorMessage);
```

### SessionState

A session state enumeration defining all possible states of the live avatar:

```java
// Import the enum
import com.newportai.liveavatar.channel.model.SessionState;

// Use the enum to create a state message
Message msg = MessageBuilder.sessionState(
sessionId,
SessionState.SPEAKING.getValue(),
seq
);

// Parse a state message
@Override
public void onSessionState(Message message) {
SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
SessionState state = SessionState.fromValue(data.getState());

switch (state) {
case IDLE:           // Idle; waiting for input
break;
case LISTENING:      // User speaking; ASR capturing audio
break;
case THINKING:       // System processing; LLM/TTS preparation
break;
case STAGING:        // Preparing to generate the Live avatar
break;
case SPEAKING:       // Live avatar providing a standard response
break;
case PROMPT_THINKING:  // Preparing the prompt script
break;
case PROMPT_STAGING:   // Preparing the Live avatar for the prompt
break;
case PROMPT_SPEAKING:  // Live avatar delivering the prompt
break;
}
}
```

**Explanation of All States**:

| State | Speaker | System Behavior |
|------|---------|---------|
| IDLE | None | Waiting for input |
| LISTENING | User | ASR capturing audio |
| THINKING | System (Brain) | Preparing LLM/TTS |
| STAGING | System (Body) | Preparing to generate Live avatar |
| SPEAKING | System (Body) | Live avatar delivering standard response |
| PROMPT_THINKING | System (Brain) | Preparing prompt text |
| PROMPT_STAGING | System (Body) | Preparing to generate Live avatar |
| PROMPT_SPEAKING | System (Body) | Live avatar broadcasting prompt audio |

### StreamingResponseHandler

A streaming response handler that supports automatic reordering of out-of-sequence messages:

```java
StreamingResponseHandler streamHandler = new StreamingResponseHandler();

// Use within the listener
@Override
public void onResponseChunk(Message message) {
streamHandler.handleChunk(message, chunk -> {
System.out.println("Seq: " + chunk.getSeq() + ", Text: " + chunk.getText());
});
}

@Override
public void onResponseDone(Message message) {
streamHandler.handleDone(message, responseId -> {
System.out.println("Response " + responseId + " completed");
});
}
```

## Use Cases

### Scenario 1: Live avatar Service Client

Connects to the developer's server, sends user input, and receives responses:

```java
AvatarWebSocketClient client = new AvatarWebSocketClient(url, new AvatarChannelListenerAdapter() {
@Override
public void onConnected() {
// Send session.init after connecting
Message msg = MessageBuilder.sessionInit(sessionId, userId);
client.sendMessage(msg);
}

@Override
public void onSessionReady(Message message) {
// Session is ready; user input can now be sent
Message textMsg = MessageBuilder.inputText(requestId, userInput);
client.sendMessage(textMsg);
}

@Override
public void onResponseChunk(Message message) {
// Receive streaming response chunks and play them back
TextData data = JsonUtil.convertData(message.getData(), TextData.class);
playAvatarSpeech(data.getText());
}
});
```

### Scenario 2: Developer Server

Receiving input from the Live Avatar service and returning an AI response:

```java
AvatarWebSocketClient client = new AvatarWebSocketClient(url, new AvatarChannelListenerAdapter() {
@Override
public void onSessionInit(Message message) {
// The Live Avatar initiates the connection; return "ready".
String sessionId = extractSessionId(message);
Message readyMsg = MessageBuilder.sessionReady(sessionId);
client.sendMessage(readyMsg);
}

@Override
public void onInputText(Message message) {
// Receive user input and call the AI ​​to generate a response
TextData input = JsonUtil.convertData(message.getData(), TextData.class);
String aiResponse = callAIModel(input.getText());

// Stream the response
String responseId = generateResponseId();
for (int i = 0; i < aiResponse.length(); i++) {
Message chunk = MessageBuilder.responseChunk(
sessionId, message.getRequestId(), responseId, i,
String.valueOf(aiResponse.charAt(i))
);
client.sendMessage(chunk);
}

// Send completion signal
Message done = MessageBuilder.responseDone(sessionId, message.getRequestId(), responseId);
client.sendMessage(done);
}
});
```

### Scenario 3: Real-time ASR (Speech Recognition)

Handling real-time speech recognition:

```java
@Override
public void onAsrPartial(Message message) {
// Partial recognition result; can be used for real-time display
TextData data = JsonUtil.convertData(message.getData(), TextData.class);
updateSubtitle(data.getText());
}

@Override
public void onAsrFinal(Message message) {
// Final recognition result; used for AI processing
TextData data = JsonUtil.convertData(message.getData(), TextData.class);
processUserInput(data.getText());
}
```

### Scenario 4: Interruption Control

Implementing the interruption of current playback:

```java
// User interrupts the avatar's speech
public void interruptAvatar() {
Message interruptMsg = MessageBuilder.controlInterrupt(sessionId);
client.sendMessage(interruptMsg);
}

// Developer server receives the interruption signal
@Override
public void onControlInterrupt(Message message) {
// Stop current AI generation and cancel the response
String responseId = getCurrentResponseId();
Message cancelMsg = MessageBuilder.responseCancel(message.getSessionId(), responseId);
client.sendMessage(cancelMsg);
}
```

## Complete Examples

Check the example code located in the `src/test/java/com/newportai/liveavatar/channel/example/` directory:

| File | Mode | Description |
|------|------|-------------|
| `WebSocketExample.java` | Inbound | Minimal SDK usage — calls REST API, connects, sends input |
| `LiveAvatarServiceInboundSimulator.java` | Inbound | Interactive developer client — calls REST API to provision a session, then drives the conversation |
| `LiveAvatarServiceOutboundSimulator.java` | Outbound | Interactive live-avatar-service client — connects directly to a developer server, sends `session.init` / `session.state` / `system.idleTrigger` / audio |

**Complete Server Implementation (works for both modes):** See the [`liveavatar-channel-server-example/`](./liveavatar-channel-server-example/) project

## Error Handling

The SDK provides comprehensive exception handling:

```java
try {
client.connect();
client.sendMessage(message);
} catch (ConnectionException e) {
// Connection exception
System.err.println("Connection error: " + e.getMessage());
} catch (MessageSerializationException e) {
// Message serialization exception
System.err.println("Serialization error: " + e.getMessage());
}

// Handling errors within the listener
@Override
public void onError(Throwable error) {
// Handle runtime errors
error.printStackTrace();
}

@Override
public void onErrorMessage(Message message) {
// Handle protocol error messages
ErrorData error = JsonUtil.convertData(message.getData(), ErrorData.class);
System.err.println("Error: " + error.getCode() + " - " + error.getMessage());
}
```

## Custom Configuration

### Customizing OkHttpClient

```java
OkHttpClient customClient = new OkHttpClient.Builder()
.connectTimeout(20, TimeUnit.SECONDS)
.readTimeout(0, TimeUnit.SECONDS)  // Infinite read timeout (streaming connection)
.writeTimeout(20, TimeUnit.SECONDS)
.pingInterval(30, TimeUnit.SECONDS)  // Heartbeat interval
​​   .build();

AvatarWebSocketClient client = new AvatarWebSocketClient(url, listener, customClient);
```

## WebRTC Data Channel Support

The protocol remains completely identical on a WebRTC Data Channel as it is on a WebSocket. WebRTC does not require a heartbeat mechanism (connection status is managed by ICE); therefore, a Data Channel client can be implemented based on `Message` and `MessageBuilder`. ## Development and Building

### Multi-Module Build

This project utilizes a Maven multi-module structure, comprising two modules: the SDK and a server example.

```bash
# Clone the project
git clone <repository-url>
cd liveavatar-channel

# Build all modules (Recommended)
mvn clean install

# Compile only (without installing)
mvn clean compile

# Run tests
mvn test

# Package all modules
mvn clean package
```

### Build SDK Only

```bash
cd liveavatar-channel-sdk
mvn clean install
```

### Run Server Example

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

The server will start at `ws://localhost:8080/avatar/ws`.

### Run the Simulators (Testing Tools)

**Inbound mode** — developer client connects to the platform server:
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceInboundSimulator"
```

**Outbound mode** — live avatar service connects to a developer server:
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceOutboundSimulator"
```

For details, see: [QUICKSTART.md](./QUICKSTART.md)
For protocol design, see [PROTOCOL.md](./PROTOCOL.md)

## License

see: [LICENSE](./LICENSE)

## Contributing

Issues and Pull Requests are welcome!
