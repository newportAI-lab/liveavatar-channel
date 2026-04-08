package com.newportai.liveavatar.channel.model;

/**
 * Session state enumeration
 * Defines all possible session states
 */
public enum SessionState {

    /**
     * No one is speaking, waiting for input
     */
    IDLE("IDLE"),

    /**
     * User is speaking, ASR is capturing audio
     */
    LISTENING("LISTENING"),

    /**
     * System (brain) is thinking, LLM/TTS preparation
     */
    THINKING("THINKING"),

    /**
     * System (body) is staging, preparing to generate live avatar
     */
    STAGING("STAGING"),

    /**
     * System (body) is speaking, live avatar is normally responding
     */
    SPEAKING("SPEAKING"),

    /**
     * System (brain) is thinking for prompt, preparing reminder script
     */
    PROMPT_THINKING("PROMPT_THINKING"),

    /**
     * System (body) is staging for prompt, preparing to generate live avatar
     */
    PROMPT_STAGING("PROMPT_STAGING"),

    /**
     * System (body) is speaking prompt, live avatar is broadcasting reminder voice
     */
    PROMPT_SPEAKING("PROMPT_SPEAKING");

    private final String value;

    SessionState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Get SessionState from string value
     *
     * @param value state value
     * @return SessionState enum
     */
    public static SessionState fromValue(String value) {
        for (SessionState state : SessionState.values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
