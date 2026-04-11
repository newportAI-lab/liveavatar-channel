# Live Avatar Channel SDK - 快速开始

[English](./QUICKSTART.md) | **中文**

## 技术栈概览

### ✅ 本 SDK 支持的功能

**WebSocket 模式：**
- ✅ 文本协议（JSON 格式消息）
- ✅ 音频/图像协议（二进制数据）
- ✅ 开发者需自行提供 WebSocket 服务端
- ✅ 完整的连接管理与心跳机制

## 项目结构

本项目采用 Maven 多模块结构：

```
liveavatar-channel/                             # 项目根目录
├── Parent POM                                    # 父 POM
├── README.md                                   # 使用文档（英文）
├── README.zh.md                                # 使用文档（中文）
├── PROTOCOL.md                                 # 协议文档（英文）
├── PROTOCOL.zh.md                              # 协议文档（中文）
├── QUICKSTART.md                               # 快速开始（英文）
├── QUICKSTART.zh.md                            # 快速开始（中文）
├── liveavatar-channel-sdk/                     # SDK 模块
│   ├── pom.xml                                 # SDK 模块 POM
│   └── src/
│       ├── main/java/com/newportai/liveavatar/channel/
│       │   ├── client/                         # 客户端实现
│       │   │   ├── AvatarWebSocketClient.java  # WebSocket 客户端
│       │   │   └── StreamingResponseHandler.java
│       │   ├── exception/                      # 异常类
│       │   ├── listener/                       # 事件监听器
│       │   ├── model/                          # 数据模型
│       │   │   ├── Message.java                # 基础消息模型
│       │   │   ├── AudioFrame.java             # 音频帧模型
│       │   │   ├── EventType.java              # 事件类型常量
│       │   │   ├── IdleTriggerData.java        # 空闲触发数据
│       │   │   └── ...
│       │   ├── reconnect/                      # 自动重连
│       │   └── util/                           # 工具类
│       └── test/java/com/newportai/liveavatar/channel/
│           └── example/                        # 示例代码
│               ├── LiveAvatarServiceInboundSimulator.java   # 开发者客户端模拟器（Inbound 模式）
│               ├── LiveAvatarServiceOutboundSimulator.java  # Live Avatar Service 模拟器（Outbound 模式）
│               ├── WebSocketExample.java                    # 最简 Inbound 模式示例
│               └── AudioProtocolExample.java               # 音频协议示例
└── liveavatar-channel-server-example/              # 服务端示例模块
    ├── pom.xml                                 # 服务端模块 POM
    ├── README.md                               # 服务端实现指南（英文）
    ├── README.zh.md                            # 服务端实现指南（中文）
    └── src/main/java/com/newportai/liveavatar/channel/server/
        ├── AvatarServerApplication.java        # Spring Boot 入口
        ├── config/
        │   └── WebSocketConfig.java            # WebSocket 配置
        ├── handler/
        │   └── AvatarChannelWebSocketHandler.java  # 消息处理器
        ├── service/
        │   ├── MessageProcessingService.java   # 业务逻辑
        │   └── AsrService.java                 # ASR 服务
        └── session/
            ├── SessionManager.java             # 会话管理
            └── AvatarSession.java              # 会话模型
```

## 构建与安装

### 方式一：构建整个项目（推荐）

在项目根目录执行以下命令：

```bash
cd liveavatar-channel

# 编译所有模块
mvn clean compile

# 运行所有测试
mvn test

# 打包所有模块
mvn clean package

# 安装所有模块到本地 Maven 仓库
mvn clean install
```

**优势：**
- ✅ 自动处理模块间依赖
- ✅ 无需手动安装 SDK
- ✅ 一次性构建所有模块

### 方式二：单独构建 SDK

```bash
cd liveavatar-channel-sdk

# 编译 SDK
mvn clean compile

# 安装 SDK 到本地仓库
mvn clean install
```

生成的 JAR 文件位于 `target/liveavatar-channel-sdk-1.0.0.jar`。

### 方式三：单独构建服务端示例

必须先构建 SDK（使用方式一或方式二）：

```bash
cd liveavatar-channel-server-example

# 运行服务端
mvn spring-boot:run

# 或打包为可执行 JAR
mvn clean package
java -jar target/liveavatar-channel-server-example-1.0.0.jar
```

