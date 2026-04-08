# Live Avatar Channel SDK - Quick Start

## Technology Stack Overview

### ✅ Supported by This SDK

**WebSocket Mode:**
- ✅ Text Protocol (JSON-formatted messages)
- ✅ Audio/Image Protocol (Binary data)
- ✅ Developers must provide their own WebSocket server
- ✅ Comprehensive connection management and heartbeat mechanisms

## Project Structure

This project adopts a Maven multi-module structure:

```
liveavatar-channel/                             # Project Root Directory
├── Parent POM                                    # Parent POM
├── README.md                                   # Usage Documentation
├── PROTOCOL.md                                 # Protocol Documentation
├── QUICKSTART.md                               # Quick Start Guide
├── liveavatar-channel-sdk/                     # SDK Module
│   ├── pom.xml                                 # SDK Module POM
│   └── src/
│       ├── main/java/com/newportai/liveavatar/channel/
│       │   ├── client/                         # Client Implementation
│       │   │   ├── AvatarWebSocketClient.java  # WebSocket Client
│       │   │   └── StreamingResponseHandler.java
│       │   ├── exception/                      # Exception Classes
│       │   ├── listener/                       # Event Listeners
│       │   ├── model/                          # Data Models
│       │   │   ├── Message.java                # Base Message Model
│       │   │   ├── AudioFrame.java             # Audio Frame Model
│       │   │   ├── EventType.java              # Event Type Constants
│       │   │   ├── IdleTriggerData.java        # Idle Trigger Data
│       │   │   └── ...
│       │   ├── reconnect/                      # Automatic Reconnection
│       │   └── util/                           # Utility Classes
│       └── test/java/com/newportai/liveavatar/channel/
│           └── example/                        # Example Code
│               ├── LiveAvatarServiceSimulator.java    # Live Avatar Service Simulator
│               ├── WebSocketExample.java              # WebSocket Example
│               └── AudioProtocolExample.java          # Audio Protocol Example
└── liveavatar-channel-server-example/              # Server Example Module
├── pom.xml                                 # Server Module POM
├── README.md                               # Server Implementation Guide
└── src/main/java/com/newportai/liveavatar/channel/server/
├── AvatarServerApplication.java        # Spring Boot Entry Point
├── config/
│   └── WebSocketConfig.java            # WebSocket Configuration
├── handler/
│   └── AvatarChannelWebSocketHandler.java  # Message Handler
├── service/
│   ├── MessageProcessingService.java   # Business Logic
│   └── AsrService.java                 # ASR Service
└── session/
├── SessionManager.java             # Session Management
└── AvatarSession.java              # Session Model
```

## Building and Installation

### Method 1: Build the Entire Project (Recommended)

Execute the following commands in the project root directory:

```bash
cd liveavatar-channel

# Compile all modules
mvn clean compile

# Run all tests
mvn test

# Package all modules
mvn clean package

# Install all modules to the local Maven repository
mvn clean install
```

**Advantages**:
- ✅ Automatically handles inter-module dependencies
- ✅ No need to manually install the SDK
- ✅ Builds all modules in a single pass

### Method 2: Build the SDK Separately

```bash
cd liveavatar-channel-sdk

# Compile the SDK
mvn clean compile

# Install the SDK to the local repository
mvn clean install
```

The generated JAR file is located at `target/liveavatar-channel-sdk-1.0.0.jar`.

### Method 3: Build the Server Example Separately

You must build the SDK first (using Method 1 or Method 2):

```bash
cd liveavatar-channel-server-example

# Run the server
mvn spring-boot:run

# Or package it as an executable JAR
mvn clean package
java -jar target/liveavatar-channel-server-example-1.0.0.jar
```

## Usage

### Method 1: As a Live Avatar Service Client

Connect to the developer's server, send user input, and receive AI responses. ```java
String wsUrl = "ws://your-server.com/ws";
String sessionId = "sess_" + System.currentTimeMillis();
String userId = "user_123";

// Create a streaming response handler
StreamingResponseHandler streamHandler = new StreamingResponseHandler();

// Create the client
final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];
AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
new AvatarChannelListenerAdapter() {

@Override
public void onConnected() {
try {
// Send session.init after a successful connection
Message msg = MessageBuilder.sessionInit(sessionId, userId);
clientHolder[0].sendMessage(msg);
} catch (Exception e) {
e.printStackTrace();
}
}

@Override
public void onSessionReady(Message message) {
try {
// Session is ready; send text input
Message textMsg = MessageBuilder.inputText("req_1", "Hello");
clientHolder[0].sendMessage(textMsg);
} catch (Exception e) {
e.printStackTrace();
}
}

@Override
public void onResponseChunk(Message message) {
// Process streaming responses (automatically sorted by sequence number)
streamHandler.handleChunk(message, chunk -> {
System.out.println("Chunk " + chunk.getSeq() + ": " + chunk.getText());
});
}

@Override
public void onResponseDone(Message message) {
streamHandler.handleDone(message, responseId -> {
System.out.println("Response completed: " + responseId);
});
}
});

