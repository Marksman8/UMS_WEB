package edu.ums.web.academic;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(1) per advance plus the set-based publish (see SqlResultPublisher); space O(1).

@Service
public class DefaultTermService implements TermService {

    private final JdbcTemplate jdbc;
    private final TermLifecycle lifecycle;
    private final ResultPublisher publisher;
    private final AuditService audit;

    private final RowMapper<TermView> mapper;

    public DefaultTermService(JdbcTemplate jdbc, TermLifecycle lifecycle, ResultPublisher publisher, AuditService audit) {
        this.jdbc = jdbc;
        this.lifecycle = lifecycle;
        this.publisher = publisher;
        this.audit = audit;
        this.mapper = (rs, n) -> {
            TermState state = TermState.valueOf(rs.getString("state"));
            return new TermView(rs.getLong("id"), rs.getString("name"), state.name(), Rs.date(rs, "starts_on"),
                    Rs.date(rs, "ends_on"), rs.getLong("version"),
                    lifecycle.successor(state).map(Enum::name).orElse(null));
        };
    }

    @Override
    public List<TermView> list() {
        return jdbc.query("SELECT id, name, state, starts_on, ends_on, version FROM term ORDER BY id DESC LIMIT 100", mapper);
    }

    @Override
    @Transactional
    public TermView create(NewTerm request) {
        if (!request.endsOn().isAfter(request.startsOn())) {
            throw new UmsException(ErrorCode.VALIDATION, "The end date must be after the start date.");
        }
        Long id = jdbc.queryForObject("INSERT INTO term (name, state, starts_on, ends_on) VALUES (?, 'REGISTRATION_OPEN', ?, ?) RETURNING id",
                Long.class, request.name().trim(), request.startsOn(), request.endsOn());
        audit.record("TERM_CREATE", "term", id, request.name());
        return get(id);
    }

    @Override
    @Transactional
    public TermView advance(long termId) {
        // Lock order starts with the term row.
        List<TermView> locked = jdbc.query("SELECT id, name, state, starts_on, ends_on, version FROM term WHERE id = ? FOR UPDATE", mapper, termId);
        if (locked.isEmpty()) {
            throw new UmsException(ErrorCode.NOT_FOUND);
        }
        TermView term = locked.get(0);
        TermState from = TermState.valueOf(term.state());
        TermState to = lifecycle.requireSuccessor(from);
        lifecycle.requireMove(from, to);
        if (to == TermState.RESULTS_PUBLISHED) {
            requireAllSheetsApproved(termId);
        }
        int rows = jdbc.update("UPDATE term SET state = ?, version = version + 1 WHERE id = ? AND state = ? AND version = ?",
                to.name(), termId, from.name(), term.version());
        if (rows == 0) {
            throw new UmsException(ErrorCode.STALE_DATA);
        }
        int written = to == TermState.RESULTS_PUBLISHED ? publisher.publish(termId) : 0;
        audit.record("TERM_ADVANCE", "term", termId, from + " -> " + to + (written > 0 ? " (" + written + " results published)" : ""));
        return get(termId);
    }

    private void requireAllSheetsApproved(long termId) {
        Integer pending = jdbc.queryForObject("""
                SELECT count(*) FROM mark_sheet ms JOIN section s ON s.id = ms.section_id
                 WHERE s.term_id = ? AND ms.state <> 'APPROVED'
                   AND EXISTS (SELECT 1 FROM enrolment e WHERE e.section_id = s.id AND e.state = 'ENROLLED')
                """, Integer.class, termId);
        if (pending != null && pending > 0) {
            throw new UmsException(ErrorCode.SHEETS_PENDING, pending + " mark sheet(s) are not approved yet.");
        }
    }

    private TermView get(long id) {
        return jdbc.queryForObject("SELECT id, name, state, starts_on, ends_on, version FROM term WHERE id = ?", mapper, id);
    }
}
