package com.newportai.liveavatar.channel.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AvatarAgentConfigTest {

    @Test
    public void testVoiceConfigCanBeSet() {
        AvatarAgentConfig.VoiceConfig voiceConfig = AvatarAgentConfig.VoiceConfig.builder()
                .volume(80)
                .speed(1.1)
                .stability(0.7)
                .similarityBoost(0.8)
                .style(0.2)
                .pitch(1.0)
                .build();

        AvatarAgentConfig config = AvatarAgentConfig.builder()
                .apiKey("api_key")
                .avatarId("avatar_id")
                .voiceConfig(voiceConfig)
                .build();

        assertNotNull(config.getVoiceConfig());
        assertEquals(Integer.valueOf(80), config.getVoiceConfig().getVolume());
        assertEquals(Double.valueOf(1.1), config.getVoiceConfig().getSpeed());
        assertEquals(Double.valueOf(0.7), config.getVoiceConfig().getStability());
        assertEquals(Double.valueOf(0.8), config.getVoiceConfig().getSimilarityBoost());
        assertEquals(Double.valueOf(0.2), config.getVoiceConfig().getStyle());
        assertEquals(Double.valueOf(1.0), config.getVoiceConfig().getPitch());
    }

    @Test
    public void testStartRequestIncludesVoiceConfig() throws Exception {
        AvatarAgentConfig config = AvatarAgentConfig.builder()
                .apiKey("api_key")
                .avatarId("avatar_id")
                .voiceConfig(AvatarAgentConfig.VoiceConfig.builder()
                        .volume(80)
                        .speed(1.1)
                        .stability(0.7)
                        .similarityBoost(0.8)
                        .style(0.2)
                        .pitch(1.0)
                        .build())
                .build();
        AvatarAgent agent = AvatarAgent.builder()
                .config(config)
                .listener(new AgentListener() {})
                .build();

        Method buildStartRequest = AvatarAgent.class.getDeclaredMethod("buildStartRequest");
        buildStartRequest.setAccessible(true);
        String json = (String) buildStartRequest.invoke(agent);

        @SuppressWarnings("unchecked")
        Map<String, Object> request = new ObjectMapper().readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> voiceConfig = (Map<String, Object>) request.get("voiceConfig");

        assertEquals("avatar_id", request.get("avatarId"));
        assertEquals("websocketAgent", request.get("mode"));
        assertNotNull(voiceConfig);
        assertEquals(80, ((Number) voiceConfig.get("volume")).intValue());
        assertEquals(1.1, ((Number) voiceConfig.get("speed")).doubleValue(), 0.0);
        assertEquals(0.7, ((Number) voiceConfig.get("stability")).doubleValue(), 0.0);
        assertEquals(0.8, ((Number) voiceConfig.get("similarityBoost")).doubleValue(), 0.0);
        assertEquals(0.2, ((Number) voiceConfig.get("style")).doubleValue(), 0.0);
        assertEquals(1.0, ((Number) voiceConfig.get("pitch")).doubleValue(), 0.0);
    }
}
