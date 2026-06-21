# Live Avatar Channel SDK

[English](./README.md) | **中文**

Live Avatar WebSocket Agent 协议 Java SDK。

## 快速开始

```java
AvatarAgent agent = AvatarAgent.builder()
    .config(AvatarAgentConfig.builder()
        .apiKey("lk_live_...")
        .avatarId("avatar_...")
        .build())
    .listener(new AgentListener() {
        // 语音输入（默认：开发者 ASR）— 接收原始音频，本地跑 ASR+LLM
        public void onAudioFrame(AudioFrame frame) {
            String text = yourAsr.recognize(frame);
            if (text != null) {
                String reply = yourLLM.chat(text);
                agent.sendResponseChunk("req_" + System.currentTimeMillis(), reply, 0);
                agent.sendResponseDone("req_" + System.currentTimeMillis());
            }
        }

        // 文本输入（平台 ASR 模式）— 用户打字或平台转写
        public void onTextInput(String text, String requestId) {
            String reply = yourLLM.chat(text);
            agent.sendResponseChunk(requestId, reply, 0);
            agent.sendResponseDone(requestId);
        }
    })
    .build();

SessionInfo info = agent.start();
// → info.getUserToken() + info.getSfuUrl() 给前端
```

`agent.start()` 一次调用完成 REST 请求、WebSocket 连接和协议握手。

## 特性

- **一行启动** — REST + WS + 握手全在 `agent.start()`
- **简洁回调** — 9 个方法，只需实现 `onTextInput`
- **默认开发者 ASR + 平台 TTS** — 接收原始音频，本地跑 ASR+LLM，回传文本
- **支持开发者自提供 ASR / TTS** — 覆盖全部 4 种模式组合
- **自动重连** — 指数退避策略，默认开启
- **原生心跳** — RFC 6455 ping/pong，OkHttp 自动处理

## Maven

```xml
<dependency>
    <groupId>io.github.newportai-lab</groupId>
    <artifactId>liveavatar-channel-sdk</artifactId>
    <version>1.1.5</version>
</dependency>
```

## 架构

```
AgentListener（你的 AI 逻辑 — onTextInput）
    ↓
AvatarAgent（生命周期 + 17 个发送方法）
    ↓
AvatarWebSocketClient（OkHttp3 传输层，自动握手）
```

## 核心 API

### AvatarAgent

| 方法 | 说明 |
|------|------|
| `start()` | 创建会话、连接 WS、等待握手 → `SessionInfo` |
| `stop()` | 发送 `session.close`，断开连接（可重复调用） |
| `sendResponseChunk(reqId, text, seq)` | 流式发送文本（平台 TTS） |
| `sendResponseDone(reqId)` | 文本回复结束 |
| `sendResponseAudioStart(reqId, resId)` | 开始音频回复（开发者 TTS） |
| `sendAudioFrame(frame)` | 发送音频帧 |
| `sendResponseAudioFinish(reqId, resId)` | 音频回复结束 |
| `sendVoiceStart(reqId)` | 通知平台用户开始说话（开发者 ASR） |
| `sendAsrPartial(reqId, seq, text)` | 流式 ASR 中间结果 |
| `sendAsrFinal(reqId, text)` | ASR 最终结果 |
| `sendInterrupt()` | 打断当前数字人播报 |
| `sendPrompt(text)` | 发送冷场唤醒文本 |
| `sendPromptAudioStart()` / `sendPromptAudioFinish()` | 冷场唤醒音频（开发者 TTS） |
| `isConnected()` / `getSessionInfo()` | 状态查询 |

### AgentListener

| 回调 | 触发时机 |
|------|---------|
| `onTextInput(text, requestId)` | 用户输入文本或平台 ASR 结果 |
| `onSessionInit()` | 握手完成 |
| `onSessionState(state)` | 数字人状态变更 |
| `onIdleTrigger(reason, idleMs)` | 用户闲置 — 可选回复 `sendPrompt()` |
| `onSessionClosing(reason)` | 平台即将关闭会话 |
| `onAudioFrame(frame)` | 原始音频帧（仅开发者 ASR 模式） |
| `onError(message)` | 协议或传输错误 |
| `onClosed(code, reason)` | 连接关闭 |

