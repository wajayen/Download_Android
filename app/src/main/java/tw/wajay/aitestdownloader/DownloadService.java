package tw.wajay.aitestdownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

public final class DownloadService extends Service {
    static final String ACTION_START = "tw.wajay.aitestdownloader.START";
    static final String ACTION_CANCEL = "tw.wajay.aitestdownloader.CANCEL";
    static final String ACTION_WAKE = "tw.wajay.aitestdownloader.WAKE";
    static final String EXTRA_URL = "url";
    static final String EXTRA_FILE_NAME = "file_name";

    private static final String CHANNEL_ID = "downloads";
    private static final int NOTIFICATION_ID = 42;
    private static final int MAX_CONCURRENT_DOWNLOADS = 2;

    private NotificationManager notificationManager;
    private TaskStore taskStore;
    private EventLog eventLog;
    private final Map<String, DownloadEngine> activeEngines = new LinkedHashMap<>();

    static Intent startIntent(Context context, String url, String fileName) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        return intent;
    }

    static Intent cancelIntent(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_CANCEL);
        return intent;
    }

    static Intent wakeIntent(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_WAKE);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        taskStore = new TaskStore(this);
        eventLog = new EventLog(this);
        int recovered = taskStore.recoverInterruptedTasks();
        if (recovered > 0) {
            eventLog.write("recovered", "", "running tasks requeued=" + recovered);
        }
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            synchronized (this) {
                for (DownloadEngine engine : activeEngines.values()) {
                    engine.cancel();
                }
                activeEngines.clear();
            }
            taskStore.cancelRunningOrQueued();
            eventLog.write("cancel", "", "cancel requested");
            updateNotification("Cancelled", 0, 0, false);
            stopForeground(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            if (url == null || url.trim().isEmpty()) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = FileNames.choose(Uri.parse(url), "");
            }
            JSONObject task = taskStore.enqueue(url, fileName);
            eventLog.write("queued", task.optString("id"), url);
            startForeground(NOTIFICATION_ID, buildNotification("Download queued", 0, 0, true));
            startNextAvailable();
        }
        if (ACTION_WAKE.equals(action)) {
            startForeground(NOTIFICATION_ID, buildNotification("Queue starting", 0, 0, true));
            startNextAvailable();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "?鞊???鞊??",
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    private void updateNotification(String text, long downloaded, long total, boolean ongoing) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, downloaded, total, ongoing));
    }

    private Notification buildNotification(String text, long downloaded, long total, boolean ongoing) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        PendingIntent cancelIntent = PendingIntent.getService(
                this,
                2,
                cancelIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloader")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(ongoing)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent);

        if (total > 0L) {
            int progress = (int) Math.max(0L, Math.min(100L, downloaded * 100L / total));
            builder.setProgress(100, progress, false)
                    .setContentText(text + " " + progress + "%");
        } else if (ongoing) {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private String formatProgress(long downloaded, long total) {
        if (total > 0L) {
            return String.format(Locale.US, "%.1f%%", downloaded * 100.0 / total);
        }
        return downloaded + " bytes";
    }

    private synchronized void startNextAvailable() {
        while (activeEngines.size() < MAX_CONCURRENT_DOWNLOADS) {
            JSONObject task = taskStore.nextRunnable();
            if (task == null) {
                break;
            }

            String taskId = task.optString("id");
            String url = task.optString("url");
            String fileName = task.optString("fileName");
            DownloadEngine engine = new DownloadEngine(this);
            activeEngines.put(taskId, engine);
            taskStore.markRunning(taskId);
            eventLog.write("started", taskId, url);
            eventLog.write("concurrency", taskId, "active=" + activeEngines.size() + "/" + MAX_CONCURRENT_DOWNLOADS);
            updateNotification("Downloading " + activeEngines.size() + "/" + MAX_CONCURRENT_DOWNLOADS, 0L, 0L, true);
            engine.start(url, fileName, new ServiceCallback(taskId));
        }

        if (activeEngines.isEmpty() && taskStore.nextRunnable() == null) {
            updateNotification("Queue finished", 100L, 100L, false);
            stopForeground(false);
            stopSelf();
        }
    }

    private synchronized void finishTask(String taskId) {
        activeEngines.remove(taskId);
        eventLog.write("concurrency", taskId, "active=" + activeEngines.size() + "/" + MAX_CONCURRENT_DOWNLOADS);
        startNextAvailable();
    }

    private final class ServiceCallback implements DownloadEngine.Callback {
        private final String taskId;

        ServiceCallback(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public void onStatus(String text) {
            taskStore.status(taskId, text);
            eventLog.write("status", taskId, text);
            updateNotification(text, 0L, 0L, true);
        }

        @Override
        public void onResolved(String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels) {
            int candidateCount = candidates == null ? 0 : candidates.size();
            taskStore.resolved(taskId, sourceSite, targetUrl, candidates, candidateLabels);
            eventLog.write("resolved", taskId, sourceSite + " candidates=" + candidateCount + " primary=" + targetUrl + " all=" + joinCandidates(candidates));
        }

        @Override
        public void onProgress(long downloaded, long total) {
            taskStore.progress(taskId, downloaded, total);
            updateNotification("Downloading " + formatProgress(downloaded, total), downloaded, total, true);
        }

        @Override
        public void onDone(File output) {
            String exportedLocation = output.getAbsolutePath();
            try {
                exportedLocation = OutputExporter.exportToPublicDownloads(DownloadService.this, output);
                eventLog.write("exported", taskId, exportedLocation);
            } catch (Exception exportError) {
                eventLog.write("export_failed", taskId, exportError.getMessage() == null ? exportError.toString() : exportError.getMessage());
            }
            taskStore.done(taskId, exportedLocation);
            eventLog.write("done", taskId, exportedLocation);
            updateNotification("Download complete", 100L, 100L, false);
            finishTask(taskId);
        }

        @Override
        public void onError(Exception error) {
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            taskStore.failed(taskId, message);
            eventLog.write("error", taskId, message);
            updateNotification("Download failed", 0L, 0L, false);
            finishTask(taskId);
        }

        private String joinCandidates(List<String> candidates) {
            if (candidates == null || candidates.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            int limit = Math.min(8, candidates.size());
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    builder.append(" | ");
                }
                builder.append(candidates.get(i));
            }
            if (candidates.size() > limit) {
                builder.append(" | ...");
            }
            return builder.toString();
        }
    }
}
