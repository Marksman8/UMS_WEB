package edu.ums.web.ai;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// Complexity: time O(1) per call besides the delegate; space O(1).

/** The assistant the rest of the app injects: Claude if a key is set, else local Ollama if it is running, else none. */
@Component
@Primary
public class RoutingAssistant implements AiAssistant {

    private final ClaudeAssistant claude;
    private final OllamaAssistant ollama;

    public RoutingAssistant(ClaudeAssistant claude, OllamaAssistant ollama) {
        this.claude = claude;
        this.ollama = ollama;
    }

    private AiAssistant active() {
        if (claude.available()) {
            return claude;
        }
        return ollama.available() ? ollama : null;
    }

    @Override
    public boolean available() {
        return active() != null;
    }

    @Override
    public String name() {
        AiAssistant a = active();
        return a == null ? "local" : a.name();
    }

    @Override
    public String model() {
        AiAssistant a = active();
        return a == null ? "built-in" : a.model();
    }

    @Override
    public String complete(String system, List<Turn> turns) {
        AiAssistant a = active();
        if (a == null) {
            throw new edu.ums.web.common.UmsException(edu.ums.web.common.ErrorCode.AI_UNAVAILABLE, "No AI model is available.");
        }
        return a.complete(system, turns);
    }
}
