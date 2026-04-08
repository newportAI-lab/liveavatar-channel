package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.listener.AvatarChannelListenerAdapter;
import com.newportai.liveavatar.channel.model.ImageFrame;
import com.newportai.liveavatar.channel.model.ImageHeader;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.util.ImageFrameBuilder;
import com.newportai.liveavatar.channel.util.MessageBuilder;

/**
 * Image protocol example
 *
 * <p>Demonstrates how to send image frames through WebSocket for multimodal input.
 *
 * <p><b>Important:</b>
 * <ul>
 *   <li>Image protocol is only supported on WebSocket channel</li>
 *   <li>The imageId field can be used to correlate fragmented image chunks</li>
 * </ul>
 *
 * <p>Image frame format:
 * <pre>
 * [Header (12 bytes)] + [Image Payload (JPG/PNG/WebP/GIF/AVIF)]
 * </pre>
 */
public class ImageProtocolExample {

    public static void main(String[] args) throws InterruptedException {
        // TODO: Replace with your WebSocket server URL
        String wsUrl = "ws://localhost:8080/avatar/ws";
        String sessionId = "sess_" + System.currentTimeMillis();

        final AvatarWebSocketClient[] clientHolder = new AvatarWebSocketClient[1];

        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl,
                new AvatarChannelListenerAdapter() {

            private int imageId = 0;

            @Override
            public void onConnected() {
                System.out.println("Connected to server");
                try {
                    Message initMsg = MessageBuilder.sessionInit(sessionId, "user_123");
                    clientHolder[0].sendMessage(initMsg);
                    System.out.println("Sent session.init");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSessionReady(Message message) {
                System.out.println("Session ready");
                try {
                    sendMockImageFrames(clientHolder[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Note: the live avatar service (SDK client) SENDS image frames to the developer
            // server — it does NOT receive them. onAudioFrame() is the only binary callback
            // here, for incoming TTS audio from the developer server.

            @Override
            public void onError(Throwable error) {
                System.err.println("Error: " + error.getMessage());
            }

            private void sendMockImageFrames(AvatarWebSocketClient c) throws Exception {
                System.out.println("Sending mock image frames...\n");

                // Example: send a JPEG and a PNG frame
                sendMockImage(c, ImageHeader.Format.JPG, 1280, 720, 85);
                Thread.sleep(50);
                sendMockImage(c, ImageHeader.Format.PNG, 640, 480, 0 /* lossless */);

                System.out.println("Sent " + imageId + " image frames");
            }

            private void sendMockImage(AvatarWebSocketClient c,
                                       ImageHeader.Format format,
                                       int width, int height, int quality) throws Exception {
                // In real usage this would be actual image bytes
                byte[] mockImageData = new byte[width * height / 8]; // mock compressed size

                ImageFrame frame = ImageFrameBuilder.create()
                        .format(format)
                        .quality(quality)
                        .imageId(imageId++)
                        .width(width)
                        .height(height)
                        .payload(mockImageData)
                        .build();

                c.sendImageFrame(frame);

                System.out.println("Sent image frame #" + (imageId - 1) +
                        " format=" + format.getName() +
                        " size=" + width + "x" + height +
                        " payload=" + mockImageData.length + " bytes");
            }
        });

        clientHolder[0] = client;

        try {
            client.connect();
            System.out.println("Client started\n");
            Thread.sleep(5000);
        } catch (ConnectionException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            client.disconnect();
            System.out.println("Disconnected");
        }
    }
}
