package edu.ums.web.audit;

import edu.ums.web.user.CurrentUser;
import edu.ums.web.user.UmsPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Complexity: time O(1) per entry (one INSERT); space O(1) — no buffering, events are handled inline after commit.

/**
 * Publishes an event and writes it to activity_log AFTER the business transaction commits, in its
 * own tiny transaction, so a failed audit write can never roll back real work. Outside a
 * transaction (e.g. login attempts) the event is handled immediately.
 */
@Service
public class DbAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(DbAuditService.class);

    private final ApplicationEventPublisher publisher;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public DbAuditService(ApplicationEventPublisher publisher, JdbcTemplate jdbc, CurrentUser currentUser) {
        this.publisher = publisher;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Override
    public void record(String action, String entity, Object entityId, String detail) {
        UmsPrincipal p = currentUser.find().orElse(null);
        String actor = p == null ? "anonymous" : p.username();
        String role = p == null ? null : p.role().name();
        publisher.publishEvent(new AuditEvent(actor, role, action, entity, entityId == null ? null : String.valueOf(entityId), detail, true));
    }

    @Override
    public void recordAs(String actor, String role, String action, String entity, Object entityId, String detail, boolean ok) {
        publisher.publishEvent(new AuditEvent(actor, role, action, entity, entityId == null ? null : String.valueOf(entityId), detail, ok));
    }

    @Override
    public void denied(String detail) {
        UmsPrincipal p = currentUser.find().orElse(null);
        publisher.publishEvent(new AuditEvent(p == null ? "anonymous" : p.username(), p == null ? null : p.role().name(),
                "DENIED", null, null, detail, false));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent e) {
        try {
            jdbc.update("INSERT INTO activity_log (actor, role, action, entity, entity_id, detail, ok) VALUES (?,?,?,?,?,?,?)",
                    cut(e.actor(), 40), cut(e.role(), 10), cut(e.action(), 40), cut(e.entity(), 40),
                    cut(e.entityId(), 40), cut(e.detail(), 300), e.ok());
        } catch (RuntimeException ex) {
            // Documented best-effort path: auditing must never break the request that triggered it.
            log.warn("Audit write failed for action {}: {}", e.action(), ex.getMessage());
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record AuditEvent(String actor, String role, String action, String entity, String entityId, String detail, boolean ok) {
    }
}
