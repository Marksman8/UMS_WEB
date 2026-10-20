package edu.ums.web.admin;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import edu.ums.web.faculty.SheetLifecycle;
import edu.ums.web.faculty.SheetState;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(M) per pending call, M = marks in submitted sheets (aggregated in SQL, <= 100 sheets); O(1) per decision; space O(100).

@Service
public class JdbcApprovalService implements ApprovalService {

    private final JdbcTemplate jdbc;
    private final SheetLifecycle lifecycle;
    private final AuditService audit;

    public JdbcApprovalService(JdbcTemplate jdbc, SheetLifecycle lifecycle, AuditService audit) {
        this.jdbc = jdbc;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Override
    public List<PendingSheet> pending() {
        return jdbc.query("""
                SELECT ms.id, s.id AS section_id, t.name AS term, c.code, c.title, s.code AS sec, u.full_name AS faculty,
                       stats.enrolled, stats.avg_total, stats.failing, ms.submitted_at
                  FROM mark_sheet ms
                  JOIN section s ON s.id = ms.section_id
                  JOIN term t ON t.id = s.term_id
                  JOIN course c ON c.id = s.course_id
                  JOIN app_user u ON u.id = s.faculty_id
                 CROSS JOIN LATERAL (
                       SELECT count(*) AS enrolled, round(avg(x.total), 1) AS avg_total, count(*) FILTER (WHERE x.total < 40) AS failing
                         FROM (SELECT e.id, sum(m.value / ac.max_marks * ac.weight) AS total
                                 FROM enrolment e
                                 JOIN assessment_component ac ON ac.section_id = e.section_id
                                 LEFT JOIN mark m ON m.component_id = ac.id AND m.enrolment_id = e.id
                                WHERE e.section_id = s.id AND e.state = 'ENROLLED'
                                GROUP BY e.id) x) stats
                 WHERE ms.state = 'SUBMITTED'
                 ORDER BY ms.submitted_at, ms.id LIMIT 100
                """, (rs, n) -> new PendingSheet(rs.getLong("id"), rs.getLong("section_id"), rs.getString("term"),
                rs.getString("code"), rs.getString("title"), rs.getString("sec"), rs.getString("faculty"),
                rs.getInt("enrolled"), rs.getBigDecimal("avg_total"), rs.getInt("failing"), Rs.instant(rs, "submitted_at")));
    }

    @Override
    @Transactional
    public void approve(long sheetId) {
        requireSubmitted(sheetId);
        lifecycle.move(sheetId, SheetState.SUBMITTED, SheetState.APPROVED);
        audit.record("SHEET_APPROVE", "mark_sheet", sheetId, null);
    }

    @Override
    @Transactional
    public void sendBack(long sheetId) {
        requireSubmitted(sheetId);
        lifecycle.move(sheetId, SheetState.SUBMITTED, SheetState.DRAFT);
        audit.record("SHEET_RETURN", "mark_sheet", sheetId, null);
    }

    /** Locks the sheet row so approval cannot interleave with a faculty save or another decision. */
    private void requireSubmitted(long sheetId) {
        List<String> state = jdbc.queryForList("SELECT state FROM mark_sheet WHERE id = ? FOR UPDATE", String.class, sheetId);
        if (state.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND);
        }
        if (!SheetState.SUBMITTED.name().equals(state.get(0))) {
            throw new UmsException(ErrorCode.SHEET_STATE, "Only submitted sheets can be reviewed (this one is " + state.get(0) + ").");
        }
    }
}
