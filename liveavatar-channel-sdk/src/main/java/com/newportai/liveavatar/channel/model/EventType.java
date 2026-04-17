package com.newportai.liveavatar.channel.model;

/**
 * Event types following <domain>.<action>[.<stage>] naming convention
 */
public class EventType {

    // Session events
    public static final String SESSION_INIT = "session.init";
    public static final String SESSION_READY = "session.ready";
    public static final String SESSION_STATE = "session.state";
    public static final String SESSION_CLOSING = "session.closing";

    // Input events
    public static final String INPUT_TEXT = "input.text";
    public static final String INPUT_VOICE_START = "input.voice.start";
    public static final String INPUT_VOICE_FINISH = "input.voice.finish";
    public static final String INPUT_ASR_PARTIAL = "input.asr.partial";
    public static final String INPUT_ASR_FINAL = "input.asr.final";

    // Response events
    public static final String RESPONSE_START = "response.start";
    public static final String RESPONSE_CHUNK = "response.chunk";
    public static final String RESPONSE_DONE = "response.done";
    public static final String RESPONSE_CANCEL = "response.cancel";
    public static final String RESPONSE_AUDIO_START = "response.audio.start";
    public static final String RESPONSE_AUDIO_FINISH = "response.audio.finish";
    public static final String RESPONSE_AUDIO_PROMPT_START = "response.audio.promptStart";
    public static final String RESPONSE_AUDIO_PROMPT_FINISH = "response.audio.promptFinish";

    // Control events
    public static final String CONTROL_INTERRUPT = "control.interrupt";

    // System events
    public static final String SYSTEM_PROMPT = "system.prompt";
    public static final String SYSTEM_IDLE_TRIGGER = "system.idleTrigger";

    // Scene events (LiveKit DataChannel / Scenario 4)
    // Sent by the JS SDK when the scene is ready and the conversation can start.
    public static final String SCENE_READY = "scene.ready";

    // Error events
    public static final String ERROR = "error";

    // Note: WebSocket transport uses native ping/pong control frames.
    // Application-layer ping/pong messages are not used.

    private EventType() {
    }
}
