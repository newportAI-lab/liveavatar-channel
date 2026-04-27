# Live Avatar Channel 服务端示例

[English](./README.md) | **中文**

这是 Live Avatar Channel 协议**平台/开发者服务端的参考实现**。它支持两种连接模式，演示了如何构建一个处理 Live Avatar 用户交互的 WebSocket 服务端。

## 功能说明

本服务端在两种连接模式中均扮演**服务端角色**：

1. **接受 WebSocket 连接**，地址：`ws://localhost:8080/avatar/ws`
2. **暴露 REST 会话 API**，地址：`POST http://localhost:8080/api/session/start`（Inbound 模式）
3. **处理协议消息**，包括会话管理、用户输入和系统事件
4. **处理音频数据**并执行 ASR（自动语音识别）
5. **向客户端发送流式 AI 响应**
6. **处理中断**（用户在 Avatar 说话时打断）
7. **响应空闲触发**，保持对话活跃

## 连接模式

### Inbound 模式（平台托管服务端）

开发者先调用 REST API，再连接到平台 WebSocket。
测试工具：**`LiveAvatarServiceInboundSimulator`**

```
开发者服务端                         数字人服务
     |                                      |
     |-- POST /api/session/start ---------->|
     |<-- { sessionId, wsUrl } -------------|
     |                                      |
     |-- WebSocket 连接到 wsUrl ----------->|
     |-- session.init (sessionId) --------->|
     |<-- session.ready --------------------|
     |-- input.text / audio frames -------->|
     |<-- response.chunk / response.done ---|
```

### Outbound 模式（开发者托管服务端）

Live Avatar Service 直接连接，无需调用 REST API。
测试工具：**`LiveAvatarServiceOutboundSimulator`**

```
live avatar Service  <---WebSocket--->  开发者服务端（本示例）
     （客户端）                                （服务端）
     |                                      |
     |-- POST /api/session/start ---------->|
     |<-- { sessionId, wsUrl } -------------|
     |                                      |
     |-- WebSocket 连接到 wsUrl ----------->|
     |-- session.init (sessionId) --------->|
     |<-- session.ready --------------------|
     |-- input.text / audio frames -------->|
     |<-- response.chunk / response.done ---|
```

## 快速开始

### 前置条件

- Java 8 或更高版本
- Maven 3.6+
- 本地 Maven 仓库中已安装父项目 `liveavatar-channel-sdk`

### 安装父 SDK

首先安装父 SDK：

```bash
cd ..
mvn clean install -DskipTests
```

### 运行服务端

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

你将看到：

```
===========================================
Live Avatar Channel Server started successfully!
WebSocket endpoint : ws://localhost:8080/avatar/ws
Inbound session API: POST http://localhost:8080/api/session/start
===========================================
```

### 使用模拟器测试

在另一个终端中，根据模式运行对应的模拟器：

**Inbound 模式**（开发者客户端连接平台）：

```bash
cd ..
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceInboundSimulator"
```

**Outbound 模式**（Live Avatar Service 连接开发者服务端）：

```bash
cd ..
mvn exec:java -Dexec.mainClass="com.newportai.liveavatar.channel.example.LiveAvatarServiceOutboundSimulator"
```

## 项目结构

```
liveavatar-channel-server-example/
├── pom.xml
├── README.md
└── src/main/java/com/newportai/liveavatar/channel/server/
    ├── AvatarServerApplication.java          # Spring Boot 入口
    ├── api/
    │   └── StartSessionController.java       # POST /api/session/start（Inbound 模式）
    ├── config/
    │   └── WebSocketConfig.java              # WebSocket 端点配置
    ├── handler/
    │   └── AvatarChannelWebSocketHandler.java # 主消息处理器
    ├── session/
    │   ├── SessionManager.java               # 线程安全的会话管理
    │   └── AvatarSession.java                # 会话状态模型
    └── service/
        ├── MessageProcessingService.java     # 业务逻辑处理
        └── AsrService.java                   # ASR 识别（模拟实现）
```

## 核心功能

### 1. 会话管理

`SessionManager` 使用 `ConcurrentHashMap` 安全管理多个并发 WebSocket 连接：

```java
sessionManager.initSession(wsSessionId, avatarSessionId, userId);
AvatarSession session = sessionManager.getSessionByWsId(wsSessionId);
```

### 2. 中断处理

**文本输入打断** — 用户在 Avatar 说话时发送新文本，只需取消本地任务即可。平台在处理 `input.text` 事件时会自动清空 RTC 缓冲区：

