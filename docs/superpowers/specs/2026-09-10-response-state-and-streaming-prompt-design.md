# Response State and Streaming System Prompt Design

## Scope

Upgrade the WebSocket Agent Java SDK to consume the dispatcher response
lifecycle protocol and to send streaming system prompts. Preserve the current
Java 8 baseline and all existing one-shot behavior. Upgrade the SDK artifact
from `1.2.0` to `1.3.0` and update the server example dependency accordingly.

This change does not infer response completion from `session.state`, add a
`PLAYING` state, add lifecycle replay, or change provider content generation.

## Protocol Model

Add constants for `response.state`, `system.prompt.start`,
`system.prompt.chunk`, and `system.prompt.done`.

Expose a response lifecycle event containing:

- `sessionId`
- `requestId`
- `responseId`
- `seq`
- `timestamp`
- `state`
- `reason`

State and reason remain raw strings rather than closed Java enums. This lets
new dispatcher values reach applications without breaking deserialization or
the WebSocket receive loop. Unknown JSON fields continue to be ignored by the
normal Jackson mapping behavior.

## Incoming Lifecycle Processing

`AgentListener` gains a default `onResponseState(ResponseStateEvent event)`
callback. Adding a default method preserves existing listener implementations.

`AvatarAgent` intercepts lifecycle events before forwarding them to the
application listener:

1. Validate the fields needed for correlation.
2. Under an internal lock, discard an event when its `seq` is less than or
   equal to the largest lifecycle sequence already seen for that session.
3. Find a locally registered response by `(sessionId, responseId)` and verify
   that its `requestId` matches.
4. Apply the state to that response. The first `REJECTED` or `FINISHED` event
   is terminal and cannot be overwritten.
5. Release the lock, complete any waiter, and invoke the application callback.

An event with no matching local response is still delivered to
`onResponseState`. An identity mismatch does not complete the local response,
but the raw event is still delivered. Lifecycle sequence numbers need only be
monotonic; gaps are accepted because reconnect has no replay guarantee.

Per-session deduplication state is cleared when the agent session stops.

## Local Response Tracking

Responses created by the SDK are registered before their establishing start
message is sent. Registration uses the active session ID and response ID as
the identity key.

`ResponseStream` exposes its most recent platform lifecycle event and a
`CompletableFuture<ResponseStateEvent>` for the first terminal event. Its
existing `done()` and `cancel()` methods continue to close only the local send
stream; they do not manufacture or complete a platform terminal event.

If sending an establishing start message fails, its new local registration is
removed. A successfully established response remains observable after local
`done()` until the first platform terminal state arrives or the session stops.
Stopping the session completes unresolved lifecycle futures exceptionally.

## Streaming System Prompt API

Add these entry points:

```java
PromptStream beginPrompt(String requestId);
PromptStream beginPrompt(String requestId, String responseId);
```

Construction performs these operations in order:

1. Reject null or empty IDs locally.
2. Generate a UUID response ID when the caller did not provide one.
3. Register the pending lifecycle observer.
4. Send `system.prompt.start` with the stable request and response IDs.
5. Return the stream.

`PromptStream.sendChunk(String)` sends `system.prompt.chunk` immediately; it
does not wait for `ACCEPTED`. A synchronized critical section assigns `seq`
values that are unique and ordered from 1 even when callers send concurrently. The
timestamp is generated when each chunk message is built.

`PromptStream.done()` sends `system.prompt.done` once and closes the local send
side. A repeated `done`, or a chunk after `done`, throws a deterministic
`IllegalStateException`. Failed sends do not advance the sequence or close the
stream, so the caller may retry the same operation.

The existing `sendPrompt(String)` continues to send the legacy one-shot
`system.prompt` message exactly as before.

## Message Routing

`AvatarWebSocketClient` parses `response.state` without interpreting unknown
state or reason strings and forwards it through `AgentListener`. Existing
unknown event handling remains unchanged. Application callbacks and future
completion always occur outside SDK synchronization to permit callback
re-entry without deadlock.

## Documentation

Update `PROTOCOL.md` and `PROTOCOL.zh.md` with:

- streaming prompt message examples and field constraints;
- response lifecycle fields, states, terminal reasons, and identity rules;
- `(sessionId, seq)` online deduplication and first-terminal-wins semantics;
- the distinction between `system.prompt.done`, `session.state=IDLE`, and
  `FINISHED/COMPLETED`;
- compatibility behavior for legacy `system.prompt`.

Update `README.md` and `README.zh.md` with the new callback and prompt stream
API, plus migration guidance that business completion must be tied to the
target response's `FINISHED/COMPLETED` event.

## Testing

Implementation follows test-driven development. Tests will first fail for and
then cover:

- complete lifecycle parsing and callback delivery;
- tolerance of unknown state, reason, and additional fields;
- duplicate and decreasing session sequence suppression;
- acceptance of sequence gaps;
- request identity mismatch isolation;
- delivery of events for unknown local response IDs;
- first-terminal-wins under repeated or conflicting terminal events;
- local registration before prompt start is sent;
- stable prompt IDs and chunk sequence values beginning at 1;
- concurrent prompt sends without duplicate sequence values;
- immediate chunks before `ACCEPTED`;
- retry behavior after a failed send;
- repeated `done` and chunk-after-done rejection;
- `done` and delayed `IDLE` not completing lifecycle waiters;
- preservation of the legacy one-shot prompt payload;
- artifact and example dependency version consistency.

After focused tests pass, run the complete Maven test suite with GPG signing
disabled and build all modules.
