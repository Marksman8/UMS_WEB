package edu.ums.web.dev;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

// Complexity: time O(n) per check, n <= 5000 characters (one pass lexer, no regex backtracking); space O(t) — tokens of one query.

/**
 * Decides whether a piece of SQL may run in the Dev console. Rules: one statement only; it must start with
 * SELECT / WITH / VALUES / TABLE / SHOW / EXPLAIN; no writes, DDL, locking, file or sleep functions; and the
 * password hash can never be reached (use the app_user_public view). Comments and string contents are removed
 * before the keyword check, and the constructs that could hide text from this lexer (dollar quoting, E'' strings,
 * U&'' escapes, XML query helpers) are rejected outright. The database enforces the rest: the console runs in a
 * READ ONLY transaction with a statement timeout and a row cap.
 */
@Component
public class ConsoleGuard {

    static final int MAX_LENGTH = 5000;

    private static final Set<String> FIRST_WORDS = Set.of("select", "with", "values", "table", "show", "explain");

    private static final Set<String> BLOCKED = Set.of(
            "insert", "update", "delete", "merge", "drop", "alter", "create", "truncate", "grant", "revoke", "copy",
            "call", "do", "execute", "prepare", "deallocate", "vacuum", "analyze", "analyse", "set", "reset", "lock",
            "listen", "notify", "unlisten", "refresh", "reindex", "cluster", "comment", "security", "into", "begin",
            "commit", "rollback", "savepoint", "declare", "discard", "load", "checkpoint", "import", "abort", "start",
            "pg_sleep", "pg_sleep_for", "pg_sleep_until", "pg_read_file", "pg_read_binary_file", "pg_ls_dir",
            "pg_stat_file", "pg_ls_logdir", "pg_ls_waldir", "lo_import", "lo_export", "lo_get", "lo_put", "lo_create",
            "lo_unlink", "dblink", "dblink_exec", "dblink_connect", "pg_terminate_backend", "pg_cancel_backend",
            "set_config", "nextval", "setval", "pg_advisory_lock", "pg_advisory_xact_lock", "pg_reload_conf",
            "pg_rotate_logfile", "pg_authid", "pg_shadow", "pg_user_mappings", "pg_largeobject",
            "query_to_xml", "query_to_xml_and_xmlschema", "query_to_xmlschema", "table_to_xml",
            "table_to_xml_and_xmlschema", "table_to_xmlschema", "cursor_to_xml", "schema_to_xml",
            "schema_to_xml_and_xmlschema", "database_to_xml", "database_to_xml_and_xmlschema",
            "app_user", "password_hash");

    /** Returns the query ready to run (trailing semicolon removed) or throws SQL_REJECTED with a reason. */
    public String check(String sql) {
        if (sql == null || sql.isBlank()) {
            throw reject("Type a query first.");
        }
        if (sql.length() > MAX_LENGTH) {
            throw reject("The query is too long (limit " + MAX_LENGTH + " characters).");
        }
        List<String> tokens = tokenize(sql);
        if (tokens.isEmpty()) {
            throw reject("Type a query first.");
        }
        if (!FIRST_WORDS.contains(tokens.get(0))) {
            throw reject("Only SELECT, WITH, VALUES, TABLE, SHOW and EXPLAIN queries are allowed.");
        }
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("app_user") || t.equals("password_hash")) {
                throw reject("Password hashes can never be read. Use the app_user_public view instead.");
            }
            if (BLOCKED.contains(t)) {
                throw reject("'" + t + "' is not allowed in the read-only console.");
            }
            if (t.equals("for") && i + 1 < tokens.size() && Set.of("share", "no", "key").contains(tokens.get(i + 1))) {
                throw reject("Row-locking clauses are not allowed.");
            }
        }
        return stripTrailingSemicolon(sql);
    }

    /** Splits the SQL into lower-case words, dropping comments and string contents, and rejects hiding tricks. */
    private List<String> tokenize(String sql) {
        List<String> tokens = new ArrayList<>();
        boolean seenSemicolon = false;
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '-' && next == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i = skipBlockComment(sql, i);
            } else if (c == ';') {
                seenSemicolon = true;
                i++;
            } else {
                if (seenSemicolon) {
                    throw reject("Only one statement is allowed.");
                }
                if (c == '$') {
                    throw reject("Dollar quoting and positional parameters are not allowed.");
                }
                if (c == '\'' || c == '"') {
                    requireNotEscapePrefixed(sql, i);
                    int end = findClosingQuote(sql, i, c);
                    if (c == '"') {
                        tokens.add(sql.substring(i + 1, end).replace("\"\"", "\"").toLowerCase());
                    }
                    i = end + 1;
                } else if (isWordStart(c)) {
                    int start = i;
                    while (i < n && isWordPart(sql.charAt(i))) {
                        i++;
                    }
                    tokens.add(sql.substring(start, i).toLowerCase());
                } else {
                    i++;
                }
            }
        }
        return tokens;
    }

    private static int skipBlockComment(String sql, int from) {
        int depth = 0;
        int i = from;
        int n = sql.length();
        while (i < n - 1) {
            if (sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                depth++;
                i += 2;
            } else if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        throw reject("A /* comment is never closed.");
    }

    /** Returns the index of the closing quote; doubled quotes inside are escapes. */
    private static int findClosingQuote(String sql, int open, char quote) {
        int i = open + 1;
        int n = sql.length();
        while (i < n) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < n && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i;
            }
            i++;
        }
        throw reject("A quote is never closed.");
    }

    /** E'..' and U&'..' strings use backslash escapes that this lexer does not model, so they are refused. */
    private static void requireNotEscapePrefixed(String sql, int quoteIndex) {
        if (quoteIndex == 0) {
            return;
        }
        char prev = sql.charAt(quoteIndex - 1);
        if (prev == '&') {
            throw reject("Unicode escape strings are not allowed.");
        }
        if ((prev == 'e' || prev == 'E') && (quoteIndex < 2 || !isWordPart(sql.charAt(quoteIndex - 2)))) {
            throw reject("Escape strings (E'...') are not allowed; use normal quotes.");
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        String s = sql.stripTrailing();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).stripTrailing();
        }
        return s;
    }

    private static boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static UmsException reject(String reason) {
        return new UmsException(ErrorCode.SQL_REJECTED, reason);
    }
}
