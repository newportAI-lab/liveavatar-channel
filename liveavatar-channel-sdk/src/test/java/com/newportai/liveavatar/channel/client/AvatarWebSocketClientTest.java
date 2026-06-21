package com.newportai.liveavatar.channel.client;

import com.newportai.liveavatar.channel.agent.AgentListener;
import com.newportai.liveavatar.channel.model.EventType;
import com.newportai.liveavatar.channel.model.Message;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

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
}
