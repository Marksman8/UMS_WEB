package edu.ums.web.academic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public interface SectionService {

    /** Sections of one term (or the newest term when {@code termId} is null), at most 200. */
    List<SectionRow> list(Long termId);

    SectionRow create(NewSection request);

    List<FacultyOption> faculty();

    record SectionRow(long id, long termId, String termName, String termState, long courseId, String courseCode,
                      String courseTitle, int credits, String code, String facultyName, int capacity, int enrolled,
                      int dayOfWeek, String start, String end, String room, String sheetState) {
    }

    record FacultyOption(long id, String name, String deptCode) {
    }

    record NewSection(@NotNull Long courseId, @NotNull Long termId, @NotNull Long facultyId,
                      @Size(max = 10) String code,
                      @Min(1) @Max(500) int capacity,
                      @Min(1) @Max(7) int dayOfWeek,
                      @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "use HH:mm") String start,
                      @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "use HH:mm") String end,
                      @Size(max = 30) String room) {
    }
}
