# Live Avatar Channel SDK

[English](./README.md) | **中文**

基于 WebSocket 协议的 Live Avatar Java SDK（支持文本 + 音频/图像）

## 功能特性

- 🚀 WebSocket 双向通信（文本消息 + 音频/图像帧）
- 📨 全面支持文本消息类型（session、input、response、control、system、error）
- 🔊 支持二进制音频/图像帧协议（Header + Payload）
- 🔄 流式数据传输，基于序列号的乱序处理
- 💪 类型安全的消息与音频/图像帧构建器
- 🎯 事件驱动的监听器模式
- 💓 原生 WebSocket Ping/Pong 自动心跳（5 秒间隔）
- 🔌 自动重连机制（指数退避策略）
- 📦 开箱即用，易于集成
- 🏗️ Maven 多模块项目结构

## 架构概览

本 SDK 基于 **WebSocket** 实现 Live Avatar 协议，支持以下内容：

### ✅ 已支持功能

- **文本消息：** JSON 格式的文本协议（session、input、response、control 等）
- **二进制数据：** 二进制音频/图像帧（Header + Payload）
- **心跳保活：** 原生 WebSocket Ping/Pong 控制帧（每 5 秒自动处理）
- **流式传输：** 支持文本与音频/图像数据的流式传输

### ⚠️ 技术栈说明

**WebSocket 模式：**
- ✅ 支持 Inbound 和 Outbound 两种连接模式
- ✅ 支持文本协议（JSON）
- ✅ 支持音频/图像协议（Binary）
- ✅ 本 SDK 完整支持

**构建整个项目：**
```bash
mvn clean install
```

此命令将按顺序构建 SDK 和服务端示例，无需手动将 SDK 安装到本地仓库。

## 快速开始

### 连接模式

Live Avatar Channel 协议支持两种 WebSocket 连接模式：

#### Inbound 模式（平台托管服务端）

Live Avatar 平台提供 WebSocket 服务端。开发者需要：
1. 调用平台 REST API `POST /api/session/start` → 获取 `sessionId` + `wsUrl`
2. 使用 SDK 连接到 `wsUrl`
3. 发送携带返回 `sessionId` 的 `session.init`

```java
// 第一步：调用 REST API 获取 sessionId + wsUrl（参见 LiveAvatarServiceInboundSimulator）
// 第二步：使用返回的 wsUrl 进行连接
AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
    new AvatarChannelListenerAdapter() {
        @Override
        public void onConnected() {
            // 第三步：发送携带平台颁发 sessionId 的 session.init
            client.sendMessage(MessageBuilder.sessionInit(sessionId, userId));
        }
    }
);
```

#### Outbound 模式（开发者托管服务端）

开发者实现 WebSocket 服务端，Live Avatar Service 作为客户端连接到它。

**完整服务端示例：** [`liveavatar-channel-server-example/`](./liveavatar-channel-server-example/)

该示例基于 Spring Boot，演示了：
- 如何接受来自 Live Avatar Service 的连接
- 如何处理文本和音频输入
- 如何执行 ASR（自动语音识别）
- 如何发送流式 AI 响应
- 如何处理中断和空闲唤醒

参见：[服务端示例 README](./liveavatar-channel-server-example/README.zh.md)

### Maven 依赖

在 `pom.xml` 中添加以下依赖：

```xml
<dependency>
  <groupId>com.newportai.liveavatar.channel</groupId>
  <artifactId>avatar-channel-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 基础用法

```java
import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.util.MessageBuilder;

