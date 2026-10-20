package edu.ums.web.dev;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// Complexity: time O(1) per question (a fixed list of keyword checks on <= 500 characters); space O(1).

/** Offline fallback: maps common questions about this schema to ready-made SELECTs. */
@Component
public class LocalSqlSuggester {

    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,3})\\b");

    public Optional<NlToSqlService.Suggestion> suggest(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        int n = number(q, 10);
        if (has(q, "attendance") && has(q, "below", "low", "risk", "shortage", "under")) {
            int pct = number(q, 75);
            return of("""
                    SELECT u.full_name AS student, st.roll_no, c.code AS course,
                           round(100.0 * count(*) FILTER (WHERE a.present) / count(*), 1) AS attendance_pct
                      FROM attendance a
                      JOIN enrolment e ON e.id = a.enrolment_id
                      JOIN student st ON st.user_id = e.student_id
                      JOIN app_user_public u ON u.id = st.user_id
                      JOIN section s ON s.id = e.section_id
                      JOIN course c ON c.id = s.course_id
                     GROUP BY e.id, u.full_name, st.roll_no, c.code
                    HAVING 100.0 * count(*) FILTER (WHERE a.present) / count(*) < %d
                     ORDER BY attendance_pct LIMIT 50""".formatted(pct),
                    "Students whose attendance in a course is below " + pct + "%.");
        }
        if (has(q, "cgpa", "gpa", "topper", "top ", "best student", "rank")) {
            return of("""
                    SELECT u.full_name AS student, st.roll_no,
                           round(sum(r.points * c.credits)::numeric / sum(c.credits), 2) AS cgpa, sum(c.credits) AS credits
                      FROM result r
                      JOIN enrolment e ON e.id = r.enrolment_id
                      JOIN section s ON s.id = e.section_id
                      JOIN course c ON c.id = s.course_id
                      JOIN student st ON st.user_id = e.student_id
                      JOIN app_user_public u ON u.id = st.user_id
                     GROUP BY u.full_name, st.roll_no
                     ORDER BY cgpa DESC, student LIMIT %d""".formatted(n),
                    "Top " + n + " students by credit-weighted CGPA (all attempts count).");
        }
        if (has(q, "seat", "capacity", "full", "vacan")) {
            return of("""
                    SELECT t.name AS term, c.code AS course, s.code AS section, s.capacity, s.enrolled_count,
                           s.capacity - s.enrolled_count AS seats_left
                      FROM section s JOIN course c ON c.id = s.course_id JOIN term t ON t.id = s.term_id
                     ORDER BY t.id DESC, seats_left, c.code LIMIT 100""", "Seats left per section, fullest first.");
        }
        if (has(q, "pending", "submitted", "approval", "mark sheet", "sheet")) {
            return of("""
                    SELECT ms.state, count(*) AS sheets FROM mark_sheet ms GROUP BY ms.state ORDER BY sheets DESC""",
                    "How many mark sheets are in each state.");
        }
        if (has(q, "grade") && has(q, "distribution", "count", "how many", "spread")) {
            return of("""
                    SELECT grade, count(*) AS students, round(avg(total), 1) AS avg_total
                      FROM result GROUP BY grade ORDER BY min(total) DESC""", "Grade distribution across all published results.");
        }
        if (has(q, "user", "account", "people", "how many")) {
            return of("""
                    SELECT role, count(*) AS accounts, count(*) FILTER (WHERE active) AS active
                      FROM app_user_public GROUP BY role ORDER BY role""", "Accounts per role.");
        }
        if (has(q, "student")) {
            return of("""
                    SELECT st.roll_no, u.full_name, p.code AS programme, st.current_semester AS semester
                      FROM student st JOIN app_user_public u ON u.id = st.user_id JOIN programme p ON p.id = st.programme_id
                     ORDER BY st.roll_no LIMIT 100""", "All students with programme and semester.");
        }
        if (has(q, "course", "subject", "credit")) {
            return of("""
                    SELECT c.code, c.title, c.credits, d.code AS dept,
                           (SELECT string_agg(pc.code, ', ') FROM course_prereq p JOIN course pc ON pc.id = p.prereq_id WHERE p.course_id = c.id) AS prerequisites
                      FROM course c JOIN department d ON d.id = c.dept_id ORDER BY c.code""", "Course catalogue with prerequisites.");
        }
        if (has(q, "activity", "log", "login", "audit", "failed", "denied")) {
            return of("""
                    SELECT id, at, actor, action, entity, ok, detail FROM activity_log ORDER BY id DESC LIMIT 50""",
                    "The 50 most recent audit entries.");
        }
        return Optional.empty();
    }

    private static Optional<NlToSqlService.Suggestion> of(String sql, String explanation) {
        return Optional.of(new NlToSqlService.Suggestion(sql.strip(), explanation, "templates", null));
    }

    private static boolean has(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static int number(String q, int fallback) {
        Matcher m = NUMBER.matcher(q);
        return m.find() ? Math.max(1, Math.min(Integer.parseInt(m.group(1)), 100)) : fallback;
    }
}
