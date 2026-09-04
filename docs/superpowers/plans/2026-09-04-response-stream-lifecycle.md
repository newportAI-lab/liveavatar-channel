# Response Stream Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every streamed text response use one stable `responseId` through its terminal message while preserving safe compatibility with the existing `AvatarAgent` methods.

**Architecture:** Add a focused, thread-safe `ResponseStream` lifecycle object that owns request ID, response ID, sequence number, and terminal state. `AvatarAgent` keeps one active stream per request ID, routes both the new and deprecated APIs through it, and removes a stream only after a successful terminal send.

**Tech Stack:** Java 8, Maven, JUnit 4.13.2, OkHttp WebSocket transport

**Spec:** `docs/superpowers/specs/2026-09-04-response-stream-lifecycle-design.md`

## Global Constraints

- Keep Java source and target compatibility at Java 8.
- Do not change the wire protocol or automatically send `control.interrupt`.
- Preserve source compatibility for `sendResponseStart`, `sendResponseChunk`, and `sendResponseDone`.
- Allow at most one active text response per `requestId`.
- Remove active-response state only after a successful `done` or `cancel` send.
- Make only changes required for response lifecycle correctness and its documentation.

## File Structure

- Create `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/ResponseStream.java`: owns one response's stable identifiers, sequence, transition validation, and message sends.
- Create `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/ResponseStreamTest.java`: unit tests the stream independently through a recording sender.
- Create `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/AvatarAgentResponseTest.java`: verifies new API registration and deprecated API compatibility against observable WebSocket messages.
- Modify `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/AvatarAgent.java`: creates and registers streams, delegates deprecated methods, and updates Javadoc.
- Modify `README.md` and `README.zh.md`: replace unsafe examples and recommend the lifecycle API.
- Modify `PROTOCOL.md` and `PROTOCOL.zh.md`: state stable-ID, terminal-message, turn-order, and interrupt requirements explicitly.
- Modify `liveavatar-channel-server-example/src/main/java/com/newportai/liveavatar/channel/server/service/DemoAgentService.java`: update the commented integration example.

---

### Task 1: Thread-safe `ResponseStream`

**Files:**
- Create: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/ResponseStream.java`
- Create: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/ResponseStreamTest.java`

**Interfaces:**
- Consumes: `MessageBuilder.responseStart(String, String, AudioConfigData)`, `responseChunk(String, String, int, String)`, `responseDone(String, String)`, and `responseCancel(String)`.
- Produces: package-private `ResponseStream(String requestId, String responseId, MessageSender sender, Runnable onTerminal)`, public getters, `start(AudioConfigData)`, `sendChunk(String)`, `done()`, and `cancel()`; package-private `sendChunk(String, int)` for deprecated API delegation.

- [ ] **Step 1: Write the failing identity and sequencing tests**

Create a recording `MessageSender` in `ResponseStreamTest` and add tests equivalent to:

```java
@Test
public void usesStableIdsAndIncrementingSequence() throws Exception {
    RecordingSender sender = new RecordingSender();
    ResponseStream stream = new ResponseStream("req_1", "res_1", sender, () -> {});

    stream.start(new AudioConfigData());
    stream.sendChunk("one");
    stream.sendChunk("two");
    stream.done();

    assertEquals(Arrays.asList(
            EventType.RESPONSE_START,
            EventType.RESPONSE_CHUNK,
            EventType.RESPONSE_CHUNK,
            EventType.RESPONSE_DONE), sender.events());
    for (Message message : sender.messages) {
        assertEquals("req_1", message.getRequestId());
        assertEquals("res_1", message.getResponseId());
    }
    assertEquals(Integer.valueOf(0), sender.messages.get(1).getSeq());
    assertEquals(Integer.valueOf(1), sender.messages.get(2).getSeq());
}
```

Define `ResponseStream.MessageSender` to declare:

```java
void send(Message message)
        throws ConnectionException, MessageSerializationException;
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=ResponseStreamTest test
```

