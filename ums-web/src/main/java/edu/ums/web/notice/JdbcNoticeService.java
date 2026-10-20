package edu.ums.web.notice;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.Rs;
import edu.ums.web.common.UmsException;
import edu.ums.web.user.Role;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Complexity: time O(L) per list via idx_notice_created, L <= 100; O(1) per create/delete; space O(L).

@Service
public class JdbcNoticeService implements NoticeService {

    private static final String SELECT = """
            SELECT n.id, n.title, n.body, n.audience, u.full_name AS author, n.created_at
              FROM notice n JOIN app_user u ON u.id = n.author_id
            """;

    private static final RowMapper<NoticeView> MAPPER = (rs, n) -> new NoticeView(rs.getLong("id"), rs.getString("title"),
            rs.getString("body"), rs.getString("audience"), rs.getString("author"), Rs.instant(rs, "created_at"));

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public JdbcNoticeService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public List<NoticeView> list(Role role, int limit) {
        int l = Rs.clampLimit(limit, 50, 100);
        boolean seeAll = role == Role.ADMIN || role == Role.DEV;
        return jdbc.query(SELECT + " WHERE ? OR n.audience = 'ALL' OR n.audience = ? ORDER BY n.created_at DESC, n.id DESC LIMIT ?",
                MAPPER, seeAll, role.name(), l);
    }

    @Override
    @Transactional
    public NoticeView create(long authorId, NewNotice r) {
        Long id = jdbc.queryForObject("INSERT INTO notice (title, body, audience, author_id) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, r.title().trim(), r.body().trim(), r.audience(), authorId);
        audit.record("NOTICE_CREATE", "notice", id, r.title());
        return jdbc.queryForObject(SELECT + " WHERE n.id = ?", MAPPER, id);
    }

    @Override
    @Transactional
    public void delete(long id) {
        if (jdbc.update("DELETE FROM notice WHERE id = ?", id) == 0) {
            throw new UmsException(ErrorCode.NOT_FOUND);
        }
        audit.record("NOTICE_DELETE", "notice", id, null);
    }
}
