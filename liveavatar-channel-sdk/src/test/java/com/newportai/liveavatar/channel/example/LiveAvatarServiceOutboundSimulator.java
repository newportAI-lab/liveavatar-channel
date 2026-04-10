package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.AudioFrameBuilder;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;

import java.util.Scanner;

/**
 * Interactive live-avatar-service simulator — Outbound Mode.
 *
 * <p>In <b>outbound mode</b> the <b>developer</b> hosts the WebSocket server and the
 * Live Avatar Service connects to it as a client.  This tool simulates that client —
 * it behaves exactly as the real live avatar service would in outbound mode.
 *
 * <p><b>Architecture (outbound mode):</b>
 * <pre>
 * This Simulator (Live Avatar Service / Client)  ---WebSocket--->  Developer Server
 *   Connects directly (no REST call)                                 ws://localhost:8080/avatar/ws
 *   Sends session.init (self-generated sessionId)                    Sends session.ready
 *   Sends input.text / audio frames                                  Sends response.chunk / response.done
 *   Sends session.state (avatar state changes)                       (monitors avatar state)
 *   Sends system.idleTrigger (avatar idle detection)                 Responds with system.prompt
 *   Receives system.prompt                                           Sends system.prompt
 *   Receives control.interrupt                                       Sends control.interrupt
 *   Native ping (OkHttp, 5s)                                         Native pong (Spring)
 * </pre>
 *
 * <p>Start the {@code liveavatar-channel-server-example} Spring Boot app (developer server)
 * before running this simulator.  The server will accept the connection and respond as a
 * developer server would.
 *
 * <p><b>Interactive commands (after session.ready):</b>
 * <ul>
 *   <li>Any text — sends {@code input.text} (simulates end-user text input)</li>
 *   <li>{@code audio} — sends 20 test PCM audio frames (simulates end-user voice)</li>
 *   <li>{@code state} — sends {@code session.state: LISTENING}</li>
 *   <li>{@code idle} — sends {@code system.idleTrigger} (simulates 120 s idle)</li>
 *   <li>{@code quit} — closes session and exits</li>
 * </ul>
 */
public class LiveAvatarServiceOutboundSimulator {

    // Developer server WebSocket URL — change to match your server
    private static final String DEVELOPER_SERVER_URL = "ws://localhost:8080/avatar/ws";

    // Persistent audio counters — survive multiple sendTestAudioFrames calls
    private static int audioSeq = 0;
    private static int audioTimestampMs = 0;

    public static void main(String[] args) {
        // In outbound mode the live avatar service generates its own sessionId and
        // connects directly — no REST call required.
        final String sessionId = "sess_" + System.currentTimeMillis();

        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(DEVELOPER_SERVER_URL,
                new AvatarChannelListenerAdapter() {

            @Override
            public void onConnected() {
                System.out.println("🔗 Connected to developer server");

                try {
                    // Live avatar service initiates the session
                    Message initMessage = MessageBuilder.sessionInit(sessionId, "test_user_001");
                    clientHolder[0].sendMessage(initMessage);
                    System.out.println("📤 Sent session.init: " + sessionId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("✅ Received session.ready — developer server accepted session");
                System.out.println("\n📝 Commands:");
                System.out.println("   <text>  — send input.text (end-user typed message)");
                System.out.println("   audio   — send 20 test PCM audio frames (end-user voice)");
                System.out.println("   state   — send session.state: LISTENING");
                System.out.println("   idle    — send system.idleTrigger (avatar idle 120s)");
                System.out.println("   quit    — close session and exit");
                System.out.println("\n💓 WebSocket native ping/pong handled automatically (OkHttp)\n");
            }

            @Override
            public void onResponseStart(Message message) {
                ResponseStartData data = JsonUtil.convertData(message.getData(), ResponseStartData.class);
                if (data != null && data.getAudioConfig() != null) {
                    AudioConfigData cfg = data.getAudioConfig();
                    System.out.printf("🎛️  response.start — speed=%.1f, volume=%.1f, mood=%s%n",
                            cfg.getSpeed(), cfg.getVolume(), cfg.getMood());
                } else {
                    System.out.println("🎛️  Received response.start");
                }
                // Apply audio config to TTS engine here in a real implementation
            }

            @Override
            public void onResponseChunk(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    System.out.print(data.getText());
                }
            }

            @Override
            public void onResponseDone(Message message) {
                System.out.println();
                System.out.println("✅ Received response.done (responseId=" + message.getResponseId() + ")");
                System.out.println();
            }

            @Override
            public void onResponseAudioStart(Message message) {
                System.out.println("🔊 Audio output started (responseId=" + message.getResponseId() + ")");
                // Initialize audio player; binary frames that follow contain PCM/Opus audio
            }

            @Override
            public void onResponseAudioFinish(Message message) {
                System.out.println("🔊 Audio output finished (responseId=" + message.getResponseId() + ")");
            }

            @Override
            public void onResponseAudioPromptStart(Message message) {
                System.out.println("🔔 Idle-prompt audio started");
                // Initialize audio player for prompt audio
            }

            @Override
            public void onResponseAudioPromptFinish(Message message) {
                System.out.println("🔔 Idle-prompt audio finished");
            }

            @Override
            public void onInputVoiceStart(Message message) {
                System.out.println("🎤 Voice input started (requestId=" + message.getRequestId() + ")");
            }

            @Override
            public void onInputVoiceFinish(Message message) {
                System.out.println("🎤 Voice input ended (requestId=" + message.getRequestId() + ")");
            }

            @Override
            public void onAsrPartial(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    System.out.println("🎤 ASR partial: " + data.getText());
                }
            }

            @Override
            public void onAsrFinal(Message message) {
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    System.out.println("🎤 ASR final: " + data.getText());
                }
            }

            @Override
            public void onSystemPrompt(Message message) {
                // Developer server replied to our idleTrigger — avatar should speak this text
                TextData data = JsonUtil.convertData(message.getData(), TextData.class);
                if (data != null) {
                    System.out.println("🔔 Received system.prompt: \"" + data.getText() + "\"");
                    System.out.println("   (avatar would use its TTS to speak this)");
                }
            }

            @Override
            public void onControlInterrupt(Message message) {
                System.out.println("⚠️  Received control.interrupt — stopping current playback");
            }

            @Override
            public void onSessionClose(Message message) {
                System.out.println("👋 Developer server closed the session");
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("❌ WebSocket error: " + error.getMessage());
            }
        });
        clientHolder[0] = client;

