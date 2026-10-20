package edu.ums.web.faculty;

import java.util.List;

public interface FacultySectionService {

    /** Sections taught by this faculty member in terms that are not closed. */
    List<SectionBrief> mySections(long facultyId);

    /** Live roster of one of the faculty member's own sections. */
    List<RosterRow> roster(long sectionId, long facultyId);

    record SectionBrief(long id, long termId, String termName, String termState, String courseCode, String title,
                        int credits, String code, int enrolled, int capacity, int dayOfWeek, String start, String end,
                        String room, String sheetState, int sessionsHeld) {
    }

    record RosterRow(long enrolmentId, long studentId, String rollNo, String name, String email) {
    }
}
