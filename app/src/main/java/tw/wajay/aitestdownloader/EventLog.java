package tw.wajay.aitestdownloader;

import android.content.Context;
import android.os.Environment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

final class EventLog {
    private final File logFile;

    EventLog(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        File logDir = new File(dir, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        logFile = new File(logDir, "activity.jsonl");
    }

    synchronized void write(String event, String taskId, String message) {
        JSONObject row = new JSONObject();
        try {
            row.put("time", System.currentTimeMillis());
            row.put("event", event);
            row.put("taskId", taskId == null ? "" : taskId);
            row.put("message", message == null ? "" : message);
        } catch (JSONException error) {
            return;
        }
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(row.toString());
            writer.write('\n');
        } catch (IOException ignored) {
            // Logging must never break downloads.
        }
    }

    String path() {
        return logFile.getAbsolutePath();
    }
}
