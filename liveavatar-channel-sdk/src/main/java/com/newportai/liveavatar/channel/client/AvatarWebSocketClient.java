package com.newportai.liveavatar.channel.client;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.reconnect.ExponentialBackoffStrategy;
import com.newportai.liveavatar.channel.reconnect.ReconnectStrategy;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import okhttp3.*;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket transport for the Live Avatar Channel protocol.
 *
 * <p>Package-private — use {@link com.newportai.liveavatar.channel.agent.AvatarAgent}
 * instead. It provides the full lifecycle (REST + WS + handshake) with a simplified
 * {@link AgentListener} callback interface.
 */
public class AvatarWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(AvatarWebSocketClient.class);
    private static final Set<Integer> NON_RECONNECTABLE_CLOSE_CODES =
            Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
                    1000, // Normal Closure
                    1002, // Protocol Error
                    1008  // Policy Violation
            )));

    private final String url;
    private final AgentListener listener;
    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private volatile boolean connected = false;

    // Auto-reconnect support
    private volatile boolean autoReconnectEnabled = false;
    private volatile boolean manualDisconnect = false;
    private ReconnectStrategy reconnectStrategy;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private ScheduledExecutorService reconnectScheduler;

    public AvatarWebSocketClient(String url, AgentListener listener, OkHttpClient httpClient) {
        this.url = url;
        this.listener = listener;
        this.httpClient = httpClient;
    }

    public AvatarWebSocketClient(String url, AgentListener listener) {
        this(url, listener, new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .build());
    }

    public void connect() {
        connect(false);
    }

    private void reconnect() {
        connect(true);
    }

    private void connect(boolean reconnecting) {
        if (connected) {
            logger.warn("Already connected");
            return;
        }
        if (!reconnecting) {
            manualDisconnect = false;
        }

        Request request = new Request.Builder().url(url).build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                logger.info("WebSocket connection opened");
                connected = true;
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                try {
                    handleMessage(JsonUtil.fromJson(text));
                } catch (MessageSerializationException e) {
                    logger.error("Failed to parse message: {}", text, e);
                    listener.onError(e.getMessage());
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                try {
                    AudioFrame frame = AudioFrame.parse(bytes.toByteArray());
                    logger.debug("Received audio frame: {}", frame);
                    listener.onAudioFrame(frame);
                } catch (Exception e) {
                    logger.error("Failed to parse audio frame", e);
                    listener.onError(e.getMessage());
                }
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                logger.info("WebSocket closing: {} - {}", code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                logger.info("WebSocket closed: {} - {}", code, reason);
                connected = false;
                listener.onClosed(code, reason);
                if (shouldReconnectAfterClose(code)) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                logger.error("WebSocket connection failed", t);
                connected = false;
                listener.onError(t.getMessage());
                if (autoReconnectEnabled && !manualDisconnect) {
                    scheduleReconnect();
                }
            }
        });
    }

    public void disconnect() {
        manualDisconnect = true;
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
            connected = false;
        }
    }

    public void sendMessage(Message message) throws MessageSerializationException, ConnectionException {
        if (!connected || webSocket == null) {
            throw new ConnectionException("Not connected");
        }
        String json = JsonUtil.toJson(message);
        if (!webSocket.send(json)) {
            throw new ConnectionException("Failed to send message");
        }
        logger.debug("Sent: {}", json);
    }

    public void sendAudioFrame(AudioFrame frame) throws ConnectionException {
        if (!connected || webSocket == null) throw new ConnectionException("Not connected");
        byte[] bytes = frame.encode();
        if (!webSocket.send(ByteString.of(bytes))) throw new ConnectionException("Failed to send audio frame");
        logger.debug("Sent audio frame: {} bytes", bytes.length);
    }

    public void sendImageFrame(ImageFrame frame) throws ConnectionException {
        if (!connected || webSocket == null) throw new ConnectionException("Not connected");
        byte[] bytes = frame.encode();
        if (!webSocket.send(ByteString.of(bytes))) throw new ConnectionException("Failed to send image frame");
        logger.debug("Sent image frame: {} bytes", bytes.length);
    }

    public boolean isConnected() { return connected; }

    public void enableAutoReconnect() { enableAutoReconnect(new ExponentialBackoffStrategy()); }

    public void enableAutoReconnect(ReconnectStrategy strategy) {
        this.autoReconnectEnabled = true;
        this.reconnectStrategy = strategy;
        if (this.reconnectScheduler == null) {
            this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "websocket-reconnect");
                t.setDaemon(true);
                return t;
            });
        }
    }

    public void disableAutoReconnect() {
        this.autoReconnectEnabled = false;
        if (this.reconnectScheduler != null) { this.reconnectScheduler.shutdownNow(); this.reconnectScheduler = null; }
    }

    public boolean isAutoReconnectEnabled() { return autoReconnectEnabled; }
    public int getReconnectAttempts() { return reconnectAttempts.get(); }

    // ── Event dispatch ──────────────────────────────────────────────────────

    private void handleMessage(Message message) {
        String event = message.getEvent();
        logger.debug("Received: event={}", event);
        if (event == null) { logger.debug("Null event type"); return; }

        switch (event) {
            case EventType.SESSION_INIT:
                // Auto-handshake: platform sends session.init → reply session.ready
                reconnectAttempts.set(0);
                try {
                    sendMessage(MessageBuilder.sessionReady());
                    logger.debug("Auto-replied session.ready");
                } catch (Exception e) {
                    logger.error("Failed to send session.ready", e);
                }
                listener.onSessionInit();
                break;

            case EventType.SESSION_STATE: {
                SessionStateData d = JsonUtil.convertData(message.getData(), SessionStateData.class);
                if (d != null) listener.onSessionState(SessionState.fromValue(d.getState()));
                break;
            }
            case EventType.SESSION_CLOSING: {
                CloseReasonData d = JsonUtil.convertData(message.getData(), CloseReasonData.class);
                listener.onSessionClosing(d != null ? d.getReason() : "unknown");
                break;
            }

            case EventType.INPUT_TEXT: {
                TextData d = JsonUtil.convertData(message.getData(), TextData.class);
                if (d != null && d.getText() != null)
                    listener.onTextInput(d.getText(), nz(message.getRequestId()));
                break;
            }
            case EventType.SYSTEM_IDLE_TRIGGER: {
                IdleTriggerData d = JsonUtil.convertData(message.getData(), IdleTriggerData.class);
                if (d != null) listener.onIdleTrigger(d.getReason(), d.getIdleTimeMs());
                break;
            }
            case EventType.ERROR: {
                ErrorData d = JsonUtil.convertData(message.getData(), ErrorData.class);
                listener.onError(d != null ? d.getMessage() : "Unknown platform error");
                break;
            }
            case EventType.SCENE_READY:
                logger.debug("scene.ready — conversation can begin");
                listener.onSceneReady();
                break;

            default:
                logger.debug("Unhandled event: {}", event);
                break;
        }
    }

    private static String nz(String s) { return s != null ? s : ""; }

    // ── Reconnect ───────────────────────────────────────────────────────────

    private boolean shouldReconnectAfterClose(int code) {
        return autoReconnectEnabled
                && !manualDisconnect
                && !NON_RECONNECTABLE_CLOSE_CODES.contains(code);
    }

    private void scheduleReconnect() {
        if (!autoReconnectEnabled || reconnectScheduler == null) return;
        int attempt = reconnectAttempts.incrementAndGet();
        if (!reconnectStrategy.shouldContinue(attempt)) { disableAutoReconnect(); return; }
        long delay = reconnectStrategy.getDelayMillis(attempt);
        logger.info("Reconnect #{} in {}ms", attempt, delay);
        reconnectScheduler.schedule(() -> {
            if (!connected && !manualDisconnect && autoReconnectEnabled) {
                reconnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
