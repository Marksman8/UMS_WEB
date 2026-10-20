package edu.ums.web.audit;

import java.time.Instant;
import java.util.List;

/** Read side of the audit trail. */
public interface ActivityService {

    /** Entries with id greater than {@code afterId}, oldest first (live feed polling). */
    List<ActivityRow> since(long afterId, int limit);

    /** Newest entries first, optionally starting below {@code beforeId} (keyset paging). */
    List<ActivityRow> latest(Long beforeId, int limit);

    record ActivityRow(long id, Instant at, String actor, String role, String action, String entity,
                       String entityId, String detail, boolean ok) {
    }
}
