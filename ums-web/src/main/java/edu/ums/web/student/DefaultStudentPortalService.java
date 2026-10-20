package edu.ums.web.student;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Complexity: time O(A) per attendance call, A = this student's attendance rows (idx_attendance_enrolment, aggregated in SQL);
// summary is five indexed aggregate queries; space O(C), C = courses with sessions (<= 100).

@Service
public class DefaultStudentPortalService implements StudentPortalService {

    private final JdbcTemplate jdbc;
    private final TranscriptService transcripts;
    private final int creditLimit;
    private final int threshold;

    public DefaultStudentPortalService(JdbcTemplate jdbc, TranscriptService transcripts,
                                       @Value("${ums.credit-limit}") int creditLimit,
                                       @Value("${ums.attendance-threshold}") int threshold) {
        this.jdbc = jdbc;
        this.transcripts = transcripts;
        this.creditLimit = creditLimit;
        this.threshold = threshold;
    }

    @Override
    public StudentSummary summary(long studentId) {
        List<String[]> profile = jdbc.query("""
                SELECT u.full_name, st.roll_no, p.name, st.current_semester
                  FROM student st JOIN app_user u ON u.id = st.user_id JOIN programme p ON p.id = st.programme_id
                 WHERE st.user_id = ?
                """, (rs, n) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, studentId);
        if (profile.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND, "No student profile is linked to this account.");
        }
        Integer credits = jdbc.queryForObject("""
                SELECT COALESCE(sum(c.credits), 0) FROM enrolment e
                  JOIN section s ON s.id = e.section_id
                  JOIN term t ON t.id = s.term_id AND t.state IN ('REGISTRATION_OPEN', 'IN_PROGRESS', 'GRADING')
                  JOIN course c ON c.id = s.course_id
                 WHERE e.student_id = ? AND e.state = 'ENROLLED'
                """, Integer.class, studentId);
        List<BigDecimal> last = jdbc.queryForList("SELECT sgpa FROM term_result WHERE student_id = ? ORDER BY term_id DESC LIMIT 1",
                BigDecimal.class, studentId);
        Integer open = jdbc.queryForObject("SELECT count(*) FROM section s JOIN term t ON t.id = s.term_id AND t.state = 'REGISTRATION_OPEN'", Integer.class);
        List<AttendanceLine> lines = attendance(studentId);
        int held = lines.stream().mapToInt(AttendanceLine::held).sum();
        int present = lines.stream().mapToInt(AttendanceLine::present).sum();
        BigDecimal overall = held == 0 ? null : BigDecimal.valueOf(present * 100.0 / held).setScale(1, java.math.RoundingMode.HALF_UP);
        int below = (int) lines.stream().filter(AttendanceLine::belowThreshold).count();
        String[] p = profile.get(0);
        return new StudentSummary(p[0], p[1], p[2], Integer.parseInt(p[3]), credits == null ? 0 : credits, creditLimit,
                transcripts.cgpa(studentId), last.isEmpty() ? null : last.get(0), overall, below, open == null ? 0 : open, threshold);
    }

    @Override
    public List<AttendanceLine> attendance(long studentId) {
        return jdbc.query("""
                SELECT c.code, c.title, t.name AS term, count(*) AS held, count(*) FILTER (WHERE a.present) AS present
                  FROM enrolment e
                  JOIN attendance a ON a.enrolment_id = e.id
                  JOIN section s ON s.id = e.section_id
                  JOIN course c ON c.id = s.course_id
                  JOIN term t ON t.id = s.term_id
                 WHERE e.student_id = ? AND e.state = 'ENROLLED'
                 GROUP BY t.id, t.name, c.code, c.title
                 ORDER BY t.id DESC, c.code LIMIT 100
                """, (rs, n) -> {
            int held = rs.getInt("held");
            int present = rs.getInt("present");
            BigDecimal pct = BigDecimal.valueOf(present * 100.0 / held).setScale(1, java.math.RoundingMode.HALF_UP);
            return new AttendanceLine(rs.getString("code"), rs.getString("title"), rs.getString("term"), held, present, pct,
                    pct.compareTo(BigDecimal.valueOf(threshold)) < 0);
        }, studentId);
    }
}
