# Response State and Streaming System Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add observable response lifecycle handling and a thread-safe streaming system-prompt API while preserving the legacy prompt API and upgrading the Java SDK to 1.3.0.

**Architecture:** The WebSocket client converts wire messages into an open-valued lifecycle model and forwards them through the listener. `AvatarAgent` owns session-level deduplication and pending-response correlation; response and prompt stream objects own send-side sequencing and expose a future for the first platform terminal event.

**Tech Stack:** Java 8, Maven, Jackson 2.15, OkHttp 4.11, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-10-response-state-and-streaming-prompt-design.md`

## Global Constraints

- Keep Java source and target compatibility at Java 8.
- Preserve the legacy `sendPrompt(String)` wire payload.
- Treat only the first `REJECTED` or `FINISHED` event as terminal.
- Do not infer lifecycle completion from `session.state=IDLE`, local `done()`, audio finish, or connection closure.
- Accept lifecycle sequence gaps and unknown state/reason values.
- Upgrade the SDK artifact and documented dependency version from `1.2.0` to `1.3.0`.

---

### Task 1: Wire Protocol Model and Dispatch

**Files:**
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/model/ResponseStateData.java`
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/model/ResponseStateEvent.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/model/EventType.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/AgentListener.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/client/AvatarWebSocketClient.java`
- Test: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/client/AvatarWebSocketClientTest.java`

**Interfaces:**
- Produces: `ResponseStateEvent(String sessionId, String requestId, String responseId, long seq, long timestamp, String state, String reason)` and getters.
- Produces: `AgentListener.onResponseState(ResponseStateEvent event)`.

- [ ] **Step 1: Write failing dispatch tests**

Add tests that invoke `handleMessage` with literal JSON for a complete
`response.state`, assert every field delivered to `onResponseState`, then send
unknown `state`, `reason`, and an extra JSON field and assert delivery still
succeeds.

- [ ] **Step 2: Verify the tests fail for the missing API**

Run: `mvn -q -pl liveavatar-channel-sdk -Dtest=AvatarWebSocketClientTest test`

Expected: test compilation fails because `ResponseStateEvent`, the event
constant, and callback do not exist.

- [ ] **Step 3: Implement the minimal open-valued model and route**

Create `ResponseStateData` with nullable string `state` and `reason`. Create
the immutable `ResponseStateEvent` envelope with the seven protocol fields.
Add `RESPONSE_STATE` to `EventType`, add the default listener callback, and in
`handleMessage` convert `data` then forward the envelope without enum parsing.

- [ ] **Step 4: Verify focused tests pass**

Run: `mvn -q -pl liveavatar-channel-sdk -Dtest=AvatarWebSocketClientTest test`

Expected: PASS.

### Task 2: Lifecycle Correlation and First-Terminal-Wins

**Files:**
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/ResponseLifecycle.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/ResponseStream.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/AvatarAgent.java`
- Test: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/AvatarAgentResponseTest.java`

**Interfaces:**
- Produces: `ResponseLifecycle.getLatestState()` and `getTerminalFuture()` returning `CompletableFuture<ResponseStateEvent>`.
- Produces on `ResponseStream`: `getLatestState()` and `getTerminalFuture()` delegating to its lifecycle.
- Consumes: `AgentListener.onResponseState(ResponseStateEvent event)` from Task 1.

- [ ] **Step 1: Write failing lifecycle behavior tests**

Extend the agent fixture so an incoming lifecycle callback can be injected.
Test duplicate and decreasing `seq` suppression, accepted sequence gaps,
unknown response delivery, request mismatch isolation, `ACCEPTED` state update,
and conflicting terminal events completing the future only with the first
terminal value. Assert `response.done()` and a later `onSessionState(IDLE)` do
not complete the future.

- [ ] **Step 2: Verify RED**

Run: `mvn -q -pl liveavatar-channel-sdk -Dtest=AvatarAgentResponseTest test`

Expected: compilation or assertion failure because lifecycle correlation is absent.

- [ ] **Step 3: Implement lifecycle tracking and listener interception**

Implement `ResponseLifecycle` with synchronized state application and future
completion performed after leaving its synchronized section. Key pending
responses by `sessionId + responseId`; track the highest sequence per session.
Update `LatchedListener` to route lifecycle messages into `AvatarAgent`, and
always invoke the application listener outside internal locks. Register
ordinary `ResponseStream` instances for observation without changing their
existing send-side lifecycle or request-level overlap rules.

