package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.model.SessionState;
import com.newportai.liveavatar.channel.util.MessageBuilder;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;

/**
 * Message Format Demo
 */
public class MessageFormatDemo {

    public static void main(String[] args) throws MessageSerializationException {

        System.out.println("=== Live Avatar Channel Protocol Message Format Demo ===\n");

        // 1. Session message
        System.out.println("1️⃣  Session Messages:\n");

        System.out.println("session.init:");
        Message sessionInit = MessageBuilder.sessionInit("sess_123", "user_1");
        System.out.println(MessageBuilder.toJson(sessionInit));
        System.out.println();

        System.out.println("session.ready:");
        Message sessionReady = MessageBuilder.sessionReady();
        System.out.println(MessageBuilder.toJson(sessionReady));
        System.out.println();

        System.out.println("session.state (SPEAKING):");
        Message sessionState = MessageBuilder.sessionState(SessionState.SPEAKING.getValue(), 5);
        System.out.println(MessageBuilder.toJson(sessionState));
        System.out.println();

        System.out.println("session.state (LISTENING):");
        Message listeningState = MessageBuilder.sessionState(SessionState.LISTENING.getValue(), 3);
        System.out.println(MessageBuilder.toJson(listeningState));
        System.out.println();

        System.out.println("session.closing:");
        Message sessionClose = MessageBuilder.sessionClose("timeout");
        System.out.println(MessageBuilder.toJson(sessionClose));
        System.out.println();

        // 2. Input message
        System.out.println("2️⃣  Input Messages:\n");

        System.out.println("input.text:");
        Message inputText = MessageBuilder.inputText("req_1", "what's your name?");
        System.out.println(MessageBuilder.toJson(inputText));
        System.out.println();

        System.out.println("input.voice.start:");
        Message inputVoiceStart = MessageBuilder.inputVoiceStart("req_2");
        System.out.println(MessageBuilder.toJson(inputVoiceStart));
        System.out.println();

        System.out.println("input.voice.finish:");
        Message inputVoiceFinish = MessageBuilder.inputVoiceFinish("req_2");
        System.out.println(MessageBuilder.toJson(inputVoiceFinish));
        System.out.println();

        System.out.println("input.asr.partial:");
        Message asrPartial = MessageBuilder.asrPartial("req_2", 3, "what's");
        System.out.println(MessageBuilder.toJson(asrPartial));
        System.out.println();

        System.out.println("input.asr.final:");
        Message asrFinal = MessageBuilder.asrFinal("req_2", "what's your name?");
        System.out.println(MessageBuilder.toJson(asrFinal));
        System.out.println();

        // 3. Response message
        System.out.println("3️⃣  Response Messages:\n");

        System.out.println("response.chunk:");
        Message responseChunk = MessageBuilder.responseChunk("req_1", "res_1", 12, "你好");
        System.out.println(MessageBuilder.toJson(responseChunk));
        System.out.println();

        System.out.println("response.done:");
        Message responseDone = MessageBuilder.responseDone("req_1", "res_1");
        System.out.println(MessageBuilder.toJson(responseDone));
        System.out.println();

        System.out.println("response.audio.start:");
        Message responseAudioStart = MessageBuilder.responseAudioStart("req_1", "res_1");
        System.out.println(MessageBuilder.toJson(responseAudioStart));
        System.out.println();

        System.out.println("response.audio.finish:");
        Message responseAudioFinish = MessageBuilder.responseAudioFinish("req_1", "res_1");
        System.out.println(MessageBuilder.toJson(responseAudioFinish));
        System.out.println();

        System.out.println("response.cancel:");
        Message responseCancel = MessageBuilder.responseCancel("res_1");
        System.out.println(MessageBuilder.toJson(responseCancel));
        System.out.println();

        // 4. Control message
        System.out.println("4️⃣  Control Messages:\n");

        System.out.println("control.interrupt:");
        Message controlInterrupt = MessageBuilder.controlInterrupt();
        System.out.println(MessageBuilder.toJson(controlInterrupt));
        System.out.println();

        // 5. System message
        System.out.println("5️⃣  System Messages:\n");

        System.out.println("system.prompt:");
        Message systemPrompt = MessageBuilder.systemPrompt("Are you still there?");
        System.out.println(MessageBuilder.toJson(systemPrompt));
        System.out.println();

        System.out.println("response.audio.promptStart:");
        Message promptStart = MessageBuilder.responseAudioPromptStart();
        System.out.println(MessageBuilder.toJson(promptStart));
        System.out.println();

        System.out.println("response.audio.promptFinish:");
        Message promptFinish = MessageBuilder.responseAudioPromptFinish();
        System.out.println(MessageBuilder.toJson(promptFinish));
        System.out.println();

        // 6. Error Messages
        System.out.println("6️⃣  Error Messages:\n");

        System.out.println("error:");
        Message error = MessageBuilder.error("req_1", "ASR_FAIL", "audio decode error");
        System.out.println(MessageBuilder.toJson(error));
        System.out.println();

        // 7. All Session States
        System.out.println("7️⃣  All Session States:\n");
        for (SessionState state : SessionState.values()) {
            Message stateMsg = MessageBuilder.sessionState(state.getValue(), 1);
            System.out.println(state + ": " + MessageBuilder.toJson(stateMsg));
        }
        System.out.println();

        System.out.println("=== Demo Complete ===");
    }
}
