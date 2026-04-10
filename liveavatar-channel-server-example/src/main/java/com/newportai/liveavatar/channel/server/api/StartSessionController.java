package com.newportai.liveavatar.channel.server.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API for session provisioning — Inbound Mode entry point.
 *
 * <p><b>Inbound Mode Flow:</b>
 * <ol>
 *   <li>Developer calls {@code POST /api/session/start} and receives a {@code sessionId}
 *       plus the platform WebSocket URL.</li>
 *   <li>Developer connects to the WebSocket URL using the Live Avatar Channel SDK
 *       ({@link com.newportai.liveavatar.channel.client.AvatarWebSocketClient}).</li>
 *   <li>Developer sends {@code session.init} with the obtained {@code sessionId}.</li>
 *   <li>Platform responds with {@code session.ready}; normal protocol continues.</li>
 * </ol>
 *
 * <p>Compare with <b>Outbound Mode</b>: in outbound mode the developer hosts the WebSocket
 * server and the live avatar service connects to it. In inbound mode the roles are reversed —
 * the platform hosts the WebSocket server and the developer connects to it.
 */
@RestController
@RequestMapping("/api/session")
public class StartSessionController {

    private static final Logger logger = LoggerFactory.getLogger(StartSessionController.class);

    @Value("${avatar.ws.base-url:ws://localhost:8080/avatar/ws}")
    private String wsBaseUrl;

    /**
     * Start a new avatar session.
     *
     * <p>Returns a pre-allocated {@code sessionId} and the WebSocket endpoint URL.
     * The developer must connect to {@code wsUrl} and send {@code session.init} with
     * the returned {@code sessionId} to activate the session.
     *
     * @return {@link StartSessionResponse} containing {@code sessionId} and {@code wsUrl}
     */
    @PostMapping("/start")
    public StartSessionResponse start() {
        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        logger.info("Issued session ticket: {}", sessionId);
        return new StartSessionResponse(sessionId, wsBaseUrl);
    }

    /**
     * Response payload for {@code POST /api/session/start}.
     */
    public static class StartSessionResponse {
        private final String sessionId;
        private final String wsUrl;

        public StartSessionResponse(String sessionId, String wsUrl) {
            this.sessionId = sessionId;
            this.wsUrl = wsUrl;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getWsUrl() {
            return wsUrl;
        }
    }
}
