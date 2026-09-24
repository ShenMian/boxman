package my.boxman.compat.sqlite;

import java.io.File;
import java.sql.*;
import java.util.*;

public class SQLiteDatabase {
    public static final int OPEN_READONLY = 1;
    public static final int OPEN_READWRITE = 0;

    private Connection conn;
    private final String dbPath;

    public SQLiteDatabase(String path, Connection connection) {
        this.dbPath = path;
        this.conn = connection;
    }

    public static SQLiteDatabase openDatabase(String path, Object factory, int flags) {
        File file = new File(path);
        if (!file.exists()) {
            throw new SQLiteException("Database file does not exist: " + path);
        }
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            return new SQLiteDatabase(path, c);
        } catch (SQLException e) {
            throw new SQLiteException(e);
        }
    }

    public static SQLiteDatabase openOrCreateDatabase(String path, Object factory) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            return new SQLiteDatabase(path, c);
        } catch (SQLException e) {
            throw new SQLiteException(e);
        }
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (selectionArgs != null) {
                for (int i = 0; i < selectionArgs.length; i++) {
                    ps.setString(i + 1, selectionArgs[i]);
                }
            }
            boolean hasResultSet = ps.execute();
            Cursor cursor;
            if (hasResultSet) {
                ResultSet rs = ps.getResultSet();
                cursor = Cursor.fromResultSet(rs);
            } else {
                cursor = new Cursor();
            }
            ps.close();
            return cursor;
        } catch (SQLException e) {
            throw new SQLiteException("Error executing rawQuery: " + sql, e);
        }
    }

    public Cursor rawQuery(String sql, String[] selectionArgs, Object cancelSignal) {
        return rawQuery(sql, selectionArgs);
    }

    public Cursor query(String table, String[] columns, String where,
                        String[] whereArgs, String groupBy, String having, String orderBy) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null || columns.length == 0) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns[i]);
            }
        }
        sql.append(" FROM ").append(table);
        if (where != null && !where.trim().isEmpty()) {
            sql.append(" WHERE ").append(where);
        }
        if (groupBy != null && !groupBy.trim().isEmpty()) {
            sql.append(" GROUP BY ").append(groupBy);
        }
        if (having != null && !having.trim().isEmpty()) {
            sql.append(" HAVING ").append(having);
        }
        if (orderBy != null && !orderBy.trim().isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        return rawQuery(sql.toString(), whereArgs);
    }

    public void execSQL(String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new SQLiteException("Error executing SQL: " + sql, e);
        }
    }

    public void execSQL(String sql, Object[] bindArgs) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (bindArgs != null) {
                for (int i = 0; i < bindArgs.length; i++) {
                    ps.setObject(i + 1, bindArgs[i]);
                }
            }
            ps.execute();
        } catch (SQLException e) {
            throw new SQLiteException("Error executing SQL with args: " + sql, e);
        }
    }

    public long insert(String table, String nullColumnHack, ContentValues values) {
        if (values == null || values.isEmpty()) {
            String sql = "INSERT INTO " + table + " DEFAULT VALUES";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) return keys.getLong(1);
                return -1;
            } catch (SQLException e) {
                throw new SQLiteException("Insert error: " + sql, e);
            }
        }

        StringBuilder cols = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        List<Object> vals = new ArrayList<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (cols.length() > 0) {
                cols.append(", ");
                placeholders.append(", ");
            }
            cols.append(entry.getKey());
            placeholders.append("?");
            vals.add(entry.getValue());
        }

        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < vals.size(); i++) {
                ps.setObject(i + 1, vals.get(i));
            }
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getLong(1);
            }
            return 1;
        } catch (SQLException e) {
            throw new SQLiteException("Insert error: " + sql, e);
        }
    }

    public int delete(String table, String whereClause, String[] whereArgs) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (whereArgs != null) {
                for (int i = 0; i < whereArgs.length; i++) {
                    ps.setString(i + 1, whereArgs[i]);
                }
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new SQLiteException("Delete error: " + sql, e);
        }
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new SQLiteException(e);
        }
    }

    public Connection getConnection() {
        return conn;
    }
}