Expected: compilation fails because `ResponseStream` and `MessageSender` do not exist.

- [ ] **Step 3: Implement stable identity and successful transitions**

Create `ResponseStream` as a public final class. Store final `requestId`, final `responseId`, `MessageSender`, and `onTerminal`; protect mutable `started`, `chunkSent`, `terminal`, and `nextSeq` state with synchronized public methods. Generate no IDs inside this class. Send a message before committing the corresponding state change so a checked transport exception leaves the operation retryable.

Use these signatures:

```java
public String getRequestId();
public String getResponseId();
public synchronized void start(AudioConfigData audioConfig)
        throws ConnectionException, MessageSerializationException;
public synchronized void sendChunk(String text)
        throws ConnectionException, MessageSerializationException;
synchronized void sendChunk(String text, int seq)
        throws ConnectionException, MessageSerializationException;
public synchronized void done()
        throws ConnectionException, MessageSerializationException;
public synchronized void cancel()
        throws ConnectionException, MessageSerializationException;
```

After a successful `done` or `cancel`, set `terminal = true` and invoke `onTerminal.run()` while still holding the stream lock.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=ResponseStreamTest test
```

Expected: PASS.

- [ ] **Step 5: Add failing transition and retry tests**

Add separate tests asserting:

```java
assertThrows(IllegalStateException.class, () -> stream.start(config)); // repeated start
assertThrows(IllegalStateException.class, () -> stream.start(config)); // after a chunk
assertThrows(IllegalStateException.class, () -> stream.sendChunk("late")); // after done
assertThrows(IllegalStateException.class, stream::done); // repeated terminal
```

Add a sender that throws on its first `response.done`, then succeeds. Assert the first `done()` throws, a retry sends `response.done` again, the terminal callback runs exactly once, and later sends are rejected.

- [ ] **Step 6: Run tests and verify the new transition tests fail**

Run the same focused Maven command. Expected: FAIL until transition guards and send-before-state behavior are complete.

- [ ] **Step 7: Add minimal transition validation**

Use private guards with exact failure conditions:

```java
private void requireOpen() {
    if (terminal) throw new IllegalStateException("Response is already complete");
}

private void requireStartAllowed() {
    requireOpen();
    if (started || chunkSent) {
        throw new IllegalStateException("response.start must precede all chunks");
    }
}
```

Validate non-null/non-empty `requestId` and `responseId` in the constructor and non-null text in `sendChunk` using `IllegalArgumentException`.

- [ ] **Step 8: Run focused and module tests**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=ResponseStreamTest test
mvn -q -pl liveavatar-channel-sdk test
```

Expected: both commands PASS.

- [ ] **Step 9: Commit Task 1**

```bash
git add liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/ResponseStream.java liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/ResponseStreamTest.java
git commit -m "feat: add response stream lifecycle"
```

---

### Task 2: Integrate streams into `AvatarAgent` and preserve old calls

**Files:**
- Modify: `liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/AvatarAgent.java:35-170`
- Create: `liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/AvatarAgentResponseTest.java`

**Interfaces:**
- Consumes: `ResponseStream` constructor and operations from Task 1.
- Produces: `public ResponseStream beginResponse(String requestId)`; deprecated methods retain their existing signatures and checked exceptions.

- [ ] **Step 1: Write failing new-API registration tests**

Build a test fixture with an `AvatarAgent`, an `AvatarWebSocketClient`, and a fake OkHttp `WebSocket` that records sent JSON. Install the started state using a small package-private test factory in `AvatarAgent` rather than reflection:

```java
static AvatarAgent createStartedForTest(AvatarWebSocketClient wsClient) {
    AvatarAgent agent = new AvatarAgent(
            AvatarAgentConfig.builder().apiKey("test").avatarId("test").build(),
            new AgentListener() {}, new OkHttpClient());
    agent.state.set(new State(new SessionInfo("session", "", "", "ws://test", ""), wsClient));
    return agent;
}
```

