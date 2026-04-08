# Protocol Overview
This Protocol is designed to handle communication between developer and Live avatar service via WebSocket.
Developers are required to provide the WebSocket service address.

## Text Message Type Naming Conventions
We use the term "event" to designate message types. To prevent confusion as the number of message types grows, a specific set of conventions has been established. ### Three-Part Semantic Structure
<domain>.<action>[.<stage>]

#### 1️⃣ Layer 1: Domain (Category)
| Domain | Meaning |
| --- | --- |
| session | Session Lifecycle |
| input | User Input |
| response | Model Output |
| control | Control Signals |
| system | System Behavior |
| error | Error |
| tool (Future) | Tool Calls |


---

#### 2️⃣ Layer 2: Action
Describes "what is being done"

| Action | Example |
| --- | --- |
| init | session.init |
| ready | session.ready |
| text | input.text |
| asr | input.asr |
| chunk | response.chunk |
| done | response.done |
| interrupt | control.interrupt |
| prompt | system.prompt |
| idle trigger | system.idleTrigger |


---

#### 3️⃣ Layer 3: Stage (Optional)
Used for "Streaming/State"

| Stage | Example |
| --- | --- |
| partial | input.asr.partial |
| final | input.asr.final |
| chunk | response.chunk |
| done | response.done |
| cancel | response.cancel |


# Text Protocol Design: Scenario-Specific Protocols
## Scenario 1: WebSocket Full Flow (Standard Path)
### 1️⃣ Establishing Connection
#### Client (Live Avatar Service) → Server (Developer Service)
```plain
{
"event": "session.init",
"data": {
"sessionId": "sess_123",
"userId": "u_1"
}
}
```

---

#### (Developer Service) → Client (Live Avatar Service)
```plain
{
"event": "session.ready"
}
```

---

### 2️⃣ Heartbeat
Relies on standard WebSocket protocol control frames.

Adheres to the standard WebSocket protocol (RFC 6455):

+ **Ping (0x9)**: The server may send Ping frames to the client. + **Pong (0xA)**: Upon receiving a Ping frame, the client must automatically reply with a Pong frame.

---

### 3️⃣ User Text Input
The Live Avatar Service sends a text input message.

```plain
{
"event": "input.text",
"requestId": "req_1",
"data": {
"text": "What is your name?"
}
}
```

---

### 4️⃣ Developer Service Streaming Output (Outputs text or speech as needed)
#### start (Optional)
Sent by the Developer Service **before** the first `response.chunk`. 
Use this to configure the TTS engine managed by the Live Avatar Service (speed, volume, mood), or using the default settings by not sending the `response.start` message.
If TTS is provided by the developer, the message is also unnecessary.

```plain
{
"event": "response.start",
"requestId": "req_1",
"responseId": "res_1",
"data": {
  "audioConfig": {
    "speed": 1.0,
    "volume": 1.0,
    "mood": "neutral"
  }
}
}
```

**`speed` reference**

| Value | Meaning |
| --- | --- |
| 0.5 | Very slow (suitable for teaching / elderly users) |
| 0.8 | Slightly slow |
| 1.0 | Normal (default) |
| 1.2 | Slightly fast |
| 1.5 | Very fast |
| 2.0 | Maximum speed (clarity not guaranteed) |

**`volume` reference**

| Value | Meaning |
| --- | --- |
| 0.0 | Muted |
| 0.5 | Quiet |
| 1.0 | Standard (default) |
| 1.2 | Loud |
| 1.5 | Maximum (may clip) |

**`mood` values** (extensible): `neutral` · `happy` · `sad` · `angry` · `excited` · `calm` · `serious`

---

#### chunk (Text)
```plain
{
"event": "response.chunk",
"requestId": "req_1",
"responseId": "res_1",
"seq": 12,
"timestamp": 1710000000000,
"data": {
"text": "Hello"
}
}
```

---

#### done (Text)
```plain
{
"event": "response.done",
"requestId": "req_1",
"responseId": "res_1"
}
```

---

requestId → responseId = 1:N

`seq` increments sequentially within a single response.

A single response may consist of replies from multiple agents.



### 5️⃣ State Synchronization (Sent by the Live Avatar Service)
```plain
{
"event": "session.state",
"seq": 12,
"timestamp": 1710000000000,
"data": {
"state": "SPEAKING"
}
}
```

`seq` increments sequentially within a single session. All `state` Values ​​(Subject to Future Expansion)

