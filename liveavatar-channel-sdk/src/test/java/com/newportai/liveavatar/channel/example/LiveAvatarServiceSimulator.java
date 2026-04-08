package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.AudioFrameBuilder;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;

import java.util.Scanner;

/**
 * live avatar Service Simulator
 *
 * <p><b>IMPORTANT:</b> This is NOT a developer server example. This is a TEST TOOL
 * that simulates the live avatar service (client) for testing purposes.
 *
 * <p><b>What this does:</b>
 * <ul>
 *   <li>Acts as a WebSocket CLIENT (not server)</li>
 *   <li>Connects to a developer server at ws://localhost:8080/avatar/ws</li>
 *   <li>Simulates sending session.init, input.text, audio frames, etc.</li>
 *   <li>Uses WebSocket native ping/pong (handled by OkHttp automatically)</li>
 *   <li>Receives responses from the developer server</li>
 * </ul>
 *
 * <p><b>For a proper developer server example, see:</b>
 * {@code liveavatar-channel-server-example} project (Spring Boot WebSocket Server)
 *
 * <p><b>Architecture:</b>
 * <pre>
 * This Simulator (Client)  ---WebSocket--->  Developer Server (liveavatar-channel-server-example)
 *   - Sends session.init                        - Receives session.init
 *   - Sends input.text                          - Sends session.ready
 *   - Sends audio frames                        - Processes input
 *   - Native ping (OkHttp, 5s)                  - Native pong (Spring)
 *   - Sends system.idleTrigger                  - Sends response.chunk/done
 *   - Receives response.chunk                   - Sends input.asr.partial/final
 *   - Receives control.interrupt                - Sends control.interrupt
 * </pre>
 */
public class LiveAvatarServiceSimulator {

    // Persistent audio counters — survive multiple sendTestAudioFrames calls
    private static int audioSeq = 0;
    private static int audioTimestampMs = 0;