## 使用方法

### 方式一：作为 Live Avatar Service 客户端

连接到开发者服务端，发送用户输入并接收 AI 响应。

```java
String wsUrl = "ws://your-server.com/ws";
String sessionId = "sess_" + System.currentTimeMillis();
String userId = "user_123";

// 创建流式响应处理器
StreamingResponseHandler streamHandler = new StreamingResponseHandler();

// 创建客户端
final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];
AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
    new AvatarChannelListenerAdapter() {

        @Override
        public void onConnected() {
            try {
                // 连接成功后发送 session.init
                Message msg = MessageBuilder.sessionInit(sessionId, userId);
                clientHolder[0].sendMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSessionReady(Message message) {
            try {
                // 会话就绪，发送文本输入
                Message textMsg = MessageBuilder.inputText("req_1", "Hello");
                clientHolder[0].sendMessage(textMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onResponseChunk(Message message) {
            // 处理流式响应（按序列号自动排序）
            streamHandler.handleChunk(message, chunk -> {
                System.out.println("Chunk " + chunk.getSeq() + ": " + chunk.getText());
            });
        }

        @Override
        public void onResponseDone(Message message) {
            streamHandler.handleDone(message, responseId -> {
                System.out.println("响应完成: " + responseId);
            });
        }
    });

clientHolder[0] = client;

// 连接到服务端
client.connect();

// ... 使用客户端 ...

// 断开连接
client.disconnect();
```

### 方式二：作为开发者服务端

接收 Live Avatar Service 发送的用户输入并返回 AI 响应。

```java
String wsUrl = "ws://localhost:8080/ws";

final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];
AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
    new AvatarChannelListenerAdapter() {

        private String currentSessionId;

        @Override
        public void onSessionInit(Message message) {
            // Live Avatar Service 发起连接
            SessionInitData data = JsonUtil.convertData(
                message.getData(), SessionInitData.class
            );
            currentSessionId = data.getSessionId();

            try {
                // 返回 session.ready
                Message readyMsg = MessageBuilder.sessionReady();
                clientHolder[0].sendMessage(readyMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onInputText(Message message) {
            // 接收用户输入
            TextData data = JsonUtil.convertData(message.getData(), TextData.class);
            String userInput = data.getText();

            try {
                // 调用 AI 生成响应
                String aiResponse = callYourAI(userInput);

                // 流式发送响应
                String responseId = "res_" + System.currentTimeMillis();
                for (int i = 0; i < aiResponse.length(); i++) {
                    Message chunk = MessageBuilder.responseChunk(
                        message.getRequestId(),
                        responseId,
                        i,
                        String.valueOf(aiResponse.charAt(i))
                    );
                    clientHolder[0].sendMessage(chunk);
                    Thread.sleep(50); // 模拟流式延迟
                }

                // 发送完成信号
                Message done = MessageBuilder.responseDone(
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

## 核心 API

### MessageBuilder - 消息构建器

```java
// Session 消息
MessageBuilder.sessionInit(sessionId, userId)
MessageBuilder.sessionReady()
MessageBuilder.sessionState(state, seq)
MessageBuilder.sessionClose(reason)

// Input 消息
MessageBuilder.inputText(requestId, text)
MessageBuilder.inputVoiceStart(requestId)
MessageBuilder.inputVoiceFinish(requestId)
MessageBuilder.asrPartial(requestId, seq, text)
MessageBuilder.asrFinal(requestId, text)

// Response 消息
MessageBuilder.responseStart(requestId, responseId, audioConfig)  // 可选，在第一个 chunk 前发送（仅平台 TTS 时使用）
MessageBuilder.responseChunk(requestId, responseId, seq, text)
MessageBuilder.responseDone(requestId, responseId)
MessageBuilder.responseCancel(responseId)
MessageBuilder.responseAudioStart(requestId, responseId)
MessageBuilder.responseAudioFinish(requestId, responseId)
MessageBuilder.responseAudioPromptStart()
MessageBuilder.responseAudioPromptFinish()

// Control 消息
MessageBuilder.controlInterrupt()

// System 消息
MessageBuilder.systemPrompt(text)
MessageBuilder.systemIdleTrigger(reason, idleTimeMs)

