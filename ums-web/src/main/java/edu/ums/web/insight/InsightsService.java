package edu.ums.web.insight;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Cross-cutting numbers for the dashboards: seat fill, pending work, and students whose attendance is low. */
public interface InsightsService {

    AdminSummary adminSummary();

    FacultySummary facultySummary(long facultyId);

    /** Students under the attendance threshold in the latest term that has class sessions; null facultyId means everyone. */
    List<AtRisk> atRisk(Long facultyId, int limit);

    record AtRisk(String student, String rollNo, String courseCode, String term, BigDecimal percent, int held) {
    }

    record TermHealth(long id, String name, String state) {
    }

    record AdminSummary(Map<String, Integer> usersByRole, int pendingApprovals, int openSections, int seatsTotal,
                        int seatsFilled, List<TermHealth> terms, List<AtRisk> atRisk, int eventsToday) {
    }

    record FacultySummary(int sections, int students, int draftSheets, int submittedSheets, List<AtRisk> atRisk) {
    }
}
