package com.newportai.liveavatar.channel.client;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.model.EventType;
import com.newportai.liveavatar.channel.model.Message;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AvatarWebSocketClientTest {

    @Test
    public void testSceneReadyDispatchesToListener() throws Exception {
        AtomicBoolean sceneReadyCalled = new AtomicBoolean(false);
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {
            @Override
            public void onSceneReady() {
                sceneReadyCalled.set(true);
            }
        });

        Method handleMessage = AvatarWebSocketClient.class.getDeclaredMethod("handleMessage", Message.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(client, new Message(EventType.SCENE_READY));

        assertTrue(sceneReadyCalled.get());
    }

    @Test
    public void testProtocolErrorDoesNotReconnect() throws Exception {
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {});
        client.enableAutoReconnect();

        Method shouldReconnectAfterClose = AvatarWebSocketClient.class
                .getDeclaredMethod("shouldReconnectAfterClose", int.class);
        shouldReconnectAfterClose.setAccessible(true);

        assertFalse((Boolean) shouldReconnectAfterClose.invoke(client, 1002));
        assertTrue((Boolean) shouldReconnectAfterClose.invoke(client, 1006));
    }

    @Test
    public void testSessionInitResetsReconnectAttempts() throws Exception {
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {});

        Field reconnectAttempts = AvatarWebSocketClient.class.getDeclaredField("reconnectAttempts");
        reconnectAttempts.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger) reconnectAttempts.get(client)).set(3);

        Field connected = AvatarWebSocketClient.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.set(client, true);

        Field webSocket = AvatarWebSocketClient.class.getDeclaredField("webSocket");
        webSocket.setAccessible(true);
        webSocket.set(client, new FakeWebSocket());

        Method handleMessage = AvatarWebSocketClient.class.getDeclaredMethod("handleMessage", Message.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(client, new Message(EventType.SESSION_INIT));

        assertEquals(0, client.getReconnectAttempts());
    }

    private static class FakeWebSocket implements WebSocket {
        @Override
        public Request request() {
            return new Request.Builder().url("ws://localhost").build();
        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public boolean send(String text) {
            return true;
        }

        @Override
        public boolean send(ByteString bytes) {
            return true;
        }

        @Override
        public boolean close(int code, String reason) {
            return true;
        }

        @Override
        public void cancel() {
        }
    }
}
