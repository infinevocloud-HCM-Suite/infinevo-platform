package com.infinevo.core.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The model client where no model is configured (W-114.4): every laptop and every CI run. It answers
 * {@value #NOT_CONNECTED} unless a test has scripted its replies.
 *
 * <p>A test scripts replies in order with {@link #script}; each one is used once, then the default answer returns. A
 * scripted step is a function of the request, so it can answer, or throw {@link AiModelException} to play a failed
 * call. Requests are kept for {@link #requests()} only after a test calls {@link #reset()} or {@link #recording()}:
 * the stub is also what the live app runs while no model is configured, and must not hold users' messages in memory.
 */
public class StubAiModelClient implements AiModelClient {

    public static final String NOT_CONNECTED = "The AI helper is not connected in this environment.";

    private final Deque<Function<AiModelRequest, AiModelResponse>> script = new ArrayDeque<>();
    private final List<AiModelRequest> received = new ArrayList<>();
    private boolean recording;

    @Override
    public synchronized AiModelResponse send(AiModelRequest request) {
        if (recording) {
            received.add(request);
        }
        Function<AiModelRequest, AiModelResponse> next = script.pollFirst();
        return next == null ? AiModelResponse.text(NOT_CONNECTED) : next.apply(request);
    }

    /** Queues replies, used one per call in this order. */
    public synchronized StubAiModelClient script(AiModelResponse... replies) {
        for (AiModelResponse reply : replies) {
            Objects.requireNonNull(reply, "reply must not be null");
            script.addLast(request -> reply);
        }
        return this;
    }

    /** Queues one step that computes its reply from the request, or throws. */
    public synchronized StubAiModelClient scriptStep(Function<AiModelRequest, AiModelResponse> step) {
        script.addLast(Objects.requireNonNull(step, "step must not be null"));
        return this;
    }

    /** Queues one failed call. */
    public synchronized StubAiModelClient scriptFailure() {
        return scriptStep(request -> {
            throw new AiModelException("Scripted model failure");
        });
    }

    /** Starts keeping the requests received, for a test. */
    public synchronized StubAiModelClient recording() {
        recording = true;
        return this;
    }

    /** Every request received since the last {@link #reset()}, oldest first. */
    public synchronized List<AiModelRequest> requests() {
        return List.copyOf(received);
    }

    /** Clears the script and the received requests, and starts keeping requests (tests only). */
    public synchronized void reset() {
        script.clear();
        received.clear();
        recording = true;
    }
}
