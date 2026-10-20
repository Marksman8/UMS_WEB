package edu.ums.web.insight;

import edu.ums.web.common.Rs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Complexity: time O(A + U) per call — A = attendance rows of the latest term aggregated in SQL, U = users grouped by role;
// every list is LIMITed (<= 50); space O(L).

@Service
public class JdbcInsightsService implements InsightsService {

    private static final String AT_RISK = """
            SELECT u.full_name, st.roll_no, c.code, t.name AS term,
                   round(100.0 * count(*) FILTER (WHERE a.present) / count(*), 1) AS pct, count(*) AS held
              FROM attendance a
              JOIN enrolment e ON e.id = a.enrolment_id AND e.state = 'ENROLLED'
              JOIN section s ON s.id = e.section_id
              JOIN term t ON t.id = s.term_id
              JOIN course c ON c.id = s.course_id
              JOIN student st ON st.user_id = e.student_id
              JOIN app_user u ON u.id = st.user_id
             WHERE t.id = (SELECT max(s2.term_id) FROM section s2 JOIN class_session cs ON cs.section_id = s2.id)
            """;

    private static final String AT_RISK_TAIL = """
             GROUP BY e.id, u.full_name, st.roll_no, c.code, t.name
            HAVING 100.0 * count(*) FILTER (WHERE a.present) / count(*) < ?
             ORDER BY pct, u.full_name LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final int threshold;

    public JdbcInsightsService(JdbcTemplate jdbc, @Value("${ums.attendance-threshold}") int threshold) {
        this.jdbc = jdbc;
        this.threshold = threshold;
    }

    @Override
    public List<AtRisk> atRisk(Long facultyId, int limit) {
        int l = Rs.clampLimit(limit, 10, 50);
        org.springframework.jdbc.core.RowMapper<AtRisk> mapper = (rs, n) -> new AtRisk(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getInt(6));
        if (facultyId == null) {
            return jdbc.query(AT_RISK + AT_RISK_TAIL, mapper, threshold, l);
        }
        return jdbc.query(AT_RISK + " AND s.faculty_id = ? " + AT_RISK_TAIL, mapper, facultyId, threshold, l);
    }

    @Override
    public AdminSummary adminSummary() {
        Map<String, Integer> users = new LinkedHashMap<>();
        for (String role : List.of("STUDENT", "FACULTY", "ADMIN", "DEV")) {
            users.put(role, 0);
        }
        jdbc.query("SELECT role, count(*) FROM app_user WHERE active GROUP BY role", rs -> {
            users.put(rs.getString(1), rs.getInt(2));
        });
        Integer pending = jdbc.queryForObject("SELECT count(*) FROM mark_sheet WHERE state = 'SUBMITTED'", Integer.class);
        int[] seats = jdbc.query("""
                SELECT count(*), COALESCE(sum(s.capacity), 0), COALESCE(sum(s.enrolled_count), 0)
                  FROM section s JOIN term t ON t.id = s.term_id AND t.state = 'REGISTRATION_OPEN'
                """, rs -> {
            rs.next();
            return new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)};
        });
        List<TermHealth> terms = jdbc.query("SELECT id, name, state FROM term WHERE state <> 'CLOSED' ORDER BY id DESC LIMIT 5",
                (rs, n) -> new TermHealth(rs.getLong(1), rs.getString(2), rs.getString(3)));
        Integer events = jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE at >= date_trunc('day', now())", Integer.class);
        return new AdminSummary(users, pending == null ? 0 : pending, seats[0], seats[1], seats[2], terms,
                atRisk(null, 8), events == null ? 0 : events);
    }

    @Override
    public FacultySummary facultySummary(long facultyId) {
        int[] counts = jdbc.query("""
                SELECT count(*),
                       COALESCE(sum(s.enrolled_count), 0),
                       count(*) FILTER (WHERE ms.state = 'DRAFT'),
                       count(*) FILTER (WHERE ms.state = 'SUBMITTED')
                  FROM section s
                  JOIN term t ON t.id = s.term_id AND t.state IN ('REGISTRATION_OPEN', 'IN_PROGRESS', 'GRADING')
                  LEFT JOIN mark_sheet ms ON ms.section_id = s.id
                 WHERE s.faculty_id = ?
                """, rs -> {
            rs.next();
            return new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)};
        }, facultyId);
        return new FacultySummary(counts[0], counts[1], counts[2], counts[3], atRisk(facultyId, 8));
    }
}
