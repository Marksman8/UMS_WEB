package edu.ums.web.dev;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(R*C) per query, R <= 200 rows (setMaxRows) and C columns, plus whatever the database does within the
// 5 s statement timeout; space O(R*C) — the row cap is enforced by the JDBC driver, never by loading everything.

/**
 * Executes guarded SQL inside a READ ONLY transaction with a statement timeout. The guard is the first line of
 * defence, the read-only transaction the second: even a guard bug cannot write.
 */
@Service
public class JdbcConsoleService implements ConsoleService {

    private final JdbcTemplate jdbc;
    private final ConsoleGuard guard;
    private final AuditService audit;
    private final int rowLimit;
    private final int timeoutMs;

    public JdbcConsoleService(JdbcTemplate jdbc, ConsoleGuard guard, AuditService audit,
                              @Value("${ums.console.row-limit}") int rowLimit,
                              @Value("${ums.console.timeout-ms}") int timeoutMs) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.audit = audit;
        this.rowLimit = rowLimit;
        this.timeoutMs = timeoutMs;
    }

    @Override
    @Transactional(readOnly = true)
    public QueryResult run(String sql) {
        String safe = guard.check(sql);
        long started = System.nanoTime();
        try {
            jdbc.execute("SET LOCAL statement_timeout = " + timeoutMs);
            PreparedStatementCreator creator = con -> {
                PreparedStatement ps = con.prepareStatement(safe);
                ps.setMaxRows(rowLimit + 1);
                return ps;
            };
            ResultSetExtractor<QueryResult> extractor = rs -> extract(rs, started);
            QueryResult result = jdbc.query(creator, extractor);
            audit.record("SQL_RUN", "console", null, abbreviate(safe));
            return result;
        } catch (DataAccessException e) {
            // Boundary for user-written SQL: show the database's own message (it quotes only what the user typed).
            String message = friendly(e.getMostSpecificCause().getMessage());
            audit.recordAs("console", "DEV", "SQL_ERROR", "console", null, abbreviate(safe), false);
            throw new UmsException(ErrorCode.SQL_FAILED, message, e);
        }
    }

    private QueryResult extract(ResultSet rs, long started) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        List<Column> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(new Column(md.getColumnLabel(i), md.getColumnTypeName(i)));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= rowLimit) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(cell(rs, i));
            }
            rows.add(row);
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        return new QueryResult(List.copyOf(columns), List.copyOf(rows), rows.size(), truncated, millis);
    }

    private static Object cell(ResultSet rs, int index) throws SQLException {
        Object value = rs.getObject(index);
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof java.sql.Array) {
            return rs.getString(index);
        }
        if (value instanceof byte[]) {
            return "<binary>";
        }
        return value.toString();
    }

    private static String friendly(String raw) {
        if (raw == null) {
            return "The query failed.";
        }
        if (raw.contains("statement timeout") || raw.contains("canceling statement")) {
            return "The query took too long and was stopped (5 second limit).";
        }
        String firstLine = raw.lines().findFirst().orElse(raw).replaceFirst("^ERROR:\\s*", "");
        return firstLine.length() > 300 ? firstLine.substring(0, 300) : firstLine;
    }

    private static String abbreviate(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 280 ? oneLine.substring(0, 280) : oneLine;
    }
}
