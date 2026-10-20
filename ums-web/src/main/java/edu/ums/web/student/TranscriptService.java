package edu.ums.web.student;

import java.math.BigDecimal;
import java.util.List;

public interface TranscriptService {

    Transcript transcript(long studentId);

    /** CGPA over every published result (all attempts count), or null when there are none yet. */
    BigDecimal cgpa(long studentId);

    record CourseGrade(String code, String title, int credits, BigDecimal total, String grade, int points) {
    }

    record TermBlock(long termId, String term, BigDecimal sgpa, int credits, List<CourseGrade> courses) {
    }

    record Transcript(BigDecimal cgpa, int credits, List<TermBlock> terms) {
    }
}
