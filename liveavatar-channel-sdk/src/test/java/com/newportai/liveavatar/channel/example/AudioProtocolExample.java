package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.AudioHeader;
import com.newportai.liveavatar.channel.util.AudioFrameBuilder;

/**
 * Audio protocol example — sending and receiving audio frames via WebSocket.
 */
public class AudioProtocolExample {

    public static void main(String[] args) throws InterruptedException, ConnectionException {
        String wsUrl = "ws://localhost:8080/avatar/ws";

        final AvatarWebSocketClient[] ref = new AvatarWebSocketClient[1];
        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, new AgentListener() {
            @Override
            public void onSessionInit() {
                System.out.println("Session ready — sending mock audio frames");
                try { sendMockAudioFrames(ref[0]); } catch (Exception e) { e.printStackTrace(); }
            }

            @Override
            public void onAudioFrame(AudioFrame frame) {
                AudioHeader h = frame.getHeader();
                System.out.printf("Audio frame: seq=%d ts=%d %dHz %s payload=%d bytes%n",
                        h.getSequence(), h.getTimestamp(), h.getSampleRate().getValue(),
                        h.isStereo() ? "Stereo" : "Mono", frame.getPayload().length);
            }

            @Override
            public void onError(String message) {
                System.err.println("Error: " + message);
            }
        });
        ref[0] = client;

        client.connect();
        Thread.sleep(30000);
        client.disconnect();
    }

    private static void sendMockAudioFrames(AvatarWebSocketClient c) throws Exception {
        int samples = 640;
        for (int i = 0; i < 5; i++) {
            AudioFrame f = AudioFrameBuilder.create().sequence(i & 0xFFF)
                    .timestamp((i * 40) & 0xFFFFF)
                    .sampleRate(AudioHeader.SampleRate.RATE_16KHZ)
                    .frameSize(samples).mono().payload(new byte[samples * 2]).build();
            c.sendAudioFrame(f);
            Thread.sleep(40);
        }
        System.out.println("Sent 5 mock audio frames");
    }
}
