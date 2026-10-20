package edu.ums.web.faculty;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(N*K) per grid/save, N = students (<= 500), K = components (3-6): one pass with HashMap/HashSet lookups;
// submit is one set-based count; space O(N*K) — bounded by section capacity.

/** Marks entry. The mark sheet row is locked FOR UPDATE so a save can never interleave with a submit or an approval. */
@Service
public class JdbcMarksService implements MarksService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final SectionAccess access;
    private final SheetLifecycle lifecycle;
    private final AuditService audit;

    public JdbcMarksService(JdbcTemplate jdbc, SectionAccess access, SheetLifecycle lifecycle, AuditService audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Override
    public MarkGrid grid(long sectionId, long facultyId) {
        SectionAccess.Info info = access.require(sectionId, facultyId);
        List<ComponentView> components = components(sectionId);
        Map<Long, BigDecimal> max = new HashMap<>();
        Map<Long, BigDecimal> weight = new HashMap<>();
        components.forEach(c -> {
            max.put(c.id(), c.maxMarks());
            weight.put(c.id(), c.weight());
        });

        Map<Long, Map<Long, BigDecimal>> marksByEnrolment = new HashMap<>();
        jdbc.query("""
                SELECT m.enrolment_id, m.component_id, m.value FROM mark m
                  JOIN assessment_component c ON c.id = m.component_id WHERE c.section_id = ?
                """, rs -> {
            marksByEnrolment.computeIfAbsent(rs.getLong(1), k -> new LinkedHashMap<>()).put(rs.getLong(2), rs.getBigDecimal(3));
        }, sectionId);

        List<MarkRow> rows = jdbc.query("""
                SELECT e.id, st.roll_no, u.full_name FROM enrolment e
                  JOIN student st ON st.user_id = e.student_id JOIN app_user u ON u.id = st.user_id
                 WHERE e.section_id = ? AND e.state = 'ENROLLED' ORDER BY st.roll_no LIMIT 500
                """, (rs, n) -> {
            long enrolmentId = rs.getLong(1);
            Map<Long, BigDecimal> marks = marksByEnrolment.getOrDefault(enrolmentId, Map.of());
            return new MarkRow(enrolmentId, rs.getString(2), rs.getString(3), Map.copyOf(marks), total(marks, max, weight));
        }, sectionId);

        boolean editable = info.termAcceptsMarks() && SheetState.DRAFT.name().equals(info.sheetState());
        return new MarkGrid(sectionId, info.courseCode(), info.courseTitle(), info.sheetState(), editable, components, rows);
    }

    @Override
    @Transactional
    public void save(long sectionId, long facultyId, List<MarkEntry> entries) {
        if (entries == null || entries.isEmpty() || entries.size() > 5000) {
            throw new UmsException(ErrorCode.VALIDATION, "Send between 1 and 5000 mark cells.");
        }
        SectionAccess.Info info = access.require(sectionId, facultyId);
        requireEditable(info, lockedSheetState(info.sheetId()));

        Map<Long, ComponentView> byId = new HashMap<>();
        components(sectionId).forEach(c -> byId.put(c.id(), c));
        Set<Long> enrolments = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM enrolment WHERE section_id = ? AND state = 'ENROLLED'", Long.class, sectionId));

        List<Object[]> upserts = new ArrayList<>();
        List<Object[]> clears = new ArrayList<>();
        for (MarkEntry e : entries) {
            if (e == null || e.componentId() == null || e.enrolmentId() == null) {
                throw new UmsException(ErrorCode.VALIDATION, "Every mark needs a student and a component.");
            }
            ComponentView c = byId.get(e.componentId());
            if (c == null || !enrolments.contains(e.enrolmentId())) {
                throw new UmsException(ErrorCode.VALIDATION, "A mark refers to a student or component outside this section.");
            }
            if (e.value() == null) {
                clears.add(new Object[] {e.componentId(), e.enrolmentId()});
                continue;
            }
            if (e.value().signum() < 0 || e.value().compareTo(c.maxMarks()) > 0) {
                throw new UmsException(ErrorCode.MARK_OUT_OF_RANGE, c.name() + " must be between 0 and " + c.maxMarks().stripTrailingZeros().toPlainString() + ".");
            }
            upserts.add(new Object[] {e.componentId(), e.enrolmentId(), e.value().setScale(2, RoundingMode.HALF_UP)});
        }
        if (!upserts.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO mark (component_id, enrolment_id, value) VALUES (?, ?, ?)
                    ON CONFLICT (component_id, enrolment_id) DO UPDATE SET value = EXCLUDED.value
                    """, upserts);
        }
        if (!clears.isEmpty()) {
            jdbc.batchUpdate("DELETE FROM mark WHERE component_id = ? AND enrolment_id = ?", clears);
        }
        audit.record("MARKS_SAVE", "mark_sheet", info.sheetId(), upserts.size() + " saved, " + clears.size() + " cleared");
    }

    @Override
    @Transactional
    public void submit(long sectionId, long facultyId) {
        SectionAccess.Info info = access.require(sectionId, facultyId);
        requireEditable(info, lockedSheetState(info.sheetId()));
        Integer missing = jdbc.queryForObject("""
                SELECT count(*) FROM enrolment e
                  JOIN assessment_component c ON c.section_id = e.section_id
                 WHERE e.section_id = ? AND e.state = 'ENROLLED'
                   AND NOT EXISTS (SELECT 1 FROM mark m WHERE m.component_id = c.id AND m.enrolment_id = e.id)
                """, Integer.class, sectionId);
        if (missing != null && missing > 0) {
            throw new UmsException(ErrorCode.MARKS_INCOMPLETE, missing + " mark cell(s) are still empty.");
        }
        lifecycle.move(info.sheetId(), SheetState.DRAFT, SheetState.SUBMITTED);
        audit.record("MARKS_SUBMIT", "mark_sheet", info.sheetId(), "section " + sectionId);
    }

    private void requireEditable(SectionAccess.Info info, String lockedSheetState) {
        if (!info.termAcceptsMarks()) {
            throw new UmsException(ErrorCode.TERM_STATE, "Marks can be entered while the term is in progress or in grading.");
        }
        if (!SheetState.DRAFT.name().equals(lockedSheetState)) {
            throw new UmsException(ErrorCode.SHEET_STATE, "The mark sheet is " + lockedSheetState + " and can no longer be edited.");
        }
    }

    private String lockedSheetState(long sheetId) {
        return jdbc.queryForObject("SELECT state FROM mark_sheet WHERE id = ? FOR UPDATE", String.class, sheetId);
    }

    private List<ComponentView> components(long sectionId) {
        return jdbc.query("SELECT id, name, max_marks, weight FROM assessment_component WHERE section_id = ? ORDER BY id",
                (rs, n) -> new ComponentView(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4)), sectionId);
    }

    /** Weighted total out of 100 for the marks entered so far. */
    private static BigDecimal total(Map<Long, BigDecimal> marks, Map<Long, BigDecimal> max, Map<Long, BigDecimal> weight) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> m : marks.entrySet()) {
            BigDecimal componentMax = max.get(m.getKey());
            if (componentMax != null) {
                sum = sum.add(m.getValue().multiply(weight.get(m.getKey())).divide(componentMax, 6, RoundingMode.HALF_UP));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP).min(HUNDRED);
    }
}
