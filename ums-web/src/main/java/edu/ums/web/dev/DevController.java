package edu.ums.web.dev;

import edu.ums.web.audit.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev area: SQL console, schema browser, live activity, database statistics and health. */
@RestController
@RequestMapping("/api/dev")
@PreAuthorize("hasRole('DEV')")
public class DevController {

    private final ConsoleService console;
    private final SchemaService schema;
    private final ActivityService activity;
    private final NlToSqlService nlToSql;

    public DevController(ConsoleService console, SchemaService schema, ActivityService activity, NlToSqlService nlToSql) {
        this.console = console;
        this.schema = schema;
        this.activity = activity;
        this.nlToSql = nlToSql;
    }

    @PostMapping("/query")
    public ConsoleService.QueryResult query(@Valid @RequestBody QueryRequest body) {
        return console.run(body.sql());
    }

    @PostMapping("/ask")
    public NlToSqlService.Suggestion ask(@Valid @RequestBody NlToSqlService.AskRequest body) {
        return nlToSql.suggest(body.question());
    }

    @GetMapping("/schema")
    public List<SchemaService.TableInfo> schema() {
        return schema.describe();
    }

    @GetMapping("/stats")
    public SchemaService.DbStats stats() {
        return schema.stats();
    }

    @GetMapping("/health")
    public SchemaService.Health health() {
        return schema.health();
    }

    /** Newest first; used once to fill the live feed. */
    @GetMapping("/activity/recent")
    public List<ActivityService.ActivityRow> recent(@RequestParam(defaultValue = "50") int limit) {
        return activity.latest(null, limit);
    }

    /** Everything after {@code after}, oldest first; the live feed polls this every second or two. */
    @GetMapping("/activity")
    public List<ActivityService.ActivityRow> since(@RequestParam long after, @RequestParam(defaultValue = "100") int limit) {
        return activity.since(after, limit);
    }

    public record QueryRequest(@NotBlank @Size(max = ConsoleGuard.MAX_LENGTH) String sql) {
    }
}
