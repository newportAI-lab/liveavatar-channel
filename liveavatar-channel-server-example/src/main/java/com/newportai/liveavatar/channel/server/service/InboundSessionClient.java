package com.newportai.liveavatar.channel.server.service;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbound mode session client — connects to the platform's {@code agentWsUrl} as a
 * WebSocket client, matching the integration guide §6.2 (WS Inbound) flow.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Call {@link #connect(String, String, String)} with the credentials from
 *       {@code POST /api/session/start} ({@code sessionId}, {@code agentWsUrl}).</li>
 *   <li>This service creates an {@link AvatarWebSocketClient} pointed at
 *       {@code agentWsUrl} and connects.</li>
 *   <li>On connect, it sends {@code session.init} with the platform-issued
 *       {@code sessionId}.</li>
 *   <li>The platform responds with {@code session.ready} — the session is active.</li>
 *   <li>Protocol events are handled in the listener callbacks below —
 *       extend with your AI/agent business logic.</li>
 * </ol>
 *
 * @see AvatarWebSocketClient
 */
@Service
public class InboundSessionClient {

    private static final Logger logger = LoggerFactory.getLogger(InboundSessionClient.class);

    @Value("${avatar.mode:outbound}")
    private String mode;

    private final Map<String, AvatarWebSocketClient> clients = new ConcurrentHashMap<>();

    /**
     * Connect to the platform's agentWsUrl for a new inbound session.
     *
     * @param sessionId   platform-issued session ID
     * @param userId      end-user identifier
     * @param agentWsUrl  platform-issued WebSocket URL (embeds one-time token)
     * @throws ConnectionException if the WebSocket connection fails
     */
    public void connect(String sessionId, String userId, String agentWsUrl) throws ConnectionException {
        if (!"inbound".equals(mode)) {
            logger.debug("InboundSessionClient skipped — mode is '{}', not 'inbound'", mode);
            return;
        }

        logger.info("Inbound client connecting to platform: sessionId={}, url={}", sessionId, agentWsUrl);

        final AvatarWebSocketClient[] holder = new AvatarWebSocketClient[1];

        holder[0] = new AvatarWebSocketClient(agentWsUrl, new AvatarChannelListenerAdapter() {

            @Override
            public void onConnected() {
                logger.info("Inbound client connected to platform — sending session.init");
                try {
                    Message init = MessageBuilder.sessionInit(sessionId, userId);
                    holder[0].sendMessage(init);
                } catch (Exception e) {
                    logger.error("Failed to send session.init", e);
                }
            }

            @Override
            public void onSessionReady(Message message) {
                logger.info("Inbound session ready: sessionId={}", sessionId);
            }

            @Override
            public void onInputText(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    logger.info("Received input.text: {}", data.getText());
                    handleTextInput(holder[0], data.getText(), message.getRequestId());
                }
            }

            @Override
            public void onAsrFinal(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    logger.info("Received input.asr.final (platform ASR): {}", data.getText());
                    handleTextInput(holder[0], data.getText(), message.getRequestId());
                }
            }

            @Override
            public void onInputVoiceStart(Message message) {
                logger.debug("Voice input started (platform ASR)");
            }

            @Override
            public void onInputVoiceFinish(Message message) {
                logger.debug("Voice input ended (platform ASR)");
            }

            @Override
            public void onAsrPartial(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    logger.debug("ASR partial: {}", data.getText());
                }
            }

            @Override
            public void onResponseAudioStart(Message message) {
                logger.debug("Platform TTS audio started");
            }

            @Override
            public void onResponseAudioFinish(Message message) {
                logger.debug("Platform TTS audio finished");
            }

            @Override
            public void onSessionState(Message message) {
                SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
                if (data != null) {
                    logger.debug("Session state: {}", data.getState());
                }
            }

            @Override
            public void onSystemIdleTrigger(Message message) {
                IdleTriggerData data = JsonUtil.convertData(message.getData(), IdleTriggerData.class);
                if (data != null) {
                    logger.info("Idle trigger: reason={}, idleMs={}", data.getReason(), data.getIdleTimeMs());
                }
            }

            @Override
            public void onSessionClose(Message message) {
                logger.info("Platform closed session: {}", sessionId);
                disconnect(sessionId);
            }

            @Override
            public void onControlInterrupt(Message message) {
                logger.info("Platform auto-interrupted (Scenario 2A)");
            }

            @Override
            public void onClosed(int code, String reason) {
                logger.info("Inbound client disconnected: code={}, reason={}", code, reason);
                clients.remove(sessionId);
            }

            @Override
            public void onError(Throwable error) {
                logger.error("Inbound client error for session {}", sessionId, error);
            }
        });

        holder[0].enableAutoReconnect();
        holder[0].connect();
        clients.put(sessionId, holder[0]);
    }

    /**
     * Handle text input and generate a response back to the platform.
     *
     * <p>Replace with your real AI/agent logic. This reference implementation
     * sends a simple streaming text response via {@code response.chunk}.
     */
    private void handleTextInput(AvatarWebSocketClient client, String text, String requestId) {
        String responseId = "res_" + System.currentTimeMillis();

        // When using platform TTS, send response.start with audio config first.
        // When using developer TTS, skip response.start and send audio frames directly.
        boolean developerTts = false; // set to true if you provide your own TTS

        try {
            if (!developerTts) {
                AudioConfigData audioConfig = new AudioConfigData(1.0, 1.0, "neutral");
                client.sendMessage(MessageBuilder.responseStart(requestId, responseId, audioConfig));
            }

            // Stream response chunks
            String aiResponse = "This is a response to: \"" + text + "\".";
            String[] sentences = aiResponse.split("(?<=[。！？.!?])");
            for (int i = 0; i < sentences.length; i++) {
                if (sentences[i].isEmpty()) continue;
                client.sendMessage(MessageBuilder.responseChunk(requestId, responseId, i, sentences[i]));
                Thread.sleep(50);
            }

            client.sendMessage(MessageBuilder.responseDone(requestId, responseId));
            logger.info("Response completed: {}", responseId);

        } catch (Exception e) {
            logger.error("Error generating response for request {}", requestId, e);
        }
    }

    /**
     * Disconnect an inbound session from the platform.
     */
    public void disconnect(String sessionId) {
        AvatarWebSocketClient client = clients.remove(sessionId);
        if (client != null) {
            try {
                client.sendMessage(MessageBuilder.sessionClose("user_disconnect"));
            } catch (Exception ignored) {
            }
            client.disableAutoReconnect();
            client.disconnect();
            logger.info("Inbound session disconnected: {}", sessionId);
        }
    }

    /**
     * Send a text message to the platform for the given session.
     */
    public void sendText(String sessionId, String requestId, String text)
            throws MessageSerializationException, ConnectionException {
        AvatarWebSocketClient client = clients.get(sessionId);
        if (client == null) {
            throw new ConnectionException("No inbound client for session: " + sessionId);
        }
        client.sendMessage(MessageBuilder.inputText(requestId, text));
    }

    /**
     * Send control.interrupt to the platform for the given session.
     */
    public void sendInterrupt(String sessionId) throws MessageSerializationException, ConnectionException {
        AvatarWebSocketClient client = clients.get(sessionId);
        if (client == null) {
            throw new ConnectionException("No inbound client for session: " + sessionId);
        }
        client.sendMessage(MessageBuilder.controlInterrupt());
    }

    /**
     * Check whether an inbound session is connected.
     */
    public boolean isConnected(String sessionId) {
        AvatarWebSocketClient client = clients.get(sessionId);
        return client != null && client.isConnected();
    }

    @PreDestroy
    public void shutdown() {
        clients.keySet().forEach(this::disconnect);
        logger.info("InboundSessionClient shutdown — all sessions disconnected");
    }
}
