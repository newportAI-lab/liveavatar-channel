package com.newportai.liveavatar.channel.server.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages avatar sessions with thread-safe operations
 */
@Component
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<String, AvatarSession> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> wsIdToAvatarId = new ConcurrentHashMap<>();

    /**
     * Pending inbound-mode sessions keyed by agentToken (single-use).
     * Populated by the session-start REST API; consumed when the WebSocket connection is validated.
     */
    private final ConcurrentHashMap<String, String> pendingAgentTokens = new ConcurrentHashMap<>();

    /**
     * Register a pending inbound-mode session.
     *
     * <p>Called by the session-start REST API. The agentToken is single-use and
     * bound to the sessionId. It is consumed (removed) when the connecting party
     * presents it during WebSocket handshake.
     *
     * @param sessionId  the pre-allocated session ID
     * @param agentToken the one-time token to be embedded in agentWsUrl
     */
    public void registerPendingSession(String sessionId, String agentToken) {
        pendingAgentTokens.put(agentToken, sessionId);
        logger.info("Registered pending session: {} (agentToken prefix: {}...)", sessionId, agentToken.substring(0, 8));
    }

    /**
     * Validate an agentToken and consume it atomically (single-use enforcement).
     *
     * @param agentToken the token presented during WebSocket connection
     * @return {@code true} if the token was valid and has been consumed; {@code false} otherwise
     */
    public boolean validateAndConsumeAgentToken(String agentToken) {
        String sessionId = pendingAgentTokens.remove(agentToken);
        if (sessionId != null) {
            logger.info("agentToken consumed for session: {}", sessionId);
            return true;
        }
        String prefix = agentToken.length() > 8 ? agentToken.substring(0, 8) : agentToken;
        logger.warn("Invalid or already-used agentToken: {}...", prefix);
        return false;
    }

    /**
     * Initialize a new session
     */
    public void initSession(String wsSessionId, String avatarSessionId, String userId) {
        AvatarSession session = new AvatarSession(avatarSessionId, userId, wsSessionId);
        sessionMap.put(avatarSessionId, session);
        wsIdToAvatarId.put(wsSessionId, avatarSessionId);
        logger.info("Session initialized: {} for user: {}", avatarSessionId, userId);
    }

    /**
     * Get session by WebSocket session ID
     */
    public AvatarSession getSessionByWsId(String wsSessionId) {
        String avatarId = wsIdToAvatarId.get(wsSessionId);
        return avatarId != null ? sessionMap.get(avatarId) : null;
    }

    /**
     * Get session by avatar session ID
     */
    public AvatarSession getSession(String avatarSessionId) {
        return sessionMap.get(avatarSessionId);
    }

    /**
     * Remove a session
     */
    public void removeSession(String wsSessionId) {
        String avatarId = wsIdToAvatarId.remove(wsSessionId);
        if (avatarId != null) {
            AvatarSession session = sessionMap.remove(avatarId);
            if (session != null) {
                // Cancel any active response
                if (session.hasActiveResponse()) {
                    session.cancelCurrentResponse();
                }
                // Clear audio buffer
                session.clearAudioBuffer();
                logger.info("Session removed: {}", avatarId);
            }
        }
    }

    /**
     * Get total number of active sessions
     */
    public int getActiveSessionCount() {
        return sessionMap.size();
    }
}