clientHolder[0] = client;

// Connect to the server
client.connect();

// ... Use the client ...

// Disconnect
client.disconnect();
```

// Method 2: Acting as a Developer Server

Receive user input sent by the live avatar service and return an AI response.

```java
String wsUrl = "ws://localhost:8080/ws";

final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1]
;
AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, 
new AvatarChannelListenerAdapter() { 

private String currentSessionId; 

@Override 
public void onSessionInit(Message message) { 
// initiates connection 
SessionInitData data = JsonUtil.convertData( 
message.getData(), SessionInitData.class 
); 
currentSessionId = data.getSessionId(); 

try { 
//return session.ready 
Message readyMsg = MessageBuilder.sessionReady(currentSessionId); 
clientHolder[0].sendMessage(readyMsg); 
} catch (Exception e) { 
e.printStackTrace(); 
} 
} 

@Override 
public void onInputText(Message message) { 
//Receive user input 
TextData data = JsonUtil.convertData(message.getData(), TextData.class); 
String userInput = data.getText(); 

try { 
// Call AI to generate response 
String aiResponse = callYourAI(userInput); 

// Stream the response 
String responseId = "res_" + System.currentTimeMillis(); 
for (int i = 0; i < aiResponse.length(); i++) { 
Message chunk = MessageBuilder.responseChunk( 
currentSessionId, 
message.getRequestId(), 
responseId, 
i, 
String.valueOf(aiResponse.charAt(i)) 
); 
clientHolder[0].sendMessage(chunk); 
Thread.sleep(50); // Simulate streaming delay 
} 

//Sending completed 
Message done = MessageBuilder.responseDone( 
currentSessionId, 
message.getRequestId(), 
responseId 
); 
clientHolder[0].sendMessage(done); 

} catch (Exception e) { 
e.printStackTrace(); 
} 
}
});

clientHolder[0] = client;
client.connect();
```

## Core API

### MessageBuilder - Message builder

```java
// Session message
MessageBuilder.sessionInit(sessionId, userId)
MessageBuilder.sessionReady(sessionId)
MessageBuilder.sessionState(sessionId, state, seq)
MessageBuilder.sessionClose(sessionId, reason)

//Input message
MessageBuilder.inputText(requestId, text)
MessageBuilder.asrPartial(sessionId, requestId, seq, text)
MessageBuilder.asrFinal(sessionId, requestId, text)

// Response Messages
MessageBuilder.responseStart(requestId, responseId, audioConfig)  // optional, before first chunk
MessageBuilder.responseChunk(sessionId, requestId, responseId, seq, text)
MessageBuilder.responseDone(sessionId, requestId, responseId)
MessageBuilder.responseCancel(sessionId, responseId)

// Control Messages
MessageBuilder.controlInterrupt(sessionId)

// System Messages
MessageBuilder.systemPrompt(sessionId, text)

// Error Messages
MessageBuilder.error(sessionId, requestId, code, message)

// Heartbeat Messages (WebSocket Only)
MessageBuilder.ping()
MessageBuilder.pong()
```

### SessionState - Session State Enum

Defines 8 types of live avatar session states:

```java
import com.newportai.liveavatar.channel.model.SessionState;

// Create a state message
Message msg = MessageBuilder.sessionState(
sessionId,
SessionState.SPEAKING.getValue(),
seq
);

// Parse the state and handle it
@Override
public void onSessionState(Message message) {
SessionStateData data = JsonUtil.convertData(
message.getData(),
SessionStateData.class
);

SessionState state = SessionState.fromValue(data.getState());

switch (state) {
case IDLE:            // Idle, waiting for input
case LISTENING:       // User speaking, ASR capturing audio
case THINKING:        // System thinking, LLM/TTS preparing
case STAGING:         // Preparing to generate live avatar output
case SPEAKING:        // Live avatar providing a normal response
case PROMPT_THINKING: // Preparing prompt text
case PROMPT_STAGING:  // Preparing live avatar for prompt delivery
case PROMPT_SPEAKING: // Live avatar delivering the prompt
}
}
```

**State Description Table**:

| State           | Who is Speaking | System Behavior                                  |
|-----------------|---------|--------------------------------------------------|
| IDLE            | None | Waiting for input                                |
| LISTENING       | User | ASR capturing audio                              |
| THINKING        | System (Processing) | LLM/TTS preparing                                |
| STAGING         |  SYSTEM (Body)      | Preparing to generate live avatar |
| SPEAKING        | SYSTEM (Body) | Live avatar service outputting standard response |
| PROMPT_THINKING | SYSTEM (Brain) | Preparing reminder script                        |
| PROMPT_STAGING  | SYSTEM (Body) | Preparing to generate live avatar                |
| PROMPT_SPEAKING | SYSTEM (Body) | Live avatar service broadcasting reminder audio  |

### StreamingResponseHandler - Streaming Response Handling

Supports automatic reordering of out-of-sequence messages:

```java
StreamingResponseHandler handler = new StreamingResponseHandler();

