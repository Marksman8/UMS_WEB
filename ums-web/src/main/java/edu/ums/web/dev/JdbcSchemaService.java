package edu.ums.web.dev;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Complexity: time O(T + C) per describe — T tables (<= ~30) counted by ONE UNION ALL query, each count capped at 100001 rows,
// C = columns read in one query; space O(T + C).

@Service
public class JdbcSchemaService implements SchemaService {

    private static final Logger log = LoggerFactory.getLogger(JdbcSchemaService.class);
    private static final int COUNT_CAP = 100_000;
    /** Hidden from the console on purpose: the raw account table and Flyway's bookkeeping. */
    private static final Set<String> HIDDEN = Set.of("app_user", "flyway_schema_history");

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public JdbcSchemaService(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public List<TableInfo> describe() {
        Map<String, String[]> relations = new LinkedHashMap<>(); // name -> {kind, comment}
        jdbc.query("""
                SELECT c.relname, CASE c.relkind WHEN 'v' THEN 'view' ELSE 'table' END, obj_description(c.oid, 'pg_class')
                  FROM pg_class c
                 WHERE c.relnamespace = to_regnamespace(current_schema()) AND c.relkind IN ('r', 'v')
                 ORDER BY c.relname
                """, rs -> {
            if (!HIDDEN.contains(rs.getString(1))) {
                relations.put(rs.getString(1), new String[] {rs.getString(2), rs.getString(3)});
            }
        });

        Set<String> primaryKeys = new HashSet<>();
        jdbc.query("""
                SELECT tc.table_name, kcu.column_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.key_column_usage kcu
                    ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                 WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = current_schema()
                """, rs -> {
            primaryKeys.add(rs.getString(1) + "." + rs.getString(2));
        });

        Map<String, List<ColumnInfo>> columns = new HashMap<>();
        jdbc.query("""
                SELECT table_name, column_name, data_type, is_nullable
                  FROM information_schema.columns
                 WHERE table_schema = current_schema() AND column_name <> 'password_hash'
                 ORDER BY table_name, ordinal_position
                """, rs -> {
            String table = rs.getString(1);
            columns.computeIfAbsent(table, k -> new ArrayList<>()).add(new ColumnInfo(rs.getString(2), rs.getString(3),
                    "YES".equals(rs.getString(4)), primaryKeys.contains(table + "." + rs.getString(2))));
        });

        Map<String, Long> counts = counts(relations.keySet());
        List<TableInfo> result = new ArrayList<>();
        relations.forEach((name, meta) -> {
            long n = counts.getOrDefault(name, 0L);
            result.add(new TableInfo(name, meta[0], meta[1], Math.min(n, COUNT_CAP), n > COUNT_CAP,
                    List.copyOf(columns.getOrDefault(name, List.of()))));
        });
        return List.copyOf(result);
    }

    /** One round trip: SELECT 'a' AS t, (capped count) UNION ALL ... Names come from the catalog and are quoted. */
    private Map<String, Long> counts(Set<String> names) {
        Map<String, Long> out = new HashMap<>();
        if (names.isEmpty()) {
            return out;
        }
        List<String> parts = new ArrayList<>();
        for (String name : names) {
            String quoted = "\"" + name.replace("\"", "\"\"") + "\"";
            parts.add("SELECT '" + name.replace("'", "''") + "' AS t, (SELECT count(*) FROM (SELECT 1 FROM " + quoted
                    + " LIMIT " + (COUNT_CAP + 1) + ") x) AS n");
        }
        jdbc.query(String.join(" UNION ALL ", parts), rs -> {
            out.put(rs.getString(1), rs.getLong(2));
        });
        return out;
    }

    @Override
    public DbStats stats() {
        String version = jdbc.queryForObject("SELECT version()", String.class);
        Long size = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
        Integer connections = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()", Integer.class);
        Integer max = jdbc.queryForObject("SELECT setting::int FROM pg_settings WHERE name = 'max_connections'", Integer.class);
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        return new DbStats(version, size == null ? 0 : size, connections == null ? 0 : connections, max == null ? 0 : max, schema);
    }

    @Override
    public Health health() {
        boolean up = true;
        long latency = -1;
        String migration = "unknown";
        long t0 = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            latency = (System.nanoTime() - t0) / 1_000_000;
            List<String> v = jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class);
            migration = v.isEmpty() ? "none" : v.get(0);
        } catch (DataAccessException e) {
            up = false;
            log.warn("Health check could not reach the database: {}", e.getMostSpecificCause().getMessage());
        }
        int active = 0;
        int idle = 0;
        int total = 0;
        int waiting = 0;
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                active = pool.getActiveConnections();
                idle = pool.getIdleConnections();
                total = pool.getTotalConnections();
                waiting = pool.getThreadsAwaitingConnection();
            }
        }
        Runtime rt = Runtime.getRuntime();
        int usedMb = (int) ((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        int maxMb = (int) (rt.maxMemory() / (1024 * 1024));
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new Health(up, latency, migration, uptime, active, idle, total, waiting,
                System.getProperty("java.version"), usedMb, maxMb);
    }
}
