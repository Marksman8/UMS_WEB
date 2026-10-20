package edu.ums.web.faculty;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface MarksService {

    MarkGrid grid(long sectionId, long facultyId);

    /** Saves marks; a null value clears a cell. Only allowed while the sheet is DRAFT and the term accepts marks. */
    void save(long sectionId, long facultyId, List<MarkEntry> entries);

    /** DRAFT -> SUBMITTED, after checking that every student has every mark. */
    void submit(long sectionId, long facultyId);

    record ComponentView(long id, String name, BigDecimal maxMarks, BigDecimal weight) {
    }

    record MarkRow(long enrolmentId, String rollNo, String name, Map<Long, BigDecimal> marks, BigDecimal total) {
    }

    record MarkGrid(long sectionId, String courseCode, String title, String sheetState, boolean editable,
                    List<ComponentView> components, List<MarkRow> rows) {
    }

    record MarkEntry(@NotNull Long enrolmentId, @NotNull Long componentId, BigDecimal value) {
    }
}
