package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.AudioFrameBuilder;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive developer-side simulator — Inbound Mode.
 *
 * <p>In <b>inbound mode</b> the platform (Live Avatar Service) hosts the WebSocket server.
 * The developer application connects to it after obtaining a session ticket from the
 * platform REST API.
 *
 * <p><b>Startup sequence:</b>
 * <ol>
 *   <li>{@code POST /api/session/start} → receive {@code sessionId} + {@code wsUrl}.</li>
 *   <li>Connect to {@code wsUrl} via {@link AvatarWebSocketClient}.</li>
 *   <li>Send {@code session.init} with the platform-issued {@code sessionId}.</li>
 *   <li>Receive {@code session.ready} and begin normal protocol interaction.</li>
 * </ol>
 *
 * <p><b>Architecture (inbound mode):</b>
 * <pre>
 * This Simulator (Developer Client)  ---WebSocket--->  Platform Server (liveavatar-channel-server-example)
 *   POST /api/session/start                              Returns sessionId + wsUrl
 *   Sends session.init (sessionId)                       Sends session.ready
 *   Sends input.text / audio frames                      Sends response.chunk / response.done
 *   Sends control.interrupt                              (executes interrupt)
 *   Sends system.prompt (reply to idle trigger)          Sends system.idleTrigger
 *   Receives session.state                               Sends session.state
 *   Receives control.interrupt (platform auto-interrupt, Scenario 2A only)
 *   Native pong (OkHttp, auto)                           Native ping (Spring)
 * </pre>
 *
 * <p><b>Interrupt Ownership Rule:</b> The developer (this simulator) may send
 * {@code control.interrupt} in <em>both</em> ASR modes:
 * <ul>
 *   <li><b>Platform ASR (Scenario 2A)</b> — Platform may also auto-interrupt based on its
 *       own VAD policy. The {@code onControlInterrupt} callback fires when the platform
 *       initiates such an auto-interrupt.</li>
 *   <li><b>Developer ASR / Omni (Scenario 2B)</b> — Only the developer may issue
 *       {@code control.interrupt}; the platform MUST NOT. The developer owns VAD and
 *       therefore owns the interrupt decision.</li>
 * </ul>
 *
 * <p>Start the {@code liveavatar-channel-server-example} Spring Boot app before running this.
 *
 * <p><b>ASR scenarios:</b>
 * <ul>
 *   <li><b>Scenario 2A (Platform ASR):</b> The platform runs VAD + ASR and sends
 *       {@code input.voice.start} / {@code input.asr.partial} / {@code input.voice.finish} /
 *       {@code input.asr.final} as JSON events. The developer client handles them via the
 *       {@code onInputVoiceStart}, {@code onInputVoiceFinish}, {@code onAsrPartial},
 *       {@code onAsrFinal} callbacks — no special command needed.</li>
 *   <li><b>Scenario 2B (Developer ASR):</b> The platform continuously forwards raw audio
 *       Binary Frames. The developer client receives them via {@code onAudioFrame}, runs VAD
 *       and ASR internally, and sends back {@code input.voice.start} /
 *       {@code input.asr.partial} / {@code input.voice.finish} / {@code input.asr.final}.
 *       Use the {@code audio} command to inject test frames and exercise this path.</li>
 * </ul>
 *
 * <p><b>Interactive commands (after session.ready):</b>
 * <ul>
 *   <li>Any text — sends {@code input.text}</li>
 *   <li>{@code audio} — sends 20 test PCM audio frames (triggers Scenario 2B path on server)</li>
 *   <li>{@code interrupt} — sends {@code control.interrupt}</li>
 *   <li>{@code prompt} — sends {@code system.prompt} (idle-wake reply)</li>
 *   <li>{@code quit} — closes session and exits</li>
 * </ul>
 */
public class LiveAvatarServiceInboundSimulator {

    private static final String PLATFORM_API_URL = "http://localhost:8080/api/session/start";

    // Persistent audio counters — survive multiple sendTestAudioFrames calls
    private static int audioSeq = 0;
    private static int audioTimestampMs = 0;

    // Scenario 2B: mock VAD state for onAudioFrame processing
    private static volatile boolean asrVoiceActive = false;
    private static volatile int asrFrameCount = 0;
    private static volatile int asrPartialSeq = 0;
    private static volatile String asrRequestId = null;

    /**
     * TTS ownership flag.
     *
     * <p>Set to {@code true} when the developer (this client) provides TTS:
     * after receiving a complete text response ({@code response.done}) this simulator
     * will send {@code response.audio.start} + mock audio frames + {@code response.audio.finish}
     * to the platform, simulating developer-side TTS synthesis.
     *
     * <p>Set to {@code false} when the platform provides TTS:
     * the platform synthesises and plays audio on its own, then sends
     * {@code response.audio.start} / {@code response.audio.finish} back to this client
     * as playback notifications (received via {@code onResponseAudioStart/Finish} callbacks).
     */
    private static final boolean DEVELOPER_TTS = false;

