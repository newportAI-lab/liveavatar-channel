package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.model.*;
import com.newportai.liveavatar.channel.util.AudioFrameBuilder;
import com.newportai.liveavatar.channel.util.JsonUtil;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interactive platform-perspective simulator — Inbound Mode.
 *
 * <p>In <b>inbound mode</b> the <b>platform</b> (Live Avatar Service) hosts the WebSocket server.
 * Developer applications connect to it. This simulator plays the platform's role so that
 * developer teams can test their client code without a real live-avatar deployment.
 *
 * <p>This is the platform-side counterpart of {@link LiveAvatarServiceInboundSimulator},
 * which plays the developer-client role in the same mode.
 *
 * <p><b>Architecture (inbound mode, platform view):</b>
 * <pre>
 * Developer Client (LiveAvatarServiceInboundSimulator or real app)
 *                                              ↕ WebSocket
 *                          This Simulator (Platform / WebSocket Server)
 *
 *   POST /api/session/start        ──→  Returns {sessionId, clientToken, agentWsUrl}
 *   Connects to agentWsUrl         ──→  Accepts connection
 *   Sends session.init             ──→  Validates → sends session.ready
 *   Sends input.text               ──→  Streams response.start / .chunk / .done
 *   Sends input.asr.final  [2B]    ──→  Streams response
 *   Sends input.voice.* / asr.*[2B]──→  Logged (developer runs VAD+ASR)
 *   Sends audio Binary Frames [2B] ──→  Echoed back (platform forwards to developer ASR)
 *   Sends control.interrupt        ──→  Cancels current response
 *   Sends system.prompt            ──→  Logged (developer replied to idle trigger)
 *   Sends response.audio.* [devTTS]──→  Logged (developer pushed TTS audio)
 *              ←─  system.idleTrigger   (interactive: type "idle")
 *              ←─  session.state        (interactive: type "state")
 *              ←─  control.interrupt    (interactive: type "interrupt" — Scenario 2A only)
 * </pre>
 *
 * <p><b>Scenario 2A vs 2B:</b>
 * <ul>
 *   <li><b>2A (Platform ASR)</b>: Platform runs VAD+ASR and sends {@code input.voice.start} /
 *       {@code input.asr.partial} / {@code input.voice.finish} / {@code input.asr.final}.
 *       Platform <em>may</em> auto-interrupt via {@code control.interrupt}.</li>
 *   <li><b>2B (Developer ASR)</b>: Platform forwards raw audio Binary Frames; developer
 *       runs VAD+ASR and sends back the voice/asr events. Platform MUST NOT auto-interrupt —
 *       the {@code interrupt} command is invalid in this scenario.</li>
 * </ul>
 *
 * <p><b>TTS ownership (PLATFORM_TTS flag):</b>
 * <ul>
 *   <li><b>true (Platform TTS)</b>: After {@code response.done} this simulator sends
 *       {@code response.audio.start} + mock PCM frames + {@code response.audio.finish}.
 *       {@code response.start} (with audio config) is sent before chunks.</li>
 *   <li><b>false (Developer TTS)</b>: Text-only streaming. Developer client is expected
 *       to synthesise audio and send {@code response.audio.*} frames back.</li>
 * </ul>
 *
 * <p><b>How to run:</b>
 * <ol>
 *   <li>Start this simulator (platform server).</li>
 *   <li>Start {@link LiveAvatarServiceInboundSimulator} (developer client) in a second terminal.</li>
 * </ol>
 *
 * <p><b>Interactive commands (after developer connects):</b>
 * <ul>
 *   <li>{@code idle}      — send {@code system.idleTrigger}</li>
 *   <li>{@code state}     — send {@code session.state: LISTENING}</li>
 *   <li>{@code interrupt} — send {@code control.interrupt} (Scenario 2A platform auto-interrupt only)</li>
 *   <li>{@code quit}      — shutdown the simulator</li>
 * </ul>
 */
public class PlatformInboundSimulator {

    private static final int HTTP_PORT = 8080;
    private static final int WS_PORT = 8081;
    private static final String WS_BASE_URL = "ws://localhost:" + WS_PORT;

