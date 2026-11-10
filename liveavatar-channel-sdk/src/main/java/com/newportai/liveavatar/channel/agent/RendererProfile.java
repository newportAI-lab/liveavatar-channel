package com.newportai.liveavatar.channel.agent;

/** Session lip-sync model. An omitted value keeps the platform's existing selection rules. */
public enum RendererProfile {
    /** Ordinary human model, without automatic 4K selection. */
    NORMAL("normal"),
    /** Animal lip-sync model. */
    PET("pet"),
    /** 4K lip-sync model. */
    FOUR_K("4k");

    private final String wireValue;
    RendererProfile(String wireValue) { this.wireValue = wireValue; }
    /** Value sent in the session/start rendererProfile field. */
    public String getWireValue() { return wireValue; }
}
