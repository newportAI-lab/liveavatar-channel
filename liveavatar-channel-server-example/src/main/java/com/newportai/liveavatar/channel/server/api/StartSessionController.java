package com.newportai.liveavatar.channel.server.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API for session provisioning — proxies to the Live Avatar platform dispatcher.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code POST /api/session/start} — create or reconnect a session</li>
 *   <li>{@code POST /api/session/stop}  — end an active session</li>
 * </ul>
 *
 * <p>Works for both outbound and inbound modes. In inbound mode (§6.2),
 * the caller uses the returned {@code agentWsUrl} to connect to the platform
 * via {@code AvatarWebSocketClient}.
 *
 * @see com.newportai.liveavatar.channel.client.AvatarWebSocketClient
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

    @Value("${avatar.sandbox.enabled:false}")
    private boolean sandboxEnabled;

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) StartSessionRequest request) {
        String avatarId = (request != null && request.getAvatarId() != null)
                ? request.getAvatarId()
                : defaultAvatarId;
        String sessionId = (request != null) ? request.getSessionId() : null;
        String voiceId = (request != null) ? request.getVoiceId() : null;

        String url = apiBaseUrl + "/vih/dispatcher/v1/session/start";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sandboxEnabled) {
            headers.set("X-Env-Sandbox", "true");
            logger.info("Sandbox mode enabled — routing session to sandbox environment");
        }

        Map<String, String> body = new HashMap<>();
        body.put("avatarId", avatarId);
        if (sessionId != null && !sessionId.isEmpty()) {
            body.put("sessionId", sessionId);
            logger.info("Reconnect mode: reusing sessionId={}", sessionId);
        }
        if (voiceId != null && !voiceId.isEmpty()) {
            body.put("voiceId", voiceId);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        logger.info("Calling platform dispatcher: url={}, avatarId={}, sessionId={}", url, avatarId, sessionId);

        try {
            ResponseEntity<PlatformSessionResponse> response =
                    restTemplate.postForEntity(url, entity, PlatformSessionResponse.class);

            PlatformSessionResponse apiResponse = response.getBody();
            if (apiResponse == null) {
                logger.error("Empty response from platform dispatcher");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(-1, "No response from platform dispatcher"));
            }
            if (apiResponse.code != 0) {
                return mapPlatformError(apiResponse);
            }

            PlatformSessionData data = apiResponse.data;
            logger.info("Session started via platform: sessionId={}", data.sessionId);

            return ResponseEntity.ok(new StartSessionResponse(
                    data.sessionId,
                    data.sfuUrl,
                    data.userToken,
                    data.agentToken,
                    data.agentWsUrl
            ));

        } catch (HttpClientErrorException e) {
            logger.error("Platform API client error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(-1, "Platform API error: " + e.getMessage()));
        } catch (HttpServerErrorException e) {
            logger.error("Platform API server error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(-1, "Platform temporarily unavailable. Please retry in a few seconds."));
        } catch (Exception e) {
            logger.error("Unexpected error calling platform API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(-1, "Internal error: " + e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop(@RequestBody StopSessionRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorBody(-1, "sessionId is required"));
        }

        String url = apiBaseUrl + "/vih/dispatcher/v1/session/stop";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Collections.singletonMap("sessionId", request.getSessionId());
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        logger.info("Stopping session via platform: sessionId={}", request.getSessionId());

        try {
            ResponseEntity<PlatformSessionResponse> response =
                    restTemplate.postForEntity(url, entity, PlatformSessionResponse.class);

            PlatformSessionResponse apiResponse = response.getBody();
            if (apiResponse == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(errorBody(-1, "No response from platform"));
            }
            if (apiResponse.code != 0) {
                return mapPlatformError(apiResponse);
            }

            logger.info("Session stopped: sessionId={}", request.getSessionId());
            return ResponseEntity.ok(errorBody(0, "success"));

        } catch (Exception e) {
            logger.error("Error stopping session: {}", request.getSessionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(-1, "Failed to stop session: " + e.getMessage()));
        }
    }

    /**
     * Map platform error codes to appropriate HTTP responses.
     *
     * <p>Error codes per integration guide §X:
     * <ul>
     *   <li>40001-40003: System errors (retryable) → 503</li>
     *   <li>40004: Unidentified principal → 401</li>
     *   <li>40005: Concurrency limit → 429</li>
     *   <li>40006: Quota exhausted → 429</li>
     *   <li>40007: Session access denied → 403</li>
     * </ul>
     */
    private ResponseEntity<?> mapPlatformError(PlatformSessionResponse apiResponse) {
        int code = apiResponse.code;
        String message = apiResponse.message != null ? apiResponse.message : "Unknown platform error";

        HttpStatus status;
        switch (code) {
            case 40004:
                status = HttpStatus.UNAUTHORIZED;
                break;
            case 40007:
                status = HttpStatus.FORBIDDEN;
                break;
            case 40005:
            case 40006:
                status = HttpStatus.TOO_MANY_REQUESTS;
                break;
            case 40001:
            case 40002:
            case 40003:
                status = HttpStatus.SERVICE_UNAVAILABLE;
                break;
            default:
                status = HttpStatus.BAD_GATEWAY;
                break;
        }

        logger.error("Platform error: code={}, message={}", code, message);
        return ResponseEntity.status(status)
                .body(errorBody(code, message));
    }

    private static Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        return map;
    }

    // -- request / response models --

    public static class StartSessionRequest {
        private String avatarId;
        private String sessionId;
        private String voiceId;

        public String getAvatarId() { return avatarId; }
        public void setAvatarId(String avatarId) { this.avatarId = avatarId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getVoiceId() { return voiceId; }
        public void setVoiceId(String voiceId) { this.voiceId = voiceId; }
    }

    public static class StopSessionRequest {
        private String sessionId;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlatformSessionResponse {
        private int code;
        private String message;
        private PlatformSessionData data;

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public PlatformSessionData getData() { return data; }
        public void setData(PlatformSessionData data) { this.data = data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlatformSessionData {
        private String sessionId;
        private String sfuUrl;
        private String userToken;
        private String agentToken;
        private String agentWsUrl;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getSfuUrl() { return sfuUrl; }
        public void setSfuUrl(String sfuUrl) { this.sfuUrl = sfuUrl; }

        public String getUserToken() { return userToken; }
        public void setUserToken(String userToken) { this.userToken = userToken; }

        public String getAgentToken() { return agentToken; }
        public void setAgentToken(String agentToken) { this.agentToken = agentToken; }

        public String getAgentWsUrl() { return agentWsUrl; }
        public void setAgentWsUrl(String agentWsUrl) { this.agentWsUrl = agentWsUrl; }
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

        public String getSessionId() { return sessionId; }
        public String getSfuUrl() { return sfuUrl; }
        public String getUserToken() { return userToken; }
        public String getAgentToken() { return agentToken; }
        public String getAgentWsUrl() { return agentWsUrl; }
    }
}
