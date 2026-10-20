package edu.ums.web.faculty;

import edu.ums.web.common.Rs;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Complexity: time O(S) per mySections (idx_section_faculty, S <= 100); O(N) per roster (idx_enrolment_section, N <= 500); space O(S) / O(N).

@Service
public class JdbcFacultySectionService implements FacultySectionService {

    private final JdbcTemplate jdbc;
    private final SectionAccess access;

    public JdbcFacultySectionService(JdbcTemplate jdbc, SectionAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Override
    public List<SectionBrief> mySections(long facultyId) {
        return jdbc.query("""
                SELECT s.id, t.id AS term_id, t.name AS term_name, t.state, c.code AS course_code, c.title, c.credits, s.code,
                       s.enrolled_count, s.capacity, s.day_of_week, s.start_min, s.end_min, s.room, ms.state AS sheet_state,
                       (SELECT count(*) FROM class_session cs WHERE cs.section_id = s.id) AS sessions
                  FROM section s
                  JOIN term t ON t.id = s.term_id AND t.state <> 'CLOSED'
                  JOIN course c ON c.id = s.course_id
                  LEFT JOIN mark_sheet ms ON ms.section_id = s.id
                 WHERE s.faculty_id = ?
                 ORDER BY t.id DESC, s.day_of_week, s.start_min LIMIT 100
                """, (rs, n) -> new SectionBrief(rs.getLong("id"), rs.getLong("term_id"), rs.getString("term_name"),
                rs.getString("state"), rs.getString("course_code"), rs.getString("title"), rs.getInt("credits"),
                rs.getString("code"), rs.getInt("enrolled_count"), rs.getInt("capacity"), rs.getInt("day_of_week"),
                Rs.hhmm(rs.getInt("start_min")), Rs.hhmm(rs.getInt("end_min")), rs.getString("room"),
                rs.getString("sheet_state"), rs.getInt("sessions")), facultyId);
    }

    @Override
    public List<RosterRow> roster(long sectionId, long facultyId) {
        access.require(sectionId, facultyId);
        return jdbc.query("""
                SELECT e.id, st.user_id, st.roll_no, u.full_name, u.email
                  FROM enrolment e
                  JOIN student st ON st.user_id = e.student_id
                  JOIN app_user u ON u.id = st.user_id
                 WHERE e.section_id = ? AND e.state = 'ENROLLED'
                 ORDER BY st.roll_no LIMIT 500
                """, (rs, n) -> new RosterRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                sectionId);
    }
}
