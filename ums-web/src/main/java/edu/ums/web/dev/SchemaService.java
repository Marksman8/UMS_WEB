package edu.ums.web.dev;

import java.util.List;

/** Read-only facts about the database for the Dev area: structure, statistics and health. */
public interface SchemaService {

    List<TableInfo> describe();

    DbStats stats();

    Health health();

    record ColumnInfo(String name, String type, boolean nullable, boolean primaryKey) {
    }

    record TableInfo(String name, String kind, String comment, long rows, boolean rowsCapped, List<ColumnInfo> columns) {
    }

    record DbStats(String version, long sizeBytes, int connections, int maxConnections, String schema) {
    }

    record Health(boolean databaseUp, long databaseLatencyMs, String migrationVersion, long uptimeSeconds,
                  int poolActive, int poolIdle, int poolTotal, int poolWaiting, String java, int heapUsedMb, int heapMaxMb) {
    }
}
