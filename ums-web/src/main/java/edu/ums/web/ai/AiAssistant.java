package edu.ums.web.ai;

import java.util.List;

/** A language model that can complete a conversation. Implementations: Claude (API key), Ollama (local), and a router that picks one. */
public interface AiAssistant {

    /** Short provider id shown to the user: claude, ollama or local. */
    String name();

    boolean available();

    String model();

    /** Sends a system prompt and alternating user/assistant turns (starting with a user turn) and returns the reply text. */
    String complete(String system, List<Turn> turns);

    record Turn(String role, String text) {
    }
}
