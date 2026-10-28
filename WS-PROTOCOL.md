# Live Avatar Channel — WebSocket 接口协议文档

> 版本: 1.0
> 最后更新: 2026-06-01

---

## 目录

1. [协议概述](#1-协议概述)
2. [消息基类 Message](#2-消息基类-message)
3. [事件/指令总览](#3-事件指令总览)
4. [事件详情](#4-事件详情)
   - [4.1 session.init](#41-sessioninit)
   - [4.2 session.ready](#42-sessionready)
   - [4.3 session.state](#43-sessionstate)
   - [4.4 session.closing](#44-sessionclosing)
   - [4.5 input.text](#45-inputtext)
   - [4.6 input.voice.start / input.voice.finish](#46-inputvoicestart--inputvoicefinish)
   - [4.7 input.asr.partial](#47-inputasrpartial)
   - [4.8 input.asr.final](#48-inputasrfinal)
   - [4.9 response.start](#49-responsestart)
   - [4.10 response.chunk](#410-responsechunk)
   - [4.11 response.done](#411-responsedone)
   - [4.12 response.cancel](#412-responsecancel)
   - [4.13 response.audio.start](#413-responseaudiostart)
   - [4.14 response.audio.finish](#414-responseaudiofinish)
   - [4.15 response.audio.promptStart / response.audio.promptFinish](#415-responseaudiopromptstart--responseaudiopromptfinish)
   - [4.16 control.interrupt](#416-controlinterrupt)
   - [4.17 system.prompt](#417-systemprompt)
   - [4.18 system.idleTrigger](#418-systemidletrigger)
   - [4.19 scene.ready](#419-sceneready)
   - [4.20 error](#420-error)
5. [二进制帧协议](#5-二进制帧协议)
   - [5.1 音频帧 AudioFrame](#51-音频帧-audioframe)
   - [5.2 图片帧 ImageFrame](#52-图片帧-imageframe)
6. [完整交互流程](#6-完整交互流程)
7. [新增指令设计: scene.switchVideo](#7-新增指令设计-sceneswitchvideo)
   - [7.1 背景与目的](#71-背景与目的)
   - [7.2 指令定义](#72-指令定义)
   - [7.3 数据模型](#73-数据模型)
   - [7.4 消息示例](#74-消息示例)
   - [7.5 服务端处理逻辑](#75-服务端处理逻辑)
   - [7.6 SDK 实现改动](#76-sdk-实现改动)
   - [7.7 交互流程](#77-交互流程)

---

## 1. 协议概述

- **传输层**: WebSocket (wss://)
- **消息格式**: JSON 文本消息 + 二进制帧双通道
- **心跳**: 使用 WebSocket 原生 ping/pong 控制帧，不额外使用应用层心跳
- **命名规范**: 事件名采用 `<domain>.<action>[.<stage>]` 格式，如 `session.init`、`response.chunk`

**通信模式**:
| 通道 | 传输内容 | 说明 |
|------|---------|------|
| 文本 (JSON) | 指令/事件消息 | 通过 `onMessage(String text)` 收发 |
| 二进制 | 音频帧 / 图片帧 | 通过 `onMessage(ByteString bytes)` 收发，前 2 bit 标识类型 (`01`=音频, `10`=图片) |

---

## 2. 消息基类 Message

所有 JSON 消息使用统一的信封格式。

**定义文件**: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/model/Message.java`

```json
{
  "event": "事件名",
  "sessionId": "会话ID",
  "requestId": "请求ID",
  "responseId": "响应ID",
  "seq": <序号>,
  "timestamp": <时间戳毫秒>,
  "data": { <具体数据载荷> }
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `event` | String | 是 | 事件类型，见 EventType 定义 |
| `sessionId` | String | 否 | 会话标识 |
| `requestId` | String | 否 | 请求标识，用于关联请求-响应（如 input.text 的 requestId 传递到 response 系列） |
| `responseId` | String | 否 | 响应标识 |
| `seq` | Integer | 否 | 序号（用于分片消息排序） |
| `timestamp` | Long | 否 | 毫秒时间戳 |
| `data` | Object | 否 | 具体业务数据，不同事件有不同结构 |

**序列化**: 使用 Jackson `@JsonInclude(JsonInclude.Include.NON_NULL)`，空字段不序列化。

---

## 3. 事件/指令总览

全部定义在 `EventType.java` 中，共 **20 个事件**，分为 6 大类：

### 3.1 会话事件

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `session.init` | 平台 -> Agent | 平台发起会话初始化，Agent 必须回复 `session.ready` |
| `session.ready` | Agent -> 平台 | Agent 回复就绪 |
| `session.state` | 平台 -> Agent | 通知会话状态变更 |
| `session.closing` | 平台 -> Agent | 会话即将关闭，携带关闭原因 |

### 3.2 输入事件

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `input.text` | 平台 -> Agent | 用户输入的文本（平台 ASR 模式） |
| `input.voice.start` | Agent -> 平台 | 开发者 ASR 模式：检测到用户开始说话 |
| `input.voice.finish` | Agent -> 平台 | 开发者 ASR 模式：检测到用户说话结束 |
| `input.asr.partial` | Agent -> 平台 | 开发者 ASR 模式：中间 ASR 结果 |
| `input.asr.final` | Agent -> 平台 | 开发者 ASR 模式：最终 ASR 结果 |

### 3.3 响应事件 — 平台 TTS 模式

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `response.start` | Agent -> 平台 | 可选。发送响应的 TTS 配置（语速、音量、情绪） |
| `response.chunk` | Agent -> 平台 | 发送文本响应片段 |
| `response.done` | Agent -> 平台 | 响应结束 |
| `response.cancel` | Agent -> 平台 | 取消当前响应 |

### 3.4 响应事件 — 开发者 TTS 模式

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `response.audio.start` | Agent -> 平台 | 开始发送音频帧 |
| `response.audio.finish` | Agent -> 平台 | 音频发送结束 |
| `response.audio.promptStart` | Agent -> 平台 | 开始发送主动提示音 |
| `response.audio.promptFinish` | Agent -> 平台 | 主动提示音结束 |

### 3.5 控制事件

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `control.interrupt` | Agent -> 平台 | 中断当前响应 |

### 3.6 系统事件

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `system.prompt` | Agent -> 平台 | Agent 主动发送提示给用户 |
| `system.idleTrigger` | 平台 -> Agent | 空闲超时通知 |

### 3.7 场景事件

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `scene.ready` | JS SDK -> 平台 | （经 LiveKit DataChannel）前端场景加载完成 |

### 3.8 错误事件

| 事件名 | 方向 | 说明 |
|--------|------|------|
| `error` | 双向 | 错误通知 |

---

## 4. 事件详情

### 4.1 session.init

- **方向**: 平台 -> Agent
- **常量**: `SESSION_INIT`
- **说明**: 平台发起会话初始化。SDK 收到此事件后自动回复 `session.ready`，同时回调 `listener.onSessionInit()`。

**完整消息**:
```json
{
  "event": "session.init",
  "data": {
    "sessionId": "sess_xxx",
    "userId": "user_1"
  }
}
```

**Data 载荷** (`SessionInitData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 会话唯一标识 |
| `userId` | String | 用户唯一标识 |

### 4.2 session.ready

- **方向**: Agent -> 平台
- **常量**: `SESSION_READY`
- **说明**: Agent 回复就绪。由 SDK 在收到 `session.init` 后自动发送，开发者无需手动处理。

```json
{
  "event": "session.ready"
}
```

Data 载荷为 null。

### 4.3 session.state

- **方向**: 平台 -> Agent
- **常量**: `SESSION_STATE`
- **说明**: 通知会话状态变更。回调 `listener.onSessionState(SessionState)`。

```json
{
  "event": "session.state",
  "data": {
    "state": "SPEAKING"
  }
}
```

**Data 载荷** (`SessionStateData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `state` | String | 会话状态 |

**state 可选值**:

| 值 | 枚举 | 说明 |
|----|------|------|
| `IDLE` | `SessionState.IDLE` | 空闲等待输入 |
| `LISTENING` | `SessionState.LISTENING` | 用户正在说话，ASR 采集音频 |
| `THINKING` | `SessionState.THINKING` | 系统 LLM/TTS 准备中 |
| `STAGING` | `SessionState.STAGING` | 系统准备生成数字人 |
| `SPEAKING` | `SessionState.SPEAKING` | 数字人正常播报中 |
| `PROMPT_THINKING` | `SessionState.PROMPT_THINKING` | 主动提示场景下思考中 |
| `PROMPT_STAGING` | `SessionState.PROMPT_STAGING` | 主动提示场景下准备中 |
| `PROMPT_SPEAKING` | `SessionState.PROMPT_SPEAKING` | 主动提示场景下播报中 |

### 4.4 session.closing

- **方向**: 平台 -> Agent
- **常量**: `SESSION_CLOSING`
- **说明**: 会话即将关闭。回调 `listener.onSessionClosing(reason)`。

```json
{
  "event": "session.closing",
  "data": {
    "reason": "timeout"
  }
}
```

**Data 载荷** (`CloseReasonData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `reason` | String | 关闭原因（如 `timeout`, `user_stop`, `error`） |

### 4.5 input.text

- **方向**: 平台 -> Agent
- **常量**: `INPUT_TEXT`
- **说明**: 用户输入文本（平台 ASR 模式或纯文本输入）。回调 `listener.onTextInput(text, requestId)`。

```json
{
  "event": "input.text",
  "requestId": "req_1",
  "data": {
    "text": "what's your name?",
    "final": true
  }
}
```

**Data 载荷** (`TextData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 文本内容 |
| `final` | Boolean | 是否最终结果（中间结果可能为 false） |

> **注意**: `requestId` 需传递回后续的 response 系列消息，用于关联请求-响应。

### 4.6 input.voice.start / input.voice.finish

- **方向**: Agent -> 平台
- **常量**: `INPUT_VOICE_START` / `INPUT_VOICE_FINISH`
- **说明**: 开发者 ASR 模式，检测到用户说话开始/结束。

```json
{
  "event": "input.voice.start",
  "requestId": "req_2"
}
```

```json
{
  "event": "input.voice.finish",
  "requestId": "req_2"
}
```

Data 载荷为 null。

### 4.7 input.asr.partial

- **方向**: Agent -> 平台
- **常量**: `INPUT_ASR_PARTIAL`
- **说明**: 开发者 ASR 模式，发送中间 ASR 结果。

```json
{
  "event": "input.asr.partial",
  "requestId": "req_2",
  "seq": 3,
  "data": {
    "text": "what's",
    "final": false
  }
}
```

**Data 载荷** (`TextData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 中间识别文本 |
| `final` | Boolean | 固定为 `false` |

### 4.8 input.asr.final

- **方向**: Agent -> 平台
- **常量**: `INPUT_ASR_FINAL`
- **说明**: 开发者 ASR 模式，发送最终 ASR 结果。

```json
{
  "event": "input.asr.final",
  "requestId": "req_2",
  "data": {
    "text": "what's your name?"
  }
}
```

**Data 载荷** (`TextData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 最终识别文本 |

### 4.9 response.start

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_START`
- **说明**: 可选。在 `response.chunk` 之前发送，配置 TTS 行为（仅平台 TTS 模式）。

```json
{
  "event": "response.start",
  "requestId": "req_1",
  "responseId": "res_xxx",
  "data": {
    "audioConfig": {
      "speed": 1.0,
      "volume": 1.0,
      "mood": "neutral"
    }
  }
}
```

**Data 载荷** (`ResponseStartData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `audioConfig` | AudioConfigData | TTS 音频配置 |

**AudioConfigData**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `speed` | Double | 语速: 1.0 正常, 0.5 很慢, 2.0 很快 |
| `volume` | Double | 音量: 0.0 静音, 1.0 标准, 1.5 最大 |
| `mood` | String | 情感语气: `neutral`, `happy`, `sad`, `angry`, `excited`, `calm`, `serious`（可扩展） |

### 4.10 response.chunk

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_CHUNK`
- **说明**: 发送文本响应片段（平台 TTS 模式），按 `seq` 序号排序。

```json
{
  "event": "response.chunk",
  "requestId": "req_1",
  "responseId": "res_1",
  "seq": 12,
  "timestamp": 1234567890,
  "data": {
    "text": "你好，请问有什么可以帮助你的？"
  }
}
```

**Data 载荷** (`TextData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 文本片段内容 |

### 4.11 response.done

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_DONE`
- **说明**: 响应结束。

```json
{
  "event": "response.done",
  "requestId": "req_1",
  "responseId": "res_1"
}
```

Data 载荷为 null。

### 4.12 response.cancel

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_CANCEL`
- **说明**: 取消正在进行的响应。

```json
{
  "event": "response.cancel",
  "responseId": "res_1"
}
```

Data 载荷为 null。

### 4.13 response.audio.start

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_AUDIO_START`
- **说明**: 开发者 TTS 模式，开始发送音频帧。发送此事件后，通过二进制通道发送 `AudioFrame`。

```json
{
  "event": "response.audio.start",
  "requestId": "req_1",
  "responseId": "res_1"
}
```

Data 载荷为 null。

### 4.14 response.audio.finish

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_AUDIO_FINISH`
- **说明**: 开发者 TTS 模式，音频发送结束。

```json
{
  "event": "response.audio.finish",
  "requestId": "req_1",
  "responseId": "res_1"
}
```

Data 载荷为 null。

### 4.15 response.audio.promptStart / response.audio.promptFinish

- **方向**: Agent -> 平台
- **常量**: `RESPONSE_AUDIO_PROMPT_START` / `RESPONSE_AUDIO_PROMPT_FINISH`
- **说明**: 开发者 TTS 模式，主动提示音开始/结束。不需要 `requestId`/`responseId`。

```json
{
  "event": "response.audio.promptStart"
}
```

```json
{
  "event": "response.audio.promptFinish"
}
```

### 4.16 control.interrupt

- **方向**: Agent -> 平台
- **常量**: `CONTROL_INTERRUPT`
- **说明**: 中断当前正在进行的响应。

```json
{
  "event": "control.interrupt"
}
```

Data 载荷为 null，无需 requestId。

### 4.17 system.prompt

- **方向**: Agent -> 平台
- **常量**: `SYSTEM_PROMPT`
- **说明**: Agent 主动发送提示/提醒给用户。

```json
{
  "event": "system.prompt",
  "data": {
    "text": "Are you still there?"
  }
}
```

**Data 载荷** (`TextData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 提示文本内容 |

### 4.18 system.idleTrigger

- **方向**: 平台 -> Agent
- **常量**: `SYSTEM_IDLE_TRIGGER`
- **说明**: 检测到空闲超时，通知开发者。

```json
{
  "event": "system.idleTrigger",
  "data": {
    "reason": "NO_INPUT_TIMEOUT",
    "idleTimeMs": 30000
  }
}
```

**Data 载荷** (`IdleTriggerData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `reason` | String | 触发原因 |
| `idleTimeMs` | Long | 持续空闲时间（毫秒） |

### 4.19 scene.ready

- **方向**: JS SDK -> 平台
- **常量**: `SCENE_READY`
- **说明**: 通过 LiveKit DataChannel 发送。前端 3D 场景加载完成，可以开始对话。

```json
{
  "event": "scene.ready"
}
```

Data 载荷为 null。SDK 客户端收到仅打印日志，无回调。

### 4.20 error

- **方向**: 双向
- **常量**: `ERROR`
- **说明**: 错误通知。回调 `listener.onError(message)`。

```json
{
  "event": "error",
  "requestId": "req_1",
  "data": {
    "code": "ASR_FAIL",
    "message": "audio decode error"
  }
}
```

**Data 载荷** (`ErrorData`):

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | String | 错误码 |
| `message` | String | 错误描述 |

---

## 5. 二进制帧协议

### 5.1 音频帧 AudioFrame

- **二进制类型标识**: 前 2 bit = `01`
- **用途**: 开发者 ASR 模式（平台->Agent）或 开发者 TTS 模式（Agent->平台）

**帧结构**: `[Header (9 bytes)] + [Audio Payload]`

**Header 位布局 (9 bytes / 72 bits)**:

| 位域 | 大小 (bits) | 位置 | 说明 |
|------|-------------|------|------|
| T (Type) | 2 bits | 70-71 | 固定 `01` = 音频帧 |
| C (Channel) | 1 bit | 69 | `0`=单声道, `1`=立体声 |
| K (KeyFrame) | 1 bit | 68 | `1`=关键帧（首帧或 Opus 重同步） |
| S (Sequence) | 12 bits | 56-67 | 序号 0-4095 |
| TS (Timestamp) | 20 bits | 36-55 | 时间戳（毫秒）0-1048575 |
| SR (SampleRate) | 2 bits | 34-35 | `00`=16kHz, `01`=24kHz, `10`=48kHz |
| F (FrameSize) | 12 bits | 22-33 | 每帧采样数 0-4095 |
| Codec | 2 bits | 20-21 | `00`=PCM, `01`=Opus |
| R (Reserved) | 4 bits | 16-19 | 保留位（全 0） |
| L (Length) | 16 bits | 0-15 | 载荷长度（字节）0-65535 |

### 5.2 图片帧 ImageFrame

- **二进制类型标识**: 前 2 bit = `10`
- **用途**: 数字人形象图片传输

**帧结构**: `[Header (12 bytes)] + [Image Payload]`

**Header 位布局 (12 bytes / 96 bits)**:

| 位域 | 大小 (bits) | 位置 | 说明 |
|------|-------------|------|------|
| T (Type) | 2 bits | 94-95 | 固定 `10` = 图片帧 |
| V (Version) | 2 bits | 92-93 | 协议版本 `00` |
| Format | 4 bits | 88-91 | `0`=JPG, `1`=PNG, `2`=WebP, `3`=GIF, `4`=AVIF |
| Quality | 8 bits | 80-87 | 图片质量 0-255 |
| ID | 16 bits | 64-79 | 图片唯一标识（用于分片重组） |
| W (Width) | 16 bits | 48-63 | 图片宽度（像素） |
| H (Height) | 16 bits | 32-47 | 图片高度（像素） |
| L (Length) | 32 bits | 0-31 | 载荷长度（字节）0-4294967295 |

---

## 6. 完整交互流程

```
Agent                                     Platform / Service
  |                                             |
  |--- POST /v1/session/start ----------------->|  (REST)
  |<-- { sessionId, agentWsUrl, ... } ----------|
  |                                             |
  |--- WebSocket Connect ---------------------->|
  |<-- session.init {sessionId,userId} ---------|
  |--- session.ready -------------------------->|  (SDK auto-reply)
  |<-- session.state "LISTENING" ---------------|
  |                                             |
  |   ========== 用户输入阶段 ==========          |
  |<-- input.text "..." ------------------------|  (Platform ASR)
  |       或                                     |
  |<-- [AudioFrame binary] ---------------------|  (Developer ASR)
  |--- input.voice.start ---------------------->|
  |--- input.asr.partial "..." ---------------->|
  |--- input.asr.final "..." ------------------>|
  |--- input.voice.finish --------------------->|
  |                                             |
  |   ========== Agent 回复阶段 ==========       |
  |--- response.start (可选) ------------------>|
  |--- response.chunk seq=0 "Hello" ----------->|
  |--- response.done -------------------------->|
  |       或                                     |
  |--- response.audio.start ------------------->|
  |--- [AudioFrame binary] --------------------->|
  |--- response.audio.finish ------------------>|
  |                                             |
  |   ========== 控制 =====================       |
  |--- control.interrupt ---------------------->|
  |--- system.prompt "Are you there?" --------->|
  |<-- system.idleTrigger ----------------------|
  |                                             |
  |   ========== 结束 =====================       |
  |<-- session.closing "timeout" ---------------|
  |--- WebSocket Close ------------------------>|
```

---

## 7. 新增指令设计: scene.switchVideo

### 7.1 背景与目的

当前协议支持音频和文本交互，但缺少对数字人播放视频内容的控制能力。新增 `scene.switchVideo` 指令，允许 Agent 端动态切换数字人当前播放的视频内容，支持：

- **一次性视频 (onceVideos)**: 数字人播放一次的视频列表（按顺序播放）
- **循环视频 (loopVideos)**: 数字人循环播放的视频列表（按顺序循环）

### 7.2 指令定义

```
事件名: scene.switchVideo
方向:   Agent -> 平台
说明:   Agent 通知平台切换数字人当前播放的视频内容
```

> 注：该事件名仅作为协议定义。SDK 公开 API 中不暴露对应常量，通过通用方法传入字符串使用。

### 7.3 数据模型

```json
{
  "event": "scene.switchVideo",
  "requestId": "req_1",
  "data": {
    "onceVideos": ["res_video_001", "res_video_002"],
    "loopVideos": ["res_video_bg"],
    "videoChoreographyGroups": [["A1", "A2"], ["B1"]],
    "mode": "GRACEFUL"
  }
}
```

**Data 字段**:

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `onceVideos` | String[] | 否 | 一次性播放列表，按顺序播放，播完不循环。每项可以是 resourceId，或该数字人底板视频的 alias |
| `loopVideos` | String[] | 否 | 循环播放列表。`videoChoreographyGroups` 非空时忽略。每项可以是 resourceId 或 alias |
| `videoChoreographyGroups` | String[][] | 否 | 编排分组。每组随机选一条，一轮后再从第一组开始。非空时保留 `onceVideos`，忽略 `loopVideos` |
| `mode` | String | 否 | `HARD` 立刻切；`GRACEFUL` 等当前片播完再切（默认）。无法识别时按 `GRACEFUL` |

> **约束**:
> - `onceVideos`、`loopVideos`、`videoChoreographyGroups` 至少一个非空，否则平台跳过本次请求
> - alias：3 到 32 位，字母开头，只含小写字母、数字、下划线，不能以下划线结尾。SDK 原样传递，平台命中则换成 resourceId
> - 支持的视频编码: h264

### 7.4 消息示例

**仅播放一次性视频**:

```json
{
  "event": "scene.switchVideo",
  "requestId": "req_2",
  "data": {
    "onceVideos": ["res_promo_001"],
    "loopVideos": []
  }
}
```

**仅播放循环视频**:

```json
{
  "event": "scene.switchVideo",
  "requestId": "req_3",
  "data": {
    "onceVideos": [],
    "loopVideos": ["res_ambient"]
  }
}
```

**同时播放（先播一次性，再循环）**:

```json
{
  "event": "scene.switchVideo",
  "requestId": "req_4",
  "data": {
    "onceVideos": ["res_intro", "res_main"],
    "loopVideos": ["res_waiting"]
  }
}
```

### 7.5 服务端处理逻辑

平台收到 `scene.switchVideo` 后：

1. 三个视频字段都空则跳过，不切画面
2. 把 alias 换成 resourceId（未命中则原样当 resourceId）
3. 按 `mode` 决定何时开始切：`HARD` 立刻切，`GRACEFUL` 等当前视频播完
4. 顺序播放 `onceVideos`（每个播完后播下一个）
5. `onceVideos` 播完后：有编排分组则按组随机循环；否则循环 `loopVideos`
6. 再次收到 `scene.switchVideo` 则替换当前播放内容

### 7.6 SDK 实现改动

为保护切视频功能不在公开 SDK 中暴露，SDK 层**不新增**任何与切视频相关的类型或方法。改动的**只有 `AvatarAgent.java` 一个文件**。

#### 1. AvatarAgent.java — 新增通用方法

在 `AvatarAgent` 中新增 `sendCustomEvent` 方法，用于发送 SDK 未预定义的自定义事件：

```java
public void sendCustomEvent(String requestId, String event, Object data)
        throws ConnectionException, MessageSerializationException {
    State s = requireStarted();
    Message message = new Message(event);
    message.setRequestId(requestId);
    message.setData(data);
    s.wsClient.sendMessage(message);
}
```

方法设计：

| 方面 | 说明 |
|------|------|
| 方法名 | `sendCustomEvent` — "自定义事件"，不暴露任何业务意图 |
| event 参数 | String 类型，调用方传 `"scene.switchVideo"` |
| data 参数 | Object 类型，Jackson 自动序列化，调用方传 `Map.of(...)` 或 POJO |

#### 2. 不改动的文件

| 文件 | 原因 |
|------|------|
| `EventType.java` | 不添加 `SCENE_SWITCH_VIDEO` 常量 |
| `MessageBuilder.java` | 不添加 sceneSwitchVideo 方法 |
| `SwitchVideoData.java` | 不创建此类 |

#### 3. 后端使用示例（不公开到 SDK）

```java
Map<String, List<String>> payload = new HashMap<>();
payload.put("onceVideos", List.of("res_video_001", "res_video_002"));
payload.put("loopVideos", List.of("res_video_bg"));
payload.put("mode", "GRACEFUL");
agent.sendCustomEvent("req_1", "scene.switchVideo", payload);
```

### 7.7 交互流程

```
Agent                                     Platform
  |                                             |
  |--- scene.switchVideo ---------------------->|
  |    {                                         |
  |      "onceVideos": ["res_intro"],            |
  |      "loopVideos": ["res_waiting"]           |
  |    }                                         |
  |                                             |
  |   [平台处理:]                                |
  |   1. 中断当前视频播放                         |
  |   2. 播放 onceVideos[0] = res_intro          |
  |   3. res_intro 播放完毕                      |
  |   4. 开始循环播放 loopVideos                |
  |                                             |
  |--- scene.switchVideo (再次发送) ------------->|  (切换新视频)
  |    { onceVideos: [], loopVideos: [...] }     |
  |                                             |
  |--- scene.switchVideo (清空) ----------------->|  (停止所有视频)
  |    { onceVideos: [], loopVideos: [] }        |
```
