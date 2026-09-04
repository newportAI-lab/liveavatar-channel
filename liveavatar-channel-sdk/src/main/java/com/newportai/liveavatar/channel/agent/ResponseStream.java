package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.AudioConfigData;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.util.MessageBuilder;

/** A single streamed text response with a stable request and response identity. */
public final class ResponseStream {

    interface MessageSender {
        void send(Message message) throws ConnectionException, MessageSerializationException;
    }

    private final String requestId;
    private final String responseId;
    private final MessageSender sender;
    private final Runnable onTerminal;
    private int nextSeq;
    private boolean started;
    private boolean chunkSent;
    private boolean terminal;

    ResponseStream(String requestId, String responseId, MessageSender sender, Runnable onTerminal) {
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (responseId == null || responseId.isEmpty()) {
            throw new IllegalArgumentException("responseId is required");
        }
        this.requestId = requestId;
        this.responseId = responseId;
        this.sender = sender;
        this.onTerminal = onTerminal;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getResponseId() {
        return responseId;
    }

    public synchronized void start(AudioConfigData audioConfig)
            throws ConnectionException, MessageSerializationException {
        requireStartAllowed();
        sender.send(MessageBuilder.responseStart(requestId, responseId, audioConfig));
        started = true;
    }

    public synchronized void sendChunk(String text)
            throws ConnectionException, MessageSerializationException {
        requireChunkAllowed(text);
        sender.send(MessageBuilder.responseChunk(requestId, responseId, nextSeq, text));
        nextSeq++;
        chunkSent = true;
    }

    synchronized void sendChunk(String text, int seq)
            throws ConnectionException, MessageSerializationException {
        requireChunkAllowed(text);
        sender.send(MessageBuilder.responseChunk(requestId, responseId, seq, text));
        chunkSent = true;
    }

    public synchronized void done()
            throws ConnectionException, MessageSerializationException {
        requireOpen();
        sender.send(MessageBuilder.responseDone(requestId, responseId));
        terminal = true;
        onTerminal.run();
    }

    public synchronized void cancel()
            throws ConnectionException, MessageSerializationException {
        requireOpen();
        sender.send(MessageBuilder.responseCancel(responseId));
        terminal = true;
        onTerminal.run();
    }

    private void requireOpen() {
        if (terminal) {
            throw new IllegalStateException("Response is already complete");
        }
    }

    private void requireStartAllowed() {
        requireOpen();
        if (started || chunkSent) {
            throw new IllegalStateException("response.start must precede all chunks");
        }
    }

    private void requireChunkAllowed(String text) {
        requireOpen();
        if (text == null) {
            throw new IllegalArgumentException("text is required");
        }
    }
}
