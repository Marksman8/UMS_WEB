package edu.ums.web.dev;

import java.util.List;

/** Runs one read-only query on behalf of the Dev role. */
public interface ConsoleService {

    QueryResult run(String sql);

    record Column(String name, String type) {
    }

    record QueryResult(List<Column> columns, List<List<Object>> rows, int rowCount, boolean truncated, long millis) {
    }
}
