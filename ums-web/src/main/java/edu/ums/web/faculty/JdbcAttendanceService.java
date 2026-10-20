package edu.ums.web.faculty;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(N) per mark call (one batched upsert of N <= 500 rows); O(N) per sheet; space O(N).

@Service
public class JdbcAttendanceService implements AttendanceService {

    private final JdbcTemplate jdbc;
    private final SectionAccess access;
    private final AuditService audit;
    private final Clock clock;

    public JdbcAttendanceService(JdbcTemplate jdbc, SectionAccess access, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<SessionRow> sessions(long sectionId, long facultyId) {
        access.require(sectionId, facultyId);
        return jdbc.query("""
                SELECT cs.id, cs.held_on, cs.topic,
                       count(a.enrolment_id) FILTER (WHERE a.present) AS present, count(a.enrolment_id) AS total
                  FROM class_session cs LEFT JOIN attendance a ON a.session_id = cs.id
                 WHERE cs.section_id = ?
                 GROUP BY cs.id ORDER BY cs.held_on DESC LIMIT 200
                """, (rs, n) -> new SessionRow(rs.getLong("id"), Rs.date(rs, "held_on"), rs.getString("topic"),
                rs.getInt("present"), rs.getInt("total")), sectionId);
    }

    @Override
    @Transactional
    public SessionRow openSession(long sectionId, long facultyId, NewSession request) {
        SectionAccess.Info info = access.require(sectionId, facultyId);
        if (!"IN_PROGRESS".equals(info.termState())) {
            throw new UmsException(ErrorCode.TERM_STATE, "Attendance can be taken once the term is in progress.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate day = request == null || request.heldOn() == null ? today : request.heldOn();
        if (day.isAfter(today)) {
            throw new UmsException(ErrorCode.VALIDATION, "A class session cannot be in the future.");
        }
        String topic = request == null || request.topic() == null || request.topic().isBlank() ? null : request.topic().trim();
        Long id = jdbc.queryForObject("""
                INSERT INTO class_session (section_id, held_on, topic) VALUES (?, ?, ?)
                ON CONFLICT (section_id, held_on) DO UPDATE SET topic = COALESCE(EXCLUDED.topic, class_session.topic)
                RETURNING id
                """, Long.class, sectionId, day, topic);
        audit.record("SESSION_OPEN", "class_session", id, "section " + sectionId + " on " + day);
        return new SessionRow(id, day, topic, 0, 0);
    }

    @Override
    public List<AttendanceEntry> sheet(long sessionId, long facultyId) {
        long sectionId = ownedSection(sessionId, facultyId);
        return jdbc.query("""
                SELECT e.id, st.roll_no, u.full_name, a.present
                  FROM enrolment e
                  JOIN student st ON st.user_id = e.student_id
                  JOIN app_user u ON u.id = st.user_id
                  LEFT JOIN attendance a ON a.session_id = ? AND a.enrolment_id = e.id
                 WHERE e.section_id = ? AND e.state = 'ENROLLED'
                 ORDER BY st.roll_no LIMIT 500
                """, (rs, n) -> new AttendanceEntry(rs.getLong(1), rs.getString(2), rs.getString(3),
                (Boolean) rs.getObject(4)), sessionId, sectionId);
    }

    @Override
    @Transactional
    public void mark(long sessionId, long facultyId, List<AttendanceMark> marks) {
        if (marks == null || marks.isEmpty() || marks.size() > 500) {
            throw new UmsException(ErrorCode.VALIDATION, "Send between 1 and 500 attendance rows.");
        }
        long sectionId = ownedSection(sessionId, facultyId);
        SectionAccess.Info info = access.require(sectionId, facultyId);
        if (!"IN_PROGRESS".equals(info.termState())) {
            throw new UmsException(ErrorCode.TERM_STATE, "Attendance can be taken once the term is in progress.");
        }
        List<Object[]> batch = new ArrayList<>(marks.size());
        for (AttendanceMark m : marks) {
            if (m == null || m.enrolmentId() == null || m.present() == null) {
                throw new UmsException(ErrorCode.VALIDATION, "Every attendance row needs a student and a present/absent value.");
            }
            batch.add(new Object[] {sessionId, m.present(), m.enrolmentId(), sectionId});
        }
        int[] counts = jdbc.batchUpdate("""
                INSERT INTO attendance (session_id, enrolment_id, present)
                SELECT ?, e.id, ? FROM enrolment e WHERE e.id = ? AND e.section_id = ? AND e.state = 'ENROLLED'
                ON CONFLICT (session_id, enrolment_id) DO UPDATE SET present = EXCLUDED.present
                """, batch);
        for (int c : counts) {
            if (c == 0) {
                throw new UmsException(ErrorCode.VALIDATION, "The list contains a student who is not enrolled in this section.");
            }
        }
        audit.record("ATTENDANCE_MARK", "class_session", sessionId, marks.size() + " rows");
    }

    private long ownedSection(long sessionId, long facultyId) {
        List<Long> found = jdbc.queryForList("""
                SELECT cs.section_id FROM class_session cs JOIN section s ON s.id = cs.section_id
                 WHERE cs.id = ? AND s.faculty_id = ?
                """, Long.class, sessionId, facultyId);
        if (found.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND, "That class session is not yours or does not exist.");
        }
        return found.get(0);
    }
}
