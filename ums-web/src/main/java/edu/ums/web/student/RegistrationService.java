package edu.ums.web.student;

import java.util.List;

/** Course registration for the signed-in student. */
public interface RegistrationService {

    /** Sections whose term is open for registration, with this student's status for each. */
    List<SectionOffer> openSections(long studentId);

    /** Registers the student; every rule is checked and the seat is taken by one atomic UPDATE. */
    Registered register(long studentId, long sectionId);

    void drop(long studentId, long enrolmentId);

    /** Live enrolments in terms that have not published results yet (also the timetable source). */
    List<MyCourse> myCourses(long studentId);

    record SectionOffer(long sectionId, String termName, String courseCode, String title, int credits, String code,
                        String faculty, int capacity, int enrolled, int seatsLeft, int dayOfWeek, String start,
                        String end, String room, boolean registered, String missingPrereqs) {
    }

    record MyCourse(long enrolmentId, long sectionId, String termName, String termState, String courseCode, String title,
                    int credits, String code, String faculty, int dayOfWeek, String start, String end, String room) {
    }

    record Registered(long enrolmentId, long sectionId, int creditsNow) {
    }
}
