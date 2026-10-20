package edu.ums.web.audit;

import edu.ums.web.common.Rs;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

// Complexity: time O(L) per call, L = page size (index scan on the primary key); space O(L) — limit capped at 200.

@Service
public class JdbcActivityService implements ActivityService {

    private static final String COLUMNS = "SELECT id, at, actor, role, action, entity, entity_id, detail, ok FROM activity_log ";

    private static final RowMapper<ActivityRow> MAPPER = (rs, n) -> new ActivityRow(
            rs.getLong("id"), Rs.instant(rs, "at"), rs.getString("actor"), rs.getString("role"),
            rs.getString("action"), rs.getString("entity"), rs.getString("entity_id"),
            rs.getString("detail"), rs.getBoolean("ok"));

    private final JdbcTemplate jdbc;

    public JdbcActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ActivityRow> since(long afterId, int limit) {
        return jdbc.query(COLUMNS + "WHERE id > ? ORDER BY id LIMIT ?", MAPPER, afterId, Rs.clampLimit(limit, 100, 200));
    }

    @Override
    public List<ActivityRow> latest(Long beforeId, int limit) {
        int l = Rs.clampLimit(limit, 50, 200);
        if (beforeId == null) {
            return jdbc.query(COLUMNS + "ORDER BY id DESC LIMIT ?", MAPPER, l);
        }
        return jdbc.query(COLUMNS + "WHERE id < ? ORDER BY id DESC LIMIT ?", MAPPER, beforeId, l);
    }
}
