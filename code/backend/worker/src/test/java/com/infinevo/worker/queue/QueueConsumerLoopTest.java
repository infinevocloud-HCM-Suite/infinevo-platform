package com.infinevo.worker.queue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueConsumerLoopTest {

    private QueueServiceClient queueServiceClient;
    private QueueClient queueClient;
    private ObjectMapper objectMapper;
    private JobService jobService;
    private QueueConsumer<String> consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        queueServiceClient = mock(QueueServiceClient.class);
        queueClient = mock(QueueClient.class);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        jobService = mock(JobService.class);
        consumer = mock(QueueConsumer.class);

        when(consumer.getQueueName()).thenReturn("test-queue");
        when(queueServiceClient.getQueueClient("test-queue")).thenReturn(queueClient);
    }

    @Test
    void shouldFailStartupIfDuplicateQueueConsumersRegistered() {
        QueueConsumer<String> consumer1 = mock(QueueConsumer.class);
        QueueConsumer<String> consumer2 = mock(QueueConsumer.class);
        when(consumer1.getQueueName()).thenReturn("payrun");
        when(consumer2.getQueueName()).thenReturn("payrun");

        assertThrows(
                IllegalStateException.class,
                () -> new QueueConsumerLoop(
                        queueServiceClient,
                        objectMapper,
                        List.of(consumer1, consumer2),
                        jobService,
                        Duration.ofMillis(10)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchAndDeleteMessageAfterSuccessfulProcessing() throws Exception {
        String jobId = "job-101";
        UUID tenantId = UUID.randomUUID();
        QueueMessage<String> payloadMsg = QueueMessage.of(jobId, tenantId, "test-queue", "hello");
        String rawJson = objectMapper.writeValueAsString(payloadMsg);

        QueueMessageItem messageItem = mock(QueueMessageItem.class);
        when(messageItem.getDequeueCount()).thenReturn(1L);
        when(messageItem.getMessageText()).thenReturn(rawJson);
        when(messageItem.getMessageId()).thenReturn("msg-1");
        when(messageItem.getPopReceipt()).thenReturn("pop-1");

        PagedIterable<QueueMessageItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(messageItem)).thenAnswer(inv -> Stream.empty());
        when(queueClient.receiveMessages(eq(32), any(Duration.class), any(), eq(Context.NONE)))
                .thenReturn(pagedIterable);

        QueueConsumerLoop loop = new QueueConsumerLoop(
                queueServiceClient, objectMapper, List.of(consumer), jobService, Duration.ofMillis(10));

        loop.start();
        Thread.sleep(150);
        loop.stop();

        verify(consumer, atLeastOnce()).onMessage(any());
        verify(queueClient, atLeastOnce()).deleteMessage("msg-1", "pop-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotDeleteMessageWhenConsumerThrowsException() throws Exception {
        String jobId = "job-102";
        UUID tenantId = UUID.randomUUID();
        QueueMessage<String> payloadMsg = QueueMessage.of(jobId, tenantId, "test-queue", "hello");
        String rawJson = objectMapper.writeValueAsString(payloadMsg);

        QueueMessageItem messageItem = mock(QueueMessageItem.class);
        when(messageItem.getDequeueCount()).thenReturn(1L);
        when(messageItem.getMessageText()).thenReturn(rawJson);
        when(messageItem.getMessageId()).thenReturn("msg-2");
        when(messageItem.getPopReceipt()).thenReturn("pop-2");

        PagedIterable<QueueMessageItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(messageItem)).thenAnswer(inv -> Stream.empty());
        when(queueClient.receiveMessages(eq(32), any(Duration.class), any(), eq(Context.NONE)))
                .thenReturn(pagedIterable);

        doThrow(new RuntimeException("Consumer error")).when(consumer).onMessage(any());

        QueueConsumerLoop loop = new QueueConsumerLoop(
                queueServiceClient, objectMapper, List.of(consumer), jobService, Duration.ofMillis(10));

        loop.start();
        Thread.sleep(150);
        loop.stop();

        verify(consumer, atLeastOnce()).onMessage(any());
        verify(queueClient, never()).deleteMessage("msg-2", "pop-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkFailedAndDeletePoisonMessageWhenDequeueCountIsThreeOrMore() throws Exception {
        String jobId = "job-poison";
        String rawJson = "{\"jobId\":\"" + jobId + "\",\"tenantId\":\"" + UUID.randomUUID() + "\"}";

        QueueMessageItem messageItem = mock(QueueMessageItem.class);
        when(messageItem.getDequeueCount()).thenReturn(3L);
        when(messageItem.getMessageText()).thenReturn(rawJson);
        when(messageItem.getMessageId()).thenReturn("msg-poison");
        when(messageItem.getPopReceipt()).thenReturn("pop-poison");

        PagedIterable<QueueMessageItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(messageItem)).thenAnswer(inv -> Stream.empty());
        when(queueClient.receiveMessages(eq(32), any(Duration.class), any(), eq(Context.NONE)))
                .thenReturn(pagedIterable);

        QueueConsumerLoop loop = new QueueConsumerLoop(
                queueServiceClient, objectMapper, List.of(consumer), jobService, Duration.ofMillis(10));

        loop.start();
        Thread.sleep(150);
        loop.stop();

        verify(jobService, atLeastOnce()).markFailed(eq(jobId), anyString());
        verify(queueClient, atLeastOnce()).deleteMessage("msg-poison", "pop-poison");
    }
}