```java
if (avatarSession.hasActiveResponse()) {
    avatarSession.cancelCurrentResponse();
}
```

**语音打断（场景 2B）** — 开发者通过 VAD 检测到说话开始时，发送 `input.voice.start` 即可。平台收到后会自动清空 RTC 缓冲区：

```java
if (avatarSession.hasActiveResponse()) {
    avatarSession.cancelCurrentResponse(); // 仅取消本地任务
}
Message voiceStart = MessageBuilder.inputVoiceStart(requestId);
sendMessage(session, voiceStart); // 平台自动清空 RTC 缓冲区
```

### 3. ASR 处理 — 场景 2B（开发者 ASR / Omni）

本服务端示例实现的是**场景 2B**：平台在会话期间持续将所有音频以原始 Binary Frame 流的形式转发（无开始/结束信号，平台不做 VAD），开发者完全在内部处理 VAD 和 ASR。

`AsrService` 演示了：

- 接收平台持续转发的原始音频帧
- 执行 VAD（语音活动检测）判断语音边界
- 向平台发送 `input.voice.start` / `input.voice.finish`（驱动平台状态机正常流转）
- 向平台发送流式 `input.asr.partial` 结果（用于实时字幕展示）
- 向平台发送最终 `input.asr.final` 结果（推进状态机，写入对话记录）
- 触发下游 AI 响应流程

**与场景 2A 的关键区别：** 2A 中这些事件由平台发给开发者；2B 中由开发者发给平台 — 事件相同，方向相反。

**⚠️ 生产注意事项**：请将模拟 VAD 和 ASR 替换为真实服务：

```java
// TODO: 集成真实 ASR 服务（如阿里云、OpenAI Whisper）
```

### 4. 空闲触发处理

当 Live Avatar Service 检测到用户无操作时：

```java
@Override
private void handleIdleTrigger(WebSocketSession session, Message message) {
    IdleTriggerData data = JsonUtil.convertData(message.getData(), IdleTriggerData.class);

    // 业务逻辑：决定是否提示用户
    String promptText = determinePromptText(data);
    if (promptText != null) {
        Message prompt = MessageBuilder.systemPrompt(sessionId, promptText);
        sendMessage(session, prompt);
    }
}
```

### 5. 流式响应

以块的形式发送 AI 响应，实现自然对话流：

```java
String[] sentences = splitBySentence(aiResponse);
for (int i = 0; i < sentences.length; i++) {
    Message chunk = MessageBuilder.responseChunk(
        sessionId, requestId, responseId, i, sentences[i]
    );
    sendMessage(session, chunk);
}

Message done = MessageBuilder.responseDone(sessionId, requestId, responseId);
sendMessage(session, done);
```

## 配置

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080  # 修改 WebSocket 端口

logging:
  level:
    com.newportai.liveavatar.channel.server: DEBUG  # 调整日志级别
