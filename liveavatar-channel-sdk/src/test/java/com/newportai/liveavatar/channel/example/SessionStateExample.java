package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.model.*;

/**
 * Session state handling example — shows the events the developer receives.
 */
public class SessionStateExample {

    public static void main(String[] args) throws InterruptedException {
        String wsUrl = "ws://localhost:8080/avatar/ws";

        final AvatarWebSocketClient[] ref = new AvatarWebSocketClient[1];
        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, new AgentListener() {
            @Override
            public void onSessionInit() {
                System.out.println("Session ready");
            }

            @Override
            public void onSessionState(SessionState state) {
                System.out.println("Session state: " + state);
            }

            @Override
            public void onTextInput(String text, String requestId) {
                System.out.println("Input: " + text);
            }

            @Override
            public void onError(String message) {
                System.err.println("Error: " + message);
            }

            @Override
            public void onClosed(int code, String reason) {
                System.out.println("Closed: " + code + " " + reason);
            }
        });
        ref[0] = client;

        try { client.connect(); } catch (Exception e) { e.printStackTrace(); return; }

        Thread.sleep(60000);
        client.disconnect();
    }
}
