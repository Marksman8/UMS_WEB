package edu.ums.web.security;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Complexity: time O(1) per login (indexed lookup + one BCrypt check); space O(1).

/**
 * Lockout rule: 5 consecutive failures lock the account for 15 minutes. The counter is changed by
 * one conditional UPDATE, never read-then-write in Java, so parallel guesses cannot slip past it.
 */
@Service
public class DefaultAuthService implements AuthService {

    private static final String FIND = "SELECT id, username, password_hash, role, full_name, active, "
            + "(locked_until IS NOT NULL AND locked_until > now()) AS locked FROM app_user WHERE lower(username) = lower(?)";

    private static final String FAIL = "UPDATE app_user SET "
            + "failed_attempts = CASE WHEN failed_attempts + 1 >= 5 THEN 0 ELSE failed_attempts + 1 END, "
            + "locked_until = CASE WHEN failed_attempts + 1 >= 5 THEN now() + interval '15 minutes' ELSE locked_until END "
            + "WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String dummyHash;

    public DefaultAuthService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Override
    public UmsPrincipal login(String username, String password) {
        List<Account> found = jdbc.query(FIND, (rs, n) -> new Account(rs.getLong("id"), rs.getString("username"),
                rs.getString("password_hash"), Role.valueOf(rs.getString("role")), rs.getString("full_name"),
                rs.getBoolean("active"), rs.getBoolean("locked")), username);
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash); // keep timing similar for unknown users
            audit.recordAs(shorten(username), null, "LOGIN_FAILED", "app_user", null, "unknown user", false);
            throw new UmsException(ErrorCode.INVALID_CREDENTIALS);
        }
        Account a = found.get(0);
        if (a.locked()) {
            audit.recordAs(a.username(), a.role().name(), "LOGIN_BLOCKED", "app_user", a.id(), "account locked", false);
            throw new UmsException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!encoder.matches(password, a.hash())) {
            jdbc.update(FAIL, a.id());
            audit.recordAs(a.username(), a.role().name(), "LOGIN_FAILED", "app_user", a.id(), "wrong password", false);
            throw new UmsException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!a.active()) {
            audit.recordAs(a.username(), a.role().name(), "LOGIN_BLOCKED", "app_user", a.id(), "account disabled", false);
            throw new UmsException(ErrorCode.ACCOUNT_DISABLED);
        }
        jdbc.update("UPDATE app_user SET failed_attempts = 0, locked_until = NULL WHERE id = ? AND (failed_attempts <> 0 OR locked_until IS NOT NULL)", a.id());
        audit.recordAs(a.username(), a.role().name(), "LOGIN", "app_user", a.id(), null, true);
        return new UmsPrincipal(a.id(), a.username(), a.fullName(), a.role());
    }

    private static String shorten(String s) {
        return s == null ? "?" : (s.length() > 40 ? s.substring(0, 40) : s);
    }

    private record Account(long id, String username, String hash, Role role, String fullName, boolean active, boolean locked) {
    }
}