```

## 集成真实服务

### ASR 集成

替换 `AsrService.java` 中的模拟 ASR：

```java
private AsrResult callRealTimeAsrService(List<byte[]> audioBuffer) {
    // 示例：阿里云实时 ASR
    // 1. 初始化 ASR 客户端
    // 2. 发送音频数据
    // 3. 接收识别结果
    // 4. 返回 AsrResult
}
```

### AI 集成

替换 `MessageProcessingService.java` 中的模拟 AI：

```java
private String callAIService(String text) {
    // 示例：OpenAI GPT API
    // ChatCompletion completion = openai.createChatCompletion(request);
    // return completion.getChoices().get(0).getMessage().getContent();
}
```

## 协议消息参考

### 接收自数字人服务的消息（平台 → 开发者）

| Event                 | 说明                              | 发送时机                   |
|-----------------------|-----------------------------------|----------------------------|
| `session.init`        | 初始化会话                        | 连接建立时                 |
| `input.text`          | 用户文本输入                      | 用户输入消息时             |
| `audio frames`        | 原始用户语音（二进制，场景 2B）   | 会话期间持续转发           |
| `input.asr.partial`   | 流式 ASR 结果（仅场景 2A）        | 平台 ASR 识别过程中        |
| `input.asr.final`     | 最终 ASR 结果（仅场景 2A）        | 平台 ASR 识别完成后        |
| `input.voice.start`   | VAD 语音开始（仅场景 2A）         | 平台 VAD 触发时            |
| `input.voice.finish`  | VAD 语音结束（仅场景 2A）         | 平台 VAD 触发时            |
| `image frames`        | 用户视频输入（二进制）            | 摄像头输入时               |
| `session.state`       | Avatar 状态更新                   | 状态变化时                 |
| `system.idleTrigger`  | 检测到空闲超时                    | 无活动后                   |
| `scene.ready`         | 场景已就绪，对话可开始（场景四：LiveKit DataChannel） | JS SDK 完成场景加载后，由平台转发 |
| `session.closing`     | 连接即将关闭                      | 断开连接前                 |

> **场景 2A vs 2B：** 平台 ASR（2A）时，平台执行 ASR/VAD 并将 `input.asr.*` / `input.voice.*` **下发给**开发者。开发者 ASR / Omni（2B）时，平台持续转发原始音频 Binary Frame；开发者执行 VAD + ASR 后将同样的事件**回传给**平台（方向相反），以保持平台状态机正常流转。

### 发送给数字人服务的消息（开发者 → 平台）

| Event                         | 说明                     | 发送时机                        |
|-------------------------------|--------------------------|---------------------------------|
| `session.ready`               | 会话已初始化             | 收到 `session.init` 后          |
| `response.start`              | TTS 配置（可选）         | 使用平台 TTS 时，第一个 chunk 前 |
| `response.chunk`              | AI 响应块                | 流式响应                        |
| `response.done`               | 响应完成                 | 所有块发送完毕后                |
| `response.audio.start`        | TTS 输出开始             | 推送 TTS 音频帧前               |
| `response.audio.finish`       | TTS 输出结束             | 推送 TTS 音频帧后               |
| `response.audio.promptStart`  | 空闲提示音频开始         | 推送提示音频帧前                |
| `response.audio.promptFinish` | 空闲提示音频结束         | 推送提示音频帧后                |
| `control.interrupt`           | 中断 Avatar              | 开发者主动发起的打断（输入事件驱动的打断无需发送 — 平台收到 `input.text` 和 `input.voice.start` 时会自动清空 RTC 缓冲区）|
| `system.prompt`               | 空闲提示文本             | 响应 `system.idleTrigger` 时    |
| `error`                       | 发生错误                 | 出错时                          |

## 测试

### 手动测试清单

- [ ] 服务端在 8080 端口成功启动
- [ ] WebSocket 连接建立成功
- [ ] session init/ready 握手正常
- [ ] 文本输入处理正确
- [ ] 流式响应正常工作
- [ ] 中断机制正常工作
- [ ] 音频帧接收并解析正确
- [ ] ASR 结果发送正确
- [ ] 空闲触发处理正确
- [ ] Ping/Pong 心跳正常工作
- [ ] 多并发会话正常工作
- [ ] 断开连接和资源清理正常

### 运行测试

```bash
mvn test
```

## 故障排除

### 端口被占用

如果 8080 端口已被占用：

```yaml
# application.yml
server:
  port: 8081  # 更换为其他端口
```

### WebSocket 连接失败

检查：

1. 服务端正在运行（`mvn spring-boot:run`）
2. URL 正确：`ws://localhost:8080/avatar/ws`
3. 防火墙没有阻止连接

### 找不到 Session 错误

确保在发送其他消息之前先发送 `session.init`。

## 生产环境注意事项

### 安全性

1. **CORS**：在 `WebSocketConfig.java` 中配置允许的来源：

   ```java
   registry.addHandler(handler, "/avatar/ws")
       .setAllowedOrigins("https://facemarket.ai");
   ```

2. **认证**：添加 token 验证（可选）：

   ```java
   @Override
   public void afterConnectionEstablished(WebSocketSession session) {
       String token = session.getHandshakeHeaders().getFirst("Authorization");
       if (!validateToken(token)) {
           session.close(CloseStatus.NOT_ACCEPTABLE);
       }
   }
   ```

### 性能

1. **线程池**：调整 `MessageProcessingService` 中的线程池大小：

   ```java
   executor.setCorePoolSize(50);
   executor.setMaxPoolSize(200);
   ```

2. **音频缓冲区**：实现缓冲区大小限制以防止内存问题

3. **连接限制**：添加每用户/每 IP 的最大连接数限制

### 监控

添加指标和监控：

- WebSocket 连接数
- 活跃会话数
- 消息处理延迟
- ASR 识别成功率
- 错误率

## License

本示例是 Avatar Channel SDK 项目的一部分。

## 支持

如有问题：

- GitHub Issues: <https://github.com/newportAI-lab/liveavatar-channel/issues>
- 文档：参见父项目 README
