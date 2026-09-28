package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.ResponseStateEvent;
import com.newportai.liveavatar.channel.util.MessageBuilder;

import java.util.concurrent.CompletableFuture;

/** One streamed system prompt with stable request and response identities. */
public final class PromptStream {
    private final String requestId;
    private final String responseId;
    private final ResponseStream.MessageSender sender;
    private final ResponseLifecycle lifecycle;
    private int nextSeq = 1;
    private boolean done;

    PromptStream(String requestId, String responseId, ResponseStream.MessageSender sender,
                 ResponseLifecycle lifecycle) {
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (responseId == null || responseId.isEmpty()) {
            throw new IllegalArgumentException("responseId is required");
        }
        this.requestId = requestId;
        this.responseId = responseId;
        this.sender = sender;
        this.lifecycle = lifecycle;
    }

    public String getRequestId() { return requestId; }
    public String getResponseId() { return responseId; }
    public ResponseStateEvent getLatestState() { return lifecycle.getLatestState(); }
    public CompletableFuture<ResponseStateEvent> getTerminalFuture() {
        return lifecycle.getTerminalFuture();
    }

    public synchronized void sendChunk(String text)
            throws ConnectionException, MessageSerializationException {
        requireOpen();
        if (text == null) throw new IllegalArgumentException("text is required");
        sender.send(MessageBuilder.systemPromptChunk(
                requestId, responseId, nextSeq, System.currentTimeMillis(), text));
        nextSeq++;
    }

    public synchronized void done()
            throws ConnectionException, MessageSerializationException {
        requireOpen();
        sender.send(MessageBuilder.systemPromptDone(requestId, responseId));
        done = true;
    }

    private void requireOpen() {
        if (done) throw new IllegalStateException("Prompt stream is already done");
    }
}