所有方法有默认空实现 — 只需覆盖你需要的。

### AvatarAgentConfig

```java
AvatarAgentConfig.builder()
    .apiKey("lk_live_...")          // 必填
    .avatarId("avatar_...")         // 必填
    .baseUrl("https://facemarket.ai")  // 默认
    .sandbox(false)                 // true = X-Env-Sandbox 头
    .developerAsr(true)             // true = 开发者自提供 ASR（默认）
    .developerTts(false)            // true = 开发者自提供 TTS
    .reconnectEnabled(true)
    .voiceId("voice_...")           // 可选音色覆盖
    .userId("user_...")             // 可选
    .build();
```

### SessionInfo

| Getter | 说明 |
|--------|------|
| `getSessionId()` | 平台会话标识 |
| `getUserToken()` | 前端 RTC 入会 token |
| `getSfuUrl()` | 前端 LiveKit SFU 地址 |

## 模式组合

| 配置 | 通过 AgentListener 接收 | 通过 AvatarAgent 发送 |
|------|------------------------|----------------------|
| **开发者 ASR + 平台 TTS**（默认） | `onAudioFrame` | `sendVoiceStart`, `sendAsrPartial`, `sendAsrFinal` → `sendResponseChunk`, `sendResponseDone` |
| 平台 ASR + 开发者 TTS | `onTextInput` | `sendResponseAudioStart`, `sendAudioFrame`, `sendResponseAudioFinish` |
| 平台 ASR + 平台 TTS | `onTextInput` | `sendResponseChunk`, `sendResponseDone` |
| 开发者 ASR + 开发者 TTS | `onAudioFrame` | ASR 事件 → `sendResponseAudioStart`, `sendAudioFrame`, `sendResponseAudioFinish` |

## 服务端示例

[`liveavatar-channel-server-example/`](./liveavatar-channel-server-example/) 中的 Spring Boot 参考应用。

```bash
cd liveavatar-channel-server-example
mvn spring-boot:run
```

### REST API

```bash
# 创建会话
curl -X POST http://localhost:8080/api/session/start \
  -H "Content-Type: application/json" \
  -d '{"avatarId": "avatar_xxx"}'
# → {"sessionId":"...", "userToken":"...", "sfuUrl":"...", "agentWsUrl":"..."}

# 结束会话
curl -X POST http://localhost:8080/api/session/stop \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "sess_xxx"}'
```

### 配置（`application.yml`）

| 键 | 默认值 | 说明 |
|-----|-------|------|
| `avatar.api.key` |（示例）| 控制台 API Key |
| `avatar.id` |（示例）| 默认头像 ID |
| `avatar.api.base-url` | `https://facemarket.ai` | 平台调度器 URL |
| `avatar.sandbox.enabled` | `false` | 路由到沙箱（30 分钟/月免费） |
| `avatar.asr.developer-enabled` | `true` | `false` = 平台 ASR，`true` = 开发者 ASR（默认） |
| `avatar.tts.developer-enabled` | `false` | `true` = 开发者自提供 TTS |

### 自定义

编辑 `DemoAgentService.onTextInput()` — 替换 echo AI：

```java
@Override
public void onTextInput(String text, String requestId) {
    String reply = yourLLM.chat(text);
    agent.sendResponseChunk(requestId, reply, 0);
    agent.sendResponseDone(requestId);
}
```

## 构建

```bash
mvn clean install -DskipTests -Dgpg.skip=true
```

## 协议

完整 WebSocket 协议定义见 [PROTOCOL.zh.md](./PROTOCOL.zh.md)。

## License

[LICENSE](./LICENSE)
