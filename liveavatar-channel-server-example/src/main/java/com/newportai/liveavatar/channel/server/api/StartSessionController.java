package com.newportai.liveavatar.channel.server.api;

import com.newportai.liveavatar.channel.server.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li>Platform (caller) sends {@code POST /api/session/start} with API Key and receives
 *       {@code sessionId}, {@code clientToken}, and {@code agentWsUrl}.</li>
 *   <li>{@code agentWsUrl} embeds a single-use {@code agentToken} bound to the session.
 *       It must never be forwarded to the frontend.</li>
 *   <li>The platform connects to {@code agentWsUrl} as a WebSocket client.</li>
 *   <li>After connection, the platform sends {@code session.init}; the developer backend
 *       responds with {@code session.ready}; normal protocol continues.</li>
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

    @Autowired
    private SessionManager sessionManager;

    /**
     * Start a new avatar session.
     *
     * <p>Returns a pre-allocated {@code sessionId}, a {@code clientToken} for the
     * end-user's RTC connection, and an {@code agentWsUrl} that embeds a single-use
     * {@code agentToken}. The caller must connect to {@code agentWsUrl} as a WebSocket
     * client; after connection the platform sends {@code session.init} to activate the session.
     *
     * @return {@link StartSessionResponse} containing {@code sessionId}, {@code clientToken},
     *         and {@code agentWsUrl}
     */
    @PostMapping("/start")
    public StartSessionResponse start() {
        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String agentToken = UUID.randomUUID().toString().replace("-", "");
        String clientToken = "rtc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String agentWsUrl = wsBaseUrl + "?agentToken=" + agentToken;

        sessionManager.registerPendingSession(sessionId, agentToken);

        logger.info("Issued session ticket: sessionId={}", sessionId);
        return new StartSessionResponse(sessionId, clientToken, agentWsUrl);
    }

    /**
     * Response payload for {@code POST /api/session/start}.
     */
    public static class StartSessionResponse {
        private final String sessionId;
        private final String clientToken;
        private final String agentWsUrl;

        public StartSessionResponse(String sessionId, String clientToken, String agentWsUrl) {
            this.sessionId = sessionId;
            this.clientToken = clientToken;
            this.agentWsUrl = agentWsUrl;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getClientRtcToken() {
            return clientToken;
        }

        public String getAgentWsUrl() {
            return agentWsUrl;
        }
    }
}
