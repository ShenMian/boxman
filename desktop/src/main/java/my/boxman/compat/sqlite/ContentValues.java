package my.boxman.compat.sqlite;

import java.util.LinkedHashMap;

public class ContentValues extends LinkedHashMap<String, Object> {
    public void put(String key, String value) {
        super.put(key, value);
    }

    public void put(String key, Byte value) {
        super.put(key, value);
    }

    public void put(String key, Short value) {
        super.put(key, value);
    }

    public void put(String key, Integer value) {
        super.put(key, value);
    }

    public void put(String key, Long value) {
        super.put(key, value);
    }

    public void put(String key, Float value) {
        super.put(key, value);
    }

    public void put(String key, Double value) {
        super.put(key, value);
    }

    public void put(String key, Boolean value) {
        super.put(key, value);
    }

    public void put(String key, byte[] value) {
        super.put(key, value);
    }

    public Object get(String key) {
        return super.get(key);
    }

    public String getAsString(String key) {
        Object val = super.get(key);
        return val == null ? null : val.toString();
    }

    public Integer getAsInteger(String key) {
        Object val = super.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return null;
    }

    public Long getAsLong(String key) {
        Object val = super.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }
}
