package tw.wajay.aitestdownloader;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        final String refererUrl;

        ResolvedTarget(String primaryUrl, String sourceSite, List<String> fallbackUrls, String refererUrl) {
            this.primaryUrl = primaryUrl;
            this.sourceSite = sourceSite;
            this.fallbackUrls = fallbackUrls;
            this.refererUrl = refererUrl;
        }
    }

    private static final class HlsSegment {
        final String url;
        final String byteRange;
        final HlsKey key;
        final HlsMap map;
        final int sequence;
        final boolean discontinuity;

        HlsSegment(String url, String byteRange, HlsKey key, HlsMap map, int sequence, boolean discontinuity) {
            this.url = url;
            this.byteRange = byteRange;
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
        final int width;
        final int height;
        final String name;
        final String codecs;

        Variant(String url, int bandwidth, int width, int height, String name, String codecs) {
            this.url = url;
            this.bandwidth = bandwidth;
            this.width = width;
            this.height = height;
            this.name = name == null ? "" : name;
            this.codecs = codecs == null ? "" : codecs;
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
    private final CookieManager cookieManager;
    private final Map<String, String> requestHeaders = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    DownloadEngine(Context context) {
        this.context = context.getApplicationContext();
        cookieManager = new CookieManager(new PersistentCookieStore(this.context), CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
    }

    private static final class PersistentCookieStore implements CookieStore {
        private static final String PREFS_NAME = "download_http_cookies";
        private static final String KEY_COOKIES = "cookies";

        private final SharedPreferences prefs;
        private final List<StoredCookie> cookies = new ArrayList<>();

        PersistentCookieStore(Context context) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            load();
        }

        @Override
        public synchronized void add(URI uri, HttpCookie cookie) {
            if (uri == null || cookie == null || cookie.getName() == null || cookie.getName().isEmpty()) {
                return;
            }
            StoredCookie stored = StoredCookie.from(uri, cookie);
            remove(stored.uri, stored.cookie);
            if (stored.isExpired()) {
                save();
                return;
            }
            cookies.add(stored);
            save();
        }

        @Override
        public synchronized List<HttpCookie> get(URI uri) {
            boolean changed = removeExpiredLocked();
            List<HttpCookie> matches = new ArrayList<>();
            for (StoredCookie stored : cookies) {
                if (stored.matches(uri)) {
                    matches.add(stored.cookie);
                }
            }
            if (changed) {
                save();
            }
            return matches;
        }

        @Override
        public synchronized List<HttpCookie> getCookies() {
            boolean changed = removeExpiredLocked();
            if (changed) {
                save();
            }
            List<HttpCookie> result = new ArrayList<>();
            for (StoredCookie stored : cookies) {
                result.add(stored.cookie);
            }
            return result;
        }

        @Override
        public synchronized List<URI> getURIs() {
            boolean changed = removeExpiredLocked();
            if (changed) {
                save();
            }
            List<URI> uris = new ArrayList<>();
            for (StoredCookie stored : cookies) {
                if (!uris.contains(stored.uri)) {
                    uris.add(stored.uri);
                }
            }
            return uris;
        }

        @Override
        public synchronized boolean remove(URI uri, HttpCookie cookie) {
            boolean removed = false;
            for (int i = cookies.size() - 1; i >= 0; i--) {
                if (cookies.get(i).sameCookie(cookie)) {
                    cookies.remove(i);
                    removed = true;
                }
            }
            if (removed) {
                save();
            }
            return removed;
        }

        @Override
        public synchronized boolean removeAll() {
            boolean hadCookies = !cookies.isEmpty();
            cookies.clear();
            save();
            return hadCookies;
        }

        private void load() {
            cookies.clear();
            String raw = prefs.getString(KEY_COOKIES, "[]");
            long now = System.currentTimeMillis();
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    StoredCookie stored = StoredCookie.fromJson(item, now);
                    if (stored != null && !stored.isExpired()) {
                        cookies.add(stored);
                    }
                }
            } catch (Exception ignored) {
                cookies.clear();
            }
        }

        private void save() {
            JSONArray array = new JSONArray();
            for (StoredCookie stored : cookies) {
                if (!stored.isExpired()) {
                    array.put(stored.toJson());
                }
            }
            prefs.edit().putString(KEY_COOKIES, array.toString()).apply();
        }

        private boolean removeExpiredLocked() {
            boolean changed = false;
            for (int i = cookies.size() - 1; i >= 0; i--) {
                if (cookies.get(i).isExpired()) {
                    cookies.remove(i);
                    changed = true;
                }
            }
            return changed;
        }
    }

    private static final class StoredCookie {
        final URI uri;
        final HttpCookie cookie;
        final long expiresAtMillis;

        StoredCookie(URI uri, HttpCookie cookie, long expiresAtMillis) {
            this.uri = uri;
            this.cookie = cookie;
            this.expiresAtMillis = expiresAtMillis;
        }

        static StoredCookie from(URI uri, HttpCookie original) {
            HttpCookie cookie = new HttpCookie(original.getName(), original.getValue());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            String domain = firstNonEmptyCookieValue(original.getDomain(), host).toLowerCase(Locale.US);
            String path = firstNonEmptyCookieValue(original.getPath(), defaultCookiePath(uri));
            cookie.setDomain(domain);
            cookie.setPath(path);
            cookie.setMaxAge(original.getMaxAge());
            cookie.setSecure(original.getSecure());
            cookie.setHttpOnly(original.isHttpOnly());
            cookie.setVersion(original.getVersion());
            long expiresAt = original.getMaxAge() > 0L ? System.currentTimeMillis() + original.getMaxAge() * 1000L : original.getMaxAge();
            return new StoredCookie(cookieUri(uri, domain), cookie, expiresAt);
        }

        static StoredCookie fromJson(JSONObject item, long now) {
            String name = item.optString("name", "");
            String value = item.optString("value", "");
            String domain = item.optString("domain", "");
            if (name.isEmpty() || domain.isEmpty()) {
                return null;
            }
            long expiresAt = item.optLong("expiresAt", -1L);
            if (expiresAt == 0L || (expiresAt > 0L && expiresAt <= now)) {
                return null;
            }
            HttpCookie cookie = new HttpCookie(name, value);
            cookie.setDomain(domain);
            cookie.setPath(item.optString("path", "/"));
            cookie.setSecure(item.optBoolean("secure", false));
            cookie.setHttpOnly(item.optBoolean("httpOnly", false));
            cookie.setVersion(item.optInt("version", 0));
            if (expiresAt > 0L) {
                cookie.setMaxAge(Math.max(1L, (expiresAt - now) / 1000L));
            } else {
                cookie.setMaxAge(-1L);
            }
            try {
                URI uri = new URI(item.optString("uri", "https://" + stripLeadingDot(domain) + "/"));
                return new StoredCookie(uri, cookie, expiresAt);
            } catch (Exception ignored) {
                return null;
            }
        }

        JSONObject toJson() {
            JSONObject item = new JSONObject();
            try {
                item.put("uri", uri.toString());
                item.put("name", cookie.getName());
                item.put("value", cookie.getValue());
                item.put("domain", cookie.getDomain());
                item.put("path", cookie.getPath());
                item.put("secure", cookie.getSecure());
                item.put("httpOnly", cookie.isHttpOnly());
                item.put("version", cookie.getVersion());
                item.put("expiresAt", expiresAtMillis);
            } catch (Exception ignored) {
                // Ignore malformed cookie state and keep saving the remaining cookies.
            }
            return item;
        }

        boolean matches(URI requestUri) {
            if (requestUri == null || isExpired()) {
                return false;
            }
            String host = requestUri.getHost() == null ? "" : requestUri.getHost().toLowerCase(Locale.US);
            String domain = cookie.getDomain() == null ? "" : cookie.getDomain().toLowerCase(Locale.US);
            String path = cookie.getPath() == null ? "/" : cookie.getPath();
            String requestPath = requestUri.getPath() == null || requestUri.getPath().isEmpty() ? "/" : requestUri.getPath();
            boolean domainMatch = host.equals(stripLeadingDot(domain)) || HttpCookie.domainMatches(domain, host);
            boolean pathMatch = requestPath.startsWith(path);
            boolean secureMatch = !cookie.getSecure() || "https".equalsIgnoreCase(requestUri.getScheme());
            return domainMatch && pathMatch && secureMatch;
        }

        boolean sameCookie(HttpCookie other) {
            if (other == null) {
                return false;
            }
            String domain = cookie.getDomain() == null ? "" : cookie.getDomain();
            String otherDomain = other.getDomain() == null ? "" : other.getDomain();
            String path = cookie.getPath() == null ? "" : cookie.getPath();
            String otherPath = other.getPath() == null ? "" : other.getPath();
            return cookie.getName().equalsIgnoreCase(other.getName())
                    && domain.equalsIgnoreCase(otherDomain)
                    && path.equals(otherPath);
        }

        boolean isExpired() {
            return expiresAtMillis == 0L || (expiresAtMillis > 0L && expiresAtMillis <= System.currentTimeMillis());
        }

        private static URI cookieUri(URI sourceUri, String domain) {
            try {
                String scheme = sourceUri.getScheme() == null ? "https" : sourceUri.getScheme();
                return new URI(scheme + "://" + stripLeadingDot(domain) + "/");
            } catch (Exception ignored) {
                return sourceUri;
            }
        }

        private static String defaultCookiePath(URI uri) {
            String path = uri.getPath();
            if (path == null || path.isEmpty() || !path.contains("/")) {
                return "/";
            }
            int lastSlash = path.lastIndexOf('/');
            return lastSlash <= 0 ? "/" : path.substring(0, lastSlash + 1);
        }

        private static String firstNonEmptyCookieValue(String first, String second) {
            return first == null || first.trim().isEmpty() ? second : first.trim();
        }

        private static String stripLeadingDot(String value) {
            return value == null || value.isEmpty() ? "" : value.replaceFirst("^\\.", "");
        }
    }

    void start(String rawUrl, String requestedName, Callback callback) {
        start(rawUrl, requestedName, "", "", callback);
    }

    void start(String rawUrl, String requestedName, String providedReferer, String cookieHeader, Callback callback) {
        start(rawUrl, requestedName, providedReferer, cookieHeader, "{}", callback);
    }

    void start(String rawUrl, String requestedName, String providedReferer, String cookieHeader, String headersJson, Callback callback) {
        cancelled.set(false);
        executor.execute(() -> {
            try {
                importCookieHeader(rawUrl, cookieHeader);
                importRequestHeaders(headersJson);
                Uri uri = Uri.parse(rawUrl);
                String fileName = FileNames.choose(uri, requestedName);
                String targetUrl = rawUrl;
                String sourceSite = MediaResolver.sourceSite(rawUrl);
                List<String> fallbackUrls = new ArrayList<>();
                String refererUrl = firstNonEmpty(providedReferer, "");
                if (!isMediaUrl(rawUrl)) {
                    ResolvedTarget resolvedTarget = resolvePageToMedia(rawUrl, callback);
                    targetUrl = resolvedTarget.primaryUrl;
                    sourceSite = resolvedTarget.sourceSite;
                    fallbackUrls = resolvedTarget.fallbackUrls;
                    refererUrl = firstNonEmpty(refererUrl, resolvedTarget.refererUrl);
                    if (requestedName == null || requestedName.trim().isEmpty()) {
                        fileName = FileNames.choose(Uri.parse(targetUrl), "");
                    }
                }
                downloadWithFallbacks(targetUrl, fallbackUrls, sourceSite, fileName, refererUrl, callback);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    private void importCookieHeader(String rawUrl, String cookieHeader) {
        String header = cookieHeader == null ? "" : cookieHeader.trim();
        if (header.isEmpty()) {
            return;
        }
        try {
            URI uri = new URI(rawUrl);
            String[] parts = header.split(";");
            for (String part : parts) {
                String value = part.trim();
                int equals = value.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                HttpCookie cookie = new HttpCookie(value.substring(0, equals).trim(), value.substring(equals + 1).trim());
                cookie.setPath("/");
                cookieManager.getCookieStore().add(uri, cookie);
            }
        } catch (Exception ignored) {
            // Invalid pasted cookie text should not block normal URL downloads.
        }
    }

    private void importRequestHeaders(String headersJson) {
        synchronized (requestHeaders) {
            requestHeaders.clear();
            String raw = headersJson == null || headersJson.trim().isEmpty() ? "{}" : headersJson.trim();
            try {
                JSONObject object = new JSONObject(raw);
                java.util.Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    String value = object.optString(name, "").trim();
                    if (isAllowedRequestHeader(name) && !value.isEmpty()) {
                        requestHeaders.put(name, value);
                    }
                }
            } catch (Exception ignored) {
                requestHeaders.clear();
            }
        }
    }

    private boolean isAllowedRequestHeader(String name) {
        return "User-Agent".equals(name)
                || "Accept".equals(name)
                || "Accept-Language".equals(name)
                || "Authorization".equals(name)
                || "X-Requested-With".equals(name)
                || "Sec-Fetch-Site".equals(name)
                || "Sec-Fetch-Mode".equals(name)
                || "Sec-Fetch-Dest".equals(name);
    }
    void cancel() {
        cancelled.set(true);
    }

    private ResolvedTarget resolvePageToMedia(String rawUrl, Callback callback) throws IOException {
        String currentUrl = rawUrl;
        for (int depth = 0; depth < PAGE_RESOLVE_DEPTH_LIMIT; depth++) {
            String pageText = readText(currentUrl);
            if (looksLikeHlsManifest(pageText)) {
                callback.onStatus(context.getString(R.string.engine_hls_manifest_detected));
                callback.onResolved(MediaResolver.sourceSite(currentUrl), currentUrl, Collections.singletonList(currentUrl), Collections.singletonList("HLS manifest"));
                return new ResolvedTarget(currentUrl, MediaResolver.sourceSite(currentUrl), new ArrayList<>(), "");
            }

            String anime1Url = resolveAnime1ApiMedia(currentUrl, pageText);
            if (anime1Url != null) {
                callback.onStatus(context.getString(R.string.engine_resolved_anime1));
                callback.onResolved("anime1", anime1Url, Collections.singletonList(anime1Url), Collections.singletonList("Anime1 API"));
                return new ResolvedTarget(anime1Url, "anime1", new ArrayList<>(), currentUrl);
            }
            MediaResolver.Result resolved = MediaResolver.resolve(pageText, currentUrl);
            callback.onStatus(context.getString(R.string.engine_resolving_page_candidate, resolved.sourceSite, depth));
            if (resolved.primaryUrl == null) {
                throw new IOException(context.getString(R.string.error_no_media_candidate));
            }
            callback.onResolved(resolved.sourceSite, resolved.primaryUrl, resolved.candidates, resolved.candidateLabels);
            if (resolved.primaryIsMedia) {
                return new ResolvedTarget(resolved.primaryUrl, resolved.sourceSite, mediaFallbacks(resolved.candidates, resolved.primaryUrl), currentUrl);
            }
            currentUrl = resolved.primaryUrl;
        }
        throw new IOException(context.getString(R.string.error_resolve_depth_exceeded));
    }

    private void downloadWithFallbacks(String primaryUrl, List<String> fallbackUrls, String sourceSite, String fileName, String refererUrl, Callback callback) throws IOException {
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
                    callback.onStatus(context.getString(R.string.engine_retrying_alternate_source, i + 1, targets.size()));
                    callback.onResolved(sourceSite, targetUrl, targets.subList(i, targets.size()), Collections.emptyList());
                }
                if (isDashUrl(targetUrl)) {
                    callback.onStatus(context.getString(R.string.engine_resolving_dash));
                    downloadDash(targetUrl, FileNames.replaceExtension(fileName, ".mp4"), refererUrl, callback);
                } else if (isHlsUrl(targetUrl) || (targetUrl.equals(primaryUrl) && !isMediaUrl(targetUrl))) {
                    callback.onStatus(context.getString(R.string.engine_resolving_hls));
                    downloadHls(targetUrl, FileNames.replaceExtension(fileName, ".ts"), refererUrl, callback);
                } else {
                    callback.onStatus(context.getString(R.string.engine_starting_http));
                    downloadHttp(targetUrl, fileName, refererUrl, callback);
                }
                return;
            } catch (IOException error) {
                lastError = error;
                if (cancelled.get() || i == targets.size() - 1) {
                    throw error;
                }
                callback.onStatus(context.getString(R.string.engine_source_failed_switching, shortMessage(error)));
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

    private void downloadHttp(String rawUrl, String fileName, String refererUrl, Callback callback) throws IOException {
        File output = outputFile(fileName);
        File part = new File(output.getParentFile(), output.getName() + ".part");
        File state = new File(output.getParentFile(), output.getName() + ".httpstate");
        ensureHttpPartialMatches(state, part, rawUrl);
        saveHttpState(state, rawUrl);
        long existing = part.exists() ? part.length() : 0L;

        HttpURLConnection connection = open(rawUrl, refererUrl);
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
            callback.onStatus(context.getString(R.string.engine_cancelled_keep_partial));
            return;
        }
        replace(part, output);
        if (state.exists()) {
            state.delete();
        }
        callback.onDone(output);
    }

    private void downloadHls(String rawUrl, String fileName, String refererUrl, Callback callback) throws IOException {
        HlsPlaylist playlist = resolveHlsPlaylist(rawUrl, refererUrl, callback);
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
                    callback.onStatus(context.getString(R.string.engine_cancelled_keep_hls_partial));
                    return;
                }
                callback.onStatus(context.getString(R.string.engine_downloading_hls_segment, index + 1, segments.size()));
                HlsSegment segment = segments.get(index);
                if (segment.discontinuity) {
                    outputStream.flush();
                }
                if (allowMapWrite) {
                    writeMapIfNeeded(outputStream, segment, writtenMaps, effectiveReferer(refererUrl, playlist.manifestUrl));
                }
                byte[] data = downloadSegmentWithRetry(segment, effectiveReferer(refererUrl, playlist.manifestUrl));
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

    private void downloadDash(String rawUrl, String fileName, String refererUrl, Callback callback) throws IOException {
        DashPlan plan = resolveDashPlan(rawUrl, refererUrl);
        if (plan.initUrl == null || plan.initUrl.isEmpty() || plan.segments.isEmpty()) {
            throw new IOException("DASH manifest did not contain a supported SegmentTemplate, SegmentList, or SegmentBase");
        }

        File output = outputFile(fileName);
        File part = new File(output.getParentFile(), output.getName() + ".part");
        File checkpoint = new File(output.getParentFile(), output.getName() + ".dashstate");
        int startIndex = loadDashCheckpoint(checkpoint, plan, part);
        boolean append = startIndex > 0 && part.exists();

        callback.onStatus(context.getString(R.string.engine_dash_representation, dashPlanDescription(plan)));
        try (FileOutputStream fos = new FileOutputStream(part, append);
             BufferedOutputStream outputStream = new BufferedOutputStream(fos)) {
            if (startIndex == 0) {
                byte[] init = readBytes(plan.initUrl, plan.initRange, effectiveReferer(refererUrl, plan.manifestUrl));
                if (init.length == 0) {
                    throw new IOException("empty DASH init segment");
                }
                outputStream.write(init);
                outputStream.flush();
            }
            for (int index = startIndex; index < plan.segments.size(); index++) {
                if (cancelled.get()) {
                    callback.onStatus(context.getString(R.string.engine_cancelled_keep_dash_partial));
                    return;
                }
                callback.onStatus(context.getString(R.string.engine_downloading_dash_segment, index + 1, plan.segments.size()));
                DashSegment segment = plan.segments.get(index);
                byte[] data = readBytes(segment.url, segment.byteRange, effectiveReferer(refererUrl, plan.manifestUrl));
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
        return open(rawUrl, "");
    }

    private HttpURLConnection open(String rawUrl, String refererUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36 AI-Test-Downloader/0.31");
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        synchronized (requestHeaders) {
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
        }
        String referer = cleanReferer(rawUrl, refererUrl);
        if (!referer.isEmpty()) {
            connection.setRequestProperty("Referer", referer);
            String origin = originFromUrl(referer);
            if (!origin.isEmpty()) {
                connection.setRequestProperty("Origin", origin);
            }
        }
        return connection;
    }

    private String readText(String rawUrl) throws IOException {
        return readText(rawUrl, "");
    }

    private String readText(String rawUrl, String refererUrl) throws IOException {
        HttpURLConnection connection = open(rawUrl, refererUrl);
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

    private String effectiveReferer(String preferred, String fallback) {
        String value = preferred == null ? "" : preferred.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return fallback == null ? "" : fallback.trim();
    }

    private String cleanReferer(String rawUrl, String refererUrl) {
        String referer = refererUrl == null ? "" : refererUrl.trim();
        if (referer.isEmpty() || referer.equals(rawUrl)) {
            return "";
        }
        return referer;
    }

    private String originFromUrl(String rawUrl) {
        try {
            URL url = new URL(rawUrl);
            StringBuilder origin = new StringBuilder();
            origin.append(url.getProtocol()).append("://").append(url.getHost());
            int port = url.getPort();
            if (port > 0 && port != url.getDefaultPort()) {
                origin.append(':').append(port);
            }
            return origin.toString();
        } catch (MalformedURLException ignored) {
            return "";
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
        final int width;
        final int height;
        final String codecs;
        final String initUrl;
        final String initRange;
        final List<DashSegment> segments;

        DashPlan(String manifestUrl, String representationId, int bandwidth, int width, int height, String codecs, String initUrl, String initRange, List<DashSegment> segments) {
            this.manifestUrl = manifestUrl;
            this.representationId = representationId;
            this.bandwidth = bandwidth;
            this.width = width;
            this.height = height;
            this.codecs = codecs == null ? "" : codecs;
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
        HttpURLConnection connection = open("https://v.anime1.me/api", pageUrl);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
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

    private DashPlan resolveDashPlan(String rawUrl, String refererUrl) throws IOException {
        String manifest = readText(rawUrl, refererUrl);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifest)));
            Element root = document.getDocumentElement();
            String periodDuration = firstNonEmpty(attrOrEmpty(root, "mediaPresentationDuration"), attrOrEmpty(firstElement(root, "Period"), "duration"));
            String mpdBase = joinUrl(rawUrl, firstBaseUrl(root));
            DashPlan best = null;
            for (Element period : childElements(root, "Period")) {
                String periodBase = joinUrl(mpdBase, firstBaseUrl(period));
                for (Element adaptation : childElements(period, "AdaptationSet")) {
                    if (isDashAudioElement(adaptation)) {
                        continue;
                    }
                    String adaptationBase = joinUrl(periodBase, firstBaseUrl(adaptation));
                    for (Element representation : childElements(adaptation, "Representation")) {
                        if (isDashAudioElement(representation)) {
                            continue;
                        }
                        DashPlan candidate = parseDashRepresentation(
                                rawUrl,
                                adaptation,
                                representation,
                                joinUrl(adaptationBase, firstBaseUrl(representation)),
                                periodDuration);
                        if (candidate != null && (best == null || compareDashPlan(candidate, best) > 0)) {
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
            return parseDashSegmentTemplate(manifestUrl, adaptation, representation, template, baseUrl, periodDuration);
        }
        Element list = firstNonNull(firstElement(representation, "SegmentList"), firstElement(adaptation, "SegmentList"));
        if (list != null) {
            return parseDashSegmentList(manifestUrl, adaptation, representation, list, baseUrl);
        }
        Element base = firstNonNull(firstElement(representation, "SegmentBase"), firstElement(adaptation, "SegmentBase"));
        if (base != null) {
            return parseDashSegmentBase(manifestUrl, adaptation, representation, base, baseUrl);
        }
        return null;
    }

    private boolean isDashAudioElement(Element element) {
        String mediaType = firstNonEmpty(attrOrEmpty(element, "mimeType"), attrOrEmpty(element, "contentType")).toLowerCase(Locale.US);
        String codecs = attrOrEmpty(element, "codecs").toLowerCase(Locale.US);
        return mediaType.contains("audio")
                || codecs.startsWith("mp4a")
                || codecs.startsWith("ac-3")
                || codecs.startsWith("ec-3")
                || codecs.startsWith("opus");
    }

    private DashPlan parseDashSegmentTemplate(String manifestUrl, Element adaptation, Element representation, Element template, String baseUrl, String periodDuration) throws IOException {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        int width = parseInt(firstNonEmpty(attrOrEmpty(representation, "width"), attrOrEmpty(adaptation, "width")), 0);
        int height = parseInt(firstNonEmpty(attrOrEmpty(representation, "height"), attrOrEmpty(adaptation, "height")), 0);
        String codecs = firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
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
        return new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, initUrl, null, segments);
    }

    private DashPlan parseDashSegmentList(String manifestUrl, Element adaptation, Element representation, Element list, String baseUrl) {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        int width = parseInt(firstNonEmpty(attrOrEmpty(representation, "width"), attrOrEmpty(adaptation, "width")), 0);
        int height = parseInt(firstNonEmpty(attrOrEmpty(representation, "height"), attrOrEmpty(adaptation, "height")), 0);
        String codecs = firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
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
        return initUrl.isEmpty() || segments.isEmpty() ? null : new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, initUrl, initRange, segments);
    }

    private DashPlan parseDashSegmentBase(String manifestUrl, Element adaptation, Element representation, Element segmentBase, String baseUrl) {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        int width = parseInt(firstNonEmpty(attrOrEmpty(representation, "width"), attrOrEmpty(adaptation, "width")), 0);
        int height = parseInt(firstNonEmpty(attrOrEmpty(representation, "height"), attrOrEmpty(adaptation, "height")), 0);
        String codecs = firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
        String mediaUrl = joinUrl(baseUrl, "");
        if (mediaUrl.isEmpty()) {
            return null;
        }
        Element init = firstElement(segmentBase, "Initialization");
        String initUrl = init == null ? mediaUrl : joinUrl(baseUrl, firstNonEmpty(attrOrEmpty(init, "sourceURL"), attrOrEmpty(init, "sourceUrl")));
        String initRange = init == null ? "" : firstNonEmpty(attrOrEmpty(init, "range"), attrOrEmpty(init, "mediaRange"));
        long initEnd = byteRangeEnd(initRange);
        if (initUrl.isEmpty() || initRange.isEmpty() || initEnd < 0L) {
            return null;
        }
        List<DashSegment> segments = new ArrayList<>();
        if (initUrl.equals(mediaUrl)) {
            segments.add(new DashSegment(mediaUrl, "bytes=" + (initEnd + 1L) + "-"));
        } else {
            segments.add(new DashSegment(mediaUrl, null));
        }
        return new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, initUrl, initRange, segments);
    }

    private int compareDashPlan(DashPlan left, DashPlan right) {
        int heightCompare = Integer.compare(left == null ? 0 : left.height, right == null ? 0 : right.height);
        if (heightCompare != 0) {
            return heightCompare;
        }
        int bandwidthCompare = Integer.compare(left == null ? 0 : left.bandwidth, right == null ? 0 : right.bandwidth);
        if (bandwidthCompare != 0) {
            return bandwidthCompare;
        }
        return Integer.compare(left == null ? 0 : left.width, right == null ? 0 : right.width);
    }

    private String dashPlanDescription(DashPlan plan) {
        if (plan == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (plan.representationId != null && !plan.representationId.trim().isEmpty()) {
            parts.add(plan.representationId);
        }
        if (plan.width > 0 && plan.height > 0) {
            parts.add(plan.width + "x" + plan.height);
        } else if (plan.height > 0) {
            parts.add(plan.height + "p");
        }
        if (plan.bandwidth > 0) {
            parts.add(plan.bandwidth + " bps");
        }
        if (!plan.codecs.isEmpty()) {
            parts.add(plan.codecs);
        }
        return parts.isEmpty() ? plan.manifestUrl : joinParts(parts);
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

    private HlsPlaylist resolveHlsPlaylist(String rawUrl, String refererUrl, Callback callback) throws IOException {
        String manifestUrl = rawUrl;
        String manifest = readText(manifestUrl, refererUrl);
        List<Variant> variants = parseVariants(manifest, manifestUrl);
        if (!variants.isEmpty()) {
            Variant selected = variants.get(0);
            for (Variant variant : variants) {
                if (compareVariant(variant, selected) > 0) {
                    selected = variant;
                }
            }
            callback.onStatus(context.getString(R.string.engine_hls_variant, selectedVariantDescription(selected)));
            manifestUrl = selected.url;
            manifest = readText(manifestUrl, effectiveReferer(refererUrl, rawUrl));
        }
        return parseMediaPlaylist(manifest, manifestUrl);
    }

    private List<Variant> parseVariants(String manifest, String manifestUrl) {
        List<Variant> variants = new ArrayList<>();
        String[] lines = manifest.split("\\r?\\n");
        int pendingBandwidth = 0;
        int pendingWidth = 0;
        int pendingHeight = 0;
        String pendingName = "";
        String pendingCodecs = "";
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pendingBandwidth = parseIntAttribute(line, "BANDWIDTH", 0);
                int[] resolution = parseResolutionAttribute(line);
                pendingWidth = resolution[0];
                pendingHeight = resolution[1];
                pendingName = attrOrEmpty(line, "NAME");
                pendingCodecs = attrOrEmpty(line, "CODECS");
            } else if (pendingBandwidth > 0 && !line.isEmpty() && !line.startsWith("#")) {
                try {
                    variants.add(new Variant(new URL(new URL(manifestUrl), line).toString(), pendingBandwidth, pendingWidth, pendingHeight, pendingName, pendingCodecs));
                } catch (MalformedURLException ignored) {
                    // Ignore malformed variants and let other candidates try.
                }
                pendingBandwidth = 0;
                pendingWidth = 0;
                pendingHeight = 0;
                pendingName = "";
                pendingCodecs = "";
            }
        }
        return variants;
    }

    private int compareVariant(Variant left, Variant right) {
        int heightCompare = Integer.compare(left == null ? 0 : left.height, right == null ? 0 : right.height);
        if (heightCompare != 0) {
            return heightCompare;
        }
        int bandwidthCompare = Integer.compare(left == null ? 0 : left.bandwidth, right == null ? 0 : right.bandwidth);
        if (bandwidthCompare != 0) {
            return bandwidthCompare;
        }
        return Integer.compare(left == null ? 0 : left.width, right == null ? 0 : right.width);
    }

    private String selectedVariantDescription(Variant variant) {
        if (variant == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (variant.width > 0 && variant.height > 0) {
            parts.add(variant.width + "x" + variant.height);
        } else if (variant.height > 0) {
            parts.add(variant.height + "p");
        }
        if (variant.bandwidth > 0) {
            parts.add(variant.bandwidth + " bps");
        }
        if (!variant.name.isEmpty()) {
            parts.add(variant.name);
        }
        if (!variant.codecs.isEmpty()) {
            parts.add(variant.codecs);
        }
        return parts.isEmpty() ? variant.url : joinParts(parts);
    }

    private String joinParts(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            String cleaned = part == null ? "" : part.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(cleaned);
        }
        return builder.toString();
    }

    private int[] parseResolutionAttribute(String line) {
        String value = attrOrEmpty(line, "RESOLUTION");
        Matcher matcher = Pattern.compile("(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE).matcher(value);
        if (!matcher.find()) {
            return new int[]{0, 0};
        }
        return new int[]{parseInt(matcher.group(1), 0), parseInt(matcher.group(2), 0)};
    }

    private HlsPlaylist parseMediaPlaylist(String manifest, String manifestUrl) {
        List<HlsSegment> segments = new ArrayList<>();
        String[] lines = manifest.split("\\r?\\n");
        HlsKey currentKey = null;
        HlsMap currentMap = null;
        int mediaSequence = 0;
        int segmentIndex = 0;
        boolean discontinuity = false;
        String pendingByteRange = null;
        Map<String, Long> nextByteRangeOffsets = new LinkedHashMap<>();
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
            if (line.startsWith("#EXT-X-BYTERANGE:")) {
                pendingByteRange = line.substring("#EXT-X-BYTERANGE:".length()).trim();
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                String segmentUrl = new URL(new URL(manifestUrl), line).toString();
                String byteRange = normalizeHlsSegmentByteRange(segmentUrl, pendingByteRange, nextByteRangeOffsets);
                segments.add(new HlsSegment(segmentUrl, byteRange, currentKey, currentMap, mediaSequence + segmentIndex, discontinuity));
                segmentIndex++;
                discontinuity = false;
                pendingByteRange = null;
            } catch (MalformedURLException ignored) {
                // Skip malformed HLS entries instead of failing the whole manifest.
                pendingByteRange = null;
            }
        }
        return new HlsPlaylist(manifestUrl, segments);
    }

    private String normalizeHlsSegmentByteRange(String segmentUrl, String rawByteRange, Map<String, Long> nextOffsets) {
        if (rawByteRange == null || rawByteRange.trim().isEmpty()) {
            if (segmentUrl != null) {
                nextOffsets.remove(segmentUrl);
            }
            return null;
        }
        String trimmed = rawByteRange.trim();
        String[] parts = trimmed.split("@", 2);
        try {
            long length = Long.parseLong(parts[0].trim());
            if (length <= 0L) {
                return trimmed;
            }
            long start;
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                start = Long.parseLong(parts[1].trim());
            } else {
                start = nextOffsets.containsKey(segmentUrl) ? nextOffsets.get(segmentUrl) : 0L;
            }
            if (start < 0L) {
                return trimmed;
            }
            nextOffsets.put(segmentUrl, start + length);
            return length + "@" + start;
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private void writeMapIfNeeded(BufferedOutputStream outputStream, HlsSegment segment, Set<String> writtenMaps, String refererUrl) throws IOException {
        if (segment.map == null || segment.map.uri == null || segment.map.uri.isEmpty()) {
            return;
        }
        String mapKey = segment.map.uri + "|" + segment.map.byteRange;
        if (writtenMaps.contains(mapKey)) {
            return;
        }
        byte[] mapBytes = readBytes(segment.map.uri, segment.map.byteRange, refererUrl);
        if (mapBytes.length == 0) {
            throw new IOException("empty HLS init map");
        }
        outputStream.write(mapBytes);
        writtenMaps.add(mapKey);
    }

    private byte[] downloadSegmentWithRetry(HlsSegment segment, String refererUrl) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= SEGMENT_RETRY_LIMIT; attempt++) {
            try {
                byte[] data = readBytes(segment.url, segment.byteRange, refererUrl);
                if (data.length == 0) {
                    throw new IOException("empty segment");
                }
                return decryptIfNeeded(segment, data, refererUrl);
            } catch (IOException error) {
                last = error;
                if (cancelled.get()) {
                    throw error;
                }
            }
        }
        throw last == null ? new IOException("segment failed") : last;
    }

    private byte[] decryptIfNeeded(HlsSegment segment, byte[] data, String refererUrl) throws IOException {
        HlsKey key = segment.key;
        if (key == null || key.method == null || "NONE".equalsIgnoreCase(key.method)) {
            return data;
        }
        if (!"AES-128".equalsIgnoreCase(key.method)) {
            throw new IOException(context.getString(R.string.error_unsupported_hls_key_method, key.method));
        }
        if (key.uri == null || key.uri.isEmpty()) {
            throw new IOException(context.getString(R.string.error_missing_hls_key_uri));
        }
        if (key.bytes == null) {
            key.bytes = readBytes(key.uri, null, refererUrl);
        }
        if (key.bytes.length != 16) {
            throw new IOException(context.getString(R.string.error_invalid_hls_key_length, key.bytes.length));
        }
        byte[] iv = key.iv == null ? ivFromSequence(segment.sequence) : key.iv;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.bytes, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException error) {
            throw new IOException(context.getString(R.string.error_hls_decrypt_failed), error);
        }
    }

    private byte[] readBytes(String rawUrl) throws IOException {
        return readBytes(rawUrl, null);
    }

    private byte[] readBytes(String rawUrl, String byteRange) throws IOException {
        return readBytes(rawUrl, byteRange, "");
    }

    private byte[] readBytes(String rawUrl, String byteRange, String refererUrl) throws IOException {
        HttpURLConnection connection = open(rawUrl, refererUrl);
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
        String trimmed = byteRange == null ? "" : byteRange.trim();
        if (trimmed.toLowerCase(Locale.US).startsWith("bytes=")) {
            return trimmed;
        }
        if (trimmed.matches("\\d+-\\d*")) {
            return "bytes=" + trimmed;
        }
        String[] parts = trimmed.split("@", 2);
        try {
            long length = Long.parseLong(parts[0].trim());
            long start = parts.length > 1 ? Long.parseLong(parts[1].trim()) : 0L;
            long end = start + length - 1L;
            return "bytes=" + start + "-" + end;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private long byteRangeEnd(String byteRange) {
        if (byteRange == null || byteRange.trim().isEmpty()) {
            return -1L;
        }
        String[] parts = byteRange.trim().split("-", 2);
        if (parts.length != 2) {
            return -1L;
        }
        try {
            return Long.parseLong(parts[1].trim());
        } catch (NumberFormatException ignored) {
            return -1L;
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

    private String attrOrEmpty(String line, String name) {
        String value = attr(line, name);
        return value == null ? "" : value.trim();
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
