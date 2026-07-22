package com.newportai.liveavatar.channel.server.api;

import com.newportai.liveavatar.channel.agent.SessionInfo;
import com.newportai.liveavatar.channel.exception.AvatarChannelException;
import com.newportai.liveavatar.channel.server.service.DemoAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for session provisioning.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code POST /api/session/start} — create a new session</li>
 *   <li>{@code POST /api/session/stop}  — end an active session</li>
 * </ul>
 *
 * <p>Uses {@link DemoAgentService} which wraps {@link com.newportai.liveavatar.channel.agent.AvatarAgent}.
 */
@RestController
@RequestMapping("/api/session")
public class StartSessionController {

    private static final Logger logger = LoggerFactory.getLogger(StartSessionController.class);

    @Autowired
    private DemoAgentService agentService;

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) StartSessionRequest request) {
        String avatarId = request != null ? request.getAvatarId() : null;
        String voiceId = request != null ? request.getVoiceId() : null;
        String rtcProvider = request != null ? request.getRtcProvider() : null;
        String sessionId = request != null ? request.getSessionId() : null;

        if (sessionId != null && !sessionId.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorBody(-1, "Reconnect not yet supported via this endpoint. "
                            + "Use AvatarAgent directly for full control."));
        }

        try {
            SessionInfo info = agentService.startSession(avatarId, voiceId, rtcProvider);
            return ResponseEntity.ok(new StartSessionResponse(info));
        } catch (AvatarChannelException e) {
            logger.error("Failed to start session", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(-1, e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop(@RequestBody StopSessionRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorBody(-1, "sessionId is required"));
        }
        agentService.stopSession(request.getSessionId());
        return ResponseEntity.ok(errorBody(0, "success"));
    }

    private static Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        return map;
    }

    // ── DTOs ──

    public static class StartSessionRequest {
        private String avatarId;
        private String sessionId;
        private String voiceId;
        private String rtcProvider;

        public String getAvatarId() { return avatarId; }
        public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getVoiceId() { return voiceId; }
        public void setVoiceId(String voiceId) { this.voiceId = voiceId; }

        public String getRtcProvider() { return rtcProvider; }
        public void setRtcProvider(String rtcProvider) { this.rtcProvider = rtcProvider; }
    }

    public static class StopSessionRequest {
        private String sessionId;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    public static class StartSessionResponse {
        private final String sessionId;
        private final String sfuUrl;
        private final String userToken;
        private final String agentWsUrl;
        private final String joinUrl;

        public StartSessionResponse(SessionInfo info) {
            this.sessionId = info.getSessionId();
            this.sfuUrl = info.getSfuUrl();
            this.userToken = info.getUserToken();
            this.agentWsUrl = info.getAgentWsUrl();
            this.joinUrl = info.getJoinUrl();
        }

        public String getSessionId() { return sessionId; }
        public String getSfuUrl() { return sfuUrl; }
        public String getUserToken() { return userToken; }
        public String getAgentWsUrl() { return agentWsUrl; }
        public String getJoinUrl() { return joinUrl; }
    }
}
