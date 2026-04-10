package com.newportai.liveavatar.channel.listener;

import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.Message;

/**
 * Event listener for Live Avatar Channel events
 *
 * <p>Supports both text messages (JSON) and audio frames (binary) over WebSocket.
 *
 */
public interface AvatarChannelListener {

    /**
     * Called when connection is established
     */
    void onConnected();

    /**
     * Called when connection is closed
     *
     * @param code   close code
     * @param reason close reason
     */
    void onClosed(int code, String reason);

    /**
     * Called when connection error occurs
     *
     * @param error error throwable
     */
    void onError(Throwable error);

    /**
     * Called when session is initialized
     *
     * @param message session.init message
     */
    void onSessionInit(Message message);

    /**
     * Called when session is ready
     *
     * @param message session.ready message
     */
    void onSessionReady(Message message);

    /**
     * Called when session state changes
     *
     * @param message session.state message
     */
    void onSessionState(Message message);

    /**
     * Called when session is closing
     *
     * @param message session.closing message
     */
    void onSessionClose(Message message);

    /**
     * Called when text input is received
     *
     * @param message input.text message
     */
    void onInputText(Message message);

    /**
     * Called when voice input starts (input.voice.start)
     *
     * @param message input.voice.start message
     */
    void onInputVoiceStart(Message message);

    /**
     * Called when voice input ends (input.voice.finish)
     *
     * @param message input.voice.finish message
     */
    void onInputVoiceFinish(Message message);

    /**
     * Called when partial ASR result is received
     *
     * @param message input.asr.partial message
     */
    void onAsrPartial(Message message);

    /**
     * Called when final ASR result is received
     *
     * @param message input.asr.final message
     */
    void onAsrFinal(Message message);

    /**
     * Called when response.start is received.
     * Contains optional TTS audio configuration (speed, volume, mood).
     * Only sent when TTS is provided by the Live Avatar Service.
     *
     * @param message response.start message
     */
    void onResponseStart(Message message);

    /**
     * Called when response chunk is received
     *
     * @param message response.chunk message
     */
    void onResponseChunk(Message message);

    /**
     * Called when response is done
     *
     * @param message response.done message
     */
    void onResponseDone(Message message);

    /**
     * Called when response is cancelled
     *
     * @param message response.cancel message
     */
    void onResponseCancel(Message message);

    /**
     * Called when audio output starts (response.audio.start)
     *
     * @param message response.audio.start message
     */
    void onResponseAudioStart(Message message);

    /**
     * Called when audio output ends (response.audio.finish)
     *
     * @param message response.audio.finish message
     */
    void onResponseAudioFinish(Message message);

    /**
     * Called when idle reminder audio starts (response.audio.promptStart)
     *
     * @param message response.audio.promptStart message
     */
    void onResponseAudioPromptStart(Message message);

    /**
     * Called when idle reminder audio ends (response.audio.promptFinish)
     *
     * @param message response.audio.promptFinish message
     */
    void onResponseAudioPromptFinish(Message message);

    /**
     * Called when interrupt control is received
     *
     * @param message control.interrupt message
     */
    void onControlInterrupt(Message message);

    /**
     * Called when idle trigger is received (system.idleTrigger).
     *
     * <p>Sent by the platform when the avatar has been idle for a configured duration.
     * In <b>inbound mode</b> the developer client receives this over the WebSocket.
     * The developer may respond with {@code system.prompt} to make the avatar speak,
     * or ignore it.
     *
     * @param message system.idleTrigger message
     */
    void onSystemIdleTrigger(Message message);

    /**
     * Called when system prompt is received
     *
     * @param message system.prompt message
     */
    void onSystemPrompt(Message message);

    /**
     * Called when error message is received
     *
     * @param message error message
     */
    void onErrorMessage(Message message);

    /**
     * Called when unknown message is received
     *
     * @param message unknown message
     */
    void onUnknownMessage(Message message);

    /**
     * Called when audio frame is received (WebSocket only).
     *
     * <p>Incoming audio frames are TTS audio produced by the developer server and
     * delivered to the live avatar service for playback.
     *
     * <p>Frame structure: [Header (9 bytes)] + [Audio Payload (PCM/Opus)]
     *
     * @param frame audio frame with header and audio payload
     */
    void onAudioFrame(AudioFrame frame);
}
