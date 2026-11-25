package com.infinevo.core.ai;

/**
 * The one way the backend asks an AI model for an answer (W-114.4). {@link AzureOpenAiModelClient} calls Azure OpenAI;
 * {@link StubAiModelClient} answers where no model is configured. {@link AiModelConfig} picks.
 *
 * <p>Stateless: every call carries the whole conversation the model should see. Implementations never log the
 * prompt, a message or the answer.
 */
public interface AiModelClient {

    /**
     * Sends one model call and waits for the answer, at most {@link AiModelRequest#timeout()}.
     *
     * @return text, tool calls, or a content-filter stop - a stop is an answer, not an error
     * @throws AiModelException on any other failure, including a timeout
     */
    AiModelResponse send(AiModelRequest request);
}
