package edu.ums.web.faculty;

import edu.ums.web.ai.AiContextContributor;
import edu.ums.web.common.Rs;
import edu.ums.web.insight.InsightsService;
import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Complexity: time O(S + R) per call — own sections (<= 100) and at-risk rows (<= 8); space O(S + R).

/** What the assistant may tell a faculty member: only their own sections, sheets and students at risk. */
@Component
public class FacultyAiContext implements AiContextContributor {

    private final FacultySectionService sections;
    private final InsightsService insights;

    public FacultyAiContext(FacultySectionService sections, InsightsService insights) {
        this.sections = sections;
        this.insights = insights;
    }

    @Override
    public Role role() {
        return Role.FACULTY;
    }

    @Override
    public List<Fact> facts(UmsPrincipal who) {
        List<Fact> facts = new ArrayList<>();
        List<FacultySectionService.SectionBrief> mine = sections.mySections(who.id());

        StringBuilder teaching = new StringBuilder();
        StringBuilder sheets = new StringBuilder();
        for (FacultySectionService.SectionBrief s : mine) {
            teaching.append(teaching.length() > 0 ? "\n" : "").append(s.termName()).append(" (").append(s.termState()).append("): ")
                    .append(s.courseCode()).append(" ").append(s.title()).append(" section ").append(s.code()).append(", ")
                    .append(Rs.dayName(s.dayOfWeek())).append(" ").append(s.start()).append("-").append(s.end()).append(" ")
                    .append(s.room()).append(", ").append(s.enrolled()).append("/").append(s.capacity()).append(" students");
            sheets.append(sheets.length() > 0 ? "\n" : "").append(s.courseCode()).append(" mark sheet: ").append(s.sheetState());
        }
        facts.add(new Fact("my sections", List.of("section", "class", "course", "teach", "student", "roster", "schedule", "today", "timetable"),
                teaching.length() == 0 ? "You have no sections in an active term." : teaching.toString()));
        facts.add(new Fact("mark sheets", List.of("sheet", "marks", "submit", "grading", "approve", "pending", "draft"),
                sheets.length() == 0 ? "You have no mark sheets." : sheets.toString()));

        StringBuilder risk = new StringBuilder();
        for (InsightsService.AtRisk r : insights.atRisk(who.id(), 8)) {
            risk.append(risk.length() > 0 ? "\n" : "").append(r.student()).append(" (").append(r.rollNo()).append(") in ")
                    .append(r.courseCode()).append(": ").append(r.percent()).append("% over ").append(r.held()).append(" classes");
        }
        facts.add(new Fact("students at risk", List.of("risk", "attendance", "low", "below", "shortage", "struggling"),
                risk.length() == 0 ? "No student of yours is below the attendance minimum." : risk.toString()));
        return List.copyOf(facts);
    }
}
