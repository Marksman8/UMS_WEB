package edu.ums.web.academic;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(S) per list (index idx_section_term, S <= 200); O(1) per create (3 inserts + 2 indexed checks); space O(S).

@Service
public class DefaultSectionService implements SectionService {

    private static final String SELECT = """
            SELECT s.id, s.term_id, t.name AS term_name, t.state AS term_state, c.id AS course_id, c.code AS course_code,
                   c.title, c.credits, s.code, u.full_name AS faculty_name, s.capacity, s.enrolled_count,
                   s.day_of_week, s.start_min, s.end_min, s.room, ms.state AS sheet_state
              FROM section s
              JOIN term t ON t.id = s.term_id
              JOIN course c ON c.id = s.course_id
              JOIN app_user u ON u.id = s.faculty_id
              LEFT JOIN mark_sheet ms ON ms.section_id = s.id
            """;

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public DefaultSectionService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public List<SectionRow> list(Long termId) {
        if (termId == null) {
            return jdbc.query(SELECT + " WHERE s.term_id = (SELECT max(id) FROM term) ORDER BY c.code, s.code LIMIT 200", this::map);
        }
        return jdbc.query(SELECT + " WHERE s.term_id = ? ORDER BY c.code, s.code LIMIT 200", this::map, termId);
    }

    @Override
    public List<FacultyOption> faculty() {
        return jdbc.query("""
                SELECT f.user_id, u.full_name, d.code FROM faculty f
                  JOIN app_user u ON u.id = f.user_id JOIN department d ON d.id = f.dept_id
                 WHERE u.active ORDER BY u.full_name LIMIT 200
                """, (rs, n) -> new FacultyOption(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    @Override
    @Transactional
    public SectionRow create(NewSection r) {
        int start = minutes(r.start());
        int end = minutes(r.end());
        if (end <= start) {
            throw new UmsException(ErrorCode.VALIDATION, "The end time must be after the start time.");
        }
        String state = jdbc.query("SELECT state FROM term WHERE id = ? FOR SHARE", rs -> rs.next() ? rs.getString(1) : null, r.termId());
        if (state == null) {
            throw new UmsException(ErrorCode.NOT_FOUND, "The selected term does not exist.");
        }
        if (!TermState.REGISTRATION_OPEN.name().equals(state)) {
            throw new UmsException(ErrorCode.TERM_STATE, "Sections can only be added while registration is open.");
        }
        Integer facultyClash = jdbc.queryForObject("""
                SELECT count(*) FROM section WHERE faculty_id = ? AND term_id = ? AND day_of_week = ?
                   AND start_min < ? AND end_min > ?
                """, Integer.class, r.facultyId(), r.termId(), r.dayOfWeek(), end, start);
        if (facultyClash != null && facultyClash > 0) {
            throw new UmsException(ErrorCode.TIME_CLASH, "That faculty member already teaches at this time.");
        }
        String code = r.code() == null || r.code().isBlank() ? "A" : r.code().trim().toUpperCase();
        String room = r.room() == null || r.room().isBlank() ? "TBA" : r.room().trim();
        Long id = jdbc.queryForObject("""
                INSERT INTO section (course_id, term_id, faculty_id, code, capacity, day_of_week, start_min, end_min, room)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, r.courseId(), r.termId(), r.facultyId(), code, r.capacity(), r.dayOfWeek(), start, end, room);
        jdbc.update("INSERT INTO mark_sheet (section_id) VALUES (?)", id);
        jdbc.update("""
                INSERT INTO assessment_component (section_id, name, max_marks, weight)
                VALUES (?, 'Quiz', 20, 20), (?, 'Midterm', 30, 30), (?, 'Final exam', 50, 50)
                """, id, id, id);
        audit.record("SECTION_CREATE", "section", id, "course " + r.courseId() + " term " + r.termId());
        return jdbc.queryForObject(SELECT + " WHERE s.id = ?", this::map, id);
    }

    private SectionRow map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new SectionRow(rs.getLong("id"), rs.getLong("term_id"), rs.getString("term_name"), rs.getString("term_state"),
                rs.getLong("course_id"), rs.getString("course_code"), rs.getString("title"), rs.getInt("credits"),
                rs.getString("code"), rs.getString("faculty_name"), rs.getInt("capacity"), rs.getInt("enrolled_count"),
                rs.getInt("day_of_week"), Rs.hhmm(rs.getInt("start_min")), Rs.hhmm(rs.getInt("end_min")),
                rs.getString("room"), rs.getString("sheet_state"));
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }
}
