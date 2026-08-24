package io.github.absketches.mitbauen.nativeapp.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;

public final class SqlUtil {

    private SqlUtil() {
    }

    public static String placeholders(final int count) {
        final StringJoiner placeholders = new StringJoiner(", ");
        for (int index = 0; index < count; index++) {
            placeholders.add("?");
        }
        return placeholders.toString();
    }

    public static void bindLongs(final PreparedStatement statement, final List<Long> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setLong(index + 1, values.get(index));
        }
    }
}
