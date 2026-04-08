package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;

/**
 * session state handling example
 */
public class SessionStateExample {

    public static void main(String[] args) throws InterruptedException {
        String wsUrl = "ws://localhost:8080/avatar/ws";
        String sessionId = "sess_" + System.currentTimeMillis();
        String userId = "user_123";

        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
                new AvatarChannelListenerAdapter() {

                    @Override
                    public void onConnected() {
                        System.out.println("✅ Connected to server");

                        try {
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
                    }

                    @Override
                    public void onSessionState(Message message) {
                        // extract state data
                        SessionStateData stateData = JsonUtil.convertData(message.getData(), SessionStateData.class);
                        String stateValue = stateData.getState();

                        SessionState state = SessionState.fromValue(stateValue);

                        if (state != null) {
                            System.out.println("🔄 Session state changed to: " + state);

                            // branches
                            switch (state) {
                                case IDLE:
                                    System.out.println("   ➡️ System is idle, waiting for user input");
                                    break;

                                case LISTENING:
                                    System.out.println("   ➡️ Listening to user, ASR is capturing audio");
                                    break;

                                case THINKING:
                                    System.out.println("   ➡️ System is thinking, LLM/TTS preparation");
                                    break;

                                case STAGING:
                                    System.out.println("   ➡️ Preparing to generate live avatar");
                                    break;

                                case SPEAKING:
                                    System.out.println("   ➡️ live avatar is speaking");
                                    break;

                                case PROMPT_THINKING:
                                    System.out.println("   ➡️ Preparing reminder script");
                                    break;

                                case PROMPT_STAGING:
                                    System.out.println("   ➡️ Preparing to generate reminder");
                                    break;

                                case PROMPT_SPEAKING:
                                    System.out.println("   ➡️ live avatar is broadcasting reminder");
                                    break;

                                default:
                                    System.out.println("   ➡️ Unknown state");
                                    break;
                            }
                        } else {
                            System.out.println("⚠️ Unknown state value: " + stateValue);
                        }

                        System.out.println("   📊 Sequence: " + message.getSeq());
                        System.out.println("   🕐 Timestamp: " + message.getTimestamp());
                    }

                    @Override
                    public void onInputText(Message message) {
                        TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                        System.out.println("📨 User input: " + data.getText());
                    }

                    @Override
                    public void onResponseChunk(Message message) {
                        TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                        System.out.println("📥 Response chunk [" + message.getSeq() + "]: " + data.getText());
                    }

                    @Override
                    public void onResponseDone(Message message) {
                        System.out.println("✅ Response completed: " + message.getResponseId());
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
            System.out.println("🚀 Client started, waiting for state changes...");

            // Keep alive
            Thread.sleep(60000);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            client.disconnect();
            System.out.println("👋 Disconnected");
        }
    }
}
