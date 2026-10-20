package edu.ums.web.admin;

import edu.ums.web.api.PageResult;
import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import edu.ums.web.user.CurrentUser;
import edu.ums.web.user.Role;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Complexity: time O(L) per list page (keyset on the primary key, L <= 100) + one BCrypt per create; CSV import O(R) for
// R <= 500 lines, each its own short transaction; space O(L) / O(R) — all bounded.

@Service
public class JdbcUserAdminService implements UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserAdminService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int MAX_IMPORT_LINES = 500;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public JdbcUserAdminService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit, CurrentUser currentUser,
                                Clock clock, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public PageResult<UserRow> list(String search, String role, Long after, int limit) {
        int l = Rs.clampLimit(limit, 50, 100);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, u.full_name, u.email, u.role, u.active,
                       (u.locked_until IS NOT NULL AND u.locked_until > now()) AS locked, u.created_at,
                       COALESCE(st.roll_no, d.code || ' - ' || f.designation, '') AS detail
                  FROM app_user u
                  LEFT JOIN student st ON st.user_id = u.id
                  LEFT JOIN faculty f ON f.user_id = u.id
                  LEFT JOIN department d ON d.id = f.dept_id
                 WHERE u.id > ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(after == null ? 0L : after);
        if (role != null && !role.isBlank()) {
            sql.append(" AND u.role = ?");
            args.add(parseRole(role).name());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (u.username ILIKE ? OR u.full_name ILIKE ? OR st.roll_no ILIKE ?)");
            String like = Rs.likeContains(search.trim());
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.id LIMIT ?");
        args.add(l + 1);
        List<UserRow> rows = jdbc.query(sql.toString(), (rs, n) -> new UserRow(rs.getLong("id"), rs.getString("username"),
                rs.getString("full_name"), rs.getString("email"), rs.getString("role"), rs.getBoolean("active"),
                rs.getBoolean("locked"), rs.getString("detail"), Rs.instant(rs, "created_at")), args.toArray());
        if (rows.size() > l) {
            List<UserRow> page = new ArrayList<>(rows.subList(0, l));
            return new PageResult<>(page, page.get(page.size() - 1).id());
        }
        return new PageResult<>(rows, null);
    }

    @Override
    @Transactional
    public CreatedUser create(NewUser r) {
        if (r.role() == Role.DEV) {
            throw new UmsException(ErrorCode.VALIDATION, "Developer accounts cannot be created from the admin area.");
        }
        boolean generated = r.password() == null || r.password().isBlank();
        String password = generated ? generatePassword() : r.password();
        requirePolicy(password);

        String programmeCode = null;
        if (r.role() == Role.STUDENT) {
            if (r.programmeId() == null) {
                throw new UmsException(ErrorCode.VALIDATION, "Choose a programme for the student.");
            }
            programmeCode = lookupCode("programme", r.programmeId());
        }
        if (r.role() == Role.FACULTY) {
            if (r.deptId() == null) {
                throw new UmsException(ErrorCode.VALIDATION, "Choose a department for the faculty member.");
            }
            lookupCode("department", r.deptId());
        }

        Long id = jdbc.queryForObject("INSERT INTO app_user (username, password_hash, role, full_name, email) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, r.username().trim(), encoder.encode(password), r.role().name(), r.fullName().trim(),
                r.email() == null || r.email().isBlank() ? null : r.email().trim());

        String rollNo = null;
        if (r.role() == Role.STUDENT) {
            int year = r.admissionYear() == null ? Year.now(clock).getValue() : r.admissionYear();
            int semester = r.semester() == null ? 1 : r.semester();
            if (year < 2000 || year > 2100 || semester < 1 || semester > 12) {
                throw new UmsException(ErrorCode.VALIDATION, "Check the admission year and semester.");
            }
            rollNo = r.rollNo() == null || r.rollNo().isBlank()
                    ? programmeCode + String.format("%02d", year % 100) + String.format("%03d", id)
                    : r.rollNo().trim().toUpperCase();
            jdbc.update("INSERT INTO student (user_id, roll_no, programme_id, admission_year, current_semester) VALUES (?, ?, ?, ?, ?)",
                    id, rollNo, r.programmeId(), year, semester);
        } else if (r.role() == Role.FACULTY) {
            jdbc.update("INSERT INTO faculty (user_id, dept_id, designation) VALUES (?, ?, ?)", id, r.deptId(),
                    r.designation() == null || r.designation().isBlank() ? "Assistant Professor" : r.designation().trim());
        }
        audit.record("USER_CREATE", "app_user", id, r.role() + " " + r.username());
        return new CreatedUser(id, r.username().trim(), r.role().name(), rollNo, generated ? password : null);
    }

    @Override
    @Transactional
    public void setActive(long userId, boolean active) {
        if (!active && userId == currentUser.id()) {
            throw new UmsException(ErrorCode.VALIDATION, "You cannot disable your own account.");
        }
        int rows = jdbc.update("UPDATE app_user SET active = ?, failed_attempts = 0, locked_until = NULL, version = version + 1 WHERE id = ?",
                active, userId);
        if (rows == 0) {
            throw new UmsException(ErrorCode.NOT_FOUND);
        }
        audit.record(active ? "USER_ENABLE" : "USER_DISABLE", "app_user", userId, null);
    }

    @Override
    @Transactional
    public String resetPassword(long userId, String newPassword) {
        String password = newPassword == null || newPassword.isBlank() ? generatePassword() : newPassword;
        requirePolicy(password);
        int rows = jdbc.update("UPDATE app_user SET password_hash = ?, failed_attempts = 0, locked_until = NULL, version = version + 1 WHERE id = ?",
                encoder.encode(password), userId);
        if (rows == 0) {
            throw new UmsException(ErrorCode.NOT_FOUND);
        }
        audit.record("PASSWORD_RESET", "app_user", userId, null);
        return password;
    }

    @Override
    public ImportReport importCsv(String csv) {
        if (csv == null || csv.isBlank() || csv.length() > 200_000) {
            throw new UmsException(ErrorCode.VALIDATION, "Paste between 1 and 500 CSV lines.");
        }
        String[] lines = csv.split("\\R");
        List<ImportLine> report = new ArrayList<>();
        int created = 0;
        int failed = 0;
        int processed = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || (i == 0 && line.toLowerCase().startsWith("role"))) {
                continue;
            }
            if (++processed > MAX_IMPORT_LINES) {
                throw new UmsException(ErrorCode.VALIDATION, "Import at most " + MAX_IMPORT_LINES + " lines at a time.");
            }
            ImportLine result = importOne(i + 1, line);
            report.add(result);
            if (result.ok()) {
                created++;
            } else {
                failed++;
            }
        }
        audit.record("USER_IMPORT", "app_user", null, created + " created, " + failed + " failed");
        return new ImportReport(created, failed, List.copyOf(report));
    }

    private ImportLine importOne(int lineNo, String line) {
        String[] f = line.split(",", -1);
        String username = f.length > 1 ? f[1].trim() : "";
        try {
            if (f.length < 4) {
                throw new UmsException(ErrorCode.VALIDATION, "Expected: role,username,fullName,email,programmeCode or deptCode");
            }
            Role role = parseRole(f[0].trim());
            String code = f.length > 4 ? f[4].trim() : "";
            Long programmeId = role == Role.STUDENT ? idByCode("programme", code) : null;
            Long deptId = role == Role.FACULTY ? idByCode("department", code) : null;
            NewUser user = new NewUser(username, null, f[2].trim(), f[3].trim().isEmpty() ? null : f[3].trim(), role,
                    programmeId, null, null, null, deptId, null);
            CreatedUser made = tx.execute(status -> create(user));
            return new ImportLine(lineNo, username, true, "Created " + role, made == null ? null : made.generatedPassword());
        } catch (UmsException e) {
            return new ImportLine(lineNo, username, false, e.getMessage(), null);
        } catch (DuplicateKeyException e) {
            return new ImportLine(lineNo, username, false, "That username or roll number already exists.", null);
        } catch (RuntimeException e) {
            // Best-effort path: one bad line must not stop the others; the cause is logged, never shown.
            log.warn("CSV import line {} failed", lineNo, e);
            return new ImportLine(lineNo, username, false, "This line could not be imported.", null);
        }
    }

    private Long idByCode(String table, String code) {
        if (code.isBlank()) {
            throw new UmsException(ErrorCode.VALIDATION, "A " + table + " code is required for this role.");
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM " + table + " WHERE upper(code) = upper(?)", Long.class, code);
        if (ids.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND, "Unknown " + table + " code " + code + ".");
        }
        return ids.get(0);
    }

    private String lookupCode(String table, Long id) {
        List<String> codes = jdbc.queryForList("SELECT code FROM " + table + " WHERE id = ?", String.class, id);
        if (codes.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND, "The selected " + table + " does not exist.");
        }
        return codes.get(0);
    }

    private static Role parseRole(String text) {
        try {
            return Role.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UmsException(ErrorCode.VALIDATION, "Unknown role: " + text, e);
        }
    }

    private static void requirePolicy(String password) {
        boolean ok = password.length() >= 8 && password.length() <= 72
                && password.chars().anyMatch(Character::isLetter) && password.chars().anyMatch(Character::isDigit);
        if (!ok) {
            throw new UmsException(ErrorCode.VALIDATION, "A password needs 8-72 characters with at least one letter and one digit.");
        }
    }

    private String generatePassword() {
        while (true) {
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (candidate.chars().anyMatch(Character::isLetter) && candidate.chars().anyMatch(Character::isDigit)) {
                return candidate;
            }
        }
    }
}
