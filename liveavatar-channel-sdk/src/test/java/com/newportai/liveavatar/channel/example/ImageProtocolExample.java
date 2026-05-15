package com.newportai.liveavatar.channel.example;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.model.ImageFrame;
import com.newportai.liveavatar.channel.model.ImageHeader;
import com.newportai.liveavatar.channel.util.ImageFrameBuilder;

/**
 * Image protocol example — sending image frames via WebSocket.
 */
public class ImageProtocolExample {

    public static void main(String[] args) throws InterruptedException, ConnectionException {
        String wsUrl = "wss://facemarket.ai/vih/dispatcher/v1/ws/agent?token=...";

        final AvatarWebSocketClient[] ref = new AvatarWebSocketClient[1];
        AvatarWebSocketClient client = new AvatarWebSocketClient(wsUrl, new AgentListener() {
            @Override
            public void onSessionInit() {
                System.out.println("Session ready — sending mock image");
                try { sendMockImage(ref[0]); } catch (Exception e) { e.printStackTrace(); }
            }

            @Override
            public void onError(String message) {
                System.err.println("Error: " + message);
            }
        });
        ref[0] = client;

        client.connect();
        Thread.sleep(10000);
        client.disconnect();
    }

    private static void sendMockImage(AvatarWebSocketClient c) throws Exception {
        ImageHeader h = new ImageHeader();
        h.setFormat(ImageHeader.Format.JPG);
        h.setWidth(640);
        h.setHeight(480);
        h.setQuality(80);
        ImageFrame frame = ImageFrameBuilder.create()
                .format(ImageHeader.Format.JPG).width(640).height(480).quality(80)
                .payload(new byte[1024]).build();
        c.sendImageFrame(frame);
        System.out.println("Sent image frame: " + frame.getFrameSize() + " bytes");
    }
}
