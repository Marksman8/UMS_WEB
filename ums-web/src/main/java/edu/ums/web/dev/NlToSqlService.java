package edu.ums.web.dev;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Turns a plain-English question into a read-only query the Dev can review and run. It never runs it itself. */
public interface NlToSqlService {

    Suggestion suggest(String question);

    record AskRequest(@NotBlank @Size(max = 500) String question) {
    }

    /** {@code source} is "claude" or "templates"; {@code note} explains anything the Dev should double-check. */
    record Suggestion(String sql, String explanation, String source, String note) {
    }
}
