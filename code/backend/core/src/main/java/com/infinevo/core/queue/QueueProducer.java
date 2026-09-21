package com.infinevo.core.queue;

/**
 * Interface for dispatching background jobs to storage queues.
 */
public interface QueueProducer {

    /**
     * Sends a QueueMessage to the specified queue.
     *
     * @param queueName target queue name
     * @param message standard message envelope
     * @param <T> payload type
     */
    <T> void send(String queueName, QueueMessage<T> message);
}
