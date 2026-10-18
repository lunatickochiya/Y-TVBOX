package com.orhanobut.hawk;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
/** In-memory storage boundary; JSONObject values are copied like serialized Hawk values. */
public final class Hawk {
    private static final Map<String, Object> data = new HashMap<>();
    @SuppressWarnings("unchecked") public static <T> T get(String key, T fallback) {
        Object value = data.get(key);
        if (value instanceof JSONObject) return (T) new JSONObject(value.toString());
        return value == null ? fallback : (T) value;
    }
    public static boolean put(String key, Object value) {
        data.put(key, value instanceof JSONObject ? new JSONObject(value.toString()) : value);
        return true;
    }
    public static boolean delete(String key) { data.remove(key); return true; }
    public static void clear() { data.clear(); }
}
