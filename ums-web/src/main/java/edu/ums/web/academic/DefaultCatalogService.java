package edu.ums.web.academic;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(L) per list (indexed scan, L <= 300); O(P) per course create, P = prerequisites; space O(L).

@Service
public class DefaultCatalogService implements CatalogService {

    private static final String COURSES = """
            SELECT c.id, c.code, c.title, c.credits, d.code AS dept_code, c.description,
                   (SELECT string_agg(pc.code, ', ' ORDER BY pc.code) FROM course_prereq p
                      JOIN course pc ON pc.id = p.prereq_id WHERE p.course_id = c.id) AS prereqs
              FROM course c JOIN department d ON d.id = c.dept_id
            """;

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public DefaultCatalogService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public List<Dept> departments() {
        return jdbc.query("SELECT id, code, name FROM department ORDER BY code LIMIT 200",
                (rs, n) -> new Dept(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    @Override
    @Transactional
    public Dept createDepartment(NewDept r) {
        Long id = jdbc.queryForObject("INSERT INTO department (code, name) VALUES (?, ?) RETURNING id", Long.class,
                r.code().trim().toUpperCase(), r.name().trim());
        audit.record("DEPARTMENT_CREATE", "department", id, r.code());
        return new Dept(id, r.code().trim().toUpperCase(), r.name().trim());
    }

    @Override
    public List<Programme> programmes() {
        return jdbc.query("""
                SELECT p.id, p.dept_id, d.code AS dept_code, p.code, p.name, p.semesters
                  FROM programme p JOIN department d ON d.id = p.dept_id ORDER BY p.code LIMIT 200
                """, (rs, n) -> new Programme(rs.getLong("id"), rs.getLong("dept_id"), rs.getString("dept_code"),
                rs.getString("code"), rs.getString("name"), rs.getInt("semesters")));
    }

    @Override
    @Transactional
    public Programme createProgramme(NewProgramme r) {
        requireExists("department", r.deptId());
        Long id = jdbc.queryForObject("INSERT INTO programme (dept_id, code, name, semesters) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, r.deptId(), r.code().trim().toUpperCase(), r.name().trim(), r.semesters());
        audit.record("PROGRAMME_CREATE", "programme", id, r.code());
        return programmes().stream().filter(p -> p.id() == id).findFirst().orElseThrow();
    }

    @Override
    public List<Course> courses(String search) {
        if (search == null || search.isBlank()) {
            return jdbc.query(COURSES + " ORDER BY c.code LIMIT 300", this::mapCourse);
        }
        String like = Rs.likeContains(search.trim());
        return jdbc.query(COURSES + " WHERE c.code ILIKE ? OR c.title ILIKE ? ORDER BY c.code LIMIT 300",
                this::mapCourse, like, like);
    }

    @Override
    @Transactional
    public Course createCourse(NewCourse r) {
        requireExists("department", r.deptId());
        Long id = jdbc.queryForObject("INSERT INTO course (code, title, credits, dept_id, description) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, r.code().trim().toUpperCase(), r.title().trim(), r.credits(), r.deptId(),
                r.description() == null || r.description().isBlank() ? null : r.description().trim());
        if (r.prereqIds() != null) {
            for (Long prereq : r.prereqIds().stream().distinct().limit(10).toList()) {
                requireExists("course", prereq);
                jdbc.update("INSERT INTO course_prereq (course_id, prereq_id) VALUES (?, ?)", id, prereq);
            }
        }
        audit.record("COURSE_CREATE", "course", id, r.code());
        return jdbc.queryForObject(COURSES + " WHERE c.id = ?", this::mapCourse, id);
    }

    private Course mapCourse(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Course(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getInt("credits"),
                rs.getString("dept_code"), rs.getString("description"), rs.getString("prereqs"));
    }

    private void requireExists(String table, Long id) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)", Boolean.class, id);
        if (!Boolean.TRUE.equals(found)) {
            throw new UmsException(ErrorCode.NOT_FOUND, "The selected " + table + " does not exist.");
        }
    }
}
