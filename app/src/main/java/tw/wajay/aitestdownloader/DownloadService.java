package tw.wajay.aitestdownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
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
    static final String EXTRA_REFERER = "referer";
    static final String EXTRA_COOKIE_HEADER = "cookie_header";
    static final String EXTRA_HEADERS_JSON = "headers_json";
    static final String EXTRA_PLAY_AFTER_THRESHOLD = "play_after_threshold";

    private static final String CHANNEL_ID = "downloads";
    private static final int NOTIFICATION_ID = 42;
    private static final int MAX_CONCURRENT_DOWNLOADS = 2;
    private static final long PLAY_THRESHOLD_BYTES = 50L * 1024L * 1024L;

    private NotificationManager notificationManager;
    private TaskStore taskStore;
    private EventLog eventLog;
    private final Map<String, DownloadEngine> activeEngines = new LinkedHashMap<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageSettings.wrap(newBase));
    }

    static Intent startIntent(Context context, String url, String fileName) {
        return startIntent(context, url, fileName, "", "");
    }

    static Intent startIntent(Context context, String url, String fileName, String referer, String cookieHeader) {
        return startIntent(context, url, fileName, referer, cookieHeader, "{}");
    }

    static Intent startIntent(Context context, String url, String fileName, String referer, String cookieHeader, String headersJson) {
        return startIntent(context, url, fileName, referer, cookieHeader, headersJson, false);
    }

    static Intent startIntent(Context context, String url, String fileName, String referer, String cookieHeader, String headersJson, boolean playAfterThreshold) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_REFERER, referer == null ? "" : referer);
        intent.putExtra(EXTRA_COOKIE_HEADER, cookieHeader == null ? "" : cookieHeader);
        intent.putExtra(EXTRA_HEADERS_JSON, headersJson == null ? "{}" : headersJson);
        intent.putExtra(EXTRA_PLAY_AFTER_THRESHOLD, playAfterThreshold);
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
        taskStore.clearCompletedTasks();
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
            updateNotification(getString(R.string.notification_cancelled), 0, 0, false);
            stopForeground(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            String referer = intent.getStringExtra(EXTRA_REFERER);
            String cookieHeader = intent.getStringExtra(EXTRA_COOKIE_HEADER);
            String headersJson = intent.getStringExtra(EXTRA_HEADERS_JSON);
            boolean playAfterThreshold = intent.getBooleanExtra(EXTRA_PLAY_AFTER_THRESHOLD, false);
            if (url == null || url.trim().isEmpty()) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = FileNames.choose(Uri.parse(url), "");
            }
            JSONObject task = taskStore.enqueue(url, fileName, referer, cookieHeader, headersJson, playAfterThreshold);
            eventLog.write("queued", task.optString("id"), url);
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_download_queued), 0, 0, true));
            startNextAvailable();
        }
        if (ACTION_WAKE.equals(action)) {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_queue_starting), 0, 0, true));
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
                getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    private void updateNotification(String text, long downloaded, long total, boolean ongoing) {
        updateNotification(text, "", downloaded, total, ongoing);
    }

    private void updateNotification(String text, String detailText, long downloaded, long total, boolean ongoing) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, detailText, downloaded, total, ongoing));
    }

    private Notification buildNotification(String text, long downloaded, long total, boolean ongoing) {
        return buildNotification(text, "", downloaded, total, ongoing);
    }

    private Notification buildNotification(String text, String detailText, long downloaded, long total, boolean ongoing) {
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
                .setContentTitle(text)
                .setContentText(detailText == null ? "" : detailText)
                .setContentIntent(contentIntent)
                .setOngoing(ongoing)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_action_cancel), cancelIntent);

        if (total > 0L) {
            int progress = (int) Math.max(0L, Math.min(100L, downloaded * 100L / total));
            builder.setProgress(100, progress, false);
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
            String referer = task.optString("referer", "");
            String cookieHeader = task.optString("cookieHeader", "");
            String headersJson = task.optString("headersJson", "{}");
            boolean playAfterThreshold = task.optBoolean("playAfterThreshold", false);
            boolean playbackStarted = task.optBoolean("playbackStarted", false);
            DownloadEngine engine = new DownloadEngine(this);
            activeEngines.put(taskId, engine);
            taskStore.markRunning(taskId);
            eventLog.write("started", taskId, url);
            eventLog.write("concurrency", taskId, "active=" + activeEngines.size() + "/" + MAX_CONCURRENT_DOWNLOADS);
            updateNotification(getString(R.string.notification_downloading_slots, activeEngines.size(), MAX_CONCURRENT_DOWNLOADS), 0L, 0L, true);
            engine.start(url, fileName, referer, cookieHeader, headersJson, new ServiceCallback(taskId, fileName, playAfterThreshold, playbackStarted));
        }

        if (activeEngines.isEmpty() && taskStore.nextRunnable() == null) {
            updateNotification(getString(R.string.notification_queue_finished), 100L, 100L, false);
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
        private final String fileName;
        private final boolean playAfterThreshold;
        private boolean playbackStarted;

        ServiceCallback(String taskId, String fileName, boolean playAfterThreshold, boolean playbackStarted) {
            this.taskId = taskId;
            this.fileName = fileName == null ? "download.bin" : fileName;
            this.playAfterThreshold = playAfterThreshold;
            this.playbackStarted = playbackStarted;
        }

        @Override
        public void onStatus(String text) {
            taskStore.status(taskId, text);
            eventLog.write("status", taskId, text);
            updateNotification(text, fileName, 0L, 0L, true);
        }

        @Override
        public void onResolved(String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels) {
            onResolved(sourceSite, targetUrl, candidates, candidateLabels, java.util.Collections.emptyList());
        }

        @Override
        public void onResolved(String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels, List<String> candidateReferers) {
            int candidateCount = candidates == null ? 0 : candidates.size();
            taskStore.resolved(taskId, sourceSite, targetUrl, candidates, candidateLabels, candidateReferers);
            eventLog.write("resolved", taskId, sourceSite + " candidates=" + candidateCount + " primary=" + targetUrl + " all=" + joinCandidates(candidates));
        }

        @Override
        public void onProgress(long downloaded, long total) {
            taskStore.progress(taskId, downloaded, total);
            maybeStartPlayback();
            updateNotification(
                    getString(R.string.notification_downloading_progress, formatProgress(downloaded, total)),
                    fileName,
                    downloaded,
                    total,
                    true);
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
            updateNotification(getString(R.string.notification_download_complete), 100L, 100L, false);
            finishTask(taskId);
        }

        @Override
        public void onError(Exception error) {
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            taskStore.failed(taskId, message);
            eventLog.write("error", taskId, message);
            updateNotification(getString(R.string.notification_download_failed), 0L, 0L, false);
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

        private void maybeStartPlayback() {
            if (!playAfterThreshold || playbackStarted) {
                return;
            }
            File partial = playablePartialFile(fileName);
            if (partial == null || partial.length() < PLAY_THRESHOLD_BYTES) {
                return;
            }
            Uri playbackUri = PartialFileProvider.contentUriFor(DownloadService.this, partial, playableDisplayName(partial.getName()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(playbackUri, mimeType(partial.getName()));
            intent.setClipData(ClipData.newUri(getContentResolver(), partial.getName(), playbackUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
                playbackStarted = true;
                eventLog.write("playback_started", taskId, partial.getAbsolutePath());
                taskStore.playbackStarted(taskId, getString(R.string.task_message_playback_started));
                updateNotification(getString(R.string.notification_playback_started), fileName, partial.length(), 0L, true);
            } catch (Exception error) {
                playbackStarted = true;
                eventLog.write("playback_failed", taskId, error.getMessage() == null ? error.toString() : error.getMessage());
                taskStore.playbackStarted(taskId, getString(R.string.task_message_playback_unavailable));
            }
        }

        private String playableDisplayName(String partialName) {
            String clean = FileNames.sanitize(partialName);
            if (clean.endsWith(".part")) {
                clean = clean.substring(0, clean.length() - 5);
            }
            return clean.isEmpty() ? fileName : clean;
        }

        private File playablePartialFile(String baseName) {
            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                dir = getFilesDir();
            }
            String[] names = new String[]{
                    baseName + ".part",
                    FileNames.replaceExtension(baseName, ".ts") + ".part",
                    FileNames.replaceExtension(baseName, ".mp4") + ".part"
            };
            File best = null;
            for (String name : names) {
                File candidate = new File(dir, name);
                if (candidate.exists() && candidate.length() > 0L && (best == null || candidate.length() > best.length())) {
                    best = candidate;
                }
            }
            return best;
        }

        private String mimeType(String name) {
            String lowered = name == null ? "" : name.toLowerCase(Locale.US);
            if (lowered.contains(".mp4") || lowered.contains(".m4v")) {
                return "video/mp4";
            }
            if (lowered.contains(".webm")) {
                return "video/webm";
            }
            if (lowered.contains(".ts")) {
                return "video/mp2t";
            }
            return "video/*";
        }
    }
}