    public static void main(String[] args) {
        // ── Step 1: Obtain session ticket from the platform REST API ──
        System.out.println("🔑 Requesting session from platform: " + PLATFORM_API_URL);
        String[] sessionInfo = requestSessionFromPlatform();
        if (sessionInfo == null) {
            System.err.println("❌ Could not obtain session. Is liveavatar-channel-server-example running?");
            return;
        }
        String sessionId = sessionInfo[0];
        String clientToken = sessionInfo[1];
        String agentWsUrl = sessionInfo[2];
        System.out.println("✅ Session ticket received: sessionId=" + sessionId);
        System.out.println("   clientToken (for end-user RTC): " + clientToken);
        System.out.println("   agentWsUrl (platform connects here, never forward to frontend): " + agentWsUrl);

        // ── Step 2: Connect to the platform WebSocket using agentWsUrl ──
        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(agentWsUrl, new AvatarChannelListenerAdapter() {

            @Override
            public void onConnected() {
                System.out.println("🔗 Connected to platform WebSocket");

                try {
                    // ── Step 3: Send session.init with the platform-issued sessionId ──
                    Message initMessage = MessageBuilder.sessionInit(sessionId, "test_user_001");
                    clientHolder[0].sendMessage(initMessage);
                    System.out.println("📤 Sent session.init: " + sessionId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("✅ Received session.ready — session is active");
                System.out.println("\n📝 Commands:");
                System.out.println("   <text>    — send input.text");
                System.out.println("   audio     — [Scenario 2B] send 20 raw PCM audio frames to server;");
                System.out.println("               onAudioFrame fires when platform sends audio frames back (developer runs VAD+ASR)");
                System.out.println("   interrupt — send control.interrupt");
                System.out.println("   prompt    — send system.prompt (reply to idle trigger)");
                System.out.println("   quit      — close session and exit");
                System.out.println("\n   [Scenario 2A] voice/ASR events arrive automatically as JSON — no command needed.");
                System.out.println("   Watch for: input.voice.start / input.asr.partial / input.voice.finish / input.asr.final\n");
                System.out.println("💓 WebSocket native ping/pong handled automatically (OkHttp)\n");
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

                if (DEVELOPER_TTS) {
                    // Developer TTS: this client owns TTS. Now that we have the complete text
                    // response we synthesise audio and push it to the platform.
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
             * Platform TTS: the platform has started playing audio on the avatar.
             * This fires when the platform owns TTS and sends response.audio.start to notify us.
             * If DEVELOPER_TTS is true this callback should not fire.
             */
            @Override
            public void onResponseAudioStart(Message message) {
                System.out.println("🔊 [Platform TTS] Audio output started (responseId=" + message.getResponseId() + ")");
            }

            /**
             * Platform TTS: the platform has finished playing audio on the avatar.
             */
            @Override
            public void onResponseAudioFinish(Message message) {
                System.out.println("🔊 [Platform TTS] Audio output finished (responseId=" + message.getResponseId() + ")");
            }

            @Override
            public void onResponseAudioPromptStart(Message message) {
                System.out.println("🔔 Idle-prompt audio started");
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

            /**
             * Scenario 2B (Developer ASR): the platform forwarded a raw audio Binary Frame.
             *
             * <p>The developer client owns VAD and ASR. It must:
             * <ol>
             *   <li>Detect speech boundaries (VAD).</li>
             *   <li>Send {@code input.voice.start} when speech begins.</li>
             *   <li>Send streaming {@code input.asr.partial} results during speech.</li>
             *   <li>Send {@code input.voice.finish} when speech ends.</li>
             *   <li>Send the final {@code input.asr.final} result.</li>
             * </ol>
             * This mock implementation uses a simple frame-count heuristic in place of a
             * real VAD/ASR service.
             */
            @Override
            public void onAudioFrame(AudioFrame frame) {
                asrFrameCount++;
                try {
                    if (!asrVoiceActive && asrFrameCount == 3) {
                        // Mock VAD: speech detected after 3 frames
                        asrVoiceActive = true;
                        asrPartialSeq = 0;
                        asrRequestId = "req_" + System.currentTimeMillis();
                        clientHolder[0].sendMessage(MessageBuilder.inputVoiceStart(asrRequestId));
                        System.out.println("📤 [2B] Sent input.voice.start: " + asrRequestId);
                    }

                    if (asrVoiceActive && asrFrameCount % 5 == 0) {
                        // Send a streaming partial result every 5 frames
                        String partial = "[partial " + asrPartialSeq + "]";
                        clientHolder[0].sendMessage(MessageBuilder.asrPartial(asrRequestId, asrPartialSeq++, partial));
                        System.out.println("📤 [2B] Sent input.asr.partial [" + (asrPartialSeq - 1) + "]: " + partial);
                    }

                    if (asrVoiceActive && asrFrameCount >= 15) {
                        // Mock VAD: silence detected — finalize the utterance
                        clientHolder[0].sendMessage(MessageBuilder.inputVoiceFinish(asrRequestId));
                        System.out.println("📤 [2B] Sent input.voice.finish: " + asrRequestId);

                        String finalText = "[mock ASR result from audio frames]";
                        clientHolder[0].sendMessage(MessageBuilder.asrFinal(asrRequestId, finalText));
                        System.out.println("📤 [2B] Sent input.asr.final: \"" + finalText + "\"");

                        asrVoiceActive = false;
                        asrFrameCount = 0;
                        asrPartialSeq = 0;
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error processing audio frame: " + e.getMessage());
                }
            }

            /**
             * Platform detected the avatar has been idle and sent a system.idleTrigger.
             * As a developer you may respond with system.prompt to make the avatar say something,
             * or simply ignore it.
             */
            @Override
            public void onSystemIdleTrigger(Message message) {
                IdleTriggerData data = JsonUtil.convertData(message.getData(), IdleTriggerData.class);
                long idleMs = data != null ? data.getIdleTimeMs() : 0;
                System.out.printf("💤 system.idleTrigger received (idleMs=%d) — consider sending system.prompt%n", idleMs);
            }

            @Override
            public void onSessionState(Message message) {
                SessionStateData data = JsonUtil.convertData(message.getData(), SessionStateData.class);
                if (data != null) {
                    System.out.println("🔄 session.state: " + data.getState());
                }
            }

            /**
             * Platform auto-interrupt (Scenario 2A only).
             * In Platform ASR mode the platform may issue control.interrupt based on its own
             * VAD policy. In Developer ASR mode (Scenario 2B) this callback should never fire
             * because the platform is forbidden from sending control.interrupt.
             */
            @Override
            public void onControlInterrupt(Message message) {
                System.out.println("⚠️  Received control.interrupt — platform auto-interrupted (Scenario 2A)");
            }

            @Override
            public void onSessionClose(Message message) {
                System.out.println("👋 Platform closed the session");
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
            System.out.println("🚀 LiveAvatarServiceInboundSimulator started (developer client perspective)");

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
                        case "interrupt":
                            clientHolder[0].sendMessage(MessageBuilder.controlInterrupt());
                            System.out.println("📤 Sent control.interrupt");
                            break;
                        case "prompt":
                            clientHolder[0].sendMessage(MessageBuilder.systemPrompt("Are you still there?"));
                            System.out.println("📤 Sent system.prompt");
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
            System.out.println("👋 LiveAvatarServiceInboundSimulator stopped");
        }
    }

    /**
     * Developer TTS: send mock TTS audio to the platform.
     *
     * <p>Wraps a burst of mock PCM audio frames with {@code response.audio.start} /
     * {@code response.audio.finish} so the platform knows when to start and stop
     * avatar lip-sync / audio playback.
     *
     * <p>Replace the mock frames with real TTS output in production.
     */
    private static void sendMockTtsAudio(AvatarWebSocketClient client, String requestId, String responseId) throws Exception {
        System.out.println("🔊 [Developer TTS] Sending mock TTS audio (requestId=" + requestId + ")");

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
        System.out.println("✅ [Developer TTS] Mock TTS audio sent");
    }

    /**
     * Call the platform REST API to provision a new session.
     *
     * @return String[]{sessionId, clientToken, agentWsUrl}, or null on failure
     */
    private static String[] requestSessionFromPlatform() {
        try {
            URL url = new URL(PLATFORM_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

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
            String clientToken = extractJsonField(body, "clientToken");
            String agentWsUrl = extractJsonField(body, "agentWsUrl");

            if (sessionId == null || agentWsUrl == null) {
                System.err.println("Unexpected response from platform: " + body);
                return null;
            }
            return new String[]{sessionId, clientToken != null ? clientToken : "", agentWsUrl};

        } catch (Exception e) {
            System.err.println("Failed to call platform API: " + e.getMessage());
            return null;
        }
    }

    /**
     * Send 20 test PCM audio frames to the platform (simulating user speech input).
     */
    private static void sendTestAudioFrames(AvatarWebSocketClient client) throws Exception {
        System.out.println("📤 Sending 20 test audio frames (simulating user speech)...");

        final int SAMPLES_PER_FRAME = 512;
        final int FRAME_DURATION_MS = SAMPLES_PER_FRAME * 1000 / 16000; // 32ms

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

    /** Minimal JSON field extractor — avoids adding a JSON dependency to this example. */
    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
