package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.client.StreamingResponseHandler;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.model.TextData;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal SDK usage example — Inbound Mode.
 *
 * <p><b>Inbound Mode</b> is where the platform (Live Avatar Service) hosts the WebSocket server.
 * As a developer you:
 * <ol>
 *   <li>Call {@code POST /api/session/start} on the platform REST API to obtain a
 *       {@code sessionId} and the WebSocket URL.</li>
 *   <li>Connect to the WebSocket URL using {@link AvatarWebSocketClient}.</li>
 *   <li>Send {@code session.init} with the {@code sessionId} returned in step 1.</li>
 *   <li>The platform responds with {@code session.ready}; normal protocol follows.</li>
 * </ol>
 *
 * <p>Run the {@code liveavatar-channel-server-example} Spring Boot app first, then execute
 * this class.
 */
public class WebSocketExample {

    // Platform REST base URL — change if the platform is deployed elsewhere
    private static final String PLATFORM_API_URL = "http://localhost:8080/api/session/start";

    public static void main(String[] args) throws InterruptedException {
        // ── Step 1: Call the platform REST API to obtain a sessionId and WebSocket URL ──
        String[] sessionInfo = requestSessionFromPlatform();
        if (sessionInfo == null) {
            System.err.println("❌ Failed to obtain session from platform. Is the server running?");
            return;
        }
        String sessionId = sessionInfo[0];
        String wsUrl = sessionInfo[1];
        String userId = "user_123";
        System.out.println("✅ Session obtained: sessionId=" + sessionId + ", wsUrl=" + wsUrl);

        // ── Step 2: Connect to the platform WebSocket ──
        StreamingResponseHandler streamHandler = new StreamingResponseHandler();
        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, new AvatarChannelListenerAdapter() {

            @Override
            public void onConnected() {
                System.out.println("✅ Connected to platform WebSocket");

                try {
                    // ── Step 3: Send session.init with the platform-issued sessionId ──
                    Message initMessage = MessageBuilder.sessionInit(sessionId, userId);
                    clientHolder[0].sendMessage(initMessage);
                    System.out.println("📤 Sent session.init: " + sessionId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("✅ Session ready — platform accepted session: " + sessionId);

                try {
                    // ── Step 4: Start sending inputs as usual ──
                    Message textMessage = MessageBuilder.inputText("req_1", "what's your name?");
                    clientHolder[0].sendMessage(textMessage);
                    System.out.println("📤 Sent input.text: what's your name?");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onResponseChunk(Message message) {
                streamHandler.handleChunk(message, chunk -> {
                    System.out.println("📥 Response chunk [" + chunk.getSeq() + "]: " + chunk.getText());
                });
            }

            @Override
            public void onResponseDone(Message message) {
                streamHandler.handleDone(message, responseId -> {
                    System.out.println("✅ Response done: " + responseId);
                });
            }

            @Override
            public void onSessionState(Message message) {
                Object data = message.getData();
                if (data instanceof java.util.Map) {
                    String state = (String) ((java.util.Map<?, ?>) data).get("state");
                    System.out.println("🔄 Session state: " + state);
                }
            }

            @Override
            public void onSystemIdleTrigger(Message message) {
                // Platform detected the avatar has been idle — respond with a prompt if desired
                System.out.println("💤 Idle trigger received — sending prompt");
                try {
                    Message prompt = MessageBuilder.systemPrompt("Are you still there?");
                    clientHolder[0].sendMessage(prompt);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onControlInterrupt(Message message) {
                System.out.println("⏸️ Control interrupt received");
            }

            @Override
            public void onSystemPrompt(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                System.out.println("💬 System prompt: " + data.getText());
            }

            @Override
            public void onErrorMessage(Message message) {
                System.err.println("❌ Error: " + message.getData());
            }

            @Override
            public void onClosed(int code, String reason) {
                System.out.println("🔌 Connection closed: " + code + " - " + reason);
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("❌ Error: " + error.getMessage());
                error.printStackTrace();
            }
        });
        clientHolder[0] = client;

        try {
            client.connect();

            // Keep alive for demo
            Thread.sleep(15000);

            // Send interrupt
            Message interruptMessage = MessageBuilder.controlInterrupt();
            client.sendMessage(interruptMessage);
            System.out.println("📤 Sent control.interrupt");

            Thread.sleep(3000);

        } catch (ConnectionException | InterruptedException | MessageSerializationException e) {
            e.printStackTrace();
        } finally {
            client.disconnect();
            System.out.println("👋 Disconnected");
        }
    }

    /**
     * Call the platform REST API to provision a new session.
     *
     * @return String[]{sessionId, wsUrl}, or null on failure
     */
    private static String[] requestSessionFromPlatform() {
        try {
            URL url = new URL(PLATFORM_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Empty body — no parameters required
            try (OutputStream os = conn.getOutputStream()) {
                os.write("{}".getBytes());
            }

            if (conn.getResponseCode() != 200) {
                System.err.println("HTTP " + conn.getResponseCode() + " from platform API");
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            String body = sb.toString();
            String sessionId = extractJsonField(body, "sessionId");
            String wsUrl = extractJsonField(body, "wsUrl");

            if (sessionId == null || wsUrl == null) {
                System.err.println("Unexpected response from platform: " + body);
                return null;
            }
            return new String[]{sessionId, wsUrl};

        } catch (Exception e) {
            System.err.println("Failed to call platform API: " + e.getMessage());
            return null;
        }
    }

    /** Minimal JSON field extractor — avoids adding a JSON dependency to this example. */
    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
