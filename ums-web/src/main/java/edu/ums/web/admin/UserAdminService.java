package edu.ums.web.admin;

import edu.ums.web.api.PageResult;
import edu.ums.web.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Account administration: list, create (with student/faculty profile), enable/disable, password reset, CSV import. */
public interface UserAdminService {

    /** Keyset page ordered by id; {@code after} is the last id of the previous page. */
    PageResult<UserRow> list(String search, String role, Long after, int limit);

    CreatedUser create(NewUser request);

    void setActive(long userId, boolean active);

    /** Sets a new password (generated when {@code newPassword} is blank) and returns the password that was set. */
    String resetPassword(long userId, String newPassword);

    /** Lines of {@code role,username,fullName,email,programmeCode-or-deptCode}; at most 500 lines. */
    ImportReport importCsv(String csv);

    record UserRow(long id, String username, String fullName, String email, String role, boolean active,
                   boolean locked, String detail, Instant createdAt) {
    }

    record NewUser(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,40}", message = "3-40 letters, digits, dot, dash or underscore") String username,
                   @Size(max = 72) String password,
                   @NotBlank @Size(max = 120) String fullName,
                   @Email @Size(max = 160) String email,
                   @NotNull Role role,
                   Long programmeId, Integer admissionYear, Integer semester, @Size(max = 20) String rollNo,
                   Long deptId, @Size(max = 60) String designation) {
    }

    record CreatedUser(long id, String username, String role, String rollNo, String generatedPassword) {
    }

    record ImportLine(int line, String username, boolean ok, String message, String password) {
    }

    record ImportReport(int created, int failed, List<ImportLine> lines) {
    }
}
