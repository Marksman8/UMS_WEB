package edu.ums.web.faculty;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.StateMachine;
import edu.ums.web.common.UmsException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static edu.ums.web.faculty.SheetState.APPROVED;
import static edu.ums.web.faculty.SheetState.DRAFT;
import static edu.ums.web.faculty.SheetState.PUBLISHED;
import static edu.ums.web.faculty.SheetState.SUBMITTED;

// Complexity: time O(1) per move (one indexed compare-and-set UPDATE); space O(1).

/** DRAFT -> SUBMITTED -> APPROVED -> PUBLISHED, with SUBMITTED -> DRAFT when a sheet is sent back. */
@Component
public class SheetLifecycle {

    private final StateMachine<SheetState> machine = StateMachine.builder(SheetState.class)
            .allow(DRAFT, SUBMITTED)
            .allow(SUBMITTED, APPROVED, DRAFT)
            .allow(APPROVED, PUBLISHED)
            .build();

    private final JdbcTemplate jdbc;

    public SheetLifecycle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean canMove(SheetState from, SheetState to) {
        return machine.canMove(from, to);
    }

    /** Moves the sheet only if it is still in {@code from}; a lost race becomes STALE_DATA. */
    public void move(long sheetId, SheetState from, SheetState to) {
        machine.require(from, to, ErrorCode.SHEET_STATE, "A mark sheet cannot move from " + from + " to " + to + ".");
        int rows = jdbc.update("""
                UPDATE mark_sheet
                   SET state = ?, version = version + 1,
                       submitted_at = CASE WHEN ?::text = 'SUBMITTED' THEN now() ELSE submitted_at END,
                       decided_at = CASE WHEN ?::text IN ('APPROVED', 'DRAFT') THEN now() ELSE decided_at END
                 WHERE id = ? AND state = ?
                """, to.name(), to.name(), to.name(), sheetId, from.name());
        if (rows == 0) {
            throw new UmsException(ErrorCode.STALE_DATA);
        }
    }
}