// 创建客户端
AvatarWebSocketClient client = new AvatarWebSocketClient(
    "ws://your-server.com/ws",
    new AvatarChannelListenerAdapter() {

        @Override
        public void onConnected() {
            System.out.println("已连接！");

            // 发送 session.init
            try {
                Message msg = MessageBuilder.sessionInit("sess_123", "user_1");
                client.sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onResponseChunk(Message message) {
            // 处理流式响应
            System.out.println("Chunk: " + message.getData());
        }
    }
);

// 连接
client.connect();

// 发送消息
Message textMessage = MessageBuilder.inputText("req_1", "Hello");
client.sendMessage(textMessage);

// 断开连接
client.disconnect();
```

## 协议支持

### 消息类型

SDK 完整支持协议中定义的所有消息类型：

#### Session 相关
- `session.init` - 会话初始化
- `session.ready` - 会话就绪
- `session.state` - 会话状态变更
- `session.closing` - 会话即将关闭

#### Input 相关
- `input.text` - 文本输入
- `input.asr.partial` - ASR 部分识别结果
- `input.asr.final` - ASR 最终识别结果
- `input.voice.start` - 用户语音开始
- `input.voice.finish` - 用户语音结束

#### Response 相关
- `response.start` - 响应开始（可选的 TTS 音频配置：speed / volume / mood）；当 TTS 由 Live Avatar Service 管理时，在第一个 chunk 前发送
- `response.chunk` - 流式响应块
- `response.done` - 响应完成
- `response.cancel` - 响应取消
- `response.voice.start` - TTS 响应开始
- `response.voice.finish` - TTS 响应结束
- `response.audio.promptStart` - TTS 提示响应开始
- `response.audio.promptFinish` - TTS 提示响应结束

#### Control 相关
- `control.interrupt` - 中断控制

#### System 相关
- `system.prompt` - 系统提示（由 response idle 状态触发）
- `system.idleTrigger` - 空闲触发（检测到无用户交互）

#### Error
- `error` - 错误消息

### 心跳机制

本 SDK 使用**原生 WebSocket Ping/Pong 控制帧**维持心跳和连接保活：

- **客户端：** OkHttp 每 5 秒自动发送原生 WebSocket ping 帧（opcode 0x9）。
- **服务端：** Spring WebSocket 自动回复 pong 帧（opcode 0xA）。
- **特点：**
  - 在传输层处理，对应用层透明。
  - 无需 JSON 序列化，效率更高。
  - 自动超时检测与重连。
  - 符合 RFC 6455 标准。

**配置示例：**
```java
OkHttpClient client = new OkHttpClient.Builder()
    .pingInterval(5, TimeUnit.SECONDS)  // 启用原生 ping
    .build();
```

> **注意：** JSON 格式的应用层 ping/pong 消息已不再使用。

### 音频协议

SDK 支持通过 WebSocket 传输二进制音频数据。

#### 音频帧结构

```
[Header（9 字节）] + [音频 Payload]
```

#### Header 字段定义（72 位）

| 字段 | 位数 | 说明 |
|------|------|------|
| T | 2 | 类型；固定为 01（音频帧）|
| C | 1 | 声道（0=单声道，1=立体声）|
| K | 1 | 是否为首帧？|
| S | 12 | 序列号（0-4095）|
| TS | 20 | 时间戳（毫秒）|
| SR | 2 | 采样率（00=16kHz，01=24kHz，10=48kHz）|
| F | 12 | 帧大小 |
| Codec | 2 | 编解码器 |
| R | 4 | 保留位 |
| L | 16 | Payload 长度 |

#### 使用示例

```java
// 创建音频帧
AudioFrame frame = AudioFrameBuilder.create()
    .mono()  // 或 .stereo(true)
    .firstFrame(true)
    .sequence(0)
    .currentTimestamp()
    .sampleRate(AudioHeader.SampleRate.RATE_48KHZ)
    .frameSize(480)
    .codec(Codec.PCM)
    .payload(pcmData)
    .build();

// 发送音频帧
client.sendAudioFrame(frame);

// 接收音频帧
@Override
public void onAudioFrame(AudioFrame frame) {
    byte[] pcmData = frame.getPayload();
    int sampleRate = frame.getHeader().getSampleRate().getValue();
    // 处理 PCM 音频数据...
}
```

> **注意：** 音频协议仅在 WebSocket 通道上支持。

## 核心组件

### AvatarWebSocketClient

WebSocket 客户端实现，提供以下功能：

```java
// 创建客户端
AvatarWebSocketClient client = new AvatarWebSocketClient(url, listener);

// 连接
client.connect();

// 发送消息
client.sendMessage(message);

// 检查连接状态
boolean connected = client.isConnected();

// 断开连接
client.disconnect();
```

### AvatarChannelListener

监听所有协议事件的事件监听器接口：

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
    // ... 更多方法
}
```

建议继承 `AvatarChannelListenerAdapter`，仅实现你需要的方法。

### MessageBuilder

消息构建器，提供创建各类消息的便捷方法：

```java
// Session 消息
Message msg = MessageBuilder.sessionInit(sessionId, userId);
Message msg = MessageBuilder.sessionReady(sessionId);
Message msg = MessageBuilder.sessionClose(sessionId, reason);

// Input 消息
Message msg = MessageBuilder.inputText(requestId, text);
Message msg = MessageBuilder.asrFinal(sessionId, requestId, text);

// Response 消息
Message msg = MessageBuilder.responseStart(requestId, responseId, audioConfig);
Message msg = MessageBuilder.responseChunk(sessionId, requestId, responseId, seq, text);
Message msg = MessageBuilder.responseDone(sessionId, requestId, responseId);

// Control 消息
Message msg = MessageBuilder.controlInterrupt(sessionId);

// System 消息
Message msg = MessageBuilder.systemPrompt(sessionId, text);

// Error 消息
Message msg = MessageBuilder.error(sessionId, requestId, code, errorMessage);
```

### SessionState

定义 Live Avatar 所有可能状态的会话状态枚举：

```java
// 导入枚举
import com.newportai.liveavatar.channel.model.SessionState;

// 使用枚举创建状态消息
Message msg = MessageBuilder.sessionState(
    sessionId,
    SessionState.SPEAKING.getValue(),
    seq
);

// 解析状态消息
@Override
public void onSessionState(Message message) {
    SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
    SessionState state = SessionState.fromValue(data.getState());

    switch (state) {
        case IDLE:             // 空闲，等待输入
            break;
        case LISTENING:        // 用户说话，ASR 捕获音频
            break;
        case THINKING:         // 系统处理中，LLM/TTS 准备中
            break;
        case STAGING:          // 准备生成 Live Avatar
            break;
        case SPEAKING:         // Live Avatar 正在输出标准响应
            break;
        case PROMPT_THINKING:  // 准备提示语
            break;
        case PROMPT_STAGING:   // 为提示准备 Live Avatar
            break;
        case PROMPT_SPEAKING:  // Live Avatar 播报提示音频
            break;
    }
}
```

**所有状态说明：**

| 状态 | 发言者 | 系统行为 |
|------|--------|----------|
| IDLE | 无 | 等待输入 |
| LISTENING | 用户 | ASR 捕获音频 |
| THINKING | 系统（大脑）| 准备 LLM/TTS |
| STAGING | 系统（躯体）| 准备生成 Live Avatar |
| SPEAKING | 系统（躯体）| Live Avatar 输出标准响应 |
| PROMPT_THINKING | 系统（大脑）| 准备提示文本 |
| PROMPT_STAGING | 系统（躯体）| 为提示准备 Live Avatar |
| PROMPT_SPEAKING | 系统（躯体）| Live Avatar 播报提示音频 |

### StreamingResponseHandler

流式响应处理器，支持乱序消息自动排序：

```java
StreamingResponseHandler streamHandler = new StreamingResponseHandler();

// 在监听器中使用
@Override
public void onResponseChunk(Message message) {
    streamHandler.handleChunk(message, chunk -> {
        System.out.println("Seq: " + chunk.getSeq() + ", Text: " + chunk.getText());
    });
}

@Override
public void onResponseDone(Message message) {
    streamHandler.handleDone(message, responseId -> {
        System.out.println("Response " + responseId + " 已完成");
    });
}
```

## 使用场景

### 场景一：Live Avatar Service 客户端

连接到开发者服务端，发送用户输入并接收响应：

```java
AvatarWebSocketClient client = new AvatarWebSocketClient(url, new AvatarChannelListenerAdapter() {
    @Override
    public void onConnected() {
        // 连接后发送 session.init
        Message msg = MessageBuilder.sessionInit(sessionId, userId);
        client.sendMessage(msg);
    }

    @Override
    public void onSessionReady(Message message) {
        // 会话就绪，可以发送用户输入
        Message textMsg = MessageBuilder.inputText(requestId, userInput);
        client.sendMessage(textMsg);
    }

    @Override
    public void onResponseChunk(Message message) {
        // 接收流式响应块并播放
        TextData data = JsonUtil.convertData(message.getData(), TextData.class);
        playAvatarSpeech(data.getText());
    }
});
```

### 场景二：开发者服务端

接收 Live Avatar Service 的输入并返回 AI 响应：

```java
AvatarWebSocketClient client = new AvatarWebSocketClient(url, new AvatarChannelListenerAdapter() {
    @Override
    public void onSessionInit(Message message) {
        // Live Avatar 发起连接，返回 "ready"
        String sessionId = extractSessionId(message);
        Message readyMsg = MessageBuilder.sessionReady(sessionId);
        client.sendMessage(readyMsg);
    }

    @Override
    public void onInputText(Message message) {
        // 接收用户输入并调用 AI 生成响应
        TextData input = JsonUtil.convertData(message.getData(), TextData.class);
        String aiResponse = callAIModel(input.getText());

        // 流式发送响应
        String responseId = generateResponseId();
        for (int i = 0; i < aiResponse.length(); i++) {
            Message chunk = MessageBuilder.responseChunk(
                sessionId, message.getRequestId(), responseId, i,
                String.valueOf(aiResponse.charAt(i))
            );
            client.sendMessage(chunk);
        }

        // 发送完成信号
        Message done = MessageBuilder.responseDone(sessionId, message.getRequestId(), responseId);
        client.sendMessage(done);
    }
});
```

### 场景三：实时 ASR（语音识别）

处理实时语音识别：

```java
@Override
public void onAsrPartial(Message message) {
    // 部分识别结果，可用于实时字幕显示
    TextData data = JsonUtil.convertData(message.getData(), TextData.class);
    updateSubtitle(data.getText());
}

@Override
public void onAsrFinal(Message message) {
    // 最终识别结果，用于 AI 处理
    TextData data = JsonUtil.convertData(message.getData(), TextData.class);
    processUserInput(data.getText());
}
```

### 场景四：中断控制

实现当前播放的中断：

```java
// 用户中断 Avatar 说话
public void interruptAvatar() {
    Message interruptMsg = MessageBuilder.controlInterrupt(sessionId);
    client.sendMessage(interruptMsg);
}

// 开发者服务端接收中断信号
@Override
public void onControlInterrupt(Message message) {
    // 停止当前 AI 生成并取消响应
    String responseId = getCurrentResponseId();
    Message cancelMsg = MessageBuilder.responseCancel(message.getSessionId(), responseId);
    client.sendMessage(cancelMsg);
}
```

## 完整示例

查看位于 `src/test/java/com/newportai/liveavatar/channel/example/` 目录下的示例代码：

| 文件 | 模式 | 说明 |
|------|------|------|
| `WebSocketExample.java` | Inbound | 最简 SDK 用法——调用 REST API、连接、发送输入 |
| `LiveAvatarServiceInboundSimulator.java` | Inbound | 交互式开发者客户端——调用 REST API 创建会话，然后驱动对话 |
| `LiveAvatarServiceOutboundSimulator.java` | Outbound | 交互式 Live Avatar Service 客户端——直接连接开发者服务端，发送 `session.init` / `session.state` / `system.idleTrigger` / 音频 |

**完整服务端实现（支持两种模式）：** 参见 [`liveavatar-channel-server-example/`](./liveavatar-channel-server-example/) 项目

## 错误处理

SDK 提供全面的异常处理：

```java
try {
    client.connect();
    client.sendMessage(message);
} catch (ConnectionException e) {
    // 连接异常
    System.err.println("连接错误: " + e.getMessage());
} catch (MessageSerializationException e) {
    // 消息序列化异常
    System.err.println("序列化错误: " + e.getMessage());
}

// 在监听器中处理错误
@Override
public void onError(Throwable error) {
    // 处理运行时错误
    error.printStackTrace();
}

@Override
public void onErrorMessage(Message message) {
    // 处理协议错误消息
    ErrorData error = JsonUtil.convertData(message.getData(), ErrorData.class);
    System.err.println("错误: " + error.getCode() + " - " + error.getMessage());
}
```

## 自定义配置

### 自定义 OkHttpClient

```java
OkHttpClient customClient = new OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)   // 无限读取超时（流式连接）
    .writeTimeout(20, TimeUnit.SECONDS)
    .pingInterval(30, TimeUnit.SECONDS) // 心跳间隔
    .build();

AvatarWebSocketClient client = new AvatarWebSocketClient(url, listener, customClient);
```

## WebRTC Data Channel 支持

协议在 WebRTC Data Channel 上与在 WebSocket 上完全相同。WebRTC 不需要心跳机制（连接状态由 ICE 管理），因此可基于 `Message` 和 `MessageBuilder` 实现 Data Channel 客户端。

## 开发与构建

### 多模块构建

本项目采用 Maven 多模块结构，包含 SDK 和服务端示例两个模块。

```bash
# 克隆项目
git clone <repository-url>
cd liveavatar-channel

# 构建所有模块（推荐）
mvn clean install

# 仅编译（不安装）
mvn clean compile

# 运行测试
mvn test

# 打包所有模块
mvn clean package
```

### 仅构建 SDK

```bash
cd liveavatar-channel-sdk
mvn clean install
```

### 运行服务端示例

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

服务端将在 `ws://localhost:8080/avatar/ws` 启动。

### 运行模拟器（测试工具）

**Inbound 模式** — 开发者客户端连接到平台服务端：
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceInboundSimulator"
```

**Outbound 模式** — Live Avatar Service 连接到开发者服务端：
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceOutboundSimulator"
```

详见：[QUICKSTART.zh.md](./QUICKSTART.zh.md)
协议设计：[PROTOCOL.zh.md](./PROTOCOL.zh.md)

## License

参见：[LICENSE](./LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request！