    /**
     * Platform TTS flag.
     * true  → platform streams audio frames after response.done.
     * false → developer provides TTS; platform sends text only.
     */
    private static final boolean PLATFORM_TTS = true;

    // Sessions provisioned via REST, waiting for WebSocket connection
    private static final ConcurrentHashMap<String, String> pendingSessions = new ConcurrentHashMap<>();

    // Single active developer connection (one-session simulator)
    private static volatile WebSocket activeConn = null;

    // Current streaming-response task (supports cancellation via control.interrupt)
    private static final AtomicReference<Future<?>> currentTask = new AtomicReference<>();

    private static final ExecutorService responseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "response-streamer");
        t.setDaemon(true);
        return t;
    });

    public static void main(String[] args) throws Exception {
        PlatformWsServer wsServer = new PlatformWsServer(new InetSocketAddress(WS_PORT));
        wsServer.start();

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        httpServer.createContext("/api/session/start", PlatformInboundSimulator::handleSessionStart);
        httpServer.setExecutor(Executors.newSingleThreadExecutor());
        httpServer.start();

        System.out.println("🚀 PlatformInboundSimulator started  (platform server perspective)");
        System.out.println("   HTTP API : http://localhost:" + HTTP_PORT + "/api/session/start");
        System.out.println("   WebSocket: " + WS_BASE_URL);
        System.out.println("   TTS mode : " + (PLATFORM_TTS ? "Platform TTS" : "Developer TTS"));
        System.out.println();
        System.out.println("⏳ Waiting for developer client to POST /api/session/start ...");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            WebSocket conn = activeConn;
            if (conn == null || !conn.isOpen()) {
                System.out.println("⚠️  No active developer connection — waiting.");
                continue;
            }

            try {
                switch (line.toLowerCase()) {
                    case "idle":
                        send(conn, MessageBuilder.systemIdleTrigger("avatar_idle", 120000));
                        System.out.println("📤 Sent system.idleTrigger (idleMs=120000)");
                        System.out.println("   Developer should respond with system.prompt");
                        break;
                    case "state":
                        send(conn, MessageBuilder.sessionState("LISTENING", 1));
                        System.out.println("📤 Sent session.state: LISTENING");
                        break;
                    case "interrupt":
                        cancelCurrentResponse();
                        send(conn, MessageBuilder.controlInterrupt());
                        System.out.println("📤 Sent control.interrupt (Scenario 2A platform auto-interrupt)");
                        System.out.println("   ⚠️  Do NOT use in Scenario 2B — developer owns VAD/interrupt");
                        break;
                    case "quit":
                        if (conn.isOpen()) {
                            send(conn, MessageBuilder.sessionClose("platform_shutdown"));
                            Thread.sleep(300);
                        }
                        wsServer.stop();
                        httpServer.stop(0);
                        responseExecutor.shutdownNow();
                        System.out.println("👋 PlatformInboundSimulator stopped");
                        return;
                    default:
                        System.out.println("❓ Commands: idle | state | interrupt | quit");
                }
            } catch (Exception e) {
                System.err.println("❌ Command error: " + e.getMessage());
            }
        }
    }

    // ── REST: POST /api/session/start ──────────────────────────────────────────

    private static void handleSessionStart(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String sessionId   = "sess_" + System.currentTimeMillis();
        String clientToken = "tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        pendingSessions.put(sessionId, clientToken);

        // agentWsUrl is the WebSocket URL the developer agent connects to.
        // clientToken is for end-user RTC (e.g. LiveKit); never expose agentWsUrl to the frontend.
        String body = "{\"sessionId\":\"" + sessionId + "\","
                + "\"clientToken\":\"" + clientToken + "\","
                + "\"agentWsUrl\":\"" + WS_BASE_URL + "\"}";
        byte[] bodyBytes = body.getBytes("UTF-8");

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bodyBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bodyBytes);
        }

        System.out.println("✅ Session provisioned: sessionId=" + sessionId);
        System.out.println("   clientToken=" + clientToken + "  agentWsUrl=" + WS_BASE_URL);
    }

    // ── WebSocket message dispatch ─────────────────────────────────────────────

    static void onWsText(WebSocket conn, String rawJson) {
        try {
            Message msg = JsonUtil.fromJson(rawJson);
            String type = msg.getEvent();

            switch (type) {
                case EventType.SESSION_INIT:
                    handleSessionInit(conn, msg);
                    break;

                case EventType.INPUT_TEXT:
                    handleInputText(conn, msg);
                    break;

                case EventType.INPUT_ASR_FINAL:
                    handleAsrFinal(conn, msg);
                    break;

                case EventType.INPUT_VOICE_START:
                    System.out.println("🎤 [2B] voice.start  requestId=" + msg.getRequestId());
                    break;

                case EventType.INPUT_VOICE_FINISH:
                    System.out.println("🎤 [2B] voice.finish requestId=" + msg.getRequestId());
                    break;

                case EventType.INPUT_ASR_PARTIAL: {
                    TextData d = JsonUtil.convertData(msg.getData(), TextData.class);
                    System.out.println("🎤 [2B] asr.partial: " + (d != null ? d.getText() : ""));
                    break;
                }

                case EventType.CONTROL_INTERRUPT:
                    System.out.println("⚠️  Developer sent control.interrupt — cancelling response");
                    cancelCurrentResponse();
                    break;

                case EventType.SYSTEM_PROMPT: {
                    TextData d = JsonUtil.convertData(msg.getData(), TextData.class);
                    System.out.println("🔔 system.prompt from developer: \""
                            + (d != null ? d.getText() : "") + "\"");
                    break;
                }

                case EventType.RESPONSE_AUDIO_START:
                    System.out.println("🔊 [Developer TTS] audio.start  responseId=" + msg.getResponseId());
                    break;

                case EventType.RESPONSE_AUDIO_FINISH:
                    System.out.println("🔊 [Developer TTS] audio.finish responseId=" + msg.getResponseId());
                    break;

                case EventType.SESSION_CLOSING:
                    System.out.println("👋 Developer closed the session");
                    activeConn = null;
                    break;

                default:
                    System.out.println("📥 Unhandled event: " + type);
            }
        } catch (Exception e) {
            System.err.println("❌ Message error: " + e.getMessage());
        }
    }

    static void onWsBinary(WebSocket conn, ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            AudioFrame frame = AudioFrame.parse(bytes);
            System.out.printf("🎵 [2B] Audio frame seq=%d  payload=%d bytes — echoing to developer ASR%n",
                    frame.getHeader().getSequence(), frame.getPayload().length);
            // Platform echoes the binary frame so the developer's onAudioFrame callback fires,
            // allowing them to run VAD+ASR in Scenario 2B.
            conn.send(ByteBuffer.wrap(frame.encode()));
        } catch (Exception e) {
            System.err.println("❌ Audio frame error: " + e.getMessage());
        }
    }

    // ── Message handlers ───────────────────────────────────────────────────────

    private static void handleSessionInit(WebSocket conn, Message msg) throws Exception {
        SessionInitData data = JsonUtil.convertData(msg.getData(), SessionInitData.class);
        String sessionId = data != null ? data.getSessionId() : null;

        if (sessionId == null || !pendingSessions.containsKey(sessionId)) {
            System.err.println("❌ Unknown sessionId: " + sessionId);
            send(conn, MessageBuilder.error(msg.getRequestId(), "SESSION_NOT_FOUND",
                    "Unknown sessionId: " + sessionId));
            return;
        }

        pendingSessions.remove(sessionId);
        activeConn = conn;

        send(conn, MessageBuilder.sessionReady());
        System.out.println("✅ session.ready → sessionId=" + sessionId
                + (data.getUserId() != null ? "  userId=" + data.getUserId() : ""));
        System.out.println();
        System.out.println("📝 Platform commands:");
        System.out.println("   idle      — send system.idleTrigger");
        System.out.println("   state     — send session.state: LISTENING");
        System.out.println("   interrupt — send control.interrupt (Scenario 2A only)");
        System.out.println("   quit      — shutdown");
        System.out.println();
    }

    private static void handleInputText(WebSocket conn, Message msg) {
        TextData data = JsonUtil.convertData(msg.getData(), TextData.class);
        String text = data != null ? data.getText() : "";
        System.out.println("📥 input.text: \"" + text + "\"");
        startStreamingResponse(conn, msg.getRequestId(), text);
    }

    private static void handleAsrFinal(WebSocket conn, Message msg) {
        TextData data = JsonUtil.convertData(msg.getData(), TextData.class);
        String text = data != null ? data.getText() : "";
        System.out.println("🎤 input.asr.final: \"" + text + "\" — generating response");
        startStreamingResponse(conn, msg.getRequestId(), text);
    }

    private static void startStreamingResponse(WebSocket conn, String requestId, String input) {
        cancelCurrentResponse();
        Future<?> task = responseExecutor.submit(() -> {
            try {
                streamResponse(conn, requestId, input);
            } catch (InterruptedException e) {
                System.out.println("⚡ Response interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("❌ Stream error: " + e.getMessage());
            }
        });
        currentTask.set(task);
    }

    private static void streamResponse(WebSocket conn, String requestId, String input)
            throws Exception {
        String responseId = "res_" + System.currentTimeMillis();
        String[] chunks = mockAiResponse(input).split("(?<=[.!?。！？])");

        if (PLATFORM_TTS) {
            send(conn, MessageBuilder.responseStart(requestId, responseId,
                    new AudioConfigData(1.0, 1.0, "neutral")));
        }

        System.out.print("📤 Streaming");
        for (int i = 0; i < chunks.length; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            send(conn, MessageBuilder.responseChunk(requestId, responseId, i, chunks[i]));
            System.out.print(".");
            Thread.sleep(100);
        }
        System.out.println();

        send(conn, MessageBuilder.responseDone(requestId, responseId));
        System.out.println("✅ response.done  responseId=" + responseId);

        if (PLATFORM_TTS) {
            sendMockTtsAudio(conn, requestId, responseId);
        } else {
            System.out.println("   [Developer TTS] awaiting developer audio frames...");
        }
    }

    private static void sendMockTtsAudio(WebSocket conn, String requestId, String responseId)
            throws Exception {
        Thread.sleep(100);
        send(conn, MessageBuilder.responseAudioStart(requestId, responseId));
        System.out.println("🔊 [Platform TTS] response.audio.start — pushing mock PCM frames");

        final int SAMPLES = 640;
        final int FRAME_MS = SAMPLES * 1000 / 16000; // 40 ms @ 16 kHz

        for (int i = 0; i < 10; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            AudioFrame frame = AudioFrameBuilder.create()
                    .sequence(i & 0xFFF)
                    .timestamp((i * FRAME_MS) & 0xFFFFF)
                    .sampleRate(AudioHeader.SampleRate.RATE_16KHZ)
                    .frameSize(SAMPLES)
                    .mono()
                    .payload(new byte[SAMPLES * 2])
                    .build();
            conn.send(ByteBuffer.wrap(frame.encode()));
            Thread.sleep(FRAME_MS);
        }

        send(conn, MessageBuilder.responseAudioFinish(requestId, responseId));
        System.out.println("🔊 [Platform TTS] response.audio.finish");
    }

    private static void cancelCurrentResponse() {
        Future<?> task = currentTask.getAndSet(null);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    private static void send(WebSocket conn, Message msg) throws Exception {
        conn.send(MessageBuilder.toJson(msg));
    }

    private static String mockAiResponse(String input) {
        return "This is a simulated platform response to: \"" + input + "\". "
                + "I am the live avatar platform. "
                + "Your message has been received and processed. "
                + "Thank you for connecting to the live avatar service.";
    }

    // ── Embedded WebSocket server ──────────────────────────────────────────────

    static class PlatformWsServer extends WebSocketServer {

        PlatformWsServer(InetSocketAddress address) {
            super(address);
            setReuseAddr(true);
        }

        @Override
        public void onStart() {
            System.out.println("✅ WebSocket server ready on port " + getPort());
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            System.out.println("🔗 Developer connected: " + conn.getRemoteSocketAddress());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            if (conn == activeConn) activeConn = null;
            System.out.println("🔌 Developer disconnected (code=" + code + ")");
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            onWsText(conn, message);
        }

        @Override
        public void onMessage(WebSocket conn, ByteBuffer message) {
            onWsBinary(conn, message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            System.err.println("❌ WS error: " + ex.getMessage());
        }
    }
}
