package edu.ums.web.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code ums.ai}. With no api-key, a local Ollama model is used if it is running; otherwise the built-in assistant. */
@ConfigurationProperties(prefix = "ums.ai")
public record AiProperties(String apiKey, String model, int maxTokens, int timeoutSeconds,
                           String ollamaUrl, String ollamaModel, int ollamaTimeoutSeconds) {
}