Test that `beginResponse("req_1")` followed by two chunks and done emits the same non-empty response ID in all three messages. Test that a second `beginResponse("req_1")` before termination throws and that beginning again after done succeeds with a different response ID.

- [ ] **Step 2: Run focused test and verify RED**

Run:

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=AvatarAgentResponseTest test
```

Expected: compilation fails because `beginResponse` and the test factory do not exist.

- [ ] **Step 3: Add active stream registration**

Add:

```java
private final ConcurrentMap<String, ResponseStream> activeResponses =
        new ConcurrentHashMap<>();
private final AtomicLong responseSequence = new AtomicLong();
```

Generate IDs without clock collisions:

```java
private String nextResponseId() {
    return "res_" + responseSequence.incrementAndGet();
}
```

Implement `beginResponse` with `putIfAbsent`. The terminal callback removes only the same stream instance:

```java
activeResponses.remove(requestId, stream);
```

Because the callback needs the constructed stream, use a one-element holder local to `beginResponse` and assign it before registration. Pass `message -> requireStarted().wsClient.sendMessage(message)` as the sender.

- [ ] **Step 4: Run focused test and verify GREEN**

Run the focused Maven command. Expected: PASS.

- [ ] **Step 5: Write failing deprecated-API tests**

Add tests that call the old methods exactly as callers do:

```java
agent.sendResponseChunk("req_legacy", "one", 0);
agent.sendResponseChunk("req_legacy", "two", 1);
agent.sendResponseDone("req_legacy");
```

Assert all messages have one response ID. Add separate tests for start + chunks + done, done without an active response, repeated start, and a later response for the same request receiving a different ID.

- [ ] **Step 6: Run focused test and verify RED**

Expected: identity assertions fail because the old methods still generate IDs per call, and invalid transitions do not throw.

- [ ] **Step 7: Delegate deprecated methods through registered streams**

Annotate all three old methods with `@Deprecated` and Javadoc pointing to `beginResponse`.

Implement behavior exactly as follows:

```java
sendResponseStart: beginResponse(requestId).start(audioConfig)
sendResponseChunk: get active stream or atomically create one, then stream.sendChunk(text, seq)
sendResponseDone: require an active stream, then stream.done()
```

Do not catch `IllegalStateException`. Do not remove entries directly from these methods; successful stream termination owns cleanup.

- [ ] **Step 8: Add and verify the concurrent creation test**

Use two threads, a `CountDownLatch` start gate, and the same request ID. Both call the first deprecated chunk with distinct sequence numbers. Assert both emitted chunks share one response ID and only one active stream existed; then call done. Run the focused test repeatedly enough to exercise the race:

```bash
for i in 1 2 3 4 5; do mvn -q -pl liveavatar-channel-sdk -Dtest=AvatarAgentResponseTest test || exit 1; done
```

Expected: all five runs PASS.

- [ ] **Step 9: Update class-level Javadoc and run module tests**

Replace the old quick start with:

```java
ResponseStream response = agent.beginResponse(requestId);
response.sendChunk(callYourAI(text));
response.done();
```

Run:

```bash
mvn -q -pl liveavatar-channel-sdk test
```

Expected: PASS.

- [ ] **Step 10: Commit Task 2**

```bash
git add liveavatar-channel-sdk/src/main/java/com/newportai/liveavatar/channel/agent/AvatarAgent.java liveavatar-channel-sdk/src/test/java/com/newportai/liveavatar/channel/agent/AvatarAgentResponseTest.java
git commit -m "fix: preserve response identity across chunks"
```

---

### Task 3: Correct public documentation and examples

**Files:**
- Modify: `README.md:12-32,74-94,210-224`
- Modify: `README.zh.md:12-32,74-94,208-222`
- Modify: `PROTOCOL.md:251-332,364-395`
- Modify: `PROTOCOL.zh.md:245-326,358-389`
- Modify: `liveavatar-channel-server-example/src/main/java/com/newportai/liveavatar/channel/server/service/DemoAgentService.java:99-106`

**Interfaces:**
- Consumes: `AvatarAgent.beginResponse(String)` and `ResponseStream` from Tasks 1-2.
- Produces: copy-paste-safe English and Chinese usage guidance.

- [ ] **Step 1: Replace quick-start examples**

In both READMEs, import/use `ResponseStream` and store one request ID per turn. For developer ASR, generate the request ID once:

```java
String requestId = "req_" + UUID.randomUUID();
ResponseStream response = agent.beginResponse(requestId);
response.sendChunk(yourLLM.chat(text));
response.done();
```

For `onTextInput`, use the callback's request ID without modification.

- [ ] **Step 2: Update API tables and server example**

Document `beginResponse(reqId)` as the preferred text-output API and group the three old methods under a deprecated compatibility entry. Change the server example comment to use a `ResponseStream` and `done()`.

- [ ] **Step 3: Add explicit protocol invariants**

Immediately after the text `response.done` example in both protocol documents, add equivalent normative language stating:

```text
One logical response MUST keep the same requestId and responseId across its
optional response.start, every response.chunk, and its terminal response.done.
response.cancel identifies that same stream by responseId. seq increases within
that response. The 1:N mapping means one request may have multiple distinct
responses over time; it never means one response per chunk. Finish or cancel
the active response before starting the next response for that request.
```

In the interrupt section, add that applications must not send
`control.interrupt` unconditionally before the next response; it is reserved
for an actual programmatic interruption.

- [ ] **Step 4: Check documentation consistency**

Run:

```bash
rg -n 'sendResponseChunk\("req_" \+ System\.currentTimeMillis|sendResponseDone\("req_" \+ System\.currentTimeMillis' README.md README.zh.md
rg -n 'beginResponse|same.*responseId|同一个.*responseId|control\.interrupt' README.md README.zh.md PROTOCOL.md PROTOCOL.zh.md
git diff --check
```

Expected: the first command returns no matches; the second shows the new API and invariants in both languages; `git diff --check` exits 0.

- [ ] **Step 5: Compile Javadoc and run all project tests**

Run:

```bash
mvn -q test -Dgpg.skip=true
mvn -q -pl liveavatar-channel-sdk package -DskipTests -Dgpg.skip=true
```

Expected: both commands exit 0, including Java compilation and Javadoc-related source validation.

- [ ] **Step 6: Commit Task 3**

```bash
git add README.md README.zh.md PROTOCOL.md PROTOCOL.zh.md liveavatar-channel-server-example/src/main/java/com/newportai/liveavatar/channel/server/service/DemoAgentService.java
git commit -m "docs: clarify response stream lifecycle"
```

---

### Task 4: Final lifecycle verification

**Files:**
- Verify only; modify Task 1-3 files only if a verification failure exposes a requirement gap.

**Interfaces:**
- Consumes: all implementation and documentation from Tasks 1-3.
- Produces: fresh evidence that the implementation meets every design requirement.

- [ ] **Step 1: Run targeted regression tests**

```bash
mvn -q -pl liveavatar-channel-sdk -Dtest=ResponseStreamTest,AvatarAgentResponseTest test
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 2: Run the full test suite**

```bash
mvn -q test -Dgpg.skip=true
```

Expected: all modules PASS.

- [ ] **Step 3: Build distributable artifacts**

```bash
mvn -q clean install -DskipTests -Dgpg.skip=true
```

Expected: reactor build exits 0 and installs both modules.

- [ ] **Step 4: Audit the final diff against the spec**

```bash
git diff HEAD~3 --check
git diff HEAD~3 --stat
git status --short
```

Confirm stable IDs, terminal cleanup, deprecated compatibility, no automatic interrupt, bilingual documentation, and no unrelated file changes. The working tree must be clean.