// Error 消息
MessageBuilder.error(requestId, code, message)
```

### SessionState - 会话状态枚举

定义 8 种 Live Avatar 会话状态：

```java
import com.newportai.liveavatar.channel.model.SessionState;

// 创建状态消息
Message msg = MessageBuilder.sessionState(
    SessionState.SPEAKING.getValue(),
    seq
);

// 解析状态并处理
@Override
public void onSessionState(Message message) {
    SessionStateData data = JsonUtil.convertData(
        message.getData(),
        SessionStateData.class
    );

    SessionState state = SessionState.fromValue(data.getState());

    switch (state) {
        case IDLE:            // 空闲，等待输入
        case LISTENING:       // 用户说话，ASR 捕获音频
        case THINKING:        // 系统思考，LLM/TTS 准备中
        case STAGING:         // 准备生成 Live Avatar 输出
        case SPEAKING:        // Live Avatar 输出正常响应
        case PROMPT_THINKING: // 准备提示文本
        case PROMPT_STAGING:  // 为提示准备 Live Avatar
        case PROMPT_SPEAKING: // Live Avatar 播报提示
    }
}
```

**状态说明表：**

| 状态 | 发言者 | 系统行为 |
|------|--------|----------|
| IDLE | 无 | 等待输入 |
| LISTENING | 用户 | ASR 捕获音频 |
| THINKING | 系统（处理）| LLM/TTS 准备中 |
| STAGING | 系统（躯体）| 准备生成 Live Avatar |
| SPEAKING | 系统（躯体）| Live Avatar 输出标准响应 |
| PROMPT_THINKING | 系统（大脑）| 准备提示语 |
| PROMPT_STAGING | 系统（躯体）| 准备生成 Live Avatar |
| PROMPT_SPEAKING | 系统（躯体）| Live Avatar 播报提示音频 |

### StreamingResponseHandler - 流式响应处理

支持乱序消息自动排序：

```java
StreamingResponseHandler handler = new StreamingResponseHandler();

// 处理 response.chunk
handler.handleChunk(message, chunk -> {
    System.out.println(chunk.getSeq() + ": " + chunk.getText());
});

// 处理 response.done
handler.handleDone(message, responseId -> {
    System.out.println("Response " + responseId + " 已完成");
});

// 处理 response.cancel
handler.handleCancel(message, responseId -> {
    System.out.println("Response " + responseId + " 已取消");
});
```

## 运行示例

### 1. 启动服务端（Spring Boot）

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

服务端将在 `ws://localhost:8080/avatar/ws` 启动，并暴露 `POST http://localhost:8080/api/session/start`。

**服务端功能：**
- ✅ Inbound：暴露 `POST /api/session/start` REST 端点，返回 `sessionId` + `wsUrl`
- ✅ Outbound：接受来自 Live Avatar Service 的直接 WebSocket 连接
- ✅ 处理 `session.init` → 响应 `session.ready`
- ✅ 处理 `input.text` → 返回 `response.chunk` / `response.done`
- ✅ 处理音频帧并执行 ASR
- ✅ 响应 `system.idleTrigger`，发送 `system.prompt`
- ✅ 有新输入时发送 `control.interrupt`

详见：[liveavatar-channel-server-example/README.zh.md](./liveavatar-channel-server-example/README.zh.md)

### 2a. Inbound 模式 — 开发者客户端模拟器

Inbound 模式下，开发者先调用平台 REST API，再作为 WebSocket 客户端连接。

```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceInboundSimulator"
```

**执行步骤：**
1. 调用 `POST /api/session/start` → 获取 `sessionId` + `wsUrl`
2. 连接到平台 WebSocket
3. 发送携带平台颁发 `sessionId` 的 `session.init`

**交互命令：**
| 命令 | 动作 |
|------|------|
| 任意文本 | 发送 `input.text` |
| `audio` | 发送 20 个测试 PCM 音频帧 |
| `interrupt` | 发送 `control.interrupt` |
| `prompt` | 发送 `system.prompt`（空闲唤醒回复）|
| `quit` | 关闭会话并退出 |

### 2b. Outbound 模式 — Live Avatar Service 模拟器

Outbound 模式下，开发者托管服务端，Live Avatar Service 直接连接。

```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceOutboundSimulator"
```

