package com.newportai.liveavatar.channel.server.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.newportai.liveavatar.channel.server.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * REST API for session provisioning.
 *
 * <p>Forwards to the Live Avatar platform's dispatcher endpoint and returns the
 * resulting {@code sessionId}, {@code agentWsUrl}, and related tokens.
 *
 * <p><b>Inbound Mode Flow:</b>
 * <ol>
 *   <li>Caller sends {@code POST /api/session/start} with optional {@code avatarId}.</li>
 *   <li>This controller calls the platform's dispatcher to provision a real session.</li>
 *   <li>The response includes {@code agentWsUrl} — the caller connects to that URL
 *       as a WebSocket client and starts the protocol with {@code session.init}.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/session")
public class StartSessionController {

    private static final Logger logger = LoggerFactory.getLogger(StartSessionController.class);

    @Value("${avatar.api.base-url:https://facemarket.ai}")
    private String apiBaseUrl;

    @Value("${avatar.api.key:lk_live_WbFb9JZvbAWhReOZ2qPdMmzrHW7puYA5BRAmBxBW9dU}")
    private String apiKey;

    @Value("${avatar.id:avatar_01k56rnqaz15fz4t0ha4ja1132}")
    private String defaultAvatarId;

    @Value("${avatar.mode:inbound}")
    private String mode;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SessionManager sessionManager;

    @PostMapping("/start")
    public StartSessionResponse start(@RequestBody(required = false) StartSessionRequest request) {
        String avatarId = (request != null && request.getAvatarId() != null)
                ? request.getAvatarId()
                : defaultAvatarId;

        String url = apiBaseUrl + "/vih/dispatcher/v1/session/start";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Collections.singletonMap("avatarId", avatarId);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        logger.info("Calling platform dispatcher: url={}, avatarId={}", url, avatarId);
        ResponseEntity<PlatformSessionResponse> response =
                restTemplate.postForEntity(url, entity, PlatformSessionResponse.class);

        PlatformSessionResponse apiResponse = response.getBody();
        if (apiResponse == null) {
            logger.error("Empty response from platform dispatcher");
            throw new RuntimeException("No response from platform dispatcher");
        }
        if (apiResponse.code != 0) {
            logger.error("Platform dispatcher returned error: code={}, message={}",
                    apiResponse.code, apiResponse.message);
            throw new RuntimeException("Failed to start session: " + apiResponse.message);
        }

        PlatformSessionData data = apiResponse.data;
        logger.info("Session started via platform: sessionId={}", data.sessionId);

        if ("inbound".equals(mode)) {
            sessionManager.registerPendingSession(data.sessionId, data.agentToken);
        }

        return new StartSessionResponse(
                data.sessionId,
                data.sfuUrl,
                data.userToken,
                data.agentToken,
                data.agentWsUrl
        );
    }

    // -- request / response models --

    public static class StartSessionRequest {
        private String avatarId;

        public String getAvatarId() {
            return avatarId;
        }

        public void setAvatarId(String avatarId) {
            this.avatarId = avatarId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlatformSessionResponse {
        private int code;
        private String message;
        private PlatformSessionData data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public PlatformSessionData getData() {
            return data;
        }

        public void setData(PlatformSessionData data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlatformSessionData {
        private String sessionId;
        private String sfuUrl;
        private String userToken;
        private String agentToken;
        private String agentWsUrl;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSfuUrl() {
            return sfuUrl;
        }

        public void setSfuUrl(String sfuUrl) {
            this.sfuUrl = sfuUrl;
        }

        public String getUserToken() {
            return userToken;
        }

        public void setUserToken(String userToken) {
            this.userToken = userToken;
        }

        public String getAgentToken() {
            return agentToken;
        }

        public void setAgentToken(String agentToken) {
            this.agentToken = agentToken;
        }

        public String getAgentWsUrl() {
            return agentWsUrl;
        }

        public void setAgentWsUrl(String agentWsUrl) {
            this.agentWsUrl = agentWsUrl;
        }
    }

    public static class StartSessionResponse {
        private final String sessionId;
        private final String sfuUrl;
        private final String userToken;
        private final String agentToken;
        private final String agentWsUrl;

        public StartSessionResponse(String sessionId, String sfuUrl, String userToken,
                                     String agentToken, String agentWsUrl) {
            this.sessionId = sessionId;
            this.sfuUrl = sfuUrl;
            this.userToken = userToken;
            this.agentToken = agentToken;
            this.agentWsUrl = agentWsUrl;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getSfuUrl() {
            return sfuUrl;
        }

        public String getUserToken() {
            return userToken;
        }

        public String getAgentToken() {
            return agentToken;
        }

        public String getAgentWsUrl() {
            return agentWsUrl;
        }
    }
}
