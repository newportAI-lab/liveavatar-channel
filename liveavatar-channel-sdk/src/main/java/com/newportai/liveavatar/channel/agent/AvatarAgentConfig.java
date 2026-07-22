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
    private final String rtcProvider; // nullable
    private final VoiceConfig voiceConfig; // nullable

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
        this.rtcProvider = builder.rtcProvider;
        this.voiceConfig = builder.voiceConfig;
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
    public String getRtcProvider() { return rtcProvider; } // nullable
    public VoiceConfig getVoiceConfig() { return voiceConfig; } // nullable

    // ── Builder ──

    public static class Builder {
        private String apiKey;
        private String avatarId;
        private String baseUrl = "https://facemarket.ai";
        private boolean sandbox;
        private boolean developerAsr = true;
        private boolean developerTts;
        private boolean reconnectEnabled = true;
        private String voiceId;   // nullable
        private String userId;    // nullable
        private String rtcProvider; // nullable
        private VoiceConfig voiceConfig; // nullable

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

        /** Optional RTC provider included in /session/start. */
        public Builder rtcProvider(String rtcProvider) { this.rtcProvider = rtcProvider; return this; }

        /** Optional voice runtime configuration. */
        public Builder voiceConfig(VoiceConfig voiceConfig) { this.voiceConfig = voiceConfig; return this; }

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

    /**
     * Optional voice runtime configuration.
     *
     * <p>All fields are nullable so callers may set only the options they need.
     */
    public static final class VoiceConfig {
        private final Integer volume;
        private final Double speed;
        private final Double stability;
        private final Double similarityBoost;
        private final Double style;
        private final Double pitch;

        private VoiceConfig(Builder builder) {
            this.volume = builder.volume;
            this.speed = builder.speed;
            this.stability = builder.stability;
            this.similarityBoost = builder.similarityBoost;
            this.style = builder.style;
            this.pitch = builder.pitch;
        }

        public static Builder builder() { return new Builder(); }

        public Integer getVolume() { return volume; }
        public Double getSpeed() { return speed; }
        public Double getStability() { return stability; }
        public Double getSimilarityBoost() { return similarityBoost; }
        public Double getStyle() { return style; }
        public Double getPitch() { return pitch; }

        public static class Builder {
            private Integer volume;
            private Double speed;
            private Double stability;
            private Double similarityBoost;
            private Double style;
            private Double pitch;

            /** Volume. */
            public Builder volume(Integer volume) { this.volume = volume; return this; }

            /** Speed. */
            public Builder speed(Double speed) { this.speed = speed; return this; }

            /** Stability, usually 0.0-1.0. */
            public Builder stability(Double stability) { this.stability = stability; return this; }

            /** Similarity boost, usually 0.0-1.0. */
            public Builder similarityBoost(Double similarityBoost) { this.similarityBoost = similarityBoost; return this; }

            /** Style, usually 0.0-1.0. */
            public Builder style(Double style) { this.style = style; return this; }

            /** Pitch, usually 0.0-2.0. */
            public Builder pitch(Double pitch) { this.pitch = pitch; return this; }

            public VoiceConfig build() { return new VoiceConfig(this); }
        }
    }
}