// Handle response.chunk
handler.handleChunk(message, chunk -> {
System.out.println(chunk.getSeq() + ": " + chunk.getText());
});

// Handle response.done
handler.handleDone(message, responseId -> {
System.out.println("Response " + responseId + " completed");
});

// Handle response.cancel
handler.handleCancel(message, responseId -> {
System.out.println("Response " + responseId + " cancelled");
});
```

## Running the Example

### 1. Start the Developer Server (Spring Boot)

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

The server will start at `ws://localhost:8080/avatar/ws`.

**Server Features:**
- ✅ Receives WebSocket connections from the live avatar service
- ✅ Handles `session.init` and responds with `session.ready`
- ✅ Handles `input.text` and returns `response.chunk`/`done`
- ✅ Processes audio frames and performs ASR (Automatic Speech Recognition)
- ✅ Responds to `system.idleTrigger` by sending `system.prompt`
- ✅ Detects interruptions and sends `control.interrupt`
- ✅ Automatically handles native WebSocket ping/pong (Spring handles the response)

For details, see: [liveavatar-channel-server-example/README.md](./liveavatar-channel-server-example/README.md)

### 2. Run the Live Avatar Service Simulator (Testing Tool)

Run in a new terminal:

```bash
cd liveavatar-channel-sdk
java -cp "target/test-classes:target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
com.newportai.liveavatar.channel.example.LiveAvatarServiceSimulator
```

**Simulator Features:**
- ✅ Connects to the developer server as a WebSocket client
- ✅ Sends `session.init`
- ✅ Sends `input.text` (user input)
- ✅ Sends audio frames (simulating speech)
- ✅ Sends `system.idleTrigger` (simulating idle detection)
- ✅ Receives `response.chunk`/`done`
- ✅ Receives `system.prompt`
- ✅ Automatically sends native WebSocket pings (every 5 seconds via OkHttp)

**Interaction Commands:**
- Enter any text → Sends `input.text`
- Enter `audio` → Sends test audio frames
- Enter `idle` → Sends `system.idleTrigger`
- Enter `state` → Sends `session.state`
- Enter `quit` → Exits

### 3. Running Other Examples

#### WebSocket Client Example
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.WebSocketExample"
```

#### Audio Protocol Example
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.AudioProtocolExample"
```

#### Session State Example
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.SessionStateExample"
```

#### Message Format Demo
```bash
cd liveavatar-channel-sdk
java -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
com.newportai.liveavatar.channel.example.MessageFormatDemo
```

## Common Use Cases

### 1. Interruption Control

```java
// User interrupts the live avatar while it is speaking
Message interruptMsg = MessageBuilder.controlInterrupt(sessionId);
client.sendMessage(interruptMsg);
```

### 2. System-Initiated Prompts (Silence Wake-up)

```java
// Developer server actively sends a prompt
Message promptMsg = MessageBuilder.systemPrompt(sessionId, "Are you still there?");
client.sendMessage(promptMsg);
```

### 3. Real-time ASR (Speech-to-Text)

```java
@Override
public void onAsrPartial(Message message) {
// Partial recognition result, used for real-time subtitles
TextData data = JsonUtil.convertData(message.getData(), TextData.class);
updateSubtitle(data.getText());
}

@Override
public void onAsrFinal(Message message) {
// Final recognition result, used for AI processing
TextData data = JsonUtil.convertData(message.getData(), TextData.class);
processUserInput(data.getText());
}
```

### 4. Error Handling

```java
@Override
public void onErrorMessage(Message message) {
ErrorData error = JsonUtil.convertData(message.getData(), ErrorData.class);
System.err.println("Error: " + error.getCode() + " - " + error.getMessage());

// Handle based on the error code
if ("ASR_FAIL".equals(error.getCode())) {
// Handle ASR failure
}
}
```

## Protocol Features

- ✅ Comprehensive support for message types
- ✅ Streaming data transmission
- ✅ Sequence numbering for out-of-order resilience (automatically handled by `StreamingResponseHandler`)
- ✅ Session lifecycle management
- ✅ Heartbeat mechanism (automatically handled by WebSocket)
- ✅ Supports both WebSocket and WebRTC Data Channels (protocol consistency maintained)

## Tech Stack

- Java 8+
- Jackson 2.15.2 (JSON Serialization)
- OkHttp 4.11.0 (WebSocket Client)
- SLF4J 2.0.7 (Logging)
- JUnit 4.13.2 (Unit Testing)

## Next Steps

1. Check `README.md` for the complete API documentation
2. Check `PROTOCOL.md` for details on the protocol design
3. Check... Complete example code located in the `src/test/java/com/newportai/liveavatar/channel/example/` directory.
4. Implement a custom `AvatarChannelListener` based on your specific requirements.

## Feedback

If you encounter any issues, please submit an Issue or contact the development team.