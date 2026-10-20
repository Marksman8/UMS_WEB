package edu.ums.web.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin review of submitted mark sheets. */
public interface ApprovalService {

    List<PendingSheet> pending();

    /** SUBMITTED -> APPROVED. */
    void approve(long sheetId);

    /** SUBMITTED -> DRAFT, so the faculty member can correct and resubmit. */
    void sendBack(long sheetId);

    record PendingSheet(long sheetId, long sectionId, String term, String courseCode, String title, String section,
                        String faculty, int enrolled, BigDecimal average, int failing, Instant submittedAt) {
    }
}
