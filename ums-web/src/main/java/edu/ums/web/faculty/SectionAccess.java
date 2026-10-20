package edu.ums.web.faculty;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ownership check shared by attendance and marks: a faculty member only ever touches their own sections. */
@Component
public class SectionAccess {

    private final JdbcTemplate jdbc;

    public SectionAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Throws NOT_FOUND (not FORBIDDEN) for other people's sections so ids cannot be probed. */
    public Info require(long sectionId, long facultyId) {
        List<Info> found = jdbc.query("""
                SELECT s.id, s.term_id, t.state, ms.id, ms.state, c.code, c.title
                  FROM section s
                  JOIN term t ON t.id = s.term_id
                  JOIN course c ON c.id = s.course_id
                  LEFT JOIN mark_sheet ms ON ms.section_id = s.id
                 WHERE s.id = ? AND s.faculty_id = ?
                """, (rs, n) -> new Info(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                rs.getString(5), rs.getString(6), rs.getString(7)), sectionId, facultyId);
        if (found.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND, "That section is not yours or does not exist.");
        }
        return found.get(0);
    }

    public record Info(long sectionId, long termId, String termState, long sheetId, String sheetState,
                       String courseCode, String courseTitle) {
        public boolean termAcceptsMarks() {
            return "IN_PROGRESS".equals(termState) || "GRADING".equals(termState);
        }
    }
}
