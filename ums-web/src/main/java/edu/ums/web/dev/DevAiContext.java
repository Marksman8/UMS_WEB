package edu.ums.web.dev;

import edu.ums.web.ai.AiContextContributor;
import edu.ums.web.audit.ActivityService;
import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Complexity: time O(T + C) per call (one catalog read + one capped UNION ALL count); space O(T + C).

/** What the assistant may tell the developer: schema, database statistics and recent activity. */
@Component
public class DevAiContext implements AiContextContributor {

    private final SchemaService schema;
    private final ActivityService activity;

    public DevAiContext(SchemaService schema, ActivityService activity) {
        this.schema = schema;
        this.activity = activity;
    }

    @Override
    public Role role() {
        return Role.DEV;
    }

    @Override
    public List<Fact> facts(UmsPrincipal who) {
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("schema", List.of("table", "column", "schema", "structure", "model", "sql", "query"), schemaText()));

        SchemaService.DbStats st = schema.stats();
        facts.add(new Fact("database", List.of("size", "version", "connection", "rows", "count", "postgres", "database"),
                st.version() + ". Size " + (st.sizeBytes() / 1024) + " KB, " + st.connections() + " of " + st.maxConnections()
                        + " connections, schema " + st.schema() + "."));

        StringBuilder log = new StringBuilder();
        for (ActivityService.ActivityRow r : activity.latest(null, 10)) {
            log.append(log.length() > 0 ? "\n" : "").append(r.at()).append(" ").append(r.actor()).append(" ").append(r.action())
                    .append(r.ok() ? "" : " (FAILED)").append(r.detail() == null ? "" : " - " + r.detail());
        }
        facts.add(new Fact("recent activity", List.of("activity", "log", "login", "failed", "denied", "error", "recent", "audit"),
                log.length() == 0 ? "No activity yet." : log.toString()));
        return List.copyOf(facts);
    }

    /** Compact one-line-per-table description, also reused as the schema prompt for natural-language SQL. */
    String schemaText() {
        StringBuilder sb = new StringBuilder();
        for (SchemaService.TableInfo t : schema.describe()) {
            sb.append(t.name()).append("(");
            sb.append(String.join(", ", t.columns().stream().map(c -> c.name() + " " + c.type()).toList()));
            sb.append(") ~").append(t.rows()).append(" rows\n");
        }
        return sb.toString();
    }
}
