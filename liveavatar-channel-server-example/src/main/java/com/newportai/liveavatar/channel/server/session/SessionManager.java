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