| **State** | **Speaker** | **System Behavior** |
| --- | --- | --- |
| **IDLE** | None | Awaiting Input |
| **LISTENING** | User | ASR Input Capture |
| **THINKING** | System (Mind) | LLM/TTS Preparation |
| **STAGING** | System (Body) | Preparing Live Avatar Generation |
| **SPEAKING** | System (Body) | Live Avatar: Normal Response Output |
| **PROMPT_THINKING** | System (Brain) | Preparing Reminder Script |
| **PROMPT_STAGING** | System (Body) | Preparing to Generate Live Avatar |
| **PROMPT_SPEAKING** | System (Body) | Live Avatar: Broadcasting Reminder Audio |


---

### 6️⃣ Interrupt (Sent by Developer Service)
```plain
{
"event": "control.interrupt",
"requestId": "req_1"// optional
}
```

A signal initiated by the Developer Service.

The Live Avatar Service is solely responsible for executing the interrupt action. The logic applies identically to both text input and audio input; the distinction lies in the Developer Service's strategy for determining when to interrupt: Text Input → Trigger immediately. Audio Input → Relies on VAD (Voice Activity Detection) or specific policy determinations.

When triggering an interrupt, providing the `requestId` helps ensure that a specific, designated conversation is interrupted precisely, thereby preventing erroneous interruptions caused by network instability. This field is optional.

---

### 7️⃣ Connection Imminently Closing (Sent by the Live Avatar Service)
```plain
{
"event": "session.closing",
"data": {
"reason": "timeout"
}
}
```

This message is typically sent proactively by the system just before a timeout is declared.

## Scenario 2: ASR + Real-time Voice (Sent by the Developer Service)
---

### ASR Recognition (The one who provides the ASR service is responsible for sending the messages)
#### User Speech-to-Text Recognition (Streaming/Partial)
```plain
{
"event": "input.asr.partial",
"requestId": "req_2",
"seq": 3,
"data": {
"text": "You are called",
"final": false
}
}
```

---

#### User Speech-to-Text Recognition (Final Result)
```plain
{
"event": "input.asr.final",
"requestId": "req_2",
"data": {
"text": "What is your name?"
}
}
```

---

### Voice Input Start/End Detection (The one who provides the ASR service is responsible for sending the messages)
#### Voice Input Start Detected
```plain
{
"event": "input.voice.start",
"requestId": "req_1"
}
```

#### Voice Input End Detected
```plain
{
"event": "input.voice.finish",
"requestId": "req_1"
}
```
It is acceptable to send only the `input.asr.final` event; `input.asr.partial` is classified as an optional message.

👉 The subsequent workflow is identical to that of text input.


### Speech Input Start/End Detection (The one who provides the TTS service is responsible for sending the messages)
#### **Speech Output Started**
```plain
{
"event": "response.audio.start",
"requestId": "req_1",
"responseId": "res_1"
}
```

#### **Speech Output Finished**
```plain
{
"event": "response.audio.finish",
"requestId": "req_1",
"responseId": "res_1"
}
```

**Scenario: TTS Provided by the Developer Service**:

After sending the "Speech Output Started" message, the developer service pushes the corresponding audio data. Once the audio data transmission is complete, the "Speech Output Finished" message is sent.

**Scenario: TTS Provided by the Live Avatar Service**:

After sending the "Speech Output Started" message, the Live Avatar Service pushes the corresponding audio data. Once the audio data transmission is complete, the "Speech Output Finished" message is sent.

---

## Scenario 3: Server-Initiated Interaction (Idle Wake-up)
### **1️⃣** **Idle Event (Sent by the Live Avatar Service)**
```plain
{
"event": "system.idleTrigger",
"data": {
"reason": "user_idle",
"idleTimeMs": 120000
}
}
```

The system detected that the live avatar has been idle for a significant period.

### 2️⃣ **Idle Prompt Text Message** (Sent by the Developer Service)
```plain
{
"event": "system.prompt",
"data": {
"text": "Are you still there?"
}
}
```

---

Upon receiving this message, the Live Avatar Service will utilize the configured TTS engine to drive the Live Avatar to speak the specified content.

The prompt text does not count toward the accumulated user idle time. ### 3️⃣ **Idle Reminder Start Message** (Sent by Developer Service)
```plain
{
"event": "response.audio.promptStart"
}
```

### 4️⃣ **Idle Reminder End Message** (Sent by Developer Service)
```plain
{
"event": "response.audio.promptFinish"
}
```

After sending the Idle Reminder Start message, the Developer Service pushes the corresponding reminder audio; the Idle Reminder End message is sent only after the prompt audio transmission is complete.

