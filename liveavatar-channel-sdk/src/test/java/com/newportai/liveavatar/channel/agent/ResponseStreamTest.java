package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.AudioConfigData;
import com.newportai.liveavatar.channel.model.EventType;
import com.newportai.liveavatar.channel.model.Message;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ResponseStreamTest {

    @Test
    public void usesStableIdsAndIncrementingSequence() throws Exception {
        RecordingSender sender = new RecordingSender();
        ResponseStream stream = new ResponseStream("req_1", "res_1", sender, () -> { });

        stream.start(new AudioConfigData());
        stream.sendChunk("one");
        stream.sendChunk("two");
        stream.done();

        assertEquals(Arrays.asList(
                EventType.RESPONSE_START,
                EventType.RESPONSE_CHUNK,
                EventType.RESPONSE_CHUNK,
                EventType.RESPONSE_DONE), sender.events());
        for (Message message : sender.messages) {
            assertEquals("req_1", message.getRequestId());
            assertEquals("res_1", message.getResponseId());
        }
        assertEquals(Integer.valueOf(0), sender.messages.get(1).getSeq());
        assertEquals(Integer.valueOf(1), sender.messages.get(2).getSeq());
    }

    @Test
    public void rejectsStartAfterStartOrChunk() throws Exception {
        ResponseStream started = new ResponseStream(
                "req_1", "res_1", new RecordingSender(), () -> { });
        started.start(new AudioConfigData());
        assertThrows(IllegalStateException.class,
                () -> started.start(new AudioConfigData()));

        ResponseStream chunked = new ResponseStream(
                "req_2", "res_2", new RecordingSender(), () -> { });
        chunked.sendChunk("one");
        assertThrows(IllegalStateException.class,
                () -> chunked.start(new AudioConfigData()));
    }

    @Test
    public void rejectsOperationsAfterDone() throws Exception {
        ResponseStream stream = new ResponseStream(
                "req_1", "res_1", new RecordingSender(), () -> { });
        stream.done();

        assertThrows(IllegalStateException.class, () -> stream.sendChunk("late"));
        assertThrows(IllegalStateException.class, stream::done);
        assertThrows(IllegalStateException.class, stream::cancel);
    }

    @Test
    public void failedDoneCanBeRetriedAndTerminatesOnce() throws Exception {
        FailsFirstDoneSender sender = new FailsFirstDoneSender();
        AtomicInteger terminalCalls = new AtomicInteger();
        ResponseStream stream = new ResponseStream(
                "req_1", "res_1", sender, terminalCalls::incrementAndGet);

        assertThrows(ConnectionException.class, stream::done);
        assertEquals(0, terminalCalls.get());

        stream.done();

        assertEquals(2, sender.doneAttempts);
        assertEquals(1, terminalCalls.get());
        assertThrows(IllegalStateException.class, stream::done);
    }

    @Test
    public void rejectsInvalidIdentifiersAndNullText() {
        RecordingSender sender = new RecordingSender();
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseStream(null, "res_1", sender, () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseStream("req_1", "", sender, () -> { }));

        ResponseStream stream = new ResponseStream("req_1", "res_1", sender, () -> { });
        assertThrows(IllegalArgumentException.class, () -> stream.sendChunk(null));
    }

    private static final class RecordingSender implements ResponseStream.MessageSender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void send(Message message)
                throws ConnectionException, MessageSerializationException {
            messages.add(message);
        }

        private List<String> events() {
            List<String> events = new ArrayList<>();
            for (Message message : messages) {
                events.add(message.getEvent());
            }
            return events;
        }
    }

    private static final class FailsFirstDoneSender implements ResponseStream.MessageSender {
        private int doneAttempts;

        @Override
        public void send(Message message) throws ConnectionException {
            if (EventType.RESPONSE_DONE.equals(message.getEvent())) {
                doneAttempts++;
                if (doneAttempts == 1) {
                    throw new ConnectionException("simulated send failure");
                }
            }
        }
    }
}
