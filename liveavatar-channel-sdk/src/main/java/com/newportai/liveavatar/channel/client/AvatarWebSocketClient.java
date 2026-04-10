package com.newportai.liveavatar.channel.client;

import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.listener.AvatarChannelListener;
import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.EventType;
import com.newportai.liveavatar.channel.model.ImageFrame;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.reconnect.ExponentialBackoffStrategy;
import com.newportai.liveavatar.channel.reconnect.ReconnectStrategy;
import com.newportai.liveavatar.channel.util.JsonUtil;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for Live Avatar Channel protocol
 *
 * <p>This client implements the WebSocket transport layer for the Live Avatar Channel protocol,
 * supporting both text messages (JSON) and audio/image frames (binary).
 *
 * <p>The developer needs to provide their own WebSocket server that implements the protocol.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Automatic WebSocket native ping/pong (5-second interval)</li>
 *   <li>Automatic reconnection with exponential backoff</li>
 *   <li>Thread-safe message sending</li>
 * </ul>
 *
 * @see com.newportai.liveavatar.channel.model.Message
 * @see com.newportai.liveavatar.channel.model.AudioFrame
 * @see com.newportai.liveavatar.channel.util.MessageBuilder
 * @see com.newportai.liveavatar.channel.util.AudioFrameBuilder
 */
