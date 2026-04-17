package com.newportai.liveavatar.channel.listener;

import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.Message;

/**
 * Abstract adapter for AvatarChannelListener
 * Provides empty implementations for all methods
 */
public abstract class AvatarChannelListenerAdapter implements AvatarChannelListener {

    @Override
    public void onConnected() {
    }

    @Override
    public void onClosed(int code, String reason) {
    }

    @Override
    public void onError(Throwable error) {
    }

    @Override
    public void onSessionInit(Message message) {
    }

    @Override
    public void onSessionReady(Message message) {
    }

    @Override
    public void onSessionState(Message message) {
    }

    @Override
    public void onSessionClose(Message message) {
    }

    @Override
    public void onInputText(Message message) {
    }

    @Override
    public void onInputVoiceStart(Message message) {
    }

    @Override
    public void onInputVoiceFinish(Message message) {
    }

    @Override
    public void onAsrPartial(Message message) {
    }

    @Override
    public void onAsrFinal(Message message) {
    }

    @Override
    public void onResponseStart(Message message) {
    }

    @Override
    public void onResponseChunk(Message message) {
    }

    @Override
    public void onResponseDone(Message message) {
    }

    @Override
    public void onResponseCancel(Message message) {
    }

    @Override
    public void onResponseAudioStart(Message message) {
    }

    @Override
    public void onResponseAudioFinish(Message message) {
    }

    @Override
    public void onResponseAudioPromptStart(Message message) {
    }

    @Override
    public void onResponseAudioPromptFinish(Message message) {
    }

    @Override
    public void onControlInterrupt(Message message) {
    }

    @Override
    public void onSystemIdleTrigger(Message message) {
    }

    @Override
    public void onSystemPrompt(Message message) {
    }

    @Override
    public void onSceneReady(Message message) {
    }

    @Override
    public void onErrorMessage(Message message) {
    }

    @Override
    public void onUnknownMessage(Message message) {
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
    }
}