    public static void main(String[] args) {
        // Connect to developer server
        String wsUrl = "ws://localhost:8080/avatar/ws";

        // Use array to hold client reference for use in anonymous class
        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        // Track session ID outside listener for access in helper methods
        final String[] currentSessionIdHolder = new String[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, new AvatarChannelListenerAdapter() {

            private String currentSessionId;

            @Override
            public void onConnected() {
                System.out.println("🔗 Connected to developer server");

                try {
                    // Simulate live avatar service: send session.init
                    currentSessionId = "sess_" + System.currentTimeMillis();
                    currentSessionIdHolder[0] = currentSessionId; // Track in holder
                    Message initMessage = MessageBuilder.sessionInit(currentSessionId, "test_user_001");
                    clientHolder[0].sendMessage(initMessage);
                    System.out.println("📤 Sent session.init: " + currentSessionId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("✅ Received session.ready");
                System.out.println("\n📝 You can now type messages to send as input.text");
                System.out.println("   Commands:");
                System.out.println("   - Type any text to send input.text");
                System.out.println("   - Type 'audio' to send test audio frames");
                System.out.println("   - Type 'idle' to send system.idleTrigger");
                System.out.println("   - Type 'state' to send session.state");
                System.out.println("   - Type 'quit' to exit");
                System.out.println("\n💓 Note: WebSocket native ping is sent automatically by OkHttp (every 5s)\n");
            }

            @Override
            public void onInputVoiceStart(Message message) {
                System.out.println("🎤 Voice input started (requestId=" + message.getRequestId() + ")");
                // e.g., show a recording indicator on the UI, disable text input
            }

            @Override
            public void onInputVoiceFinish(Message message) {
                System.out.println("🎤 Voice input ended (requestId=" + message.getRequestId() + ")");
                // e.g., hide recording indicator, wait for ASR result
            }

            @Override
            public void onResponseAudioStart(Message message) {
                System.out.println("🔊 Audio output started (responseId=" + message.getResponseId() + ")");
                // e.g., initialize audio player; subsequent binary frames contain PCM audio
            }

            @Override
            public void onResponseAudioFinish(Message message) {
                System.out.println("🔊 Audio output finished (responseId=" + message.getResponseId() + ")");
                // e.g., flush and close audio player
            }

            @Override
            public void onResponseAudioPromptStart(Message message) {
                System.out.println("🔔 Idle prompt audio started");
                // e.g., initialize audio player for prompt; subsequent binary frames are prompt audio
            }

            @Override
            public void onResponseAudioPromptFinish(Message message) {
                System.out.println("🔔 Idle prompt audio finished");
                // e.g., close prompt audio player, reset idle timer
            }

            @Override
            public void onResponseStart(Message message) {
                ResponseStartData data = JsonUtil.convertData(message.getData(), ResponseStartData.class);
                if (data != null && data.getAudioConfig() != null) {
                    AudioConfigData cfg = data.getAudioConfig();
                    System.out.printf("🎛️  Received response.start — speed=%.1f, volume=%.1f, mood=%s%n",
                            cfg.getSpeed(), cfg.getVolume(), cfg.getMood());
                } else {
                    System.out.println("🎛️  Received response.start (no audioConfig)");
                }
                // Apply cfg to your TTS engine here, e.g.:
                // ttsEngine.configure(cfg.getSpeed(), cfg.getVolume(), cfg.getMood());
            }

            @Override
            public void onResponseChunk(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                System.out.print(data.getText());
            }

            @Override
            public void onResponseDone(Message message) {
                System.out.println();
                System.out.println("✅ Received response.done");
                System.out.println();
            }

            @Override
            public void onControlInterrupt(Message message) {
                System.out.println("\n⚠️  Received control.interrupt - stopping playback");
            }

            @Override
            public void onAsrPartial(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                System.out.println("🎤 ASR Partial: " + data.getText());
            }

            @Override
            public void onAsrFinal(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                System.out.println("🎤 ASR Final: " + data.getText());
            }

            @Override
            public void onSystemPrompt(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                System.out.println("🔔 Received system.prompt: " + data.getText());
                System.out.println("   (live avatar would use its own TTS to speak this)");
            }

            @Override
            public void onSessionState(Message message) {
                // live avatar doesn't receive session.state, it sends it
                System.out.println("⚠️  Unexpected session.state received");
            }

            @Override
            public void onSessionClose(Message message) {
                System.out.println("👋 Session closed");
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("❌ Error: " + error.getMessage());
            }
        });
        clientHolder[0] = client;

        // Enable auto-reconnect with exponential backoff
        client.enableAutoReconnect();
        System.out.println("🔄 Auto-reconnect enabled (exponential backoff: 1s, 2s, 4s, ... max 60s)");

        try {
            client.connect();
            System.out.println("🚀 live avatar Service Simulator started");
            System.out.println("   This is a TEST TOOL simulating the live avatar service");
            System.out.println("   For developer server implementation, see: liveavatar-channel-server-example/");

            // Interactive console
            Scanner scanner = new Scanner(System.in);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.isEmpty()) {
                    continue;
                }

                if ("quit".equalsIgnoreCase(line)) {
                    break;
                }

                try {
                    if ("audio".equalsIgnoreCase(line)) {
                        sendTestAudioFrames(clientHolder[0], currentSessionIdHolder[0]);
                    } else if ("idle".equalsIgnoreCase(line)) {
                        sendIdleTrigger(clientHolder[0], currentSessionIdHolder[0]);
                    } else if ("state".equalsIgnoreCase(line)) {
                        sendSessionState(clientHolder[0], currentSessionIdHolder[0]);
                    } else {
                        // Send as input.text
                        String requestId = "req_" + System.currentTimeMillis();
                        Message textMessage = MessageBuilder.inputText(requestId, line);
                        clientHolder[0].sendMessage(textMessage);
                        System.out.println("📤 Sent input.text: " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Disable auto-reconnect before manual disconnect
            if (clientHolder[0] != null) {
                clientHolder[0].disableAutoReconnect();
            }

            try {
                // Send session.closing before disconnect
                if (currentSessionIdHolder[0] != null && clientHolder[0] != null) {
                    Message closingMessage = MessageBuilder.sessionClose(
                            "user_disconnect"
                    );
                    clientHolder[0].sendMessage(closingMessage);
                }
            } catch (Exception e) {
                // Ignore
            }

            if (client != null) {
                client.disconnect();
            }
            System.out.println("👋 live avatar Service Simulator stopped");
        }
    }

    /**
     * Send test audio frames (simulating user speech)
     */
    private static void sendTestAudioFrames(AvatarWebSocketClient client, String sessionId) throws Exception {
        System.out.println("📤 Sending test audio frames (simulating user speech)...");

        // Persistent counters so repeated calls don't reset seq/TS
        // 16kHz mono, 512 samples/frame = 32ms per frame
        final int SAMPLES_PER_FRAME = 512;
        final int FRAME_DURATION_MS = SAMPLES_PER_FRAME * 1000 / 16000; // 32ms

        // Send 20 audio frames with mock PCM data
        for (int i = 0; i < 20; i++) {
            byte[] pcmData = new byte[SAMPLES_PER_FRAME * 2]; // 16-bit PCM
            for (int j = 0; j < pcmData.length; j++) {
                pcmData[j] = (byte) (Math.random() * 256 - 128);
            }

            AudioFrame frame = AudioFrameBuilder.create()
                    .sequence(audioSeq & 0xFFF)          // 12-bit seq, wraps at 4096
                    .timestamp(audioTimestampMs & 0xFFFFF) // 20-bit TS (ms), wraps at ~17min
                    .sampleRate(AudioHeader.SampleRate.RATE_16KHZ)
                    .frameSize(SAMPLES_PER_FRAME)
                    .mono()
                    .payload(pcmData)
                    .build();

            client.sendAudioFrame(frame);
            audioSeq++;
            audioTimestampMs += FRAME_DURATION_MS;
            Thread.sleep(FRAME_DURATION_MS); // real-time pacing
        }

        System.out.println("✅ Sent 20 audio frames");
    }

    /**
     * Send system.idleTrigger (Scene 3: Idle wake-up)
     */
    private static void sendIdleTrigger(AvatarWebSocketClient client, String sessionId) throws Exception {
        System.out.println("📤 Sending system.idleTrigger (simulating 120s idle)...");

        Message idleTrigger = MessageBuilder.systemIdleTrigger(
                "user_idle",
                120000
        );
        client.sendMessage(idleTrigger);

        System.out.println("✅ Sent system.idleTrigger");
        System.out.println("   Developer server should respond with system.prompt if needed");
    }

    /**
     * Send session.state (simulating live avatar state changes)
     */
    private static void sendSessionState(AvatarWebSocketClient client, String sessionId) throws Exception {
        System.out.println("📤 Sending session.state (simulating live avatar state)...");

        Message stateMessage = MessageBuilder.sessionState(
                "LISTENING",
                1
        );
        client.sendMessage(stateMessage);

        System.out.println("✅ Sent session.state: LISTENING");
    }
}
