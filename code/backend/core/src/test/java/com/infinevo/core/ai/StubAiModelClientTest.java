package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.4 review: the stub the live app runs while no model is configured keeps no user message in memory. */
class StubAiModelClientTest {

    private static AiModelRequest request() {
        return new AiModelRequest("system", List.of(), List.of(), 16, Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("a stub not told to record keeps no request, and answers 'not connected'")
    void freshStub_keepsNothing() {
        StubAiModelClient stub = new StubAiModelClient();

        AiModelResponse answer = stub.send(request());

        assertThat(answer.text()).isEqualTo(StubAiModelClient.NOT_CONNECTED);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    @DisplayName("after reset() a test can read what was sent")
    void afterReset_records() {
        StubAiModelClient stub = new StubAiModelClient();
        stub.reset();

        stub.send(request());

        assertThat(stub.requests()).hasSize(1);
    }
}
