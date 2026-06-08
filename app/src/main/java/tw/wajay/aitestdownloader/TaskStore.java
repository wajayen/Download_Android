package tw.wajay.aitestdownloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

final class TaskStore {
    static final String PREFS = "download_tasks";
    private static final String KEY_TASKS = "tasks_json";
    private static final int MAX_TASKS = 50;

    static final String STATUS_QUEUED = "queued";
    static final String STATUS_RUNNING = "running";
    static final String STATUS_DONE = "done";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_CANCELLED = "cancelled";

    private final Context context;
    private final SharedPreferences prefs;

    static final class CandidateOption {
        final String taskId;
        final int index;
        final String url;
        final String label;

        CandidateOption(String taskId, int index, String url, String label) {
            this.taskId = taskId;
            this.index = index;
            this.url = url;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    TaskStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized int recoverInterruptedTasks() {
        JSONArray tasks = loadTasks();
        int recovered = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || !STATUS_RUNNING.equals(task.optString("status"))) {
                continue;
            }
            try {
                task.put("status", STATUS_QUEUED);
                task.put("message", text(R.string.task_message_recovered));
                task.put("updatedAt", System.currentTimeMillis());
                recovered++;
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
        }
        if (recovered > 0) {
            saveTasks(tasks);
        }
        return recovered;
    }

    synchronized JSONObject enqueue(String url, String fileName) {
        return enqueue(url, fileName, "", "");
    }

    synchronized JSONObject enqueue(String url, String fileName, String referer, String cookieHeader) {
        return enqueue(url, fileName, referer, cookieHeader, "{}");
    }

    synchronized JSONObject enqueue(String url, String fileName, String referer, String cookieHeader, String headersJson) {
        JSONArray tasks = loadTasks();
        JSONObject task = new JSONObject();
        try {
            task.put("id", "T" + System.currentTimeMillis());
            task.put("url", url);
            task.put("fileName", fileName);
            task.put("referer", referer == null ? "" : referer.trim());
            task.put("cookieHeader", cookieHeader == null ? "" : cookieHeader.trim());
            task.put("headersJson", normalizeHeadersJson(headersJson));
            task.put("status", STATUS_QUEUED);
            task.put("message", text(R.string.task_message_queued));
            task.put("output", "");
            task.put("downloaded", 0L);
            task.put("total", -1L);
            task.put("createdAt", System.currentTimeMillis());
            task.put("updatedAt", System.currentTimeMillis());
            tasks.put(task);
            saveTasks(trimmed(tasks));
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
        return task;
    }

    synchronized JSONObject nextRunnable() {
        JSONArray tasks = loadTasks();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null && STATUS_QUEUED.equals(task.optString("status"))) {
                return task;
            }
        }
        return null;
    }

    synchronized void markRunning(String id) {
        updateTask(id, STATUS_RUNNING, text(R.string.task_message_running), null, -1L, -1L, null);
    }

    synchronized void status(String id, String status) {
        updateTask(id, null, status, null, -1L, -1L, null);
    }

    synchronized void progress(String id, long downloaded, long total) {
        updateTask(id, STATUS_RUNNING, text(R.string.task_message_running), null, downloaded, total, null);
    }

    synchronized void resolved(String id, String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels) {
        resolved(id, sourceSite, targetUrl, candidates, candidateLabels, null);
    }

