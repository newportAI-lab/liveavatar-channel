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
 *   Receives control.interrupt (from developer)                      Sends control.interrupt
 *   Native ping (OkHttp, 5s)                                         Native pong (Spring)
 * </pre>
 *
 * <p><b>Interrupt Ownership Rule — why this simulator has no {@code interrupt} command:</b>
 * In outbound mode the developer hosts the server and handles ASR internally (Scenario 2B —
 * Developer ASR / Omni). Per the Interrupt Ownership Rule, {@code control.interrupt} authority
 * belongs to whoever provides ASR. Because the developer owns ASR in Scenario 2B, the platform
 * (this simulator) MUST NOT send {@code control.interrupt}. Sending an interrupt command from
 * this side would violate the protocol and could corrupt the developer's ASR pipeline.
 *
 * <p>The {@code onControlInterrupt} callback is still implemented because the developer server
 * <em>may</em> send {@code control.interrupt} to this simulator (the platform), and the
 * platform is responsible for executing the interrupt action (flushing the RTC buffer /
 * stopping current avatar playback).
 *
 * <p>Start the {@code liveavatar-channel-server-example} Spring Boot app (developer server)
 * before running this simulator.  The server will accept the connection and respond as a
 * developer server would.
 *
 * <p><b>Interactive commands (after session.ready):</b>
 * <ul>
 *   <li>Any text — sends {@code input.text} (simulates end-user text input)</li>
 *   <li>{@code audio} — sends 20 test PCM audio frames — <b>Scenario 2B</b> (Developer ASR):
 *       the platform forwards raw Binary Frames; the developer server runs VAD + ASR and
 *       sends back {@code input.voice.*} / {@code input.asr.*} events.</li>
 *   <li>{@code asr} — simulates a full Scenario 2A ASR sequence — <b>Scenario 2A</b>
 *       (Platform ASR): the platform runs VAD + ASR internally and sends
 *       {@code input.voice.start} → {@code input.asr.partial} → {@code input.voice.finish}
 *       → {@code input.asr.final} to the developer server.</li>
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

    /**
     * TTS ownership flag.
     *
     * <p>Set to {@code true} when the platform (this simulator) provides TTS:
     * after receiving a complete text response ({@code response.done}) from the developer
     * server, this simulator synthesises audio and pushes it back with
     * {@code response.audio.start} + mock audio frames + {@code response.audio.finish}.
     *
     * <p>Set to {@code false} when the developer server provides TTS:
     * the developer server sends {@code response.audio.start} / audio frames /
     * {@code response.audio.finish} directly (received via
     * {@code onResponseAudioStart/Finish} callbacks); no audio is sent from this side.
     */
    private static final boolean PLATFORM_TTS = false;

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
                System.out.println("   audio   — [Scenario 2B] send 20 raw PCM audio frames (platform forwards, developer does ASR)");
                System.out.println("   asr     — [Scenario 2A] simulate platform ASR: voice.start → asr.partial → voice.finish → asr.final");
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

                if (PLATFORM_TTS) {
                    // Platform TTS: this simulator owns TTS. Now that we have the complete text
                    // response we synthesise audio and push it to the developer server.
                    final String reqId = message.getRequestId();
                    final String resId = message.getResponseId();
                    new Thread(() -> {
                        try {
                            sendMockTtsAudio(clientHolder[0], reqId, resId);
                        } catch (Exception e) {
                            System.err.println("❌ Error sending TTS audio: " + e.getMessage());
                        }
                    }, "tts-sender").start();
                }
            }

            /**
             * Developer TTS: the developer server has started pushing TTS audio frames.
             * This fires when the developer server owns TTS and sends response.audio.start.
             * If PLATFORM_TTS is true this callback should not fire.
             */
            @Override
            public void onResponseAudioStart(Message message) {
                System.out.println("🔊 [Developer TTS] Audio output started (responseId=" + message.getResponseId() + ")");
                // Initialize audio player; binary frames that follow contain PCM/Opus audio
            }

            /**
             * Developer TTS: the developer server has finished pushing TTS audio frames.
             */
            @Override
            public void onResponseAudioFinish(Message message) {
                System.out.println("🔊 [Developer TTS] Audio output finished (responseId=" + message.getResponseId() + ")");
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

            /**
             * Developer server issued control.interrupt — the platform executes the interrupt
             * by flushing the RTC buffer and stopping current avatar playback.
             * Per the Interrupt Ownership Rule this is the only valid direction for
             * control.interrupt in Scenario 2B (Developer ASR / Omni).
             */
            @Override
            public void onControlInterrupt(Message message) {
                System.out.println("⚠️  Received control.interrupt from developer — stopping current playback");
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
                        case "asr":
                            sendPlatformAsrSequence(clientHolder[0]);
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
     * Platform TTS: send mock TTS audio to the developer server.
     *
     * <p>Wraps a burst of mock PCM audio frames with {@code response.audio.start} /
     * {@code response.audio.finish} so the developer server knows when avatar audio
     * playback starts and ends.
     *
     * <p>Replace the mock frames with real TTS output in production.
     */
    private static void sendMockTtsAudio(AvatarWebSocketClient client, String requestId, String responseId) throws Exception {
        System.out.println("🔊 [Platform TTS] Sending mock TTS audio (requestId=" + requestId + ")");

        client.sendMessage(MessageBuilder.responseAudioStart(requestId, responseId));
        System.out.println("📤 Sent response.audio.start");

        // TODO: Replace with real TTS audio frames, e.g.:
        //   for (AudioFrame frame : ttsService.generateAudio(responseText)) { ... }
        final int SAMPLES_PER_FRAME = 640;
        final int FRAME_DURATION_MS = SAMPLES_PER_FRAME * 1000 / 16000; // 40ms
        for (int i = 0; i < 10; i++) {
            byte[] pcmData = new byte[SAMPLES_PER_FRAME * 2];
            AudioFrame frame = AudioFrameBuilder.create()
                    .sequence(i & 0xFFF)
                    .timestamp((i * FRAME_DURATION_MS) & 0xFFFFF)
                    .sampleRate(AudioHeader.SampleRate.RATE_16KHZ)
                    .frameSize(SAMPLES_PER_FRAME)
                    .mono()
                    .payload(pcmData)
                    .build();
            client.sendAudioFrame(frame);
            Thread.sleep(FRAME_DURATION_MS);
        }

        client.sendMessage(MessageBuilder.responseAudioFinish(requestId, responseId));
        System.out.println("📤 Sent response.audio.finish");
        System.out.println("✅ [Platform TTS] Mock TTS audio sent");
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
     * Simulate a complete Scenario 2A (Platform ASR) utterance sequence.
     *
     * <p>In Platform ASR mode the platform owns VAD and ASR. It sends the following
     * sequence of JSON events to the developer server:
     * <ol>
     *   <li>{@code input.voice.start} — VAD detected speech beginning</li>
     *   <li>{@code input.asr.partial} — streaming partial results as speech progresses</li>
     *   <li>{@code input.voice.finish} — VAD detected speech end</li>
     *   <li>{@code input.asr.final} — final ASR result that drives the AI response</li>
     * </ol>
     *
     * <p>The developer server must have {@code avatar.asr.developer-enabled=false} to
     * process these events; with developer ASR enabled the audio Binary Frame path is used instead.
     */
    private static void sendPlatformAsrSequence(AvatarWebSocketClient client) throws Exception {
        String requestId = "req_" + System.currentTimeMillis();
        System.out.println("📤 [Scenario 2A] Simulating platform ASR sequence (requestId=" + requestId + ")");

        // 1. VAD detected: user started speaking
        client.sendMessage(MessageBuilder.inputVoiceStart(requestId));
        System.out.println("   → Sent input.voice.start");
        Thread.sleep(300);

        // 2. Streaming partial results while speech is in progress
        client.sendMessage(MessageBuilder.asrPartial(requestId, 0, "Hello"));
        System.out.println("   → Sent input.asr.partial [0]: \"Hello\"");
        Thread.sleep(300);

        client.sendMessage(MessageBuilder.asrPartial(requestId, 1, "Hello, how"));
        System.out.println("   → Sent input.asr.partial [1]: \"Hello, how\"");
        Thread.sleep(300);

        client.sendMessage(MessageBuilder.asrPartial(requestId, 2, "Hello, how are you?"));
        System.out.println("   → Sent input.asr.partial [2]: \"Hello, how are you?\"");
        Thread.sleep(300);

        // 3. VAD detected: user stopped speaking
        client.sendMessage(MessageBuilder.inputVoiceFinish(requestId));
        System.out.println("   → Sent input.voice.finish");
        Thread.sleep(100);

        // 4. Final ASR result — drives the AI response pipeline on the developer side
        client.sendMessage(MessageBuilder.asrFinal(requestId, "Hello, how are you?"));
        System.out.println("   → Sent input.asr.final: \"Hello, how are you?\"");
        System.out.println("✅ Platform ASR sequence complete — developer server should respond");
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
