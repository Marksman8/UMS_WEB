package edu.ums.web.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleGuardTest {

    private final ConsoleGuard guard = new ConsoleGuard();

    @Test
    void acceptsPlainReadQueries() {
        assertEquals("SELECT * FROM student LIMIT 5", guard.check("SELECT * FROM student LIMIT 5;"));
        guard.check("WITH x AS (SELECT 1) SELECT * FROM x");
        guard.check("  -- a comment\n select 1 /* inline */ ");
        guard.check("SELECT 'drop table x; --' AS harmless");
        guard.check("EXPLAIN SELECT * FROM course");
    }

    @Test
    void rejectsWritesDdlAndMultipleStatements() {
        List<String> bad = List.of(
                "DELETE FROM student", "UPDATE student SET semester = 1", "INSERT INTO notice VALUES (1)", "DROP TABLE student",
                "SELECT 1; DROP TABLE student", "TRUNCATE student", "CREATE TABLE x (a int)", "COPY student TO '/tmp/x'",
                "SELECT * FROM student FOR UPDATE", "SELECT pg_sleep(10)", "WITH d AS (DELETE FROM student RETURNING *) SELECT * FROM d",
                "SELECT pg_read_file('/etc/passwd')", "SELECT lo_import('/etc/passwd')", "");
        for (String sql : bad) {
            UmsException e = assertThrows(UmsException.class, () -> guard.check(sql), sql);
            assertEquals(ErrorCode.SQL_REJECTED, e.code(), sql);
        }
    }

    @Test
    void passwordHashesAreUnreachable() {
        List<String> bad = List.of(
                "SELECT password_hash FROM app_user", "SELECT * FROM app_user", "SELECT * FROM \"app_user\"",
                "SELECT query_to_xml('select * from app_user', true, true, '')", "SELECT * FROM ums.app_user",
                "SELECT U&\"app\\005fuser\".* FROM app_user_public", "SELECT * FROM pg_shadow");
        for (String sql : bad) {
            assertThrows(UmsException.class, () -> guard.check(sql), sql);
        }
        guard.check("SELECT * FROM app_user_public");
    }

    @Test
    void rejectsTrickyQuoting() {
        for (String sql : List.of("SELECT E'a\\'b'", "SELECT $$x$$", "SELECT 1 /* never closed", "SELECT 'never closed", "SELECT $1")) {
            assertThrows(UmsException.class, () -> guard.check(sql), sql);
        }
    }

    @Test
    void rejectsOverlongInput() {
        assertThrows(UmsException.class, () -> guard.check("SELECT " + "1,".repeat(ConsoleGuard.MAX_LENGTH)));
    }
}
