package edu.ums.web.faculty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    List<SessionRow> sessions(long sectionId, long facultyId);

    /** Starts (or re-opens) the class session of a date; the date defaults to today and cannot be in the future. */
    SessionRow openSession(long sectionId, long facultyId, NewSession request);

    /** Roster with the recorded present/absent value (null = not marked yet). */
    List<AttendanceEntry> sheet(long sessionId, long facultyId);

    void mark(long sessionId, long facultyId, List<@Valid AttendanceMark> marks);

    record SessionRow(long id, LocalDate heldOn, String topic, int present, int total) {
    }

    record NewSession(LocalDate heldOn, @Size(max = 160) String topic) {
    }

    record AttendanceEntry(long enrolmentId, String rollNo, String name, Boolean present) {
    }

    record AttendanceMark(@NotNull Long enrolmentId, @NotNull Boolean present) {
    }
}
