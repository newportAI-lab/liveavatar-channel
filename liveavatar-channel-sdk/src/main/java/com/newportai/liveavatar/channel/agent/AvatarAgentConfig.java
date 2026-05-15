package com.newportai.liveavatar.channel.agent;


/**
 * Immutable configuration for {@link AvatarAgent}.
 *
 * <p>Create via the fluent builder:
 * <pre>{@code
 * AvatarAgentConfig config = AvatarAgentConfig.builder()
 *     .apiKey("lk_live_...")
 *     .avatarId("avatar_...")
 *     .sandbox(true)
 *     .build();
 * }</pre>
 */
public final class AvatarAgentConfig {

    private final String apiKey;
    private final String avatarId;
    private final String baseUrl;
    private final boolean sandbox;
    private final boolean developerAsr;
    private final boolean developerTts;
    private final boolean reconnectEnabled;
    private final String voiceId;   // nullable
    private final String userId;    // nullable

    private AvatarAgentConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.avatarId = builder.avatarId;
        this.baseUrl = builder.baseUrl;
        this.sandbox = builder.sandbox;
        this.developerAsr = builder.developerAsr;
        this.developerTts = builder.developerTts;
        this.reconnectEnabled = builder.reconnectEnabled;
        this.voiceId = builder.voiceId;
        this.userId = builder.userId;
    }

    public static Builder builder() { return new Builder(); }

    // ── Getters ──

    public String getApiKey() { return apiKey; }
    public String getAvatarId() { return avatarId; }
    public String getBaseUrl() { return baseUrl; }
    public boolean isSandbox() { return sandbox; }
    public boolean isDeveloperAsr() { return developerAsr; }
    public boolean isDeveloperTts() { return developerTts; }
    public boolean isReconnectEnabled() { return reconnectEnabled; }
    public String getVoiceId() { return voiceId; }   // nullable
    public String getUserId() { return userId; }     // nullable

    // ── Builder ──

    public static class Builder {
        private String apiKey;
        private String avatarId;
        private String baseUrl = "https://facemarket.ai";
        private boolean sandbox;
        private boolean developerAsr;
        private boolean developerTts;
        private boolean reconnectEnabled = true;
        private String voiceId;   // nullable
        private String userId;    // nullable

        /** API Key from the console (required). */
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }

        /** Avatar ID from the console (required). */
        public Builder avatarId(String avatarId) { this.avatarId = avatarId; return this; }

        /** Platform dispatcher base URL. Default: {@code https://facemarket.ai}. */
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }

        /** Enable sandbox environment (30 free min/month). Default: false. */
        public Builder sandbox(boolean sandbox) { this.sandbox = sandbox; return this; }

        /** Developer provides ASR (Omni mode). Default: false (platform ASR). */
        public Builder developerAsr(boolean developerAsr) { this.developerAsr = developerAsr; return this; }

        /** Developer provides TTS. Default: false (platform TTS). */
        public Builder developerTts(boolean developerTts) { this.developerTts = developerTts; return this; }

        /** Enable auto-reconnect with exponential backoff. Default: true. */
        public Builder reconnectEnabled(boolean reconnectEnabled) { this.reconnectEnabled = reconnectEnabled; return this; }

        /** Optional voice ID to override the avatar default. */
        public Builder voiceId(String voiceId) { this.voiceId = voiceId; return this; }

        /** Optional user identifier included in session.init. */
        public Builder userId(String userId) { this.userId = userId; return this; }

        /**
         * Builds the config.
         * @throws IllegalStateException if apiKey or avatarId is null or blank
         */
        public AvatarAgentConfig build() {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("apiKey is required");
            }
            if (avatarId == null || avatarId.trim().isEmpty()) {
                throw new IllegalStateException("avatarId is required");
            }
            return new AvatarAgentConfig(this);
        }
    }
}
