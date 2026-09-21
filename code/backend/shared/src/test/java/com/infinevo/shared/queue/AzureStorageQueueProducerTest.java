package com.infinevo.shared.queue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureStorageQueueProducerTest {

    private QueueServiceClient queueServiceClient;
    private QueueClient queueClient;
    private ObjectMapper objectMapper;
    private AzureStorageQueueProducer producer;

    @BeforeEach
    void setUp() {
        queueServiceClient = mock(QueueServiceClient.class);
        queueClient = mock(QueueClient.class);
        when(queueServiceClient.getQueueClient("payrun")).thenReturn(queueClient);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        producer = new AzureStorageQueueProducer(queueServiceClient, objectMapper);
    }

    @Test
    void shouldSendMessageSuccessfully() {
        QueueMessage<String> message = QueueMessage.of("job-1", UUID.randomUUID(), "payrun", "payload");

        producer.send("payrun", message);

        verify(queueClient).createIfNotExists();
        verify(queueClient).sendMessage(anyString());
    }

    @Test
    void shouldThrowExceptionWhenQueueNameIsNull() {
        QueueMessage<String> message = QueueMessage.of("job-1", UUID.randomUUID(), "payrun", "payload");
        assertThrows(NullPointerException.class, () -> producer.send(null, message));
    }

    @Test
    void shouldThrowExceptionWhenMessageIsNull() {
        assertThrows(NullPointerException.class, () -> producer.send("payrun", null));
    }
}
