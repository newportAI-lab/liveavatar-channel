package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.model.ResponseStateEvent;

import java.util.concurrent.CompletableFuture;

/** Tracks platform lifecycle state for one response identity. */
final class ResponseLifecycle {
    private final String requestId;
    private final String responseId;
    private final CompletableFuture<ResponseStateEvent> terminalFuture = new CompletableFuture<>();
    private volatile ResponseStateEvent latestState;
    private boolean terminal;

    ResponseLifecycle(String requestId, String responseId) {
        this.requestId = requestId;
        this.responseId = responseId;
    }

    String getRequestId() { return requestId; }
    String getResponseId() { return responseId; }
    ResponseStateEvent getLatestState() { return latestState; }
    CompletableFuture<ResponseStateEvent> getTerminalFuture() { return terminalFuture; }

    synchronized boolean record(ResponseStateEvent event) {
        if (terminal) return false;
        latestState = event;
        if (event.isTerminal()) {
            terminal = true;
            return true;
        }
        return false;
    }

    void completeTerminal(ResponseStateEvent event) {
        terminalFuture.complete(event);
    }

    void fail(Throwable error) {
        terminalFuture.completeExceptionally(error);
    }
}
