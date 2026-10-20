package edu.ums.web.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(E log G) per term, E = enrolments of the term, G = 8 grade bands (set-based SQL, no per-row Java);
// space O(1) in Java — the database streams the work.

/** Grades are computed entirely in SQL: weighted total per enrolment, grade band lookup, then SGPA per student. */
@Component
public class SqlResultPublisher implements ResultPublisher {

    private static final String PUBLISH_SHEETS = """
            UPDATE mark_sheet ms
               SET state = 'PUBLISHED', decided_at = now(), version = ms.version + 1
              FROM section s
             WHERE s.id = ms.section_id AND s.term_id = ? AND ms.state = 'APPROVED'
            """;

    private static final String WRITE_RESULTS = """
            INSERT INTO result (enrolment_id, total, grade, points)
            SELECT e.id, t.total, g.grade, g.points
              FROM enrolment e
              JOIN section s ON s.id = e.section_id AND s.term_id = ?
              JOIN mark_sheet ms ON ms.section_id = s.id AND ms.state = 'PUBLISHED'
             CROSS JOIN LATERAL (
                   SELECT round(COALESCE(sum(m.value / c.max_marks * c.weight), 0), 2) AS total
                     FROM assessment_component c
                     LEFT JOIN mark m ON m.component_id = c.id AND m.enrolment_id = e.id
                    WHERE c.section_id = e.section_id) t
             CROSS JOIN LATERAL (
                   SELECT grade, points FROM grade_scale WHERE min_total <= t.total ORDER BY min_total DESC LIMIT 1) g
             WHERE e.state = 'ENROLLED'
            ON CONFLICT (enrolment_id) DO NOTHING
            """;

    private static final String WRITE_SGPA = """
            INSERT INTO term_result (student_id, term_id, credits, sgpa)
            SELECT e.student_id, s.term_id, sum(c.credits),
                   round(sum(r.points * c.credits)::numeric / sum(c.credits), 2)
              FROM result r
              JOIN enrolment e ON e.id = r.enrolment_id
              JOIN section s ON s.id = e.section_id
              JOIN course c ON c.id = s.course_id
             WHERE s.term_id = ?
             GROUP BY e.student_id, s.term_id
            ON CONFLICT (student_id, term_id) DO UPDATE SET credits = EXCLUDED.credits, sgpa = EXCLUDED.sgpa
            """;

    private final JdbcTemplate jdbc;

    public SqlResultPublisher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int publish(long termId) {
        jdbc.update(PUBLISH_SHEETS, termId);
        int written = jdbc.update(WRITE_RESULTS, termId);
        jdbc.update(WRITE_SGPA, termId);
        return written;
    }
}
