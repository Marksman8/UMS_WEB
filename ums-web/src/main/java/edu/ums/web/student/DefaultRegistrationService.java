package edu.ums.web.student;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(E + P) per register, E = this student's live enrolments in the term (<= ~10), P = prerequisites of
// the course (<= ~5), all indexed; O(S) per openSections, S <= 200; space O(S).

/**
 * Registration rules in order: term open (row share lock) -> student row lock -> duplicate course ->
 * prerequisites -> time clash -> credit limit -> seat. The seat is taken by one conditional UPDATE
 * ({@code enrolled_count < capacity}), so simultaneous requests for the last seat produce exactly one winner.
 * Lock order: term, student, section.
 */
@Service
public class DefaultRegistrationService implements RegistrationService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final int creditLimit;

    public DefaultRegistrationService(JdbcTemplate jdbc, AuditService audit, @Value("${ums.credit-limit}") int creditLimit) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.creditLimit = creditLimit;
    }

    /** SQL text that yields the comma-separated prerequisite codes the student has NOT passed; the course is referenced by {@code courseRef}. */
    private static String missingPrereqsSql(String courseRef) {
        return "SELECT string_agg(pc.code, ', ' ORDER BY pc.code) "
                + "FROM course_prereq p JOIN course pc ON pc.id = p.prereq_id "
                + "WHERE p.course_id = " + courseRef + " "
                + "AND NOT EXISTS (SELECT 1 FROM result r "
                + "JOIN enrolment pe ON pe.id = r.enrolment_id "
                + "JOIN section ps ON ps.id = pe.section_id "
                + "WHERE pe.student_id = ? AND ps.course_id = p.prereq_id AND r.grade <> 'F')";
    }

    @Override
    public List<SectionOffer> openSections(long studentId) {
        String sql = "SELECT s.id, t.name AS term_name, c.code AS course_code, c.title, c.credits, s.code, u.full_name AS faculty, "
                + "s.capacity, s.enrolled_count, s.day_of_week, s.start_min, s.end_min, s.room, "
                + "EXISTS (SELECT 1 FROM enrolment e WHERE e.section_id = s.id AND e.student_id = ? AND e.state = 'ENROLLED') AS registered, "
                + "(" + missingPrereqsSql("c.id") + ") AS missing "
                + "FROM section s "
                + "JOIN term t ON t.id = s.term_id AND t.state = 'REGISTRATION_OPEN' "
                + "JOIN course c ON c.id = s.course_id "
                + "JOIN app_user u ON u.id = s.faculty_id "
                + "ORDER BY c.code, s.code LIMIT 200";
        return jdbc.query(sql, (rs, n) -> new SectionOffer(rs.getLong("id"), rs.getString("term_name"), rs.getString("course_code"),
                rs.getString("title"), rs.getInt("credits"), rs.getString("code"), rs.getString("faculty"),
                rs.getInt("capacity"), rs.getInt("enrolled_count"), rs.getInt("capacity") - rs.getInt("enrolled_count"),
                rs.getInt("day_of_week"), Rs.hhmm(rs.getInt("start_min")), Rs.hhmm(rs.getInt("end_min")),
                rs.getString("room"), rs.getBoolean("registered"), rs.getString("missing")), studentId, studentId);
    }

    @Override
    @Transactional
    public Registered register(long studentId, long sectionId) {
        SectionInfo s = loadSection(sectionId);
        requireRegistrationOpen(s.termId(), "Registration is not open for this term.");   // 1. term (share lock)
        lockStudent(studentId);                                                          // 2. student (serialises own clicks)

        Integer same = jdbc.queryForObject("""
                SELECT count(*) FROM enrolment e JOIN section x ON x.id = e.section_id
                 WHERE e.student_id = ? AND e.state = 'ENROLLED' AND x.term_id = ? AND x.course_id = ?
                """, Integer.class, studentId, s.termId(), s.courseId());
        if (same != null && same > 0) {
            throw new UmsException(ErrorCode.ALREADY_ENROLLED);
        }
        String missing = jdbc.queryForObject(missingPrereqsSql("?"), String.class, s.courseId(), studentId);
        if (missing != null) {
            throw new UmsException(ErrorCode.PREREQ_MISSING, "Complete first: " + missing + ".");
        }
        List<String> clash = jdbc.queryForList("""
                SELECT c.code FROM enrolment e JOIN section x ON x.id = e.section_id JOIN course c ON c.id = x.course_id
                 WHERE e.student_id = ? AND e.state = 'ENROLLED' AND x.term_id = ? AND x.day_of_week = ?
                   AND x.start_min < ? AND x.end_min > ? LIMIT 1
                """, String.class, studentId, s.termId(), s.day(), s.endMin(), s.startMin());
        if (!clash.isEmpty()) {
            throw new UmsException(ErrorCode.TIME_CLASH, "This overlaps " + clash.get(0) + " in your timetable.");
        }
        Integer current = jdbc.queryForObject("""
                SELECT COALESCE(sum(c.credits), 0) FROM enrolment e JOIN section x ON x.id = e.section_id
                  JOIN course c ON c.id = x.course_id
                 WHERE e.student_id = ? AND e.state = 'ENROLLED' AND x.term_id = ?
                """, Integer.class, studentId, s.termId());
        int after = (current == null ? 0 : current) + s.credits();
        if (after > creditLimit) {
            throw new UmsException(ErrorCode.CREDIT_LIMIT, "You would have " + after + " credits; the limit is " + creditLimit + ".");
        }
        int seat = jdbc.update("UPDATE section SET enrolled_count = enrolled_count + 1, version = version + 1 "   // 3. section (atomic seat)
                + "WHERE id = ? AND enrolled_count < capacity", sectionId);
        if (seat == 0) {
            throw new UmsException(ErrorCode.SECTION_FULL);
        }
        Long enrolmentId = jdbc.queryForObject("INSERT INTO enrolment (student_id, section_id) VALUES (?, ?) RETURNING id",
                Long.class, studentId, sectionId);
        audit.record("REGISTER", "enrolment", enrolmentId, "section " + sectionId + " (" + s.courseCode() + ")");
        return new Registered(enrolmentId, sectionId, after);
    }

    @Override
    @Transactional
    public void drop(long studentId, long enrolmentId) {
        List<long[]> found = jdbc.query("""
                SELECT e.section_id, s.term_id FROM enrolment e JOIN section s ON s.id = e.section_id
                 WHERE e.id = ? AND e.student_id = ? AND e.state = 'ENROLLED'
                """, (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)}, enrolmentId, studentId);
        if (found.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND);
        }
        long sectionId = found.get(0)[0];
        long termId = found.get(0)[1];
        requireRegistrationOpen(termId, "Courses can only be dropped while registration is open.");
        lockStudent(studentId);
        int changed = jdbc.update("UPDATE enrolment SET state = 'DROPPED', version = version + 1 "
                + "WHERE id = ? AND student_id = ? AND state = 'ENROLLED'", enrolmentId, studentId);
        if (changed == 0) {
            throw new UmsException(ErrorCode.STALE_DATA);
        }
        jdbc.update("UPDATE section SET enrolled_count = enrolled_count - 1, version = version + 1 WHERE id = ? AND enrolled_count > 0", sectionId);
        audit.record("DROP", "enrolment", enrolmentId, "section " + sectionId);
    }

    @Override
    public List<MyCourse> myCourses(long studentId) {
        return jdbc.query("""
                SELECT e.id, s.id AS section_id, t.name AS term_name, t.state, c.code AS course_code, c.title, c.credits, s.code,
                       u.full_name AS faculty, s.day_of_week, s.start_min, s.end_min, s.room
                  FROM enrolment e
                  JOIN section s ON s.id = e.section_id
                  JOIN term t ON t.id = s.term_id AND t.state IN ('REGISTRATION_OPEN', 'IN_PROGRESS', 'GRADING')
                  JOIN course c ON c.id = s.course_id
                  JOIN app_user u ON u.id = s.faculty_id
                 WHERE e.student_id = ? AND e.state = 'ENROLLED'
                 ORDER BY s.day_of_week, s.start_min LIMIT 100
                """, (rs, n) -> new MyCourse(rs.getLong("id"), rs.getLong("section_id"), rs.getString("term_name"),
                rs.getString("state"), rs.getString("course_code"), rs.getString("title"), rs.getInt("credits"),
                rs.getString("code"), rs.getString("faculty"), rs.getInt("day_of_week"), Rs.hhmm(rs.getInt("start_min")),
                Rs.hhmm(rs.getInt("end_min")), rs.getString("room")), studentId);
    }

    /** Takes a share lock on the term row (a concurrent "advance" waits) and checks the stage. */
    private void requireRegistrationOpen(long termId, String message) {
        String state = jdbc.queryForObject("SELECT state FROM term WHERE id = ? FOR SHARE", String.class, termId);
        if (!"REGISTRATION_OPEN".equals(state)) {
            throw new UmsException(ErrorCode.TERM_STATE, message);
        }
    }

    private void lockStudent(long studentId) {
        List<Long> rows = jdbc.queryForList("SELECT user_id FROM student WHERE user_id = ? FOR UPDATE", Long.class, studentId);
        if (rows.isEmpty()) {
            throw new UmsException(ErrorCode.FORBIDDEN, "Only students can register for courses.");
        }
    }

    private SectionInfo loadSection(long sectionId) {
        List<SectionInfo> found = jdbc.query("""
                SELECT s.term_id, s.course_id, c.code, c.credits, s.day_of_week, s.start_min, s.end_min
                  FROM section s JOIN course c ON c.id = s.course_id WHERE s.id = ?
                """, (rs, n) -> new SectionInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                rs.getInt(6), rs.getInt(7)), sectionId);
        if (found.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND, "That section does not exist.");
        }
        return found.get(0);
    }

    private record SectionInfo(long termId, long courseId, String courseCode, int credits, int day, int startMin, int endMin) {
    }
}