Prompt audio is excluded from the user's cumulative idle time calculation.

## Scenario 4: LiveKit DataChannel (Low-Latency Path)
👉 Core Principle:

**Aside from the fact that ping/pong requests are no longer required, the protocol format remains entirely identical; the only difference is that traffic is routed via RTC.**

---

## Scenario 5: Error Handling (Optional)
---

### Error (Sent by Developer Service)
```plain
{
"event": "error",
"requestId": "req_1",
"data": {
"code": "ASR_FAIL",
"message": "audio decode error"
}
}
```

---

### Stream Cancellation (Sent by Developer Service)
```plain
{
"event": "response.cancel",
"responseId": "response_1"
}
```



# Audio Protocol Design (WebSocket Channel Only)
Audio consists of binary data; each audio packet is encapsulated within the following data structure:

## 📦 Data Structure
```plain
| Header (9 bytes) | Audio Payload |
```

---

## 🧠 Header Bit Definitions
Total: 8 * 9 = 72 bits

Listed in sequential order, indicating the number of bits occupied by each field. | Field | Bits | Bit Offset (High → Low) | Range/Values ​​| Description |
| --- | --- | --- | --- | --- |
| **T (Type)** | 2 | 70–71 | `01` | Fixed as Audio Frame |
| **C (Channel)** | 1 | 69 | 0 / 1 | 0=Mono, 1=Stereo |
| **K (Key)** | 1 | 68 | 0 / 1 | Key Frame (First Frame / Opus Resync) |
| **S (Seq)** | 12 | 56–67 | 0–4095 | Sequence Number (Wrapping) |
| **TS (Timestamp)** | 20 | 36–55 | 0–1,048,575 | Timestamp (ms, Wrapping) |
| **SR (SampleRate)** | 2 | 34–35 | 00/01/10 | 00=16kHz, 01=24kHz, 10=48kHz |
| **F (Samples)** | 12 | 22–33 | 0–4095 | Samples per frame (e.g., 24k/40ms = 960) |
| **Codec** | 2 | 20–21 | 00/01 | 00=PCM, 01=Opus |
| **R (Reserved)** | 4 | 16–19 | 0000 | Reserved Bits |
| **L (Length)** | 16 | 0–15 | 0–65535 | Payload Length (Bytes) |


Both the Sequence Number (Seq) and Timestamp (TS) are incremental; however, due to their limited bit-widths, they must support wrapping.

### Wrapping Rules
Both TS and Seq function as wrapping counters. The receiving end *must* use modular arithmetic for comparisons; direct comparison based on magnitude is prohibited. ### The Jitter Buffer must be based on the TS (Timestamp), not the Seq (Sequence Number).
Sorting Priority:
1. TS (Primary Sorting Key)
2. Seq (Secondary Key for Duplicate Removal)

### Packet Loss / Out-of-Order Window
Maximum Out-of-Order Window ≈ 200–500 ms

## 🧠 Audio Payload
This contains the actual raw audio binary data, specifically formatted as PCM or Opus binary data.

Whether the audio data is sent from the Live Avatar Service to a developer, or from a developer to the Live Avatar Service, it must strictly adhere to this format.

# Image Protocol Design (WebSocket Channel Only)
Image data is transmitted as binary data; each image packet is encapsulated within the following data structure (this applies exclusively to scenarios involving multimodal image stream input).

## 📦 Data Structure
```plain
| Header (12 bytes) | Audio Payload |
```

## 🧠 Header Bit Definitions
Total: 8 × 12 = 96 bits

The following lists the bit allocation for each field, presented in sequential order. | Field | Bits | Bit Offset (High → Low) | Range/Values ​​| Description |
| --- | --- | --- | --- | --- |
| **T (Type)** | 2 | 94–95 | `10` | Fixed identifier for image frames |
| **V (Version)** | 2 | 92–93 | `00` | Protocol version (reserved for extensions) |
| **F (Format)** | 4 | 88–91 | 0–4 | 0=JPG, 1=PNG, 2=WebP, 3=GIF, 4=AVIF |
| **Q (Quality)** | 8 | 80–87 | 0–255 | Image quality (encoding quality/compression level) |
| **ID (ImageId)** | 16 | 64–79 | 0–65535 | Unique image identifier (used for fragmentation/reassembly) |
| **W (Width)** | 16 | 48–63 | 0–65535 | Image width (pixels) |
| **H (Height)** | 16 | 32–47 | 0–65535 | Image height (pixels) |
| **L (Length)** | 32 | 0–31 | 0–4,294,967,295 | Payload length (bytes) |