package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.client.AvatarWebSocketClient;
import com.newportai.liveavatar.channel.model.AudioConfigData;
import com.newportai.liveavatar.channel.model.Message;
import com.newportai.liveavatar.channel.util.JsonUtil;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class AvatarAgentResponseTest {

    @Test
    public void beginResponseKeepsOneIdentityUntilDone() throws Exception {
        Fixture fixture = new Fixture();

        ResponseStream response = fixture.agent.beginResponse("req_1");
        response.sendChunk("one");
        response.sendChunk("two");
        response.done();

        List<Message> messages = fixture.messages();
        assertEquals(3, messages.size());
        String responseId = messages.get(0).getResponseId();
        for (Message message : messages) {
            assertEquals("req_1", message.getRequestId());
            assertEquals(responseId, message.getResponseId());
        }
    }

    @Test
    public void beginResponseRejectsOverlapButAllowsLaterResponse() throws Exception {
        Fixture fixture = new Fixture();

        ResponseStream first = fixture.agent.beginResponse("req_1");
        assertThrows(IllegalStateException.class,
                () -> fixture.agent.beginResponse("req_1"));
        first.done();

        ResponseStream second = fixture.agent.beginResponse("req_1");

        assertNotEquals(first.getResponseId(), second.getResponseId());
    }

    @Test
    public void legacyChunksAndDoneReuseOneResponseId() throws Exception {
        Fixture fixture = new Fixture();

        fixture.agent.sendResponseChunk("req_legacy", "one", 0);
        Thread.sleep(2);
        fixture.agent.sendResponseChunk("req_legacy", "two", 1);
        Thread.sleep(2);
        fixture.agent.sendResponseDone("req_legacy");

        List<Message> messages = fixture.messages();
        assertEquals(3, messages.size());
        String responseId = messages.get(0).getResponseId();
        assertEquals(responseId, messages.get(1).getResponseId());
        assertEquals(responseId, messages.get(2).getResponseId());
    }

    @Test
    public void legacyStartRejectsOverlapAndDoneRequiresActiveResponse() throws Exception {
        Fixture fixture = new Fixture();

        assertThrows(IllegalStateException.class,
                () -> fixture.agent.sendResponseDone("missing"));

        fixture.agent.sendResponseStart("req_legacy", new AudioConfigData());
        assertThrows(IllegalStateException.class,
                () -> fixture.agent.sendResponseStart("req_legacy", new AudioConfigData()));
    }

    @Test
    public void legacyCompletedResponseIsReplacedWithNewIdentity() throws Exception {
        Fixture fixture = new Fixture();

        fixture.agent.sendResponseChunk("req_legacy", "first", 0);
        fixture.agent.sendResponseDone("req_legacy");
        String firstId = fixture.messages().get(0).getResponseId();

        fixture.agent.sendResponseChunk("req_legacy", "second", 0);
        fixture.agent.sendResponseDone("req_legacy");
        String secondId = fixture.messages().get(2).getResponseId();

        assertNotEquals(firstId, secondId);
    }

    @Test
    public void differentRequestsHaveDifferentActiveResponseIds() throws Exception {
        Fixture fixture = new Fixture();

        ResponseStream first = fixture.agent.beginResponse("req_1");
        ResponseStream second = fixture.agent.beginResponse("req_2");

        assertNotEquals(first.getResponseId(), second.getResponseId());
    }

    @Test
    public void concurrentLegacyChunksForOneRequestShareOneResponseId() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                start.await();
                fixture.agent.sendResponseChunk("req_shared", "one", 0);
                return null;
            });
            Future<?> second = executor.submit(() -> {
                start.await();
                fixture.agent.sendResponseChunk("req_shared", "two", 1);
                return null;
            });

            start.countDown();
            first.get();
            second.get();
            fixture.agent.sendResponseDone("req_shared");
        } finally {
            executor.shutdownNow();
        }

        List<Message> messages = fixture.messages();
        assertEquals(3, messages.size());
        String responseId = messages.get(0).getResponseId();
        assertEquals(responseId, messages.get(1).getResponseId());
        assertEquals(responseId, messages.get(2).getResponseId());
    }

    @Test
    public void legacyCancelClosesMatchingActiveResponse() throws Exception {
        Fixture fixture = new Fixture();
        ResponseStream first = fixture.agent.beginResponse("req_1");

        fixture.agent.sendResponseCancel(first.getResponseId());
        ResponseStream second = fixture.agent.beginResponse("req_1");

        assertNotEquals(first.getResponseId(), second.getResponseId());
    }

    private static final class Fixture {
        private final RecordingWebSocket webSocket = new RecordingWebSocket();
        private final AvatarAgent agent;

        private Fixture() throws Exception {
            AvatarWebSocketClient client = new AvatarWebSocketClient(
                    "ws://localhost", new AgentListener() { });
            setField(client, "connected", true);
            setField(client, "webSocket", webSocket);

            agent = AvatarAgent.builder()
                    .config(AvatarAgentConfig.builder()
                            .apiKey("test")
                            .avatarId("test")
                            .build())
                    .listener(new AgentListener() { })
                    .build();
            setStartedState(agent, client);
        }

        private List<Message> messages() throws Exception {
            List<Message> messages = new ArrayList<>();
            for (String json : webSocket.messages()) {
                messages.add(JsonUtil.fromJson(json));
            }
            return messages;
        }
    }

    private static void setStartedState(AvatarAgent agent, AvatarWebSocketClient client)
            throws Exception {
        Class<?> stateClass = Class.forName(AvatarAgent.class.getName() + "$State");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(
                SessionInfo.class, AvatarWebSocketClient.class);
        constructor.setAccessible(true);
        Object started = constructor.newInstance(
                new SessionInfo("session", "", "", "ws://localhost", ""), client);

        Field stateField = AvatarAgent.class.getDeclaredField("state");
        stateField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> state = (AtomicReference<Object>) stateField.get(agent);
        state.set(started);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingWebSocket implements WebSocket {
        private final List<String> textMessages = new ArrayList<>();

        @Override
        public Request request() {
            return new Request.Builder().url("ws://localhost").build();
        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public synchronized boolean send(String text) {
            textMessages.add(text);
            return true;
        }

        private synchronized List<String> messages() {
            return new ArrayList<>(textMessages);
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
