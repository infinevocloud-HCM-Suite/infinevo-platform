package com.infinevo.worker.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.azure.storage.queue.QueueServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.job.service.JobService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QueueConsumerLoopConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(QueueConsumerLoop.class)
            .withBean(QueueServiceClient.class, () -> mock(QueueServiceClient.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(JobService.class, () -> mock(JobService.class))
            .withBean(List.class, Collections::emptyList)
            .withInitializer(context ->
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()));

    @Test
    @DisplayName("Endpoint only -> QueueConsumerLoop bean is present")
    void endpointOnlyCreatesLoopBean() {
        runner.withPropertyValues("azure.storage.queue.endpoint=https://stinfinevodev.queue.core.windows.net/")
                .run(context -> assertThat(context).hasSingleBean(QueueConsumerLoop.class));
    }

    @Test
    @DisplayName("Neither connection-string nor endpoint -> QueueConsumerLoop bean is absent")
    void neitherSetOmitsLoopBean() {
        runner.run(context -> assertThat(context).doesNotHaveBean(QueueConsumerLoop.class));
    }
}
