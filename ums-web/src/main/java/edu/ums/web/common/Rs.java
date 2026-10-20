package edu.ums.web.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

/** Pure helpers for reading JDBC result sets and for building safe LIKE patterns. */
public final class Rs {

    private Rs() {
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    /** Escapes %, _ and \ so user text matches literally inside ILIKE. */
    public static String likeContains(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static int clampLimit(Integer requested, int fallback, int max) {
        if (requested == null || requested < 1) {
            return fallback;
        }
        return Math.min(requested, max);
    }

    /** 1 = Mon ... 7 = Sun. */
    public static String dayName(int day) {
        return switch (day) {
            case 1 -> "Mon";
            case 2 -> "Tue";
            case 3 -> "Wed";
            case 4 -> "Thu";
            case 5 -> "Fri";
            case 6 -> "Sat";
            case 7 -> "Sun";
            default -> "?";
        };
    }

    public static String hhmm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
