package com.infinevo.payroll.payrun;

import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The queue for the payroll integration tests (W-29.4): keeps every message sent, in order, so a test
 * can deliver it to {@link InProcessPayRunWorker} — once, twice, or not at all. {@link #failNextWith}
 * makes the next send throw, as an unreachable queue would.
 */
public class RecordingQueueProducer implements QueueProducer {

    private final List<QueueMessage<String>> sent = new ArrayList<>();
    private final AtomicReference<RuntimeException> nextFailure = new AtomicReference<>();

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> void send(String queueName, QueueMessage<T> message) {
        RuntimeException failure = nextFailure.getAndSet(null);
        if (failure != null) {
            throw failure;
        }
        sent.add((QueueMessage<String>) message);
    }

    public synchronized List<QueueMessage<String>> sent() {
        return List.copyOf(sent);
    }

    /** The message sent for {@code jobId}. */
    public synchronized QueueMessage<String> forJob(String jobId) {
        return sent.stream()
                .filter(m -> m.getJobId().equals(jobId))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No message was sent for job " + jobId));
    }

    public void failNextWith(RuntimeException failure) {
        nextFailure.set(failure);
    }

    public synchronized void clear() {
        sent.clear();
        nextFailure.set(null);
    }
}
