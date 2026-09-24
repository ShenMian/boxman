package my.boxman.compat.sqlite;

import java.sql.*;
import java.util.*;

public class Cursor {
    private final List<Object[]> rows = new ArrayList<>();
    private final Map<String, Integer> colMap = new HashMap<>();
    private int colCount;
    private int currentIndex = -1;

    public static Cursor fromResultSet(ResultSet rs) throws SQLException {
        Cursor c = new Cursor();
        ResultSetMetaData meta = rs.getMetaData();
        c.colCount = meta.getColumnCount();
        for (int i = 1; i <= c.colCount; i++) {
            // 支持精确列名及不区分大小写
            String colName = meta.getColumnLabel(i);
            if (colName == null || colName.isEmpty()) {
                colName = meta.getColumnName(i);
            }
            c.colMap.put(colName, i - 1);
            c.colMap.put(colName.toLowerCase(Locale.ROOT), i - 1);
        }
        while (rs.next()) {
            Object[] row = new Object[c.colCount];
            for (int i = 0; i < c.colCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            c.rows.add(row);
        }
        rs.close();
        return c;
    }

    public int getCount() {
        return rows.size();
    }

    public boolean moveToFirst() {
        currentIndex = 0;
        return currentIndex < rows.size();
    }

    public boolean moveToNext() {
        currentIndex++;
        return currentIndex < rows.size();
    }

    public boolean moveToLast() {
        currentIndex = rows.size() - 1;
        return currentIndex >= 0;
    }

    public int getInt(int col) {
        Object val = getVal(col);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public long getLong(int col) {
        Object val = getVal(col);
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public String getString(int col) {
        Object val = getVal(col);
        return val == null ? null : val.toString();
    }

    public byte[] getBlob(int col) {
        Object val = getVal(col);
        if (val instanceof byte[]) return (byte[]) val;
        return null;
    }

    public int getColumnIndex(String name) {
        Integer i = colMap.get(name);
        if (i == null) {
            i = colMap.get(name.toLowerCase(Locale.ROOT));
        }
        return i != null ? i : -1;
    }

    private Object getVal(int col) {
        if (currentIndex < 0 || currentIndex >= rows.size() || col < 0 || col >= colCount) {
            return null;
        }
        return rows.get(currentIndex)[col];
    }

    public void close() {
        rows.clear();
        colMap.clear();
        currentIndex = -1;
    }
}
