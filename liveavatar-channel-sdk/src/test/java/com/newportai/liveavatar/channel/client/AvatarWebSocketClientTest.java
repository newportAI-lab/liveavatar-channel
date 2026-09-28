package com.newportai.liveavatar.channel.client;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.model.EventType;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.model.ResourceTransitionData;
import com.newportai.liveavatar.channel.model.ResponseStateEvent;
import com.newportai.liveavatar.channel.util.JsonUtil;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AvatarWebSocketClientTest {

    @Test
    public void testResponseStateDispatchesCompleteLifecycleEvent() throws Exception {
        AtomicReference<ResponseStateEvent> received = new AtomicReference<>();
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {
            @Override
            public void onResponseState(ResponseStateEvent event) {
                received.set(event);
            }
        });

        invokeHandleMessage(client, JsonUtil.fromJson("{"
                + "\"event\":\"response.state\","
                + "\"sessionId\":\"session-123\","
                + "\"requestId\":\"request-42\","
                + "\"responseId\":\"response-7\","
                + "\"seq\":128,"
                + "\"timestamp\":1788960000000,"
                + "\"data\":{\"state\":\"FINISHED\",\"reason\":\"COMPLETED\"}"
                + "}"));

        ResponseStateEvent event = received.get();
        assertEquals("session-123", event.getSessionId());
        assertEquals("request-42", event.getRequestId());
        assertEquals("response-7", event.getResponseId());
        assertEquals(128L, event.getSeq());
        assertEquals(1788960000000L, event.getTimestamp());
        assertEquals("FINISHED", event.getState());
        assertEquals("COMPLETED", event.getReason());
    }

    @Test
    public void testResponseStatePreservesUnknownStateAndReason() throws Exception {
        AtomicReference<ResponseStateEvent> received = new AtomicReference<>();
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {
            @Override
            public void onResponseState(ResponseStateEvent event) {
                received.set(event);
            }
        });

        invokeHandleMessage(client, JsonUtil.fromJson("{"
                + "\"event\":\"response.state\","
                + "\"sessionId\":\"session-123\","
                + "\"requestId\":\"request-42\","
                + "\"responseId\":\"response-7\","
                + "\"seq\":129,"
                + "\"timestamp\":1788960000001,"
                + "\"data\":{\"state\":\"PLAYING_V2\",\"reason\":\"NEW_REASON\",\"futureField\":true}"
                + "}"));

        assertEquals("PLAYING_V2", received.get().getState());
        assertEquals("NEW_REASON", received.get().getReason());
    }

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
    public void testResourceTransitionDispatchesToListener() throws Exception {
        AtomicBoolean transitionCalled = new AtomicBoolean(false);
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {
            @Override
            public void onResourceTransition(ResourceTransitionData data) {
                transitionCalled.set(
                        "video-a".equals(data.getPreviousResourceId())
                                && "video-b".equals(data.getNextResourceId())
                                && "switch from video-a to video-b".equals(data.getMessage()));
            }
        });

        Message message = JsonUtil.fromJson("{"
                + "\"event\":\"scene.resourceTransition\","
                + "\"data\":{"
                + "\"previousResourceId\":\"video-a\","
                + "\"nextResourceId\":\"video-b\","
                + "\"message\":\"switch from video-a to video-b\""
                + "}"
                + "}");
        Method handleMessage = AvatarWebSocketClient.class.getDeclaredMethod("handleMessage", Message.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(client, message);

        assertTrue(transitionCalled.get());
    }

    @Test
    public void testMalformedResourceTransitionDoesNotDispatch() throws Exception {
        AtomicBoolean transitionCalled = new AtomicBoolean(false);
        AvatarWebSocketClient client = new AvatarWebSocketClient("ws://localhost", new AgentListener() {
            @Override
            public void onResourceTransition(ResourceTransitionData data) {
                transitionCalled.set(true);
            }
        });

        Message message = JsonUtil.fromJson("{"
                + "\"event\":\"scene.resourceTransition\","
                + "\"data\":{\"nextResourceId\":\"video-b\"}"
                + "}");
        Method handleMessage = AvatarWebSocketClient.class.getDeclaredMethod("handleMessage", Message.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(client, message);

        assertFalse(transitionCalled.get());
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

    private static void invokeHandleMessage(AvatarWebSocketClient client, Message message)
            throws Exception {
        Method handleMessage = AvatarWebSocketClient.class.getDeclaredMethod("handleMessage", Message.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(client, message);
    }
}
