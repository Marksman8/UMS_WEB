package edu.ums.web.ai;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Complexity: time O(F + H) per message — F facts of the caller's role, H <= 8 history turns — plus one bounded model call;
// space O(F + H).

/**
 * Chat orchestration. With a key the facts go to Claude as a grounded system prompt; if Claude is unreachable (or no key
 * is set) the local assistant answers from the same facts, so the feature never just disappears.
 */
@Service
public class DefaultAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChatService.class);
    private static final int MAX_HISTORY = 8;

    private final Map<Role, AiContextContributor> contributors = new EnumMap<>(Role.class);
    private final AiAssistant claude;
    private final LocalAssistant local;
    private final AuditService audit;
    private final Clock clock;

    public DefaultAiChatService(List<AiContextContributor> all, AiAssistant claude, LocalAssistant local,
                                AuditService audit, Clock clock) {
        all.forEach(c -> contributors.put(c.role(), c));
        this.claude = claude;
        this.local = local;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Reply chat(UmsPrincipal who, String message, List<HistoryTurn> history) {
        AiContextContributor contributor = contributors.get(who.role());
        if (contributor == null) {
            throw new UmsException(ErrorCode.NOT_FOUND, "The assistant is not available for this role.");
        }
        List<AiContextContributor.Fact> facts = contributor.facts(who);
        String text = message.trim();
        if (claude.available()) {
            String provider = claude.name();
            try {
                String reply = claude.complete(systemPrompt(who, facts), turns(history, text));
                audit.record("AI_CHAT", "assistant", null, provider);
                return new Reply(reply, provider);
            } catch (UmsException e) {
                log.warn("{} unavailable, answering locally: {}", provider, e.getMessage());
                audit.record("AI_CHAT", "assistant", null, "local (" + provider + " unavailable)");
                return new Reply(local.answer(who, text, facts), "local");
            }
        }
        audit.record("AI_CHAT", "assistant", null, "local");
        return new Reply(local.answer(who, text, facts), "local");
    }

    @Override
    public Status status() {
        return claude.available() ? new Status(claude.name(), claude.model()) : new Status("local", "built-in");
    }

    private String systemPrompt(UmsPrincipal who, List<AiContextContributor.Fact> facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the assistant inside a University Management System. You are talking to ")
                .append(who.fullName()).append(", a ").append(who.role().name().toLowerCase()).append(". Today is ")
                .append(LocalDate.now(clock)).append(".\n")
                .append("Answer ONLY from the facts below; they are the live data this person is allowed to see. ")
                .append("If the answer is not in the facts, say you do not have it and point to the page in the app where it may be found. ")
                .append("Be brief (under 120 words), warm and plain: no markdown tables, no invented grades, dates or people. ")
                .append("Never reveal passwords or hashes, and never claim to have changed any data.\n\nFACTS:\n");
        for (AiContextContributor.Fact f : facts) {
            sb.append("## ").append(f.key()).append('\n').append(f.text()).append("\n\n");
        }
        return sb.toString();
    }

    /** Keeps the last turns, enforces user/assistant alternation starting with a user turn, and appends the new question. */
    static List<AiAssistant.Turn> turns(List<HistoryTurn> history, String message) {
        List<AiAssistant.Turn> out = new ArrayList<>();
        if (history != null) {
            int from = Math.max(0, history.size() - MAX_HISTORY);
            for (HistoryTurn h : history.subList(from, history.size())) {
                if (h == null || h.text() == null || h.text().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(h.role()) ? "assistant" : "user";
                if (out.isEmpty() && role.equals("assistant")) {
                    continue;
                }
                if (!out.isEmpty() && out.get(out.size() - 1).role().equals(role)) {
                    continue;
                }
                out.add(new AiAssistant.Turn(role, h.text()));
            }
        }
        if (!out.isEmpty() && out.get(out.size() - 1).role().equals("user")) {
            out.remove(out.size() - 1);
        }
        out.add(new AiAssistant.Turn("user", message));
        return out;
    }
}
