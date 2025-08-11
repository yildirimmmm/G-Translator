package extension;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SettingsManager {

    private final File file;

    public SettingsManager(File baseDir) {
        File cacheDir = new File(baseDir, "cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        this.file = new File(cacheDir, "cache.json");
    }

    public Map<String, Object> load() {
        Map<String, Object> map = new HashMap<>();
        if (!file.exists()) return map;
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
            String text = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(text);
            for (String key : json.keySet()) {
                map.put(key, json.get(key));
            }
        } catch (Exception ignore) { }
        return map;
    }

    public void save(Map<String, Object> map) {
        JSONObject json = new JSONObject(map);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) { }
    }
}
