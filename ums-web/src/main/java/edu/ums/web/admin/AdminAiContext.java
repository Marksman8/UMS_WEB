package edu.ums.web.admin;

import edu.ums.web.academic.TermService;
import edu.ums.web.ai.AiContextContributor;
import edu.ums.web.insight.InsightsService;
import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Complexity: time O(1) per call — a handful of LIMITed summary queries; space O(1).

/** What the assistant may tell an administrator: institution-wide counts, terms, pending approvals, risk. */
@Component
public class AdminAiContext implements AiContextContributor {

    private final InsightsService insights;
    private final ApprovalService approvals;
    private final TermService terms;

    public AdminAiContext(InsightsService insights, ApprovalService approvals, TermService terms) {
        this.insights = insights;
        this.approvals = approvals;
        this.terms = terms;
    }

    @Override
    public Role role() {
        return Role.ADMIN;
    }

    @Override
    public List<Fact> facts(UmsPrincipal who) {
        InsightsService.AdminSummary s = insights.adminSummary();
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("overview", List.of("user", "people", "count", "how many", "student", "faculty", "account", "overview"),
                "Active accounts: " + s.usersByRole() + ". Events logged today: " + s.eventsToday() + "."));

        StringBuilder t = new StringBuilder();
        for (TermService.TermView v : terms.list()) {
            t.append(t.length() > 0 ? "\n" : "").append(v.name()).append(": ").append(v.state())
                    .append(v.nextState() == null ? "" : " (next: " + v.nextState() + ")");
        }
        facts.add(new Fact("terms", List.of("term", "stage", "registration", "publish", "results", "semester", "advance"),
                t.length() == 0 ? "No terms exist." : t.toString()));

        StringBuilder a = new StringBuilder();
        for (ApprovalService.PendingSheet p : approvals.pending()) {
            a.append(a.length() > 0 ? "\n" : "").append(p.courseCode()).append(" ").append(p.section()).append(" by ").append(p.faculty())
                    .append(": ").append(p.enrolled()).append(" students, average ").append(p.average()).append(", failing ").append(p.failing());
        }
        facts.add(new Fact("approvals", List.of("approval", "pending", "sheet", "marks", "review", "submitted"),
                a.length() == 0 ? "No mark sheets are waiting for approval." : "Waiting for approval:\n" + a));

        facts.add(new Fact("seats", List.of("seat", "capacity", "fill", "full", "section", "open"),
                s.openSections() + " sections are open for registration with " + s.seatsFilled() + " of " + s.seatsTotal() + " seats taken."));

        StringBuilder r = new StringBuilder();
        for (InsightsService.AtRisk x : s.atRisk()) {
            r.append(r.length() > 0 ? "\n" : "").append(x.student()).append(" (").append(x.rollNo()).append(") in ")
                    .append(x.courseCode()).append(": ").append(x.percent()).append("%");
        }
        facts.add(new Fact("students at risk", List.of("risk", "attendance", "low", "below", "shortage"),
                r.length() == 0 ? "No student is below the attendance minimum." : r.toString()));
        return List.copyOf(facts);
    }
}
