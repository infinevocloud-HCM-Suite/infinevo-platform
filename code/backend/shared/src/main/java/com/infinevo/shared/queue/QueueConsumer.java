package com.infinevo.shared.queue;

/**
 * Interface for consuming background jobs from queues.
 */
public interface QueueConsumer<T> {

    /**
     * Target queue name consumed by this listener.
     */
    String getQueueName();

    /**
     * Processes incoming queue message.
     *
     * @param message standard message envelope
     */
    void onMessage(QueueMessage<T> message);
}