- [ ] **Step 4: Verify GREEN**

Run: `mvn -q -pl liveavatar-channel-sdk -Dtest=AvatarAgentResponseTest test`

Expected: PASS.

### Task 3: Streaming System Prompt

**Files:**
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/PromptStream.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/AvatarAgent.java`
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/util/MessageBuilder.java`
- Test: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/util/MessageBuilderTest.java`
- Test: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/PromptStreamTest.java`
- Test: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/AvatarAgentResponseTest.java`

**Interfaces:**
- Produces: `MessageBuilder.systemPromptStart(requestId, responseId)`, `systemPromptChunk(requestId, responseId, seq, timestamp, text)`, and `systemPromptDone(requestId, responseId)`.
- Produces: `AvatarAgent.beginPrompt(String requestId)` and `beginPrompt(String requestId, String responseId)`.
- Produces: `PromptStream.sendChunk(String)`, `done()`, identity getters, `getLatestState()`, and `getTerminalFuture()`.

- [ ] **Step 1: Write failing builder and stream tests**

Assert literal serialized start/chunk/done fields, local rejection of empty IDs,
stable IDs, chunk sequences `1, 2`, concurrent uniqueness, immediate chunks
without `ACCEPTED`, retry of the same sequence after send failure, repeated
`done` rejection, chunk-after-done rejection, and unchanged legacy
`system.prompt` serialization.

- [ ] **Step 2: Verify RED**

Run: `mvn -q -pl liveavatar-channel-sdk -Dtest=MessageBuilderTest,PromptStreamTest,AvatarAgentResponseTest test`

Expected: compilation failure because the streaming prompt interfaces are absent.

- [ ] **Step 3: Implement builders and PromptStream**

Build chunk messages with IDs, an integer sequence, timestamp, and `TextData`.
Synchronize each send operation so a sequence advances only after a successful
send and `done` becomes terminal only after a successful send. In
`beginPrompt`, validate IDs, register lifecycle state before sending start,
remove registration if start fails, and default response IDs to UUID-derived
values. Do not wait for `ACCEPTED`.

- [ ] **Step 4: Verify GREEN**

Run: `mvn -q -pl liveavatar-channel-sdk -Dtest=MessageBuilderTest,PromptStreamTest,AvatarAgentResponseTest test`

Expected: PASS.

### Task 4: Documentation and Version Upgrade

**Files:**
- Modify: `liveavatar-channel-sdk/pom.xml`
- Modify: `liveavatar-channel-server-example/pom.xml`
- Modify: `README.md`
- Modify: `README.zh.md`
- Modify: `PROTOCOL.md`
- Modify: `PROTOCOL.zh.md`

**Interfaces:**
- Consumes: public API and wire shapes finalized in Tasks 1-3.

- [ ] **Step 1: Update version references**

Change the SDK artifact, example dependency, and README dependency snippets
from `1.2.0` to `1.3.0`.

- [ ] **Step 2: Document lifecycle and prompt streaming**

Add exact JSON examples, field requirements, terminal states and reasons,
online deduplication, identity matching, first-terminal-wins, legacy prompt
compatibility, and Java examples that advance work only for the target
`FINISHED/COMPLETED` response.

- [ ] **Step 3: Check documentation consistency**

Run: `rg -n "1\.2\.0|system\.prompt\.(start|chunk|done)|response\.state|FINISHED|COMPLETED" README.md README.zh.md PROTOCOL.md PROTOCOL.zh.md liveavatar-channel-sdk/pom.xml liveavatar-channel-server-example/pom.xml`

Expected: no `1.2.0` remains in the listed files; both languages name the new
events and completion rule.

### Task 5: Full Verification

**Files:**
- Verify all files changed by Tasks 1-4.

**Interfaces:**
- Consumes: the complete SDK upgrade.

- [ ] **Step 1: Run the complete test suite**

Run: `mvn test -Dgpg.skip=true`

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 2: Build and install all modules**

Run: `mvn clean install -DskipTests -Dgpg.skip=true`

Expected: BUILD SUCCESS for parent, SDK `1.3.0`, and server example.

- [ ] **Step 3: Inspect the final diff**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors and only files required by this plan are changed.