public class AvatarWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(AvatarWebSocketClient.class);

    private final String url;
    private final AvatarChannelListener listener;
    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private volatile boolean connected = false;

    // Auto-reconnect support
    private volatile boolean autoReconnectEnabled = false;
    private volatile boolean manualDisconnect = false;
    private ReconnectStrategy reconnectStrategy;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private ScheduledExecutorService reconnectScheduler;

    /**
     * Create WebSocket client with default configuration
     *
     * @param url      WebSocket server URL (must be provided by developer)
     * @param listener event listener
     */
    public AvatarWebSocketClient(String url, AvatarChannelListener listener) {
        this(url, listener, new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(5, TimeUnit.SECONDS)
                .build());
    }

    /**
     * Create WebSocket client with custom OkHttpClient
     *
     * @param url        WebSocket server URL (must be provided by developer)
     * @param listener   event listener
     * @param httpClient custom OkHttpClient
     */
    public AvatarWebSocketClient(String url, AvatarChannelListener listener, OkHttpClient httpClient) {
        this.url = url;
        this.listener = listener;
        this.httpClient = httpClient;
    }

    /**
     * Connect to WebSocket server
     *
     * @throws ConnectionException if connection fails
     */
    public void connect() throws ConnectionException {
        if (connected) {
            logger.warn("Already connected");
            return;
        }

        manualDisconnect = false; // Reset manual disconnect flag

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("WebSocket connection opened");
                connected = true;
                reconnectAttempts.set(0); // Reset reconnect attempts on successful connection
                listener.onConnected();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    Message message = JsonUtil.fromJson(text);
                    handleMessage(message);
                } catch (MessageSerializationException e) {
                    logger.error("Failed to parse message: {}", text, e);
                    listener.onError(e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    // Incoming binary frames from the developer server are TTS audio only.
                    // (Image frames are sent by the avatar service, not received from the developer.)
                    AudioFrame frame = AudioFrame.parse(bytes.toByteArray());
                    logger.debug("Received audio frame: {}", frame);
                    listener.onAudioFrame(frame);
                } catch (Exception e) {
                    logger.error("Failed to parse audio frame", e);
                    listener.onError(e);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket connection closing: {} - {}", code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket connection closed: {} - {}", code, reason);
                connected = false;
                listener.onClosed(code, reason);

                // Auto-reconnect if enabled and not manually disconnected
                if (autoReconnectEnabled && !manualDisconnect) {
                    logger.info("Connection closed unexpectedly, will attempt to reconnect");
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket connection failed", t);
                connected = false;
                listener.onError(t);

                // Auto-reconnect if enabled and not manually disconnected
                if (autoReconnectEnabled && !manualDisconnect) {
                    logger.info("Connection failed, will attempt to reconnect");
                    scheduleReconnect();
                }
            }
        });
    }

    /**
     * Disconnect from WebSocket server
     *
     * <p>This is a manual disconnect and will not trigger auto-reconnect
     */
    public void disconnect() {
        manualDisconnect = true; // Mark as manual disconnect
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
            connected = false;
        }
    }

    /**
     * Send message to server
     *
     * @param message message to send
     * @throws MessageSerializationException if message serialization fails
     * @throws ConnectionException           if not connected
     */
    public void sendMessage(Message message) throws MessageSerializationException, ConnectionException {
        if (!connected || webSocket == null) {
            throw new ConnectionException("Not connected");
        }

        String json = JsonUtil.toJson(message);
        boolean success = webSocket.send(json);

        if (!success) {
            throw new ConnectionException("Failed to send message");
        }

        logger.debug("Sent message: {}", json);
    }

    /**
     * Send audio frame to server
     *
     * <p>Audio frames are sent as binary WebSocket messages.
     *
     * @param frame audio frame to send
     * @throws ConnectionException if not connected or send fails
     */
    public void sendAudioFrame(AudioFrame frame) throws ConnectionException {
        if (!connected || webSocket == null) {
            throw new ConnectionException("Not connected");
        }

        byte[] frameBytes = frame.encode();
        boolean success = webSocket.send(ByteString.of(frameBytes));

        if (!success) {
            throw new ConnectionException("Failed to send audio frame");
        }

        logger.debug("Sent audio frame: {} bytes", frameBytes.length);
    }

    /**
     * Send image frame to server
     *
     * <p>Image frames are sent as binary WebSocket messages.
     *
     * @param frame image frame to send
     * @throws ConnectionException if not connected or send fails
     */
    public void sendImageFrame(ImageFrame frame) throws ConnectionException {
        if (!connected || webSocket == null) {
            throw new ConnectionException("Not connected");
        }

        byte[] frameBytes = frame.encode();
        boolean success = webSocket.send(ByteString.of(frameBytes));

        if (!success) {
            throw new ConnectionException("Failed to send image frame");
        }

        logger.debug("Sent image frame: {} bytes", frameBytes.length);
    }

    /**
     * Check if client is connected
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Handle incoming message and dispatch to appropriate listener method
     */
    private void handleMessage(Message message) {
        String event = message.getEvent();
        logger.debug("Received message: event={}, sessionId={}, requestId={}",
                event, message.getSessionId(), message.getRequestId());

        if (event == null) {
            listener.onUnknownMessage(message);
            return;
        }

        switch (event) {
            case EventType.SESSION_INIT:
                listener.onSessionInit(message);
                break;

            case EventType.SESSION_READY:
                listener.onSessionReady(message);
                break;

            case EventType.SESSION_STATE:
                listener.onSessionState(message);
                break;

            case EventType.SESSION_CLOSING:
                listener.onSessionClose(message);
                break;

            case EventType.INPUT_TEXT:
                listener.onInputText(message);
                break;

            case EventType.INPUT_VOICE_START:
                listener.onInputVoiceStart(message);
                break;

            case EventType.INPUT_VOICE_FINISH:
                listener.onInputVoiceFinish(message);
                break;

            case EventType.INPUT_ASR_PARTIAL:
                listener.onAsrPartial(message);
                break;

            case EventType.INPUT_ASR_FINAL:
                listener.onAsrFinal(message);
                break;

            case EventType.RESPONSE_START:
                listener.onResponseStart(message);
                break;

            case EventType.RESPONSE_CHUNK:
                listener.onResponseChunk(message);
                break;

            case EventType.RESPONSE_DONE:
                listener.onResponseDone(message);
                break;

            case EventType.RESPONSE_CANCEL:
                listener.onResponseCancel(message);
                break;

            case EventType.RESPONSE_AUDIO_START:
                listener.onResponseAudioStart(message);
                break;

            case EventType.RESPONSE_AUDIO_FINISH:
                listener.onResponseAudioFinish(message);
                break;

            case EventType.RESPONSE_AUDIO_PROMPT_START:
                listener.onResponseAudioPromptStart(message);
                break;

            case EventType.RESPONSE_AUDIO_PROMPT_FINISH:
                listener.onResponseAudioPromptFinish(message);
                break;

            case EventType.CONTROL_INTERRUPT:
                listener.onControlInterrupt(message);
                break;

            case EventType.SYSTEM_IDLE_TRIGGER:
                listener.onSystemIdleTrigger(message);
                break;

            case EventType.SYSTEM_PROMPT:
                listener.onSystemPrompt(message);
                break;

            case EventType.ERROR:
                listener.onErrorMessage(message);
                break;

            default:
                listener.onUnknownMessage(message);
                break;
        }
    }

    /**
     * Enable auto-reconnect with default exponential backoff strategy
     *
     * <p>When enabled, the client will automatically reconnect when connection is lost
     * (but not when manually disconnected via {@link #disconnect()})
     */
    public void enableAutoReconnect() {
        enableAutoReconnect(new ExponentialBackoffStrategy());
    }

    /**
     * Enable auto-reconnect with custom strategy
     *
     * @param strategy reconnect strategy
     */
    public void enableAutoReconnect(ReconnectStrategy strategy) {
        this.autoReconnectEnabled = true;
        this.reconnectStrategy = strategy;
        if (this.reconnectScheduler == null) {
            this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "websocket-reconnect");
                thread.setDaemon(true);
                return thread;
            });
        }
        logger.info("Auto-reconnect enabled with strategy: {}", strategy);
    }

    /**
     * Disable auto-reconnect
     */
    public void disableAutoReconnect() {
        this.autoReconnectEnabled = false;
        if (this.reconnectScheduler != null) {
            this.reconnectScheduler.shutdown();
            this.reconnectScheduler = null;
        }
        logger.info("Auto-reconnect disabled");
    }

    /**
     * Check if auto-reconnect is enabled
     *
     * @return true if enabled
     */
    public boolean isAutoReconnectEnabled() {
        return autoReconnectEnabled;
    }

    /**
     * Get current reconnect attempt count
     *
     * @return reconnect attempt count (0 if connected or never reconnected)
     */
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /**
     * Schedule reconnect with exponential backoff
     */
    private void scheduleReconnect() {
        if (!autoReconnectEnabled || reconnectScheduler == null) {
            return;
        }

        int attempt = reconnectAttempts.incrementAndGet();

        if (!reconnectStrategy.shouldContinue(attempt)) {
            logger.warn("Reconnect attempt limit reached, stopping auto-reconnect");
            disableAutoReconnect();
            return;
        }

        long delayMs = reconnectStrategy.getDelayMillis(attempt);
        logger.info("Scheduling reconnect attempt #{} in {}ms", attempt, delayMs);

        reconnectScheduler.schedule(() -> {
            if (!connected && !manualDisconnect && autoReconnectEnabled) {
                logger.info("Attempting to reconnect (attempt #{})", attempt);
                try {
                    connect();
                } catch (ConnectionException e) {
                    logger.error("Reconnect attempt #{} failed: {}", attempt, e.getMessage());
                    // onFailure will be called, which will schedule next reconnect
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
