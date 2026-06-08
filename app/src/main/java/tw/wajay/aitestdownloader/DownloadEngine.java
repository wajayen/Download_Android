package tw.wajay.aitestdownloader;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class DownloadEngine {
    private static final Pattern ABSOLUTE_MEDIA_URL = Pattern.compile(
            "https?://[^\\s\"'<>\\\\]+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\\s\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_MEDIA_URL = Pattern.compile(
            "(?:src|href|file|url)\\s*[:=]\\s*[\"']([^\"']+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE_PAIR = Pattern.compile("([A-Z0-9-]+)=((?:\"[^\"]+\")|[^,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANIME1_API_REQ = Pattern.compile("data-apireq=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final int SEGMENT_RETRY_LIMIT = 3;
    private static final int PAGE_RESOLVE_DEPTH_LIMIT = 4;
    private static final int MAX_DASH_DURATION_SEGMENTS = 2000;

    private static final class ResolvedTarget {
        final String primaryUrl;
        final String sourceSite;
        final List<String> fallbackUrls;

        ResolvedTarget(String primaryUrl, String sourceSite, List<String> fallbackUrls) {
            this.primaryUrl = primaryUrl;
            this.sourceSite = sourceSite;
            this.fallbackUrls = fallbackUrls;
        }
    }

    private static final class HlsSegment {
        final String url;
        final HlsKey key;
        final HlsMap map;
        final int sequence;
        final boolean discontinuity;

        HlsSegment(String url, HlsKey key, HlsMap map, int sequence, boolean discontinuity) {
            this.url = url;
            this.key = key;
            this.map = map;
            this.sequence = sequence;
            this.discontinuity = discontinuity;
        }
    }

    private static final class HlsKey {
        final String method;
        final String uri;
        final byte[] iv;
        byte[] bytes;

        HlsKey(String method, String uri, byte[] iv) {
            this.method = method;
            this.uri = uri;
            this.iv = iv;
        }
    }

    private static final class HlsMap {
        final String uri;
        final String byteRange;

        HlsMap(String uri, String byteRange) {
            this.uri = uri;
            this.byteRange = byteRange;
        }
    }

    private static final class Variant {
        final String url;
        final int bandwidth;

        Variant(String url, int bandwidth) {
            this.url = url;
            this.bandwidth = bandwidth;
        }
    }

    private interface ProgressSink {
        void onProgress(long downloaded, long total);
    }

    interface Callback {
        void onStatus(String text);
        void onResolved(String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels);
        void onProgress(long downloaded, long total);
        void onDone(File output);
        void onError(Exception error);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    DownloadEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    void start(String rawUrl, String requestedName, Callback callback) {
        cancelled.set(false);
        executor.execute(() -> {
            try {
                Uri uri = Uri.parse(rawUrl);
                String fileName = FileNames.choose(uri, requestedName);
                String targetUrl = rawUrl;
                String sourceSite = MediaResolver.sourceSite(rawUrl);
                List<String> fallbackUrls = new ArrayList<>();
                if (!isMediaUrl(rawUrl)) {
                    ResolvedTarget resolvedTarget = resolvePageToMedia(rawUrl, callback);
                    targetUrl = resolvedTarget.primaryUrl;
                    sourceSite = resolvedTarget.sourceSite;
                    fallbackUrls = resolvedTarget.fallbackUrls;
                    if (requestedName == null || requestedName.trim().isEmpty()) {
                        fileName = FileNames.choose(Uri.parse(targetUrl), "");
                    }
                }
                downloadWithFallbacks(targetUrl, fallbackUrls, sourceSite, fileName, callback);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }
    void cancel() {
        cancelled.set(true);
    }

    private ResolvedTarget resolvePageToMedia(String rawUrl, Callback callback) throws IOException {
        String currentUrl = rawUrl;
        for (int depth = 0; depth < PAGE_RESOLVE_DEPTH_LIMIT; depth++) {
            String pageText = readText(currentUrl);
            if (looksLikeHlsManifest(pageText)) {
                callback.onStatus("????HLS manifest");
                callback.onResolved(MediaResolver.sourceSite(currentUrl), currentUrl, Collections.singletonList(currentUrl), Collections.singletonList("HLS manifest"));
                return new ResolvedTarget(currentUrl, MediaResolver.sourceSite(currentUrl), new ArrayList<>());
            }

            String anime1Url = resolveAnime1ApiMedia(currentUrl, pageText);
            if (anime1Url != null) {
                callback.onStatus("Resolved Anime1 API media");
                callback.onResolved("anime1", anime1Url, Collections.singletonList(anime1Url), Collections.singletonList("Anime1 API"));
                return new ResolvedTarget(anime1Url, "anime1", new ArrayList<>());
            }
            MediaResolver.Result resolved = MediaResolver.resolve(pageText, currentUrl);
            callback.onStatus("Resolving page candidate: " + resolved.sourceSite + " depth=" + depth);
            if (resolved.primaryUrl == null) {
                throw new IOException("?蹓遴????潮???????? mp4/m3u8 ?謕?");
            }
            callback.onResolved(resolved.sourceSite, resolved.primaryUrl, resolved.candidates, resolved.candidateLabels);
            if (resolved.primaryIsMedia) {
                return new ResolvedTarget(resolved.primaryUrl, resolved.sourceSite, mediaFallbacks(resolved.candidates, resolved.primaryUrl));
            }
            currentUrl = resolved.primaryUrl;
        }
        throw new IOException("?蹓遴????????瞍?????????????URL");
    }

    private void downloadWithFallbacks(String primaryUrl, List<String> fallbackUrls, String sourceSite, String fileName, Callback callback) throws IOException {
        List<String> targets = new ArrayList<>();
        targets.add(primaryUrl);
        for (String fallbackUrl : fallbackUrls) {
            if (fallbackUrl != null && !fallbackUrl.equals(primaryUrl) && !targets.contains(fallbackUrl)) {
                targets.add(fallbackUrl);
            }
        }

        IOException lastError = null;
        for (int i = 0; i < targets.size(); i++) {
            String targetUrl = targets.get(i);
            try {
                if (i > 0) {
                    callback.onStatus("Retrying alternate source " + (i + 1) + "/" + targets.size());
                    callback.onResolved(sourceSite, targetUrl, targets.subList(i, targets.size()), Collections.emptyList());
                }
                if (isDashUrl(targetUrl)) {
                    callback.onStatus("Resolving DASH manifest");
                    downloadDash(targetUrl, FileNames.replaceExtension(fileName, ".mp4"), callback);
                } else if (isHlsUrl(targetUrl) || (targetUrl.equals(primaryUrl) && !isMediaUrl(targetUrl))) {
                    callback.onStatus("Resolving HLS manifest");
                    downloadHls(targetUrl, FileNames.replaceExtension(fileName, ".ts"), callback);
                } else {
                    callback.onStatus("Starting HTTP download");
                    downloadHttp(targetUrl, fileName, callback);
                }
                return;
            } catch (IOException error) {
                lastError = error;
                if (cancelled.get() || i == targets.size() - 1) {
                    throw error;
                }
                callback.onStatus("Source failed, switching: " + shortMessage(error));
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private List<String> mediaFallbacks(List<String> candidates, String primaryUrl) {
        List<String> fallbacks = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.equals(primaryUrl) && isMediaUrl(candidate) && !fallbacks.contains(candidate)) {
                fallbacks.add(candidate);
            }
        }
        return fallbacks;
    }

    private String shortMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 96 ? message.substring(0, 96) : message;
    }

    private void downloadHttp(String rawUrl, String fileName, Callback callback) throws IOException {
        File output = outputFile(fileName);
        File part = new File(output.getParentFile(), output.getName() + ".part");
        File state = new File(output.getParentFile(), output.getName() + ".httpstate");
        ensureHttpPartialMatches(state, part, rawUrl);
        saveHttpState(state, rawUrl);
        long existing = part.exists() ? part.length() : 0L;

        HttpURLConnection connection = open(rawUrl);
        if (existing > 0L) {
            connection.setRequestProperty("Range", "bytes=" + existing + "-");
        }
        int code = connection.getResponseCode();
        boolean canResume = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL;
        if (existing > 0L && !canResume) {
            existing = 0L;
        }
        long contentLength = connection.getContentLengthLong();
        long total = contentLength > 0L ? existing + contentLength : -1L;

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fos = new FileOutputStream(part, canResume);
             BufferedOutputStream outputStream = new BufferedOutputStream(fos)) {
            copy(input, outputStream, existing, total, callback::onProgress);
        } finally {
            connection.disconnect();
        }

        if (cancelled.get()) {
            callback.onStatus("Cancelled; keeping partial file");
            return;
        }
        replace(part, output);
        if (state.exists()) {
            state.delete();
        }
        callback.onDone(output);
    }

    private void downloadHls(String rawUrl, String fileName, Callback callback) throws IOException {
        HlsPlaylist playlist = resolveHlsPlaylist(rawUrl, callback);
        List<HlsSegment> segments = playlist.segments;
        if (segments.isEmpty()) {
            throw new IOException("HLS manifest did not contain downloadable segments");
        }

        File output = outputFile(fileName);
        File part = new File(output.getParentFile(), output.getName() + ".part");
        File checkpoint = new File(output.getParentFile(), output.getName() + ".hlsstate");
        int startIndex = loadHlsCheckpoint(checkpoint, playlist, part);
        boolean append = startIndex > 0 && part.exists();
        Set<String> writtenMaps = new HashSet<>();
        boolean allowMapWrite = startIndex == 0;

        try (FileOutputStream fos = new FileOutputStream(part, append);
             BufferedOutputStream outputStream = new BufferedOutputStream(fos)) {
            for (int index = startIndex; index < segments.size(); index++) {
                if (cancelled.get()) {
                    callback.onStatus("Cancelled; keeping HLS partial file");
                    return;
                }
                callback.onStatus("??? HLS ??芣 " + (index + 1) + " / " + segments.size());
                HlsSegment segment = segments.get(index);
                if (segment.discontinuity) {
                    outputStream.flush();
                }
                if (allowMapWrite) {
                    writeMapIfNeeded(outputStream, segment, writtenMaps);
                }
                byte[] data = downloadSegmentWithRetry(segment);
                outputStream.write(data);
                outputStream.flush();
                saveHlsCheckpoint(checkpoint, playlist, index + 1);
                callback.onProgress(index + 1L, segments.size());
            }
        }

        replace(part, output);
        if (checkpoint.exists()) {
            checkpoint.delete();
        }
        callback.onDone(output);
    }

    private void downloadDash(String rawUrl, String fileName, Callback callback) throws IOException {
        DashPlan plan = resolveDashPlan(rawUrl);
        if (plan.initUrl == null || plan.initUrl.isEmpty() || plan.segments.isEmpty()) {
            throw new IOException("DASH manifest did not contain a supported SegmentTemplate or SegmentList");
        }

        File output = outputFile(fileName);
        File part = new File(output.getParentFile(), output.getName() + ".part");
        File checkpoint = new File(output.getParentFile(), output.getName() + ".dashstate");
        int startIndex = loadDashCheckpoint(checkpoint, plan, part);
        boolean append = startIndex > 0 && part.exists();

        callback.onStatus("DASH representation " + plan.representationId + " bandwidth=" + plan.bandwidth);
        try (FileOutputStream fos = new FileOutputStream(part, append);
             BufferedOutputStream outputStream = new BufferedOutputStream(fos)) {
            if (startIndex == 0) {
                byte[] init = readBytes(plan.initUrl, plan.initRange);
                if (init.length == 0) {
                    throw new IOException("empty DASH init segment");
                }
                outputStream.write(init);
                outputStream.flush();
            }
            for (int index = startIndex; index < plan.segments.size(); index++) {
                if (cancelled.get()) {
                    callback.onStatus("Cancelled; keeping DASH partial file");
                    return;
                }
                callback.onStatus("Downloading DASH segment " + (index + 1) + " / " + plan.segments.size());
                DashSegment segment = plan.segments.get(index);
                byte[] data = readBytes(segment.url, segment.byteRange);
                if (data.length == 0) {
                    throw new IOException("empty DASH segment");
                }
                outputStream.write(data);
                outputStream.flush();
                saveDashCheckpoint(checkpoint, plan, index + 1);
                callback.onProgress(index + 1L, plan.segments.size());
            }
        }

        replace(part, output);
        if (checkpoint.exists()) {
            checkpoint.delete();
        }
        callback.onDone(output);
    }

    private boolean looksLikeHlsManifest(String text) {
        return text != null && text.trim().startsWith("#EXTM3U");
    }

    private void copy(InputStream input, BufferedOutputStream output, long downloadedStart, long total, ProgressSink progressSink) throws IOException {
        byte[] buffer = new byte[128 * 1024];
        long downloaded = downloadedStart;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (cancelled.get()) {
                return;
            }
            output.write(buffer, 0, read);
            downloaded += read;
            progressSink.onProgress(downloaded, total);
        }
        output.flush();
    }

    private HttpURLConnection open(String rawUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android Downloader/0.3");
        connection.setRequestProperty("Accept", "*/*");
        return connection;
    }

    private String readText(String rawUrl) throws IOException {
        HttpURLConnection connection = open(rawUrl);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static final class DashSegment {
        final String url;
        final String byteRange;

        DashSegment(String url, String byteRange) {
            this.url = url;
            this.byteRange = byteRange;
        }
    }

    private static final class DashPlan {
        final String manifestUrl;
        final String representationId;
        final int bandwidth;
        final String initUrl;
        final String initRange;
        final List<DashSegment> segments;

        DashPlan(String manifestUrl, String representationId, int bandwidth, String initUrl, String initRange, List<DashSegment> segments) {
            this.manifestUrl = manifestUrl;
            this.representationId = representationId;
            this.bandwidth = bandwidth;
            this.initUrl = initUrl;
            this.initRange = initRange;
            this.segments = segments;
        }
    }

    private String resolveAnime1ApiMedia(String pageUrl, String pageText) throws IOException {
        if (!"anime1".equals(MediaResolver.sourceSite(pageUrl))) {
            return null;
        }
        Matcher matcher = ANIME1_API_REQ.matcher(pageText == null ? "" : pageText);
        if (!matcher.find()) {
            return null;
        }
        String dataReq = java.net.URLDecoder.decode(matcher.group(1), "UTF-8");
        String body = "d=" + URLEncoder.encode(dataReq, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL("https://v.anime1.me/api").openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android Downloader/0.16");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Referer", pageUrl);
        connection.setRequestProperty("Origin", "https://anime1.me");
        try (java.io.OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes("UTF-8"));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return pickAnime1MediaUrl(builder.toString());
        } catch (Exception error) {
            throw new IOException("Anime1 API media resolution failed", error);
        } finally {
            connection.disconnect();
        }
    }

    private String pickAnime1MediaUrl(String jsonText) throws Exception {
        JSONObject object = new JSONObject(jsonText == null ? "{}" : jsonText);
        for (String key : new String[]{"s", "src", "url", "file"}) {
            String candidate = normalizeProtocolRelative(object.optString(key, ""));
            if (isMediaUrl(candidate)) {
                return candidate;
            }
        }
        for (String key : new String[]{"sources", "source", "videos"}) {
            JSONArray array = object.optJSONArray(key);
            if (array == null) {
                continue;
            }
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject source = (JSONObject) item;
                    for (String sourceKey : new String[]{"src", "url", "file"}) {
                        String candidate = normalizeProtocolRelative(source.optString(sourceKey, ""));
                        if (isMediaUrl(candidate)) {
                            return candidate;
                        }
                    }
                } else {
                    String candidate = normalizeProtocolRelative(String.valueOf(item));
                    if (isMediaUrl(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeProtocolRelative(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim().replace("\\/", "/");
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        return value;
    }

    private DashPlan resolveDashPlan(String rawUrl) throws IOException {
        String manifest = readText(rawUrl);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifest)));
            Element root = document.getDocumentElement();
            String periodDuration = firstNonEmpty(attrOrEmpty(root, "mediaPresentationDuration"), attrOrEmpty(firstElement(root, "Period"), "duration"));
            DashPlan best = null;
            for (Element period : childElements(root, "Period")) {
                String periodBase = joinUrl(rawUrl, firstBaseUrl(period));
                for (Element adaptation : childElements(period, "AdaptationSet")) {
                    String mimeType = firstNonEmpty(attrOrEmpty(adaptation, "mimeType"), attrOrEmpty(adaptation, "contentType"));
                    if (!mimeType.isEmpty() && mimeType.toLowerCase(Locale.US).contains("audio")) {
                        continue;
                    }
                    String adaptationBase = joinUrl(periodBase, firstBaseUrl(adaptation));
                    for (Element representation : childElements(adaptation, "Representation")) {
                        DashPlan candidate = parseDashRepresentation(
                                rawUrl,
                                adaptation,
                                representation,
                                joinUrl(adaptationBase, firstBaseUrl(representation)),
                                periodDuration);
                        if (candidate != null && (best == null || candidate.bandwidth > best.bandwidth)) {
                            best = candidate;
                        }
                    }
                }
            }
            if (best == null) {
                throw new IOException("unsupported DASH MPD structure");
            }
            return best;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("DASH manifest parse failed", error);
        }
    }

    private DashPlan parseDashRepresentation(String manifestUrl, Element adaptation, Element representation, String baseUrl, String periodDuration) throws IOException {
        Element template = firstNonNull(firstElement(representation, "SegmentTemplate"), firstElement(adaptation, "SegmentTemplate"));
        if (template != null) {
            return parseDashSegmentTemplate(manifestUrl, representation, template, baseUrl, periodDuration);
        }
        Element list = firstNonNull(firstElement(representation, "SegmentList"), firstElement(adaptation, "SegmentList"));
        if (list != null) {
            return parseDashSegmentList(manifestUrl, representation, list, baseUrl);
        }
        return null;
    }

    private DashPlan parseDashSegmentTemplate(String manifestUrl, Element representation, Element template, String baseUrl, String periodDuration) throws IOException {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        String initPattern = attrOrEmpty(template, "initialization");
        String mediaPattern = attrOrEmpty(template, "media");
        if (initPattern.isEmpty() || mediaPattern.isEmpty()) {
            return null;
        }
        int startNumber = parseInt(attrOrEmpty(template, "startNumber"), 1);
        int timescale = parseInt(attrOrEmpty(template, "timescale"), 1);
        List<DashSegment> segments = new ArrayList<>();
        Element timeline = firstElement(template, "SegmentTimeline");
        if (timeline != null) {
            long number = startNumber;
            long time = -1L;
            for (Element s : childElements(timeline, "S")) {
                long duration = parseLong(attrOrEmpty(s, "d"), 0L);
                if (duration <= 0L) {
                    continue;
                }
                if (!attrOrEmpty(s, "t").isEmpty()) {
                    time = parseLong(attrOrEmpty(s, "t"), time < 0L ? 0L : time);
                } else if (time < 0L) {
                    time = 0L;
                }
                int repeat = parseInt(attrOrEmpty(s, "r"), 0);
                int count = Math.max(1, repeat + 1);
                for (int i = 0; i < count && segments.size() < MAX_DASH_DURATION_SEGMENTS; i++) {
                    String mediaUrl = fillDashTemplate(mediaPattern, representationId, bandwidth, number, time);
                    segments.add(new DashSegment(joinUrl(baseUrl, mediaUrl), null));
                    number++;
                    time += duration;
                }
            }
        } else {
            int duration = parseInt(attrOrEmpty(template, "duration"), 0);
            int segmentCount = estimateDashSegmentCount(periodDuration, duration, timescale);
            for (int i = 0; i < segmentCount; i++) {
                long number = startNumber + i;
                String mediaUrl = fillDashTemplate(mediaPattern, representationId, bandwidth, number, -1L);
                segments.add(new DashSegment(joinUrl(baseUrl, mediaUrl), null));
            }
        }
        String initUrl = joinUrl(baseUrl, fillDashTemplate(initPattern, representationId, bandwidth, startNumber, -1L));
        return new DashPlan(manifestUrl, representationId, bandwidth, initUrl, null, segments);
    }

    private DashPlan parseDashSegmentList(String manifestUrl, Element representation, Element list, String baseUrl) {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        Element init = firstElement(list, "Initialization");
        String initUrl = init == null ? "" : joinUrl(baseUrl, firstNonEmpty(attrOrEmpty(init, "sourceURL"), attrOrEmpty(init, "sourceUrl")));
        String initRange = init == null ? null : firstNonEmpty(attrOrEmpty(init, "range"), attrOrEmpty(init, "mediaRange"));
        List<DashSegment> segments = new ArrayList<>();
        for (Element segment : childElements(list, "SegmentURL")) {
            String media = attrOrEmpty(segment, "media");
            if (media.isEmpty()) {
                continue;
            }
            String range = firstNonEmpty(attrOrEmpty(segment, "mediaRange"), attrOrEmpty(segment, "range"));
            segments.add(new DashSegment(joinUrl(baseUrl, media), range.isEmpty() ? null : range));
        }
        return initUrl.isEmpty() || segments.isEmpty() ? null : new DashPlan(manifestUrl, representationId, bandwidth, initUrl, initRange, segments);
    }

    private int estimateDashSegmentCount(String durationText, int segmentDuration, int timescale) {
        if (segmentDuration <= 0 || timescale <= 0) {
            return 0;
        }
        long totalMillis = parseIsoDurationMillis(durationText);
        if (totalMillis <= 0L) {
            return 0;
        }
        double segmentMillis = (segmentDuration * 1000.0) / timescale;
        int count = (int) Math.ceil(totalMillis / segmentMillis);
        return Math.max(0, Math.min(count, MAX_DASH_DURATION_SEGMENTS));
    }

    private String fillDashTemplate(String pattern, String representationId, int bandwidth, long number, long time) {
        String value = pattern
                .replace("$RepresentationID$", representationId)
                .replace("$Bandwidth$", String.valueOf(bandwidth));
        value = replaceDashNumber(value, "Number", number);
        value = replaceDashNumber(value, "Time", time);
        return value;
    }

    private String replaceDashNumber(String value, String token, long number) {
        if (number < 0L) {
            return value;
        }
        Pattern pattern = Pattern.compile("\\$" + token + "(?:%0(\\d+)d)?\\$");
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String widthText = matcher.group(1);
            String replacement;
            if (widthText == null || widthText.isEmpty()) {
                replacement = String.valueOf(number);
            } else {
                replacement = String.format(Locale.US, "%0" + widthText + "d", number);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private HlsPlaylist resolveHlsPlaylist(String rawUrl, Callback callback) throws IOException {
        String manifestUrl = rawUrl;
        String manifest = readText(manifestUrl);
        List<Variant> variants = parseVariants(manifest, manifestUrl);
        if (!variants.isEmpty()) {
            Variant selected = variants.get(0);
            for (Variant variant : variants) {
                if (variant.bandwidth > selected.bandwidth) {
                    selected = variant;
                }
            }
            callback.onStatus("?鞊? HLS variant bandwidth=" + selected.bandwidth);
            manifestUrl = selected.url;
            manifest = readText(manifestUrl);
        }
        return parseMediaPlaylist(manifest, manifestUrl);
    }

    private List<Variant> parseVariants(String manifest, String manifestUrl) {
        List<Variant> variants = new ArrayList<>();
        String[] lines = manifest.split("\\r?\\n");
        int pendingBandwidth = 0;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pendingBandwidth = parseIntAttribute(line, "BANDWIDTH", 0);
            } else if (pendingBandwidth > 0 && !line.isEmpty() && !line.startsWith("#")) {
                try {
                    variants.add(new Variant(new URL(new URL(manifestUrl), line).toString(), pendingBandwidth));
                } catch (MalformedURLException ignored) {
                    // Ignore malformed variants and let other candidates try.
                }
                pendingBandwidth = 0;
            }
        }
        return variants;
    }

    private HlsPlaylist parseMediaPlaylist(String manifest, String manifestUrl) {
        List<HlsSegment> segments = new ArrayList<>();
        String[] lines = manifest.split("\\r?\\n");
        HlsKey currentKey = null;
        HlsMap currentMap = null;
        int mediaSequence = 0;
        int segmentIndex = 0;
        boolean discontinuity = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = parseTrailingInt(line, 0);
                continue;
            }
            if (line.startsWith("#EXT-X-KEY:")) {
                currentKey = parseKey(line, manifestUrl);
                continue;
            }
            if (line.startsWith("#EXT-X-MAP:")) {
                currentMap = parseMap(line, manifestUrl);
                continue;
            }
            if (line.startsWith("#EXT-X-DISCONTINUITY")) {
                discontinuity = true;
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                segments.add(new HlsSegment(new URL(new URL(manifestUrl), line).toString(), currentKey, currentMap, mediaSequence + segmentIndex, discontinuity));
                segmentIndex++;
                discontinuity = false;
            } catch (MalformedURLException ignored) {
                // Skip malformed HLS entries instead of failing the whole manifest.
            }
        }
        return new HlsPlaylist(manifestUrl, segments);
    }

    private void writeMapIfNeeded(BufferedOutputStream outputStream, HlsSegment segment, Set<String> writtenMaps) throws IOException {
        if (segment.map == null || segment.map.uri == null || segment.map.uri.isEmpty()) {
            return;
        }
        String mapKey = segment.map.uri + "|" + segment.map.byteRange;
        if (writtenMaps.contains(mapKey)) {
            return;
        }
        byte[] mapBytes = readBytes(segment.map.uri, segment.map.byteRange);
        if (mapBytes.length == 0) {
            throw new IOException("empty HLS init map");
        }
        outputStream.write(mapBytes);
        writtenMaps.add(mapKey);
    }

    private byte[] downloadSegmentWithRetry(HlsSegment segment) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= SEGMENT_RETRY_LIMIT; attempt++) {
            try {
                byte[] data = readBytes(segment.url);
                if (data.length == 0) {
                    throw new IOException("empty segment");
                }
                return decryptIfNeeded(segment, data);
            } catch (IOException error) {
                last = error;
                if (cancelled.get()) {
                    throw error;
                }
            }
        }
        throw last == null ? new IOException("segment failed") : last;
    }

    private byte[] decryptIfNeeded(HlsSegment segment, byte[] data) throws IOException {
        HlsKey key = segment.key;
        if (key == null || key.method == null || "NONE".equalsIgnoreCase(key.method)) {
            return data;
        }
        if (!"AES-128".equalsIgnoreCase(key.method)) {
            throw new IOException("????皜? HLS key method: " + key.method);
        }
        if (key.uri == null || key.uri.isEmpty()) {
            throw new IOException("HLS AES-128 key ?餌?? URI");
        }
        if (key.bytes == null) {
            key.bytes = readBytes(key.uri);
        }
        if (key.bytes.length != 16) {
            throw new IOException("HLS AES-128 key ??撞??餈方蝬?" + key.bytes.length);
        }
        byte[] iv = key.iv == null ? ivFromSequence(segment.sequence) : key.iv;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.bytes, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException error) {
            throw new IOException("HLS segment ????剜??", error);
        }
    }

    private byte[] readBytes(String rawUrl) throws IOException {
        return readBytes(rawUrl, null);
    }

    private byte[] readBytes(String rawUrl, String byteRange) throws IOException {
        HttpURLConnection connection = open(rawUrl);
        if (byteRange != null && !byteRange.isEmpty()) {
            String rangeHeader = hlsByteRangeToHttpRange(byteRange);
            if (rangeHeader != null) {
                connection.setRequestProperty("Range", rangeHeader);
            }
        }
        try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
            byte[] buffer = new byte[128 * 1024];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (cancelled.get()) {
                    throw new IOException("cancelled");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private HlsKey parseKey(String line, String manifestUrl) {
        String method = attr(line, "METHOD");
        String uri = attr(line, "URI");
        String ivHex = attr(line, "IV");
        String resolvedUri = null;
        if (uri != null && !uri.isEmpty()) {
            try {
                resolvedUri = new URL(new URL(manifestUrl), uri).toString();
            } catch (MalformedURLException ignored) {
                resolvedUri = uri;
            }
        }
        return new HlsKey(method, resolvedUri, parseIv(ivHex));
    }

    private HlsMap parseMap(String line, String manifestUrl) {
        String uri = attr(line, "URI");
        String byteRange = attr(line, "BYTERANGE");
        String resolvedUri = null;
        if (uri != null && !uri.isEmpty()) {
            try {
                resolvedUri = new URL(new URL(manifestUrl), uri).toString();
            } catch (MalformedURLException ignored) {
                resolvedUri = uri;
            }
        }
        return new HlsMap(resolvedUri, byteRange);
    }

    private String hlsByteRangeToHttpRange(String byteRange) {
        String[] parts = byteRange.split("@", 2);
        try {
            long length = Long.parseLong(parts[0]);
            long start = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
            long end = start + length - 1L;
            return "bytes=" + start + "-" + end;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parseIntAttribute(String line, String name, int fallback) {
        String value = attr(line, name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseTrailingInt(String line, int fallback) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(line.substring(colon + 1).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String attr(String line, String name) {
        Matcher matcher = ATTRIBUTE_PAIR.matcher(line);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                String value = matcher.group(2).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private String attrOrEmpty(Element element, String name) {
        if (element == null || !element.hasAttribute(name)) {
            return "";
        }
        return element.getAttribute(name).trim();
    }

    private List<Element> childElements(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && name.equals(localName(node))) {
                out.add((Element) node);
            }
        }
        return out;
    }

    private Element firstElement(Element parent, String name) {
        List<Element> children = childElements(parent, name);
        return children.isEmpty() ? null : children.get(0);
    }

    private Element firstNonNull(Element first, Element second) {
        return first != null ? first : second;
    }

    private String firstBaseUrl(Element parent) {
        Element base = firstElement(parent, "BaseURL");
        return base == null ? "" : base.getTextContent().trim();
    }

    private String localName(Node node) {
        String local = node.getLocalName();
        return local == null ? node.getNodeName() : local;
    }

    private String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? (second == null ? "" : second.trim()) : first.trim();
    }

    private String joinUrl(String baseUrl, String value) {
        if (value == null || value.trim().isEmpty()) {
            return baseUrl;
        }
        try {
            return new URL(new URL(baseUrl), value.trim().replace("\\/", "/")).toString();
        } catch (MalformedURLException ignored) {
            return value.trim();
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseIsoDurationMillis(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }
        Matcher matcher = Pattern.compile("P(?:\\d+D)?T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?", Pattern.CASE_INSENSITIVE).matcher(raw.trim());
        if (!matcher.matches()) {
            return 0L;
        }
        double hours = matcher.group(1) == null ? 0.0 : Double.parseDouble(matcher.group(1));
        double minutes = matcher.group(2) == null ? 0.0 : Double.parseDouble(matcher.group(2));
        double seconds = matcher.group(3) == null ? 0.0 : Double.parseDouble(matcher.group(3));
        return (long) ((hours * 3600.0 + minutes * 60.0 + seconds) * 1000.0);
    }

    private byte[] parseIv(String ivHex) {
        if (ivHex == null || ivHex.isEmpty()) {
            return null;
        }
        String hex = ivHex.startsWith("0x") || ivHex.startsWith("0X") ? ivHex.substring(2) : ivHex;
        if (hex.length() > 32) {
            hex = hex.substring(hex.length() - 32);
        }
        while (hex.length() < 32) {
            hex = "0" + hex;
        }
        byte[] out = new byte[16];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private byte[] ivFromSequence(int sequence) {
        byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 0);
        iv[12] = (byte) ((sequence >> 24) & 0xff);
        iv[13] = (byte) ((sequence >> 16) & 0xff);
        iv[14] = (byte) ((sequence >> 8) & 0xff);
        iv[15] = (byte) (sequence & 0xff);
        return iv;
    }

    private List<String> extractMediaCandidates(String pageText, String pageUrl) {
        List<String> candidates = new ArrayList<>();
        addRegexCandidates(candidates, ABSOLUTE_MEDIA_URL.matcher(pageText), pageUrl, false);
        addRegexCandidates(candidates, ATTR_MEDIA_URL.matcher(pageText), pageUrl, true);
        return candidates;
    }

    private void addRegexCandidates(List<String> out, Matcher matcher, String pageUrl, boolean firstGroup) {
        while (matcher.find()) {
            String value = firstGroup ? matcher.group(1) : matcher.group();
            try {
                String resolved = new URL(new URL(pageUrl), value.replace("\\/", "/")).toString();
                if (!out.contains(resolved)) {
                    out.add(resolved);
                }
            } catch (MalformedURLException ignored) {
                // Ignore bad candidates; sites often include escaped player fragments.
            }
        }
    }

    private boolean isMediaUrl(String rawUrl) {
        String lowered = rawUrl.toLowerCase(Locale.US);
        return lowered.contains(".m3u8") || lowered.contains(".mp4") || lowered.contains(".mpd")
                || lowered.contains(".webm") || lowered.contains(".m4v");
    }

    private boolean isHlsUrl(String rawUrl) {
        return rawUrl.toLowerCase(Locale.US).contains(".m3u8");
    }

    private boolean isDashUrl(String rawUrl) {
        return rawUrl.toLowerCase(Locale.US).contains(".mpd");
    }

    private File outputFile(String fileName) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }

    private void replace(File part, File output) throws IOException {
        if (output.exists() && !output.delete()) {
            throw new IOException("Could not replace output file: " + output.getName());
        }
        if (!part.renameTo(output)) {
            throw new IOException("Could not finalize output file: " + output.getName());
        }
    }

    private void ensureHttpPartialMatches(File state, File part, String rawUrl) {
        if (!part.exists()) {
            return;
        }
        Properties props = new Properties();
        String savedUrl = "";
        if (state.exists()) {
            try (InputStream input = new java.io.FileInputStream(state)) {
                props.load(input);
                savedUrl = props.getProperty("url", "");
            } catch (IOException ignored) {
                savedUrl = "";
            }
        }
        if (!rawUrl.equals(savedUrl)) {
            part.delete();
            state.delete();
        }
    }

    private void saveHttpState(File state, String rawUrl) throws IOException {
        Properties props = new Properties();
        props.setProperty("url", rawUrl);
        try (FileOutputStream output = new FileOutputStream(state, false)) {
            props.store(output, "HTTP download checkpoint");
        }
    }

    private int loadHlsCheckpoint(File checkpoint, HlsPlaylist playlist, File part) {
        if (!checkpoint.exists() || !part.exists() || part.length() <= 0L) {
            return 0;
        }
        Properties props = new Properties();
        try (InputStream input = new java.io.FileInputStream(checkpoint)) {
            props.load(input);
            String manifestUrl = props.getProperty("manifestUrl", "");
            int completed = Integer.parseInt(props.getProperty("completed", "0"));
            int segmentCount = Integer.parseInt(props.getProperty("segmentCount", "0"));
            if (playlist.manifestUrl.equals(manifestUrl) && segmentCount == playlist.segments.size() && completed > 0 && completed < playlist.segments.size()) {
                return completed;
            }
        } catch (Exception ignored) {
            // Invalid checkpoint: restart safely.
        }
        checkpoint.delete();
        return 0;
    }

    private void saveHlsCheckpoint(File checkpoint, HlsPlaylist playlist, int completed) throws IOException {
        Properties props = new Properties();
        props.setProperty("manifestUrl", playlist.manifestUrl);
        props.setProperty("segmentCount", String.valueOf(playlist.segments.size()));
        props.setProperty("completed", String.valueOf(completed));
        try (FileOutputStream output = new FileOutputStream(checkpoint, false)) {
            props.store(output, "HLS download checkpoint");
        }
    }

    private int loadDashCheckpoint(File checkpoint, DashPlan plan, File part) {
        if (!checkpoint.exists() || !part.exists() || part.length() <= 0L) {
            return 0;
        }
        Properties props = new Properties();
        try (InputStream input = new java.io.FileInputStream(checkpoint)) {
            props.load(input);
            String manifestUrl = props.getProperty("manifestUrl", "");
            String representationId = props.getProperty("representationId", "");
            int completed = Integer.parseInt(props.getProperty("completed", "0"));
            int segmentCount = Integer.parseInt(props.getProperty("segmentCount", "0"));
            if (plan.manifestUrl.equals(manifestUrl)
                    && plan.representationId.equals(representationId)
                    && segmentCount == plan.segments.size()
                    && completed > 0
                    && completed < plan.segments.size()) {
                return completed;
            }
        } catch (Exception ignored) {
            // Invalid checkpoint: restart safely.
        }
        checkpoint.delete();
        return 0;
    }

    private void saveDashCheckpoint(File checkpoint, DashPlan plan, int completed) throws IOException {
        Properties props = new Properties();
        props.setProperty("manifestUrl", plan.manifestUrl);
        props.setProperty("representationId", plan.representationId);
        props.setProperty("segmentCount", String.valueOf(plan.segments.size()));
        props.setProperty("completed", String.valueOf(completed));
        try (FileOutputStream output = new FileOutputStream(checkpoint, false)) {
            props.store(output, "DASH download checkpoint");
        }
    }

    private static final class HlsPlaylist {
        final String manifestUrl;
        final List<HlsSegment> segments;

        HlsPlaylist(String manifestUrl, List<HlsSegment> segments) {
            this.manifestUrl = manifestUrl;
            this.segments = segments;
        }
    }
}
