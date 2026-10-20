package edu.ums.web.student;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Complexity: time O(R) per transcript, R = this student's results (<= 500, indexed by enrolment.student_id);
// CGPA is one aggregate query; space O(R).

@Service
public class DefaultTranscriptService implements TranscriptService {

    private final JdbcTemplate jdbc;

    public DefaultTranscriptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Transcript transcript(long studentId) {
        Map<Long, BigDecimal> sgpa = new LinkedHashMap<>();
        jdbc.query("SELECT term_id, sgpa FROM term_result WHERE student_id = ?", rs -> {
            sgpa.put(rs.getLong(1), rs.getBigDecimal(2));
        }, studentId);

        Map<Long, List<CourseGrade>> byTerm = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        jdbc.query("""
                SELECT t.id AS term_id, t.name AS term, c.code, c.title, c.credits, r.total, r.grade, r.points
                  FROM result r
                  JOIN enrolment e ON e.id = r.enrolment_id
                  JOIN section s ON s.id = e.section_id
                  JOIN term t ON t.id = s.term_id
                  JOIN course c ON c.id = s.course_id
                 WHERE e.student_id = ?
                 ORDER BY t.id, c.code LIMIT 500
                """, rs -> {
            long termId = rs.getLong("term_id");
            names.putIfAbsent(termId, rs.getString("term"));
            byTerm.computeIfAbsent(termId, k -> new ArrayList<>()).add(new CourseGrade(rs.getString("code"),
                    rs.getString("title"), rs.getInt("credits"), rs.getBigDecimal("total"), rs.getString("grade"), rs.getInt("points")));
        }, studentId);

        List<TermBlock> blocks = new ArrayList<>();
        byTerm.forEach((termId, courses) -> blocks.add(new TermBlock(termId, names.get(termId), sgpa.get(termId),
                courses.stream().mapToInt(CourseGrade::credits).sum(), List.copyOf(courses))));
        int credits = blocks.stream().mapToInt(TermBlock::credits).sum();
        return new Transcript(cgpa(studentId), credits, List.copyOf(blocks));
    }

    @Override
    public BigDecimal cgpa(long studentId) {
        return jdbc.query("""
                SELECT sum(r.points * c.credits) AS weighted, sum(c.credits) AS credits
                  FROM result r
                  JOIN enrolment e ON e.id = r.enrolment_id
                  JOIN section s ON s.id = e.section_id
                  JOIN course c ON c.id = s.course_id
                 WHERE e.student_id = ?
                """, rs -> {
            rs.next();
            BigDecimal weighted = rs.getBigDecimal("weighted");
            BigDecimal credits = rs.getBigDecimal("credits");
            if (weighted == null || credits == null || credits.signum() == 0) {
                return null;
            }
            return weighted.divide(credits, 2, RoundingMode.HALF_UP);
        }, studentId);
    }
}
