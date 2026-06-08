package tw.wajay.aitestdownloader;

import android.net.Uri;
import android.util.Base64;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class MediaResolver {
    static final class Result {
        final String sourceSite;
        final String primaryUrl;
        final boolean primaryIsMedia;
        final List<String> candidates;
        final List<String> candidateLabels;

        Result(String sourceSite, String primaryUrl, boolean primaryIsMedia, List<String> candidates, List<String> candidateLabels) {
            this.sourceSite = sourceSite;
            this.primaryUrl = primaryUrl;
            this.primaryIsMedia = primaryIsMedia;
            this.candidates = candidates;
            this.candidateLabels = candidateLabels;
        }
    }

    private static final Pattern ABSOLUTE_MEDIA_URL = Pattern.compile(
            "https?://[^\\s\"'<>\\\\]+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\\s\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROTOCOL_RELATIVE_MEDIA_URL = Pattern.compile(
            "(?<!:)//[^\\s\"'<>\\\\]+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\\s\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_MEDIA_URL = Pattern.compile(
            "(?:src|href|file|url|play_url|playUrl|video|source|hlsUrl|hls_url|hls|m3u8|playlist|manifest_url|manifestUrl|stream_url|streamUrl|video_url|videoUrl|media_url|mediaUrl|dash_url|dashUrl|mpd|backupUrl|backup_url)\\s*[:=]\\s*[\"']([^\"']+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_MEDIA_ASSIGNMENT = Pattern.compile(
            "(?:source|hlsUrl|hls_url|hls|m3u8|playlist|manifest_url|manifestUrl|stream_url|streamUrl|video_url|videoUrl|media_url|mediaUrl|dash_url|dashUrl|mpd|backupUrl|backup_url)\\s*[:=]\\s*[\"']((?:https?:)?//[^\"']+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_ESCAPED_MEDIA_URL = Pattern.compile(
            "https?:\\\\/\\\\/[^\\s\"'<>]+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_URL = Pattern.compile(
            "<iframe[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_OBJECT_START = Pattern.compile(
            "(?:var\\s+)?(player_data|player_aaaa|player)\\s*=\\s*\\{",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SITE_PLAY_LINK = Pattern.compile(
            "<a([^>]+)href=[\"']([^\"']*(?:/(?:vod)?play/|/watch/|/video/|/eps/|/episode/|/vod/detail/|/detail/|/index\\.php/vod/(?:play|detail)/|/dianying/|/dianshiju/|/zongyi/|/dongman/)[^\"']+)[\"']([^>]*)>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANI_GAMER_EPISODE_LINK = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*(?:animeVideo\\.php\\?sn=\\d+|\\?sn=\\d+)[^\"']*)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANI_GAMER_VIDEO_SN = Pattern.compile(
            "animefun\\.videoSn\\s*=\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final String[] PLAYER_KEYS = new String[]{
            "url", "src", "play_url", "playUrl", "urls", "backup", "backup_urls",
            "m3u8_urls", "playlist", "sources", "file", "video", "videos",
            "qualities", "streams", "stream_url", "streamUrl", "manifest_url",
            "manifestUrl", "hlsUrl", "hls_url", "dashUrl", "dash_url",
            "mediaUrl", "media_url", "backupUrl", "backup_url"
    };

    private MediaResolver() {
    }

    static Result resolve(String pageText, String pageUrl) {
        String site = sourceSite(pageUrl);
        List<String> candidates = new ArrayList<>();
        Map<String, String> labels = new LinkedHashMap<>();
        addPlayerObjectCandidates(candidates, labels, pageText, pageUrl);
        addGenericCandidates(candidates, labels, pageText, pageUrl);
        addIframeCandidates(candidates, labels, pageText, pageUrl);
        addSitePlayLinkCandidates(candidates, labels, pageText, pageUrl, site);
        addAniGamerCandidates(candidates, labels, pageText, pageUrl, site);
        sortCandidates(site, candidates);
        String primary = candidates.isEmpty() ? null : candidates.get(0);
        return new Result(site, primary, primary != null && isMediaUrl(primary), candidates, labelList(candidates, labels));
    }

    static boolean isMediaUrl(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains(".m3u8")
                || lowered.contains(".mp4")
                || lowered.contains(".mpd")
                || lowered.contains(".webm")
                || lowered.contains(".m4v");
    }

    static String sourceSite(String rawUrl) {
        String host = Uri.parse(rawUrl).getHost();
        String lowered = host == null ? "" : host.toLowerCase(Locale.US);
        if (lowered.contains("movieffm.net")) {
            return "movieffm";
        }
        if (lowered.contains("gimy")) {
            return "gimy";
        }
        if (lowered.contains("xiaoyakankan")) {
            return "xiaoyakankan";
        }
        if (lowered.contains("nnyy.in")) {
            return "nnyy";
        }
        if (lowered.contains("thanju.com")) {
            return "thanju";
        }
        if (lowered.contains("3kor.com")) {
            return "3kor";
        }
        if (lowered.contains("dramasq")) {
            return "dramasq";
        }
        if (lowered.contains("olevod") || lowered.contains("olehdtv")) {
            return "olevod";
        }
        if (lowered.contains("777tv.ai")) {
            return "777tv";
        }
        if (lowered.contains("99itv.net")) {
            return "99itv";
        }
        if (lowered.contains("dailymotion.com") || lowered.contains("dai.ly")) {
            return "dailymotion";
        }
        if (lowered.contains("youtube.com") || lowered.contains("youtu.be")) {
            return "youtube";
        }
        if (lowered.contains("bilibili.com") || lowered.contains("b23.tv")) {
            return "bilibili";
        }
        if (lowered.contains("iqiyi.com")) {
            return "iqiyi";
        }
        if (lowered.contains("ikanbot")) {
            return "ikanbot";
        }
        if (lowered.contains("yfsp")) {
            return "yfsp";
        }
        if (lowered.contains("anime1.")) {
            return "anime1";
        }
        if ("ani.gamer.com.tw".equals(lowered)) {
            return "ani_gamer";
        }
        return "generic";
    }

    private static void addGenericCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl) {
        addRegexCandidates(out, labels, ABSOLUTE_MEDIA_URL.matcher(pageText), pageUrl, false, "direct media");
        addRegexCandidates(out, labels, PROTOCOL_RELATIVE_MEDIA_URL.matcher(pageText), pageUrl, false, "protocol relative media");
        addRegexCandidates(out, labels, ATTR_MEDIA_URL.matcher(pageText), pageUrl, true, "media attribute");
        addRegexCandidates(out, labels, SCRIPT_MEDIA_ASSIGNMENT.matcher(pageText), pageUrl, true, "player script");
        addRegexCandidates(out, labels, JSON_ESCAPED_MEDIA_URL.matcher(pageText), pageUrl, false, "json escaped media");
    }

    private static void addIframeCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl) {
        Matcher matcher = IFRAME_URL.matcher(pageText);
        while (matcher.find()) {
            String iframe = resolveUrl(pageUrl, matcher.group(1));
            if (iframe != null && looksLikeMediaOrPlayer(iframe) && !out.contains(iframe)) {
                out.add(iframe);
                putLabel(labels, iframe, "iframe/player");
            }
        }
    }

    private static void addSitePlayLinkCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!supportsSitePlayLinks(site)) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>(out);
        Matcher matcher = SITE_PLAY_LINK.matcher(pageText);
        while (matcher.find()) {
            String resolved = resolveUrl(pageUrl, matcher.group(2));
            if (resolved != null && sameOrKnownMediaHost(pageUrl, resolved) && seen.add(resolved)) {
                out.add(resolved);
                putLabel(labels, resolved, anchorLabel(matcher.group(1) + " " + matcher.group(3), matcher.group(4)));
            }
        }
    }

    private static void addAniGamerCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!"ani_gamer".equals(site)) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>(out);
        Matcher linkMatcher = ANI_GAMER_EPISODE_LINK.matcher(pageText == null ? "" : pageText);
        while (linkMatcher.find()) {
            addAniGamerCandidate(out, labels, seen, pageUrl, linkMatcher.group(1), cleanLabel(linkMatcher.group()));
        }
        Matcher snMatcher = ANI_GAMER_VIDEO_SN.matcher(pageText == null ? "" : pageText);
        while (snMatcher.find()) {
            addAniGamerCandidate(out, labels, seen, pageUrl, "https://ani.gamer.com.tw/animeVideo.php?sn=" + snMatcher.group(1), "videoSn " + snMatcher.group(1));
        }
    }

    private static void addAniGamerCandidate(List<String> out, Map<String, String> labels, Set<String> seen, String pageUrl, String value, String label) {
        String resolved = resolveUrl(pageUrl, value);
        if (resolved == null || sameUrlWithoutFragment(pageUrl, resolved)) {
            return;
        }
        Uri uri = Uri.parse(resolved);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
        String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.US);
        if ("ani.gamer.com.tw".equals(host) && path.endsWith("/animevideo.php") && query.contains("sn=") && seen.add(resolved)) {
            out.add(resolved);
            putLabel(labels, resolved, label);
        }
    }

    private static void addPlayerObjectCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl) {
        Set<String> seen = new LinkedHashSet<>(out);
        for (JSONObject object : extractPlayerObjects(pageText)) {
            int encryptMode = object.optInt("encrypt", 0);
            String objectLabel = firstObjectLabel(object);
            JSONArray videoUrls = object.optJSONArray("videourls");
            if (videoUrls != null) {
                for (int i = 0; i < videoUrls.length(); i++) {
                    Object item = videoUrls.opt(i);
                    if (item instanceof JSONObject) {
                        JSONObject itemObject = (JSONObject) item;
                        addCandidateValue(out, labels, seen, itemObject.opt("url"), pageUrl, encryptMode, firstNonEmpty(firstObjectLabel(itemObject), objectLabel));
                    } else {
                        addCandidateValue(out, labels, seen, item, pageUrl, encryptMode, firstNonEmpty(objectLabel, "videourls"));
                    }
                }
            }
            for (String key : PLAYER_KEYS) {
                addCandidateValue(out, labels, seen, object.opt(key), pageUrl, encryptMode, firstNonEmpty(objectLabel, "player " + key));
            }
        }
    }

    private static List<JSONObject> extractPlayerObjects(String pageText) {
        List<JSONObject> objects = new ArrayList<>();
        String text = pageText == null ? "" : pageText;
        Matcher matcher = PLAYER_OBJECT_START.matcher(text);
        while (matcher.find()) {
            int objectStart = matcher.end() - 1;
            String blob = balancedObject(text, objectStart);
            if (blob == null) {
                continue;
            }
            JSONObject object = parseLooseJsonObject(blob);
            if (object != null) {
                objects.add(object);
            }
        }
        return objects;
    }

    private static String balancedObject(String text, int start) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == quote) {
                    inString = false;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                inString = true;
                quote = ch;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static JSONObject parseLooseJsonObject(String blob) {
        String normalized = blob == null ? "" : blob.trim();
        try {
            return new JSONObject(normalized);
        } catch (JSONException ignored) {
            // MacCMS player objects are often JavaScript literals, not strict JSON.
        }
        normalized = normalized
                .replaceAll("([\\{,]\\s*)([A-Za-z_][A-Za-z0-9_]*)\\s*:", "$1\"$2\":")
                .replace('\'', '"')
                .replaceAll(",\\s*([}\\]])", "$1");
        try {
            return new JSONObject(normalized);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static void addCandidateValue(List<String> out, Map<String, String> labels, Set<String> seen, Object raw, String pageUrl, int encryptMode, String label) {
        if (raw == null || raw == JSONObject.NULL) {
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                addCandidateValue(out, labels, seen, array.opt(i), pageUrl, encryptMode, label);
            }
            return;
        }
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            String objectLabel = firstNonEmpty(firstObjectLabel(object), label);
            for (String key : PLAYER_KEYS) {
                addCandidateValue(out, labels, seen, object.opt(key), pageUrl, encryptMode, firstNonEmpty(objectLabel, "player " + key));
            }
            return;
        }
        String decoded = decodeMacCmsUrl(String.valueOf(raw), encryptMode);
        for (String value : splitCandidateValue(decoded)) {
            String resolved = resolveUrl(pageUrl, value);
            if (resolved != null && looksLikeMediaOrPlayer(resolved) && seen.add(resolved)) {
                out.add(resolved);
                putLabel(labels, resolved, label);
            }
        }
    }

    private static List<String> splitCandidateValue(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        for (String part : raw.split("\\$\\$\\$|#|\\n|\\r")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int dollar = trimmed.lastIndexOf('$');
            if (dollar >= 0 && dollar + 1 < trimmed.length()) {
                trimmed = trimmed.substring(dollar + 1).trim();
            }
            values.add(trimmed);
        }
        return values;
    }

    private static String decodeMacCmsUrl(String rawUrl, int encryptMode) {
        String decoded = rawUrl == null ? "" : rawUrl.trim();
        if (decoded.isEmpty()) {
            return decoded;
        }
        try {
            if (encryptMode == 1) {
                decoded = URLDecoder.decode(decoded, "UTF-8");
            } else if (encryptMode == 2) {
                byte[] bytes = Base64.decode(decoded, Base64.DEFAULT);
                decoded = URLDecoder.decode(new String(bytes, "UTF-8"), "UTF-8");
            }
        } catch (Exception ignored) {
            // Keep the raw value; many sites mix encoded and plain values.
        }
        return decoded.replace("\\/", "/").replace("&amp;", "&");
    }

    private static void addRegexCandidates(List<String> out, Map<String, String> labels, Matcher matcher, String pageUrl, boolean firstGroup, String label) {
        Set<String> seen = new LinkedHashSet<>(out);
        while (matcher.find()) {
            String value = firstGroup ? matcher.group(1) : matcher.group();
            String resolved = resolveUrl(pageUrl, value);
            if (resolved != null && seen.add(resolved)) {
                out.add(resolved);
                putLabel(labels, resolved, label);
            }
        }
    }

    private static List<String> labelList(List<String> candidates, Map<String, String> labels) {
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            out.add(labels.containsKey(candidate) ? labels.get(candidate) : "");
        }
        return out;
    }

    private static void putLabel(Map<String, String> labels, String url, String label) {
        String cleaned = cleanLabel(label);
        if (url == null || url.isEmpty() || cleaned.isEmpty() || labels.containsKey(url)) {
            return;
        }
        labels.put(url, cleaned);
    }

    private static String firstObjectLabel(JSONObject object) {
        if (object == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String key : new String[]{"from", "server", "source", "type", "quality", "name", "label", "title"}) {
            String value = cleanLabel(object.optString(key, ""));
            if (!value.isEmpty() && builder.indexOf(value) < 0) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static String anchorLabel(String attrs, String body) {
        String attrLabel = firstAttributeLabel(attrs);
        if (!attrLabel.isEmpty()) {
            return attrLabel;
        }
        return cleanLabel(body);
    }

    private static String firstAttributeLabel(String attrs) {
        String text = attrs == null ? "" : attrs;
        Matcher matcher = Pattern.compile("(?:title|aria-label|data-title|data-name|data-label|data-source|data-from)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(text);
        while (matcher.find()) {
            String label = cleanLabel(matcher.group(1));
            if (!label.isEmpty()) {
                return label;
            }
        }
        return "";
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? (second == null ? "" : second.trim()) : first.trim();
    }

    private static String cleanLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
    }

    private static boolean sameOrKnownMediaHost(String pageUrl, String targetUrl) {
        String pageHost = Uri.parse(pageUrl).getHost();
        String targetHost = Uri.parse(targetUrl).getHost();
        String page = pageHost == null ? "" : pageHost.toLowerCase(Locale.US);
        String target = targetHost == null ? "" : targetHost.toLowerCase(Locale.US);
        return target.equals(page)
                || target.contains("movieffm")
                || target.contains("gimy")
                || target.contains("xiaoyakankan")
                || target.contains("nnyy.in")
                || target.contains("thanju.com")
                || target.contains("3kor.com")
                || target.contains("dramasq")
                || target.contains("olevod")
                || target.contains("olehdtv")
                || target.contains("777tv.ai")
                || target.contains("99itv.net")
                || target.contains("dailymotion")
                || target.contains("dmcdn.net")
                || target.contains("googlevideo")
                || target.contains("youtube")
                || target.contains("bilivideo")
                || target.contains("bilibili")
                || target.contains("iqiyi")
                || target.contains("qiyi")
                || target.contains("ikanbot")
                || target.contains("yfsp")
                || "ani.gamer.com.tw".equals(target);
    }

    private static boolean sameUrlWithoutFragment(String left, String right) {
        Uri leftUri = Uri.parse(left);
        Uri rightUri = Uri.parse(right);
        String leftKey = (leftUri.getScheme() + "://" + leftUri.getHost() + leftUri.getPath() + "?" + leftUri.getQuery()).toLowerCase(Locale.US);
        String rightKey = (rightUri.getScheme() + "://" + rightUri.getHost() + rightUri.getPath() + "?" + rightUri.getQuery()).toLowerCase(Locale.US);
        return leftKey.equals(rightKey);
    }

    private static String resolveUrl(String pageUrl, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String cleaned = value.trim()
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("\\u0026", "&");
        try {
            return new URL(new URL(pageUrl), cleaned).toString();
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static boolean looksLikeMediaOrPlayer(String url) {
        String lowered = url.toLowerCase(Locale.US);
        return isMediaUrl(url)
                || lowered.contains("/parse")
                || lowered.contains("/player")
                || lowered.contains("/dp/")
                || lowered.contains("/ap/")
                || lowered.contains("/play/")
                || lowered.contains("/vodplay/")
                || lowered.contains("/watch/")
                || lowered.contains("/video/")
                || lowered.contains("/eps/")
                || lowered.contains("/episode/")
                || lowered.contains("/vod/detail/")
                || lowered.contains("/detail/")
                || lowered.contains("/index.php/vod/play/")
                || lowered.contains("/index.php/vod/detail/")
                || lowered.contains("/dianying/")
                || lowered.contains("/dianshiju/")
                || lowered.contains("/zongyi/")
                || lowered.contains("/dongman/")
                || lowered.contains("animevideo.php?sn=");
    }

    private static void sortCandidates(String sourceSite, List<String> candidates) {
        Collections.sort(candidates, Comparator.comparingInt(url -> score(sourceSite, url)));
    }

    private static int score(String sourceSite, String url) {
        String lowered = url.toLowerCase(Locale.US);
        int score = 1000;
        if (lowered.contains(".m3u8")) {
            score -= 400;
        }
        if (lowered.contains(".mp4") || lowered.contains(".webm") || lowered.contains(".m4v")) {
            score -= 250;
        }
        if (!isMediaUrl(url)) {
            score += 300;
        }
        if (lowered.contains("1080")) {
            score -= 80;
        } else if (lowered.contains("720")) {
            score -= 60;
        }
        if ("movieffm".equals(sourceSite)) {
            score += hostPenalty(lowered, new String[]{"xluuss", "lzcdn", "hhuus", "qsstvw", "gsuus", "bfllvip"});
        } else if ("gimy".equals(sourceSite)) {
            score += hostPenalty(lowered, new String[]{"ppqrrs", "qqqrst", "vodcnd", "phimgood", "ryiplay"});
        } else if ("xiaoyakankan".equals(sourceSite)) {
            score += hostPenalty(lowered, new String[]{"huyall", "ijycnd", "jisuzyv", "gsuus", "qsstvw", "taopianplay1"});
        } else if ("ani_gamer".equals(sourceSite)) {
            if (lowered.contains("animevideo.php?sn=")) {
                score -= 120;
            }
        } else if ("dailymotion".equals(sourceSite)) {
            if (lowered.contains(".m3u8")) {
                score -= 120;
            }
            if (lowered.contains("dmcdn.net")) {
                score -= 40;
            }
        } else if ("bilibili".equals(sourceSite)) {
            if (lowered.contains("bilivideo")) {
                score -= 80;
            }
        } else if ("youtube".equals(sourceSite)) {
            if (lowered.contains("googlevideo")) {
                score -= 80;
            }
        } else if (isMacCmsLikeSite(sourceSite)) {
            if (lowered.contains("/vodplay/")
                    || lowered.contains("/vod/play/")
                    || lowered.contains("/index.php/vod/play/")
                    || lowered.contains("/play/")
                    || lowered.contains("/eps/")
                    || lowered.contains("/episode/")) {
                score -= 120;
            }
            if (lowered.contains("/vod/detail/")
                    || lowered.contains("/index.php/vod/detail/")
                    || lowered.contains("/detail/")
                    || lowered.contains("/dianying/")
                    || lowered.contains("/dianshiju/")
                    || lowered.contains("/zongyi/")
                    || lowered.contains("/dongman/")) {
                score -= 40;
            }
        }
        if (lowered.contains("/player") || lowered.contains("/parse") || lowered.contains("/ap/")) {
            score += 80;
        }
        return score;
    }

    private static int hostPenalty(String url, String[] preferredMarkers) {
        for (int i = 0; i < preferredMarkers.length; i++) {
            if (url.contains(preferredMarkers[i])) {
                return i * 5;
            }
        }
        return 80;
    }

    private static boolean supportsSitePlayLinks(String site) {
        return "movieffm".equals(site)
                || "gimy".equals(site)
                || "xiaoyakankan".equals(site)
                || isMacCmsLikeSite(site);
    }

    private static boolean isMacCmsLikeSite(String site) {
        return "nnyy".equals(site)
                || "thanju".equals(site)
                || "3kor".equals(site)
                || "dramasq".equals(site)
                || "olevod".equals(site)
                || "777tv".equals(site)
                || "99itv".equals(site);
    }
}
