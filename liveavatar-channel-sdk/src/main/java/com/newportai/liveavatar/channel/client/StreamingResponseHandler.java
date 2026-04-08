package com.newportai.liveavatar.channel.client;

import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.model.TextData;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Handler for streaming responses with sequence ordering
 * Supports out-of-order message handling
 */
public class StreamingResponseHandler {

    private final Map<String, ResponseStream> streams = new HashMap<>();

    /**
     * Handle response chunk message
     *
     * @param message      response.chunk message
     * @param chunkHandler callback for each chunk
     */
    public void handleChunk(Message message, Consumer<ChunkData> chunkHandler) {
        String responseId = message.getResponseId();
        if (responseId == null) {
            return;
        }

        ResponseStream stream = streams.computeIfAbsent(responseId, k -> new ResponseStream());
        ChunkData chunk = new ChunkData(
                message.getSessionId(),
                message.getRequestId(),
                message.getResponseId(),
                message.getSeq(),
                message.getTimestamp(),
                extractText(message)
        );

        stream.addChunk(chunk, chunkHandler);
    }

    /**
     * Handle response done message
     *
     * @param message      response.done message
     * @param doneHandler  callback when response is done
     */
    public void handleDone(Message message, Consumer<String> doneHandler) {
        String responseId = message.getResponseId();
        if (responseId != null) {
            ResponseStream stream = streams.remove(responseId);
            if (stream != null) {
                stream.markDone();
                if (doneHandler != null) {
                    doneHandler.accept(responseId);
                }
            }
        }
    }

    /**
     * Handle response cancel message
     *
     * @param message       response.cancel message
     * @param cancelHandler callback when response is cancelled
     */
    public void handleCancel(Message message, Consumer<String> cancelHandler) {
        String responseId = message.getResponseId();
        if (responseId != null) {
            streams.remove(responseId);
            if (cancelHandler != null) {
                cancelHandler.accept(responseId);
            }
        }
    }

    /**
     * Clear all streams
     */
    public void clearAll() {
        streams.clear();
    }

    /**
     * Clear specific response stream
     */
    public void clearStream(String responseId) {
        streams.remove(responseId);
    }

    private String extractText(Message message) {
        Object data = message.getData();
        if (data instanceof Map) {
            Object text = ((Map<?, ?>) data).get("text");
            return text != null ? text.toString() : "";
        } else if (data instanceof TextData) {
            return ((TextData) data).getText();
        }
        return "";
    }

    /**
     * Response stream with sequence ordering
     */
    private static class ResponseStream {
        private final TreeMap<Integer, ChunkData> pendingChunks = new TreeMap<>();
        private int expectedSeq = 0;
        private boolean done = false;

        void addChunk(ChunkData chunk, Consumer<ChunkData> handler) {
            if (done) {
                return;
            }

            if (chunk.seq == expectedSeq) {
                // Expected sequence, deliver immediately
                handler.accept(chunk);
                expectedSeq++;

                // Deliver any pending chunks that are now in order
                while (!pendingChunks.isEmpty() && pendingChunks.firstKey() == expectedSeq) {
                    ChunkData pendingChunk = pendingChunks.remove(expectedSeq);
                    handler.accept(pendingChunk);
                    expectedSeq++;
                }
            } else if (chunk.seq > expectedSeq) {
                // Future sequence, store for later
                pendingChunks.put(chunk.seq, chunk);
            }
            // Ignore chunks with seq < expectedSeq (duplicates or late arrivals)
        }

        void markDone() {
            done = true;
            pendingChunks.clear();
        }
    }

    /**
     * Chunk data wrapper
     */
    public static class ChunkData {
        private final String sessionId;
        private final String requestId;
        private final String responseId;
        private final Integer seq;
        private final Long timestamp;
        private final String text;

        public ChunkData(String sessionId, String requestId, String responseId,
                         Integer seq, Long timestamp, String text) {
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.responseId = responseId;
            this.seq = seq;
            this.timestamp = timestamp;
            this.text = text;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getResponseId() {
            return responseId;
        }

        public Integer getSeq() {
            return seq;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public String getText() {
            return text;
        }
    }
}
