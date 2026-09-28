package com.newportai.liveavatar.channel.agent;

import com.newportai.liveavatar.channel.exception.ConnectionException;
import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.EventType;
import com.newportai.liveavatar.channel.model.Message;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class PromptStreamTest {

    @Test
    public void usesStableIdsAndSequenceStartingAtOne() throws Exception {
        RecordingSender sender = new RecordingSender();
        PromptStream stream = stream(sender);

        stream.sendChunk("one");
        stream.sendChunk("two");
        stream.done();

        assertEquals(Integer.valueOf(1), sender.messages.get(0).getSeq());
        assertEquals(Integer.valueOf(2), sender.messages.get(1).getSeq());
        for (Message message : sender.messages) {
            assertEquals("req_1", message.getRequestId());
            assertEquals("prompt_1", message.getResponseId());
        }
        assertFalse(stream.getTerminalFuture().isDone());
    }

    @Test
    public void concurrentChunksReceiveUniqueSequences() throws Exception {
        RecordingSender sender = new RecordingSender();
        PromptStream stream = stream(sender);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 40; i++) {
                final int value = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    stream.sendChunk("chunk-" + value);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }

        Set<Integer> sequences = new HashSet<>();
        for (Message message : sender.messages) sequences.add(message.getSeq());
        assertEquals(40, sequences.size());
        for (int seq = 1; seq <= 40; seq++) {
            org.junit.Assert.assertTrue(sequences.contains(seq));
        }
    }

    @Test
    public void failedChunkRetriesSameSequenceAndFailedDoneCanRetry() throws Exception {
        FailingSender sender = new FailingSender();
        PromptStream stream = stream(sender);

        assertThrows(ConnectionException.class, () -> stream.sendChunk("one"));
        stream.sendChunk("one");
        assertEquals(Integer.valueOf(1), sender.successful.get(0).getSeq());

        assertThrows(ConnectionException.class, stream::done);
        stream.done();
        assertEquals(2, sender.doneAttempts);
    }

    @Test
    public void rejectsInvalidIdsNullTextAndOperationsAfterDone() throws Exception {
        RecordingSender sender = new RecordingSender();
        assertThrows(IllegalArgumentException.class,
                () -> new PromptStream("", "prompt_1", sender, lifecycle()));
        assertThrows(IllegalArgumentException.class,
                () -> new PromptStream("req_1", null, sender, lifecycle()));

        PromptStream stream = stream(sender);
        assertThrows(IllegalArgumentException.class, () -> stream.sendChunk(null));
        stream.done();
        assertThrows(IllegalStateException.class, stream::done);
        assertThrows(IllegalStateException.class, () -> stream.sendChunk("late"));
    }

    private static PromptStream stream(ResponseStream.MessageSender sender) {
        return new PromptStream("req_1", "prompt_1", sender, lifecycle());
    }

    private static ResponseLifecycle lifecycle() {
        return new ResponseLifecycle("req_1", "prompt_1");
    }

    private static class RecordingSender implements ResponseStream.MessageSender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public synchronized void send(Message message)
                throws ConnectionException, MessageSerializationException {
            messages.add(message);
        }
    }

    private static final class FailingSender implements ResponseStream.MessageSender {
        private boolean failChunk = true;
        private int doneAttempts;
        private final List<Message> successful = new ArrayList<>();

        @Override
        public void send(Message message) throws ConnectionException {
            if (EventType.SYSTEM_PROMPT_CHUNK.equals(message.getEvent()) && failChunk) {
                failChunk = false;
                throw new ConnectionException("simulated chunk failure");
            }
            if (EventType.SYSTEM_PROMPT_DONE.equals(message.getEvent())) {
                doneAttempts++;
                if (doneAttempts == 1) throw new ConnectionException("simulated done failure");
            }
            successful.add(message);
        }
    }
}
