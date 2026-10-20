package edu.ums.web.student;

import java.math.BigDecimal;
import java.util.List;

/** Read-only dashboard data for the signed-in student. */
public interface StudentPortalService {

    StudentSummary summary(long studentId);

    List<AttendanceLine> attendance(long studentId);

    record StudentSummary(String fullName, String rollNo, String programme, int semester, int currentCredits,
                          int creditLimit, BigDecimal cgpa, BigDecimal lastSgpa, BigDecimal attendancePercent,
                          int coursesBelowThreshold, int openSections, int attendanceThreshold) {
    }

    record AttendanceLine(String code, String title, String term, int held, int present, BigDecimal percent, boolean belowThreshold) {
    }
}
