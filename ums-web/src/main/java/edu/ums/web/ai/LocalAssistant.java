package edu.ums.web.ai;

import edu.ums.web.user.UmsPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

// Complexity: time O(F*K) per question, F facts (<= 8) x K keywords (<= 12); space O(F) — no model, no network.

/** Answers from the person's own facts by keyword routing. Works with no API key and no internet. */
@Component
public class LocalAssistant {

    private static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "good morning", "good evening", "namaste");

    public String answer(UmsPrincipal who, String question, List<AiContextContributor.Fact> facts) {
        String q = question.toLowerCase(Locale.ROOT).trim();
        String first = who.fullName().split(" ")[0];
        if (GREETINGS.stream().anyMatch(g -> q.equals(g) || q.startsWith(g + " ") || q.startsWith(g + ","))) {
            return "Hello " + first + "! Ask me about " + topics(facts) + ".";
        }
        List<String> parts = new ArrayList<>();
        for (AiContextContributor.Fact fact : facts) {
            if (fact.keywords().stream().anyMatch(q::contains)) {
                parts.add(fact.text());
            }
        }
        if (parts.isEmpty()) {
            return "I can answer questions about " + topics(facts) + ". For example, try one of those words in a question.";
        }
        return String.join("\n\n", parts);
    }

    private static String topics(List<AiContextContributor.Fact> facts) {
        return String.join(", ", facts.stream().map(AiContextContributor.Fact::key).toList());
    }
}
