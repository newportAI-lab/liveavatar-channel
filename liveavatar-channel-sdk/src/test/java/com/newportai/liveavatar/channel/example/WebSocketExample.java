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

/**
 * Example usage of Live Avatar Channel WebSocket SDK
 *
 * <p>This example demonstrates how to use the SDK as a live avatar service client,
 * connecting to a developer-provided WebSocket server.
 *
 * <p><b>Important:</b> You need to provide your own WebSocket server that implements
 * the Live Avatar Channel protocol. The server should handle session.init, input.text,
 * and return response.chunk/response.done messages.
 */
public class WebSocketExample {

    public static void main(String[] args) throws InterruptedException {
        // TODO: Replace with your WebSocket server URL
        String wsUrl = "ws://localhost:8080/avatar/ws";
        String sessionId = "sess_" + System.currentTimeMillis();
        String userId = "user_123";

        // Create streaming response handler
        StreamingResponseHandler streamHandler = new StreamingResponseHandler();

        // Use array to hold client reference for use in anonymous class
        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        // Create client with listener
        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, new AvatarChannelListenerAdapter() {

            @Override
            public void onConnected() {
                System.out.println("✅ Connected to server");

                try {
                    // Send session.init after connection
                    Message initMessage = MessageBuilder.sessionInit(sessionId, userId);
                    clientHolder[0].sendMessage(initMessage);
                    System.out.println("📤 Sent session.init");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("✅ Session ready: " + message.getSessionId());

                try {
                    // Send text input
                    Message textMessage = MessageBuilder.inputText("req_1", "what's your name?");
                    clientHolder[0].sendMessage(textMessage);
                    System.out.println("📤 Sent input.text: what's your name?");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onResponseChunk(Message message) {
                // Handle streaming response with sequence ordering
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
            // Connect to server
            client.connect();

            // Keep alive for demo
            Thread.sleep(30000);

            // Send interrupt after 10 seconds
            Thread.sleep(10000);
            Message interruptMessage = MessageBuilder.controlInterrupt();
            client.sendMessage(interruptMessage);
            System.out.println("📤 Sent control.interrupt");

            // Wait a bit more
            Thread.sleep(5000);

        } catch (ConnectionException | InterruptedException | MessageSerializationException e) {
            e.printStackTrace();
        } finally {
            // Disconnect
            client.disconnect();
            System.out.println("👋 Disconnected");
        }
    }
}