**执行步骤：**
- 直接连接到 `ws://localhost:8080/avatar/ws`（无需调用 REST API）
- 自动生成 `sessionId` 并发送 `session.init`

**交互命令：**
| 命令 | 动作 |
|------|------|
| 任意文本 | 发送 `input.text` |
| `audio` | 发送 20 个测试 PCM 音频帧 |
| `state` | 发送 `session.state: LISTENING` |
| `idle` | 发送 `system.idleTrigger`（120 秒空闲）|
| `quit` | 关闭会话并退出 |

### 3. 运行其他示例

#### 最简 Inbound 模式示例
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.WebSocketExample"
```

#### 音频协议示例
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.AudioProtocolExample"
```

#### 会话状态示例
```bash
cd liveavatar-channel-sdk
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.SessionStateExample"
```

## 常见使用场景

### 1. 中断控制

```java
// 用户中断 Live Avatar 说话时
Message interruptMsg = MessageBuilder.controlInterrupt();
client.sendMessage(interruptMsg);
```

### 2. 系统主动提示（静默唤醒）

```java
// 开发者服务端主动发送提示
Message promptMsg = MessageBuilder.systemPrompt("您还在吗？");
client.sendMessage(promptMsg);
```

### 3. 实时 ASR（语音转文字）

**场景 2A — 平台 ASR：** 平台将识别结果下发给开发者。

```java
@Override
public void onAsrPartial(Message message) {
    // 部分识别结果（平台 → 开发者），用于实时字幕
    TextData data = JsonUtil.convertData(message.getData(), TextData.class);
    updateSubtitle(data.getText());
}

@Override
public void onAsrFinal(Message message) {
    // 最终识别结果（平台 → 开发者），用于 AI 处理
    TextData data = JsonUtil.convertData(message.getData(), TextData.class);
    processUserInput(data.getText());
}
```

**场景 2B — 开发者 ASR / Omni：** 平台在会话期间持续转发所有音频为原始 Binary Frame — 无开始/结束事件，平台不做任何 VAD。开发者在内部执行 VAD 和 ASR，并将**同样的 `input.voice.*`/`input.asr.*` 事件回传给平台**（方向相反），保持平台状态机正常流转，再发 `response.*`：

```java
@Override
public void onAudioFrame(AudioFrame frame) {
    // 平台持续转发，开发者自己做 VAD + ASR
    if (vadDetectsVoice(frame)) {
        if (justStartedSpeaking()) {
            sendMessage(MessageBuilder.inputVoiceStart(requestId));      // → 平台
        }
        sendMessage(MessageBuilder.asrPartial(requestId, seq, text));    // → 平台
    } else if (previouslyVoiceActive()) {
        sendMessage(MessageBuilder.inputVoiceFinish(requestId));         // → 平台
        sendMessage(MessageBuilder.asrFinal(requestId, finalText));      // → 平台
        sendResponseChunks(finalText, requestId);
    }
}
```

### 4. 错误处理

```java
@Override
public void onErrorMessage(Message message) {
    ErrorData error = JsonUtil.convertData(message.getData(), ErrorData.class);
    System.err.println("错误: " + error.getCode() + " - " + error.getMessage());

    // 根据错误码进行处理
    if ("ASR_FAIL".equals(error.getCode())) {
        // 处理 ASR 失败
    }
}
```

## 协议特性

- ✅ 全面支持消息类型
- ✅ 流式数据传输
- ✅ 序列号乱序恢复（由 `StreamingResponseHandler` 自动处理）
- ✅ 会话生命周期管理
- ✅ 心跳机制（由 WebSocket 自动处理）
- ✅ 支持 WebSocket 和 WebRTC Data Channel（保持协议一致性）

## 技术栈

- Java 8+
- Jackson 2.15.2（JSON 序列化）
- OkHttp 4.11.0（WebSocket 客户端）
- SLF4J 2.0.7（日志）
- JUnit 4.13.2（单元测试）

## 后续步骤

1. 查看 `README.zh.md` 获取完整 API 文档
2. 查看 `PROTOCOL.zh.md` 了解协议设计详情
3. 参考 `src/test/java/com/newportai/liveavatar/channel/example/` 目录中的完整示例代码
4. 根据具体需求实现自定义 `AvatarChannelListener`

## 反馈

如遇问题，欢迎提交 Issue 或联系开发团队。
