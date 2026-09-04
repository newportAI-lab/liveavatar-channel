# Response Stream Lifecycle Design

## Problem

`AvatarAgent.sendResponseStart`, `sendResponseChunk`, and
`sendResponseDone` currently generate a new `responseId` for every call. The
dispatcher treats those messages as different responses, so later chunks and
the terminal `response.done` are rejected instead of completing one streamed
response.

The public API also hides `responseId`, provides no response lifecycle object,
and does not prevent a caller from starting another response before the active
one is complete. The README examples reinforce the problem by generating the
`requestId` separately for `response.chunk` and `response.done`.

## Goals

- Use one `responseId` for `response.start`, every `response.chunk`, and
  `response.done` or `response.cancel` in a single response.
- Make the safe lifecycle the preferred public API.
- Preserve source compatibility for the existing text-response methods.
- Reject invalid lifecycle transitions locally instead of sending messages the
  dispatcher will reject.
- Document that `control.interrupt` is explicit and is not part of normal
  response turnover.

## Non-goals

- Changing the wire protocol.
- Automatically interrupting playback when a new response is requested.
- Supporting simultaneous text responses for the same `requestId` through the
  deprecated compatibility API.
- Refactoring developer-TTS audio response APIs in this change.

## Preferred API

`AvatarAgent.beginResponse(requestId)` returns a `ResponseStream`. Creation
allocates one response ID, which the stream owns until it reaches a terminal
state.

```java
ResponseStream response = agent.beginResponse(requestId);
response.sendChunk("First part");
response.sendChunk("Second part");
response.done();
```

`ResponseStream` exposes:

- `getRequestId()` and `getResponseId()` for correlation and diagnostics.
- An optional `start(AudioConfigData)` call before the first chunk.
- `sendChunk(String)`, which assigns monotonically increasing sequence numbers
  beginning at zero.
- `done()`, which sends `response.done` with the stream's request and response
  IDs.
- `cancel()`, which sends `response.cancel` and closes the stream.

The stream is thread-safe. Exactly one terminal operation can succeed. Calls to
`start`, `sendChunk`, `done`, or `cancel` after termination throw
`IllegalStateException`. `start` after a chunk and repeated `start` calls are
also rejected.

## Backward-compatible API

The existing methods remain available and are marked deprecated:

```java
sendResponseStart(requestId, audioConfig);
sendResponseChunk(requestId, text, seq);
sendResponseDone(requestId);
```

`AvatarAgent` maintains one compatibility response per `requestId`:

- `sendResponseStart` creates it and sends `response.start`.
- A first `sendResponseChunk` without a preceding start creates it lazily.
- Later chunks reuse its `responseId`.
- `sendResponseDone` requires an active response, reuses its IDs, and removes
  it after the message is sent successfully.
- Starting an additional compatibility response for the same `requestId`
  before `done` is rejected.
- Calling `sendResponseDone` without an active response is rejected.

The compatibility registry uses atomic map operations so concurrent callers
cannot create two response IDs for one active request. Entries are removed only
after a successful terminal send; a transport failure therefore remains
visible and retryable rather than silently losing lifecycle state.

The existing `sendResponseCancel(responseId)` method remains for source
compatibility. New code should prefer `ResponseStream.cancel()` because the
stream can close its own lifecycle state reliably.

## Turn and Interrupt Semantics

The SDK will not automatically send `control.interrupt`. A new user input
boundary already causes the platform to clear its playback buffer according to
the protocol. Applications should cancel their internal LLM/TTS work and close
or cancel the active response as appropriate.

`sendInterrupt()` remains an explicit operation for genuine programmatic
interruptions such as a timeout or business override. Documentation will warn
against calling it unconditionally before every response.

The new API rejects a second active response for the same `requestId`. It does
not globally serialize unrelated request IDs because applications may need to
receive overlapping work and decide explicitly whether to finish, cancel, or
interrupt it.

## Documentation Changes

The English and Chinese README quick starts will use one callback-provided or
locally stored `requestId` and the `ResponseStream` API. The API tables will
recommend `beginResponse` and label the legacy methods deprecated.

The protocol documents will state explicitly:

- One logical response has one stable `responseId`.
- The optional start, all chunks, and `response.done` carry the same
  `requestId` and `responseId`; `response.cancel` identifies the same stream by
  its `responseId`.
- `requestId -> responseId = 1:N` means multiple distinct, sequential
  responses may belong to one request; it does not mean one response ID per
  chunk.
- A response must end with `response.done` or `response.cancel` before another
  response for the same request starts.
- `control.interrupt` is sent only when an actual interruption is required.

## Tests

Tests will cover the observable messages sent by both APIs:

1. A new stream uses the same request and response IDs for start, multiple
   chunks, and done.
2. `sendChunk` assigns sequential sequence numbers.
3. A stream rejects sends and repeated terminal calls after completion.
4. The deprecated methods reuse one response ID across multiple chunks and
   done.
5. Deprecated `done` without an active response is rejected.
6. A second deprecated start for an active request is rejected.
7. A completed compatibility response is removed so a later response for the
   same request receives a new response ID.
8. Concurrent creation for one request produces only one active compatibility
   response.

Implementation will follow test-driven development: each behavior is first
captured by a failing test, then the minimum production change is added.
