package android.content;
import java.util.HashMap;
import java.util.Map;
public class SharedPreferences {
    private final Map<String, String> data = new HashMap<>();
    public Map<String, String> getAll() { return new HashMap<>(data); }
    public String getString(String key, String fallback) { return data.getOrDefault(key, fallback); }
    public Editor edit() { return new Editor(); }
    public class Editor {
        private final Map<String, String> pending = new HashMap<>();
        public Editor putString(String key, String value) { pending.put(key, value); return this; }
        public boolean commit() { data.putAll(pending); return true; }
    }
}
