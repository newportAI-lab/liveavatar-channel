package com.newportai.liveavatar.channel.agent;

/**
 * What one interrupt stops.
 *
 * <p>{@code null} and {@link #VOICE_ONLY} stop voice only.
 * {@link #BGM_ONLY} clears queued external audio and leaves voice playing.
 * {@link #VOICE_AND_ACCOMPANIMENT} stops voice and clears queued external audio.
 * Audio that belongs to the base video is never cleared.</p>
 */
public enum InterruptScope {
    VOICE_ONLY,
    VOICE_AND_ACCOMPANIMENT,
    BGM_ONLY
}