        client.enableAutoReconnect();
        System.out.println("🔄 Auto-reconnect enabled (exponential backoff: 1s → 60s max)");

        try {
            client.connect();
            System.out.println("🚀 LiveAvatarServiceOutboundSimulator started (live avatar service perspective)");
            System.out.println("   Connecting to developer server: " + DEVELOPER_SERVER_URL);

            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                if ("quit".equalsIgnoreCase(line)) {
                    break;
                }

                try {
                    switch (line.toLowerCase()) {
                        case "audio":
                            sendTestAudioFrames(clientHolder[0]);
                            break;
                        case "state":
                            sendSessionState(clientHolder[0]);
                            break;
                        case "idle":
                            sendIdleTrigger(clientHolder[0]);
                            break;
                        default:
                            String requestId = "req_" + System.currentTimeMillis();
                            clientHolder[0].sendMessage(MessageBuilder.inputText(requestId, line));
                            System.out.println("📤 Sent input.text: " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            clientHolder[0].disableAutoReconnect();
            try {
                clientHolder[0].sendMessage(MessageBuilder.sessionClose("user_disconnect"));
            } catch (Exception ignored) {
            }
            client.disconnect();
            System.out.println("👋 LiveAvatarServiceOutboundSimulator stopped");
        }
    }

    /**
     * Send 20 test PCM audio frames (simulating end-user speech captured by the avatar device).
     */
    private static void sendTestAudioFrames(AvatarWebSocketClient client) throws Exception {
        System.out.println("📤 Sending 20 test audio frames (simulating end-user voice)...");

        final int SAMPLES_PER_FRAME = 512;
        final int FRAME_DURATION_MS = SAMPLES_PER_FRAME * 1000 / 16000; // 32ms @ 16kHz

        for (int i = 0; i < 20; i++) {
            byte[] pcmData = new byte[SAMPLES_PER_FRAME * 2]; // 16-bit PCM
            for (int j = 0; j < pcmData.length; j++) {
                pcmData[j] = (byte) (Math.random() * 256 - 128);
            }

            AudioFrame frame = AudioFrameBuilder.create()
                    .sequence(audioSeq & 0xFFF)
                    .timestamp(audioTimestampMs & 0xFFFFF)
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
     * Send session.state to report the avatar's current state to the developer server.
     */
    private static void sendSessionState(AvatarWebSocketClient client) throws Exception {
        Message stateMessage = MessageBuilder.sessionState("LISTENING", 1);
        client.sendMessage(stateMessage);
        System.out.println("📤 Sent session.state: LISTENING");
    }

    /**
     * Send system.idleTrigger to notify the developer server that the avatar has been idle.
     * The developer server may respond with system.prompt to make the avatar speak.
     */
    private static void sendIdleTrigger(AvatarWebSocketClient client) throws Exception {
        Message idleTrigger = MessageBuilder.systemIdleTrigger("user_idle", 120000);
        client.sendMessage(idleTrigger);
        System.out.println("📤 Sent system.idleTrigger (idleMs=120000)");
        System.out.println("   Developer server should respond with system.prompt");
    }
}
