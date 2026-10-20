package edu.ums.web.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public interface TermService {

    List<TermView> list();

    TermView create(NewTerm request);

    /** Moves the term to its next stage (publishing results when it reaches RESULTS_PUBLISHED). */
    TermView advance(long termId);

    record TermView(long id, String name, String state, LocalDate startsOn, LocalDate endsOn, long version, String nextState) {
    }

    record NewTerm(@NotBlank @Size(max = 40) String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
    }
}
