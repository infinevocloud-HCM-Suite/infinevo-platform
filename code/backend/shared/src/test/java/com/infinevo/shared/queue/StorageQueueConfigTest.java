package com.infinevo.shared.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.queue.QueueServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class StorageQueueConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StorageQueueConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    @DisplayName("Endpoint only -> QueueServiceClient and QueueProducer exist, URL matches endpoint")
    void endpointOnlyCreatesBeans(CapturedOutput output) {
        runner.withPropertyValues("azure.storage.queue.endpoint=https://stinfinevodev.queue.core.windows.net/")
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueServiceClient.class);
                    assertThat(context).hasSingleBean(QueueProducer.class);
                    QueueServiceClient client = context.getBean(QueueServiceClient.class);
                    assertThat(client.getQueueServiceUrl()).startsWith("https://stinfinevodev.queue.core.windows.net");
                    assertThat(output.getOut())
                            .contains(
                                    "Queue: Storage Queue at https://stinfinevodev.queue.core.windows.net/ by managed identity");
                });
    }

    @Test
    @DisplayName("Connection string and endpoint -> connection string wins")
    void connectionStringTakesPrecedenceOverEndpoint(CapturedOutput output) {
        String connStr =
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;";
        runner.withPropertyValues(
                        "azure.storage.queue.connection-string=" + connStr,
                        "azure.storage.queue.endpoint=https://stinfinevodev.queue.core.windows.net/")
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueServiceClient.class);
                    assertThat(context).hasSingleBean(QueueProducer.class);
                    QueueServiceClient client = context.getBean(QueueServiceClient.class);
                    assertThat(client.getQueueServiceUrl()).contains("127.0.0.1:10001");
                    assertThat(output.getOut()).contains("Queue: Storage Queue by connection string (an emulator)");
                });
    }

    @Test
    @DisplayName("Neither set -> no beans created")
    void neitherSetCreatesNoBeans() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(QueueServiceClient.class);
            assertThat(context).doesNotHaveBean(QueueProducer.class);
        });
    }

    @Test
    @DisplayName("Connection string never appears in captured logs")
    void connectionStringNeverLogged(CapturedOutput output) {
        String sensitiveKey = "superSecretKey9876543210ABCXYZ==";
        String connStr = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=" + sensitiveKey
                + ";QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;";

        runner.withPropertyValues("azure.storage.queue.connection-string=" + connStr)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueServiceClient.class);
                    assertThat(output.getOut()).doesNotContain(sensitiveKey);
                    assertThat(output.getOut()).doesNotContain(connStr);
                });
    }
}
