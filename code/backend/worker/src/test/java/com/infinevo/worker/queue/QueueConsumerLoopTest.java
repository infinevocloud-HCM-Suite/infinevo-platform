package com.infinevo.worker.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueConsumerLoopTest {

    private static final Duration POLL = Duration.ofMillis(10);
    private static final Duration WAIT = Duration.ofSeconds(5);

    private QueueServiceClient queueServiceClient;
    private QueueClient queueClient;
    private ObjectMapper objectMapper;
    private JobService jobService;
    private QueueConsumer<String> consumer;
    private QueueConsumerLoop loop;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        queueServiceClient = mock(QueueServiceClient.class);
        queueClient = mock(QueueClient.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        jobService = mock(JobService.class);
        consumer = mock(QueueConsumer.class);

        when(consumer.getQueueName()).thenReturn("test-queue");
        when(queueServiceClient.getQueueClient("test-queue")).thenReturn(queueClient);
    }

    @AfterEach
    void tearDown() {
        if (loop != null) {
            loop.stop();
        }
        TenantContext.clear();
    }

    private QueueConsumerLoop newLoop(List<QueueConsumer<String>> consumers) {
        loop = new QueueConsumerLoop(
                queueServiceClient, objectMapper, consumers, jobService, POLL, Duration.ofSeconds(30));
        return loop;
    }

    private static QueueMessageItem item(String id, String body, long dequeueCount) {
        QueueMessageItem messageItem = mock(QueueMessageItem.class);
        when(messageItem.getDequeueCount()).thenReturn(dequeueCount);
        when(messageItem.getMessageText()).thenReturn(body);
        when(messageItem.getMessageId()).thenReturn(id);
        when(messageItem.getPopReceipt()).thenReturn("pop-" + id);
        return messageItem;
    }

    /** First receive returns the given items, every later receive returns nothing. */
    @SuppressWarnings("unchecked")
    private static void deliverOnce(QueueClient client, QueueMessageItem... items) {
        PagedIterable<QueueMessageItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(items)).thenAnswer(inv -> Stream.empty());
        when(client.receiveMessages(eq(32), any(Duration.class), any(), eq(Context.NONE)))
                .thenReturn(pagedIterable);
    }

    private String json(QueueMessage<String> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFailStartupIfDuplicateQueueConsumersRegistered() {
        QueueConsumer<String> consumer1 = mock(QueueConsumer.class);
        QueueConsumer<String> consumer2 = mock(QueueConsumer.class);
        when(consumer1.getQueueName()).thenReturn("payrun");
        when(consumer2.getQueueName()).thenReturn("payrun");

        assertThrows(IllegalStateException.class, () -> newLoop(List.of(consumer1, consumer2)));
    }

    @Test
    void shouldDispatchAndDeleteMessageAfterSuccessfulProcessing() {
        QueueMessage<String> payloadMsg = QueueMessage.of("job-101", UUID.randomUUID(), "test-queue", "hello");
        deliverOnce(queueClient, item("msg-1", json(payloadMsg), 1L));

        newLoop(List.of(consumer)).start();

        await().atMost(WAIT).untilAsserted(() -> verify(queueClient).deleteMessage("msg-1", "pop-msg-1"));
        verify(consumer).onMessage(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchEachQueueToItsOwnConsumer() {
        QueueConsumer<String> other = mock(QueueConsumer.class);
        QueueClient otherClient = mock(QueueClient.class);
        when(other.getQueueName()).thenReturn("other-queue");
        when(queueServiceClient.getQueueClient("other-queue")).thenReturn(otherClient);

        QueueMessage<String> forTest = QueueMessage.of("job-t", UUID.randomUUID(), "test-queue", "t");
        QueueMessage<String> forOther = QueueMessage.of("job-o", UUID.randomUUID(), "other-queue", "o");
        deliverOnce(queueClient, item("msg-t", json(forTest), 1L));
        deliverOnce(otherClient, item("msg-o", json(forOther), 1L));

        newLoop(List.of(consumer, other)).start();

        await().atMost(WAIT).untilAsserted(() -> {
            verify(queueClient).deleteMessage("msg-t", "pop-msg-t");
            verify(otherClient).deleteMessage("msg-o", "pop-msg-o");
        });
        verify(consumer).onMessage(argThat(m -> "job-t".equals(m.getJobId())));
        verify(other).onMessage(argThat(m -> "job-o".equals(m.getJobId())));
        verify(consumer, never()).onMessage(argThat(m -> "job-o".equals(m.getJobId())));
        verify(other, never()).onMessage(argThat(m -> "job-t".equals(m.getJobId())));
    }

    @Test
    void shouldNotDeleteMessageWhenConsumerThrowsException() {
        QueueMessage<String> payloadMsg = QueueMessage.of("job-102", UUID.randomUUID(), "test-queue", "hello");
        deliverOnce(queueClient, item("msg-2", json(payloadMsg), 1L));
        doThrow(new RuntimeException("Consumer error")).when(consumer).onMessage(any());

        newLoop(List.of(consumer)).start();

        await().atMost(WAIT).untilAsserted(() -> verify(consumer).onMessage(any()));
        // A few more polls: a delete after the exception would be the defect this test guards.
        await().pollDelay(Duration.ofMillis(100)).atMost(WAIT).untilAsserted(() -> verify(queueClient, never())
                .deleteMessage("msg-2", "pop-msg-2"));
    }

    @Test
    void shouldMarkFailedUnderTheMessagesTenantAndDeletePoisonMessage() {
        String jobId = "job-poison";
        UUID tenantId = UUID.randomUUID();
        String rawJson = "{\"jobId\":\"" + jobId + "\",\"tenantId\":\"" + tenantId + "\"}";
        deliverOnce(queueClient, item("msg-poison", rawJson, 3L));

        // core.job_status is RLS-isolated: markFailed only writes when the tenant is bound.
        AtomicReference<UUID> boundAtCall = new AtomicReference<>();
        doAnswer(inv -> {
                    boundAtCall.set(TenantContext.current().orElse(null));
                    return null;
                })
                .when(jobService)
                .markFailed(eq(jobId), any());

        newLoop(List.of(consumer)).start();

        await().atMost(WAIT).untilAsserted(() -> verify(queueClient).deleteMessage("msg-poison", "pop-msg-poison"));
        verify(jobService).markFailed(jobId, "poison: delivered 3 times");
        assertThat(boundAtCall.get()).isEqualTo(tenantId);
        verify(consumer, never()).onMessage(any());
    }

    @Test
    void shouldStillDeletePoisonMessageAndContinueTheBatchWhenMarkFailedThrows() {
        UUID tenantId = UUID.randomUUID();
        String poison = "{\"jobId\":\"job-p\",\"tenantId\":\"" + tenantId + "\"}";
        QueueMessage<String> good = QueueMessage.of("job-good", tenantId, "test-queue", "ok");
        deliverOnce(queueClient, item("msg-p", poison, 3L), item("msg-good", json(good), 1L));
        doThrow(new RuntimeException("db down")).when(jobService).markFailed(eq("job-p"), any());

        newLoop(List.of(consumer)).start();

        await().atMost(WAIT).untilAsserted(() -> {
            verify(queueClient).deleteMessage("msg-p", "pop-msg-p");
            verify(queueClient).deleteMessage("msg-good", "pop-msg-good");
        });
        verify(consumer).onMessage(any());
    }

    @Test
    void shouldMarkFailedAndDeleteMessageThatIsJsonButNotAQueueMessage() {
        UUID tenantId = UUID.randomUUID();
        // Valid JSON, so jobId and tenantId are readable, but enqueuedAt cannot bind to an Instant.
        String broken = "{\"jobId\":\"job-bad\",\"tenantId\":\"" + tenantId + "\",\"enqueuedAt\":\"not-a-date\"}";
        deliverOnce(queueClient, item("msg-bad", broken, 1L));

        newLoop(List.of(consumer)).start();

        await().atMost(WAIT).untilAsserted(() -> verify(queueClient).deleteMessage("msg-bad", "pop-msg-bad"));
        verify(jobService).markFailed(eq("job-bad"), startsWith("unparseable: "));
        verify(consumer, never()).onMessage(any());
    }

    @Test
    void shouldDeleteTruncatedMessageWithoutMarkingAnythingWhenNoJobIdIsReadable() {
        String truncated = "{\"jobId\":\"job-trunc\",\"tenantId\":";
        deliverOnce(queueClient, item("msg-trunc", truncated, 1L));

        newLoop(List.of(consumer)).start();

        await().atMost(WAIT).untilAsserted(() -> verify(queueClient).deleteMessage("msg-trunc", "pop-msg-trunc"));
        verify(jobService, never()).markFailed(any(), any());
        verify(consumer, never()).onMessage(any());
    }
}