    synchronized void resolved(String id, String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels, List<String> candidateReferers) {
        int candidateCount = candidates == null ? 0 : candidates.size();
        JSONArray tasks = loadTasks();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || !id.equals(task.optString("id"))) {
                continue;
            }
            try {
                task.put("sourceSite", sourceSite == null ? "" : sourceSite);
                task.put("resolvedUrl", targetUrl == null ? "" : targetUrl);
                task.put("candidateCount", candidateCount);
                task.put("candidateUrls", candidateArray(candidates));
                if (candidateLabels != null && !candidateLabels.isEmpty()) {
                    task.put("candidateLabels", candidateArray(candidateLabels));
                }
                if (candidateReferers != null && !candidateReferers.isEmpty()) {
                    task.put("candidateReferers", candidateArray(candidateReferers));
                }
                task.put("updatedAt", System.currentTimeMillis());
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
            break;
        }
        saveTasks(tasks);
    }

    synchronized void done(String id, String output) {
        updateTask(id, STATUS_DONE, text(R.string.task_message_done), output, -1L, -1L, null);
    }

    synchronized void failed(String id, String message) {
        updateTask(id, STATUS_FAILED, text(R.string.task_message_failed, message), null, -1L, -1L, message);
    }

    synchronized void cancelled(String id) {
        updateTask(id, STATUS_CANCELLED, text(R.string.task_message_cancelled), null, -1L, -1L, null);
    }

    synchronized void cancelRunningOrQueued() {
        JSONArray tasks = loadTasks();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            String status = task.optString("status");
            if (STATUS_RUNNING.equals(status) || STATUS_QUEUED.equals(status)) {
                try {
                    task.put("status", STATUS_CANCELLED);
                    task.put("message", text(R.string.task_message_cancelled));
                    task.put("updatedAt", System.currentTimeMillis());
                } catch (JSONException error) {
                    throw new IllegalStateException(error);
                }
            }
        }
        saveTasks(tasks);
    }

    synchronized JSONObject retryLatestFailedOrCancelled() {
        JSONArray tasks = loadTasks();
        for (int i = tasks.length() - 1; i >= 0; i--) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            String status = task.optString("status");
            if (!STATUS_FAILED.equals(status) && !STATUS_CANCELLED.equals(status)) {
                continue;
            }
            try {
                task.put("status", STATUS_QUEUED);
                task.put("message", text(R.string.task_message_retry));
                task.put("downloaded", 0L);
                task.put("total", -1L);
                task.put("error", "");
                task.put("updatedAt", System.currentTimeMillis());
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
            saveTasks(tasks);
            return task;
        }
        return null;
    }

    synchronized JSONObject retryNextCandidate() {
        JSONArray tasks = loadTasks();
        for (int i = tasks.length() - 1; i >= 0; i--) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            JSONArray candidateUrls = task.optJSONArray("candidateUrls");
            if (candidateUrls == null || candidateUrls.length() < 2) {
                continue;
            }
            String nextUrl = nextCandidateUrl(task, candidateUrls);
            if (nextUrl.isEmpty()) {
                continue;
            }
            int nextIndex = candidateIndex(candidateUrls, nextUrl);
            String previousUrl = task.optString("url", "");
            try {
                task.put("url", nextUrl);
                task.put("status", STATUS_QUEUED);
                task.put("message", text(R.string.task_message_alternate));
                task.put("resolvedUrl", nextUrl);
                task.put("downloaded", 0L);
                task.put("total", -1L);
                task.put("output", "");
                task.put("error", "");
                task.put("referer", candidateRefererAt(task, nextIndex, task.optString("referer", ""), previousUrl));
                task.put("headersJson", normalizeHeadersJson(task.optString("headersJson", "{}")));
                task.put("updatedAt", System.currentTimeMillis());
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
            saveTasks(tasks);
            return task;
        }
        return null;
    }

    synchronized List<CandidateOption> latestCandidateOptions() {
        JSONArray tasks = loadTasks();
        for (int i = tasks.length() - 1; i >= 0; i--) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            JSONArray candidateUrls = task.optJSONArray("candidateUrls");
            if (candidateUrls == null || candidateUrls.length() == 0) {
                continue;
            }
            JSONArray candidateLabels = task.optJSONArray("candidateLabels");
            List<CandidateOption> options = new java.util.ArrayList<>();
            String taskId = task.optString("id", "");
            String current = task.optString("resolvedUrl", "");
            String sourceSite = task.optString("sourceSite", "");
            for (int c = 0; c < candidateUrls.length(); c++) {
                String url = candidateUrls.optString(c, "");
                if (url.isEmpty()) {
                    continue;
                }
                String marker = url.equals(current) ? text(R.string.source_current_marker) : "";
                String resolverLabel = candidateLabels == null ? "" : candidateLabels.optString(c, "");
                options.add(new CandidateOption(taskId, c, url, candidateLabel(c + 1, marker, sourceSite, url, resolverLabel)));
            }
            if (!options.isEmpty()) {
                return options;
            }
        }
        return new java.util.ArrayList<>();
    }

    synchronized JSONObject retryCandidate(String taskId, String selectedUrl) {
        if (taskId == null || taskId.isEmpty() || selectedUrl == null || selectedUrl.isEmpty()) {
            return null;
        }
        JSONArray tasks = loadTasks();
        for (int i = tasks.length() - 1; i >= 0; i--) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || !taskId.equals(task.optString("id"))) {
                continue;
            }
            JSONArray candidateUrls = task.optJSONArray("candidateUrls");
            if (!containsCandidate(candidateUrls, selectedUrl)) {
                return null;
            }
            int selectedIndex = candidateIndex(candidateUrls, selectedUrl);
            String previousUrl = task.optString("url", "");
            try {
                task.put("url", selectedUrl);
                task.put("status", STATUS_QUEUED);
                task.put("message", text(R.string.task_message_selected_source));
                task.put("resolvedUrl", selectedUrl);
                task.put("downloaded", 0L);
                task.put("total", -1L);
                task.put("output", "");
                task.put("error", "");
                task.put("referer", candidateRefererAt(task, selectedIndex, task.optString("referer", ""), previousUrl));
                task.put("headersJson", normalizeHeadersJson(task.optString("headersJson", "{}")));
                task.put("updatedAt", System.currentTimeMillis());
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
            saveTasks(tasks);
            return task;
        }
        return null;
    }

    synchronized int clearFinishedTasks() {
        JSONArray tasks = loadTasks();
        JSONArray kept = new JSONArray();
        int cleared = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            String status = task.optString("status");
            if (STATUS_DONE.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status)) {
                cleared++;
                continue;
            }
            kept.put(task);
        }
        if (cleared > 0) {
            saveTasks(kept);
        }
        return cleared;
    }

    synchronized String summary() {
        JSONArray tasks = loadTasks();
        if (tasks.length() == 0) {
            return text(R.string.task_summary_empty);
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, tasks.length() - 6);
        for (int i = tasks.length() - 1; i >= start; i--) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(task.optString("id")).append("  ");
            builder.append(label(task.optString("status"))).append('\n');
            builder.append(task.optString("fileName", "download.bin"));
            long downloaded = task.optLong("downloaded", 0L);
            long total = task.optLong("total", -1L);
            if (total > 0L) {
                builder.append('\n').append(String.format(Locale.US, "%.1f%%", downloaded * 100.0 / total));
            } else if (downloaded > 0L) {
                builder.append('\n').append(downloaded).append(" bytes");
            }
            String output = task.optString("output", "");
            if (!output.isEmpty()) {
                builder.append('\n').append(output);
            }
            appendResolvedSummary(builder, task);
            String message = task.optString("message", "");
            if (!message.isEmpty() && !message.equals(label(task.optString("status")))) {
                builder.append('\n').append(message);
            }
        }
        return builder.toString();
    }

    private void appendResolvedSummary(StringBuilder builder, JSONObject task) {
        String resolvedUrl = task.optString("resolvedUrl", "");
        if (resolvedUrl.isEmpty()) {
            return;
        }
        builder.append('\n').append(text(
                R.string.task_summary_resolved,
                task.optString("sourceSite", "generic"),
                task.optInt("candidateCount", 1)));
        JSONArray candidateUrls = task.optJSONArray("candidateUrls");
        JSONArray candidateLabels = task.optJSONArray("candidateLabels");
        builder.append('\n').append(resolvedPreview(task, resolvedUrl, candidateUrls, candidateLabels));
        if (candidateUrls == null || candidateUrls.length() <= 1) {
            return;
        }
        int preview = Math.min(3, candidateUrls.length());
        for (int c = 0; c < preview; c++) {
            String candidate = candidateUrls.optString(c, "");
            if (!candidate.isEmpty()) {
                builder.append('\n').append(text(R.string.task_summary_candidate, c + 1, candidatePreview(task, candidate, candidateLabels, c)));
            }
        }
        if (candidateUrls.length() > preview) {
            builder.append('\n').append(text(R.string.task_summary_candidate_more, candidateUrls.length() - preview));
        }
    }

    private String resolvedPreview(JSONObject task, String resolvedUrl, JSONArray candidateUrls, JSONArray candidateLabels) {
        if (candidateUrls != null) {
            for (int i = 0; i < candidateUrls.length(); i++) {
                if (resolvedUrl.equals(candidateUrls.optString(i, ""))) {
                    return candidatePreview(task, resolvedUrl, candidateLabels, i);
                }
            }
        }
        return readableCandidate(task.optString("sourceSite", ""), resolvedUrl, "");
    }

    private String candidatePreview(JSONObject task, String url, JSONArray candidateLabels, int index) {
        String resolverLabel = candidateLabels == null ? "" : candidateLabels.optString(index, "");
        return readableCandidate(task.optString("sourceSite", ""), url, resolverLabel);
    }

    private String readableCandidate(String sourceSite, String url, String resolverLabel) {
        StringBuilder builder = new StringBuilder();
        String tags = candidateTags(sourceSite, url);
        if (!tags.isEmpty()) {
            builder.append('[').append(tags).append("] ");
        }
        String label = compactLabel(resolverLabel);
        if (!label.isEmpty()) {
            builder.append(label).append(" - ");
        }
        builder.append(shortUrl(url));
        return builder.toString();
    }

    private JSONArray candidateArray(List<String> candidates) {
        JSONArray array = new JSONArray();
        if (candidates == null) {
            return array;
        }
        int limit = Math.min(12, candidates.size());
        for (int i = 0; i < limit; i++) {
            String candidate = candidates.get(i);
            if (candidate != null && !candidate.trim().isEmpty()) {
                array.put(candidate);
            }
        }
        return array;
    }

    private String nextCandidateUrl(JSONObject task, JSONArray candidateUrls) {
        String current = task.optString("resolvedUrl", "");
        if (current.isEmpty()) {
            current = task.optString("url", "");
        }
        int currentIndex = -1;
        for (int i = 0; i < candidateUrls.length(); i++) {
            if (current.equals(candidateUrls.optString(i, ""))) {
                currentIndex = i;
                break;
            }
        }
        int start = currentIndex < 0 ? 0 : currentIndex + 1;
        for (int offset = 0; offset < candidateUrls.length(); offset++) {
            int index = (start + offset) % candidateUrls.length();
            String candidate = candidateUrls.optString(index, "");
            if (!candidate.isEmpty() && !candidate.equals(current)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean containsCandidate(JSONArray candidateUrls, String selectedUrl) {
        if (candidateUrls == null) {
            return false;
        }
        for (int i = 0; i < candidateUrls.length(); i++) {
            if (selectedUrl.equals(candidateUrls.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }

    private int candidateIndex(JSONArray candidateUrls, String selectedUrl) {
        if (candidateUrls == null || selectedUrl == null) {
            return -1;
        }
        for (int i = 0; i < candidateUrls.length(); i++) {
            if (selectedUrl.equals(candidateUrls.optString(i, ""))) {
                return i;
            }
        }
        return -1;
    }

    private String candidateRefererAt(JSONObject task, int index, String existingReferer, String previousUrl) {
        if (index >= 0) {
            JSONArray candidateReferers = task.optJSONArray("candidateReferers");
            if (candidateReferers != null) {
                String referer = candidateReferers.optString(index, "").trim();
                if (!referer.isEmpty()) {
                    return referer;
                }
            }
        }
        return firstNonEmpty(existingReferer, previousUrl);
    }

    private String candidateLabel(int index, String marker, String sourceSite, String url, String resolverLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append(text(R.string.source_label_prefix, index, marker));
        String tags = candidateTags(sourceSite, url);
        if (!tags.isEmpty()) {
            builder.append(" [").append(tags).append("]");
        }
        String cleanedLabel = compactLabel(resolverLabel);
        if (!cleanedLabel.isEmpty()) {
            builder.append(' ').append(cleanedLabel);
        }
        builder.append(" - ").append(shortUrl(url));
        return builder.toString();
    }

    private String shortUrl(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath();
        String value = host + path;
        if (value.isEmpty()) {
            value = url;
        }
        return value.length() > 72 ? value.substring(0, 72) + "..." : value;
    }

    private String compactLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private String candidateTags(String sourceSite, String url) {
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        StringBuilder tags = new StringBuilder();
        appendTag(tags, mediaKind(lowered));
        appendTag(tags, qualityTag(lowered));
        appendTag(tags, episodeTag(lowered));
        appendTag(tags, hostSourceTag(lowered));
        appendTag(tags, normalizedSourceSite(sourceSite));
        return tags.toString();
    }

    private String mediaKind(String loweredUrl) {
        if (loweredUrl.contains(".m3u8")) {
            return "HLS";
        }
        if (loweredUrl.contains(".mpd")) {
            return "DASH";
        }
        if (loweredUrl.contains(".mp4")) {
            return "MP4";
        }
        if (loweredUrl.contains(".webm")) {
            return "WEBM";
        }
        if (loweredUrl.contains(".m4v")) {
            return "M4V";
        }
        if (loweredUrl.contains("/player") || loweredUrl.contains("/parse") || loweredUrl.contains("/play/") || loweredUrl.contains("/vodplay/")) {
            return "PLAYER";
        }
        return "";
    }

    private String qualityTag(String loweredUrl) {
        java.util.regex.Matcher pMatcher = java.util.regex.Pattern.compile("(?<!\\d)(2160|1440|1080|720|480|360)p?(?!\\d)").matcher(loweredUrl);
        if (pMatcher.find()) {
            return pMatcher.group(1) + "p";
        }
        java.util.regex.Matcher sizeMatcher = java.util.regex.Pattern.compile("(?<!\\d)(3840x2160|2560x1440|1920x1080|1280x720|854x480|640x360)(?!\\d)").matcher(loweredUrl);
        if (!sizeMatcher.find()) {
            return "";
        }
        String size = sizeMatcher.group(1);
        if (size.endsWith("2160")) {
            return "2160p";
        }
        if (size.endsWith("1440")) {
            return "1440p";
        }
        if (size.endsWith("1080")) {
            return "1080p";
        }
        if (size.endsWith("720")) {
            return "720p";
        }
        if (size.endsWith("480")) {
            return "480p";
        }
        if (size.endsWith("360")) {
            return "360p";
        }
        return "";
    }

    private String episodeTag(String loweredUrl) {
        java.util.regex.Matcher epMatcher = java.util.regex.Pattern.compile("(?:/|[-_])ep(?:isode)?[-_ ]?(\\d+)(?:\\D|$)").matcher(loweredUrl);
        if (epMatcher.find()) {
            return "EP" + epMatcher.group(1);
        }
        java.util.regex.Matcher playMatcher = java.util.regex.Pattern.compile("/(?:vod)?play/(?:id/)?\\d+/(?:sid/)?\\d+/(?:nid/)?(\\d+)").matcher(loweredUrl);
        if (playMatcher.find()) {
            return "EP" + playMatcher.group(1);
        }
        java.util.regex.Matcher htmlMatcher = java.util.regex.Pattern.compile("/(?:play/)?\\d+/(?:\\d+[-_])?(\\d+)\\.html").matcher(loweredUrl);
        if (htmlMatcher.find()) {
            return "EP" + htmlMatcher.group(1);
        }
        java.util.regex.Matcher dashMatcher = java.util.regex.Pattern.compile("(?:episode|ep|part|seg|segment)[-_]?(\\d+)").matcher(loweredUrl);
        return dashMatcher.find() ? "EP" + dashMatcher.group(1) : "";
    }

    private String hostSourceTag(String loweredUrl) {
        String[][] markers = new String[][]{
                {"xluuss", "XLU"}, {"lzcdn", "LZ"}, {"hhuus", "HH"},
                {"qsstvw", "QSS"}, {"gsuus", "GS"}, {"bfllvip", "BFL"},
                {"ppqrrs", "PPQ"}, {"qqqrst", "QQQ"}, {"vodcnd", "VODCND"},
                {"phimgood", "PHIM"}, {"ryiplay", "RYI"}, {"huyall", "HUYA"},
                {"ijycnd", "IJY"}, {"jisuzyv", "JISU"}, {"taopianplay1", "TAOPIAN"},
                {"animevideo.php", "AniGamer"}, {"anime1", "Anime1"}, {"777tv", "777TV"},
                {"99itv", "99iTV"}, {"nnyy", "NNYY"}, {"olevod", "Olevod"},
                {"olehdtv", "OleHDTV"}, {"dramasq", "DramaSQ"}, {"thanju", "Thanju"},
                {"3kor", "3KOR"}, {"dmcdn.net", "DMCDN"}, {"dailymotion", "Dailymotion"},
                {"googlevideo", "GoogleVideo"}, {"youtube", "YouTube"}, {"bilivideo", "BiliVideo"},
                {"bilibili", "Bilibili"}, {"iqiyi", "iQIYI"}, {"qiyi", "iQIYI"},
                {"ikanbot", "Ikanbot"}, {"yfsp", "YFSP"}
        };
        for (String[] marker : markers) {
            if (loweredUrl.contains(marker[0])) {
                return marker[1];
            }
        }
        return "";
    }

    private String normalizedSourceSite(String sourceSite) {
        if (sourceSite == null || sourceSite.trim().isEmpty() || "generic".equals(sourceSite)) {
            return "";
        }
        return sourceSite.trim();
    }

    private String firstNonEmpty(String first, String second) {
        String value = first == null ? "" : first.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return second == null ? "" : second.trim();
    }

    private String normalizeHeadersJson(String headersJson) {
        String raw = headersJson == null || headersJson.trim().isEmpty() ? "{}" : headersJson.trim();
        try {
            return new JSONObject(raw).toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private void appendTag(StringBuilder tags, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (tags.length() > 0) {
            tags.append(' ');
        }
        tags.append(value);
    }

    private void updateTask(String id, String status, String message, String output, long downloaded, long total, String error) {
        JSONArray tasks = loadTasks();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || !id.equals(task.optString("id"))) {
                continue;
            }
            try {
                if (status != null) {
                    task.put("status", status);
                }
                if (message != null) {
                    task.put("message", message);
                }
                if (output != null) {
                    task.put("output", output);
                }
                if (downloaded >= 0L) {
                    task.put("downloaded", downloaded);
                }
                if (total >= 0L) {
                    task.put("total", total);
                }
                if (error != null) {
                    task.put("error", error);
                }
                task.put("updatedAt", System.currentTimeMillis());
            } catch (JSONException jsonError) {
                throw new IllegalStateException(jsonError);
            }
            break;
        }
        saveTasks(tasks);
    }

    private JSONArray loadTasks() {
        String raw = prefs.getString(KEY_TASKS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void saveTasks(JSONArray tasks) {
        prefs.edit().putString(KEY_TASKS, tasks.toString()).apply();
    }

    private JSONArray trimmed(JSONArray tasks) {
        if (tasks.length() <= MAX_TASKS) {
            return tasks;
        }
        JSONArray trimmed = new JSONArray();
        int start = tasks.length() - MAX_TASKS;
        for (int i = start; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null) {
                trimmed.put(task);
            }
        }
        return trimmed;
    }

    private String label(String status) {
        if (STATUS_QUEUED.equals(status)) {
            return text(R.string.task_label_queued);
        }
        if (STATUS_RUNNING.equals(status)) {
            return text(R.string.task_label_running);
        }
        if (STATUS_DONE.equals(status)) {
            return text(R.string.task_label_done);
        }
        if (STATUS_FAILED.equals(status)) {
            return text(R.string.task_label_failed);
        }
        if (STATUS_CANCELLED.equals(status)) {
            return text(R.string.task_label_cancelled);
        }
        return status == null ? "" : status;
    }

    private String text(int resId, Object... args) {
        return context.getString(resId, args);
    }
}
