package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.AudioFrame;
import com.newportai.liveavatar.channel.model.AudioHeader;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.util.AudioFrameBuilder;
import com.newportai.liveavatar.channel.util.MessageBuilder;

import java.util.Random;

/**
 * Audio protocol example
 *
 * <p>Demonstrates how to send and receive audio frames through WebSocket.
 *
 * <p>Audio frame format:
 * <pre>
 * [Header (9 bytes)] + [Audio Payload (PCM data)]
 * </pre>
 */
public class AudioProtocolExample {

    public static void main(String[] args) throws InterruptedException {
        // TODO: Replace with your WebSocket server URL
        String wsUrl = "ws://localhost:8080/avatar/ws";
        String sessionId = "sess_" + System.currentTimeMillis();

        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
                new AvatarChannelListenerAdapter() {

            private int audioSeq = 0;

            @Override
            public void onConnected() {
                System.out.println("✅ Connected to server");

                try {
                    // Initialize session
                    Message initMsg = MessageBuilder.sessionInit(sessionId, "user_123");
                    clientHolder[0].sendMessage(initMsg);
                    System.out.println("📤 Sent session.init");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("✅ Session ready");

                try {
                    // Send some mock audio frames
                    sendMockAudioFrames(clientHolder[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAudioFrame(AudioFrame frame) {
                AudioHeader header = frame.getHeader();
                System.out.println("🔊 Received audio frame:");
                System.out.println("   Sequence: " + header.getSequence());
                System.out.println("   Timestamp: " + header.getTimestamp() + " ms");
                System.out.println("   Sample Rate: " + header.getSampleRate().getValue() + " Hz");
                System.out.println("   Channels: " + (header.isStereo() ? "Stereo" : "Mono"));
                System.out.println("   First Frame: " + header.isFirstFrame());
                System.out.println("   Payload Size: " + frame.getPayload().length + " bytes");
                System.out.println();
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("❌ Error: " + error.getMessage());
                error.printStackTrace();
            }

            /**
             * Send mock audio frames for demonstration
             */
            private void sendMockAudioFrames(AvatarWebSocketClient client) throws Exception {
                System.out.println("📤 Sending mock audio frames...\n");

                // Send 5 audio frames as example
                for (int i = 0; i < 5; i++) {
                    // Create mock PCM data (in real scenario, this would be actual audio)
                    byte[] mockPcmData = generateMockPcmData(960); // 40ms @ 24kHz mono

                    // Build audio frame
                    AudioFrame frame = AudioFrameBuilder.create()
                            .mono()  // or .stereo(true)
                            .firstFrame(i == 0)  // Mark first frame
                            .sequence(audioSeq & 0xFFF)              // 12-bit, wraps at 4096
                            .timestamp((int) (System.currentTimeMillis() & 0xFFFFF)) // 20-bit ms
                            .sampleRate(AudioHeader.SampleRate.RATE_24KHZ)
                            .frameSize(960)
                            .payload(mockPcmData)
                            .build();

                    // Send audio frame
                    client.sendAudioFrame(frame);
                    audioSeq++;

                    System.out.println("📤 Sent audio frame #" + i +
                            " (seq=" + frame.getHeader().getSequence() +
                            ", size=" + mockPcmData.length + " bytes)");

                    // Simulate 10ms frame interval
                    Thread.sleep(10);
                }

                System.out.println("\n✅ Sent " + audioSeq + " audio frames");
            }

            /**
             * Generate mock PCM data for demonstration
             * In real scenario, this would be actual audio data from microphone
             */
            private byte[] generateMockPcmData(int samples) {
                // PCM 16-bit format: 2 bytes per sample
                byte[] pcmData = new byte[samples * 2];
                Random random = new Random();

                // Generate random noise (not actual audio, just for demo)
                for (int i = 0; i < pcmData.length; i += 2) {
                    short sample = (short) (random.nextInt(2000) - 1000);
                    pcmData[i] = (byte) (sample & 0xFF);
                    pcmData[i + 1] = (byte) ((sample >> 8) & 0xFF);
                }

                return pcmData;
            }
        });

        clientHolder[0] = client;

        try {
            client.connect();
            System.out.println("🚀 Client started\n");

            // Keep alive for demo
            Thread.sleep(5000);

        } catch (ConnectionException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            client.disconnect();
            System.out.println("\n👋 Disconnected");
        }
    }
}
