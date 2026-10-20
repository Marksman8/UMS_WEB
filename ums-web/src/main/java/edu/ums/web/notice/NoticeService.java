package edu.ums.web.notice;

import edu.ums.web.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public interface NoticeService {

    /** Notices this role may read, newest first, at most {@code limit} (max 100). */
    List<NoticeView> list(Role role, int limit);

    NoticeView create(long authorId, NewNotice request);

    void delete(long id);

    record NoticeView(long id, String title, String body, String audience, String author, Instant createdAt) {
    }

    record NewNotice(@NotBlank @Size(max = 160) String title,
                     @NotBlank @Size(max = 2000) String body,
                     @NotBlank @Pattern(regexp = "ALL|STUDENT|FACULTY", message = "ALL, STUDENT or FACULTY") String audience) {
    }
}
