package tw.wajay.aitestdownloader;

import android.net.Uri;
import android.util.Base64;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
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
            "(?:src|href|file|url|play_url|playUrl|playurl|video|source|hlsUrl|hls_url|hls|m3u8|playlist|manifest_url|manifestUrl|stream_url|streamUrl|video_url|videoUrl|media_url|mediaUrl|dash_url|dashUrl|mpd|backupUrl|backup_url)\\s*[:=]\\s*[\"']([^\"']+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_MEDIA_ASSIGNMENT = Pattern.compile(
            "(?:source|playurl|hlsUrl|hls_url|hls|m3u8|playlist|manifest_url|manifestUrl|stream_url|streamUrl|video_url|videoUrl|media_url|mediaUrl|dash_url|dashUrl|mpd|backupUrl|backup_url)\\s*[:=]\\s*[\"']((?:https?:)?//[^\"']+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_ESCAPED_MEDIA_URL = Pattern.compile(
            "https?:\\\\/\\\\/[^\\s\"'<>]+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXDROP_WURL_MEDIA_URL = Pattern.compile(
            "\\bwurl\\s*[:=]\\s*[\"']((?:https?:)?//[^\"']+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_URL = Pattern.compile(
            "<iframe[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_OBJECT_START = Pattern.compile(
            "(?:var\\s+)?(player_data|player_aaaa|player)\\s*=\\s*\\{",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SITE_PLAY_LINK = Pattern.compile(
            "<a([^>]+)href=[\"']([^\"']*(?:/(?:vod)?play/|/vodplay/|/watch/|/video/|/videos/|/embed/|/amateurjav_content/|/eps/|/episode/|/vod/detail/|/voddetail/|/voddetail2/|/detail/|/title/|/drama/|/index\\.php/vod/(?:play|detail)/|/dianying/|/dianshiju/|/zongyi/|/dongman/)[^\"']+)[\"']([^>]*)>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANI_GAMER_EPISODE_LINK = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*(?:animeVideo\\.php\\?sn=\\d+|\\?sn=\\d+)[^\"']*)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANI_GAMER_VIDEO_SN = Pattern.compile(
            "animefun\\.videoSn\\s*=\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_MEDIA_URL = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:video(?::url|:secure_url)?|twitter:player:stream|twitter:player)[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOCIAL_JSON_MEDIA_URL = Pattern.compile(
            "\"(?:video_url|playable_url|playable_url_quality_hd|browser_native_hd_url|browser_native_sd_url|contentUrl|download_url|media_url|source|src|url)\"\\s*:\\s*\"((?:https?:\\\\?/\\\\?/|\\\\?/\\\\?/)[^\"\\\\]*(?:\\\\.[A-Za-z0-9]+|/video/|/ext_tw_video/|/video/t1/|/v/t42\\.|/v/t50\\.|fbcdn|cdninstagram|twimg)[^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_VARIANT_URL = Pattern.compile(
            "\"url\"\\s*:\\s*\"((?:https?:\\\\?/\\\\?/|\\\\?/\\\\?/)[^\"]+(?:video\\.twimg\\.com|\\.m3u8|\\.mp4)[^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_VIDEOS_BLOCK = Pattern.compile(
            "\"videos\"\\s*:\\s*\\[(.*?)\\]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MOVIEFFM_JSON_URL = Pattern.compile(
            "\"url\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_DOWNLOAD_HREF = Pattern.compile(
            "href=[\"']([^\"']+\\?download[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_SHORTCODE_URL = Pattern.compile(
            "\\[pmoive\\b[^\\]]*\\burl\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_VIDEOURL = Pattern.compile(
            "videourl\\s*:\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_IFRAME_JSON_URL = Pattern.compile(
            "\"url\"\\s*:\\s*\"((?:https?:)?//[^\"]+)\"\\s*,\\s*\"type\"\\s*:\\s*\"iframe\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_DRAMA_DETAIL_URL = Pattern.compile(
            "href=[\"'](https?://www\\.movieffm\\.net/drama/\\d+/)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_EPISODE_JSON = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_EPISODE_ABSOLUTE = Pattern.compile(
            "https://www\\.movieffm\\.net/[^\"'<>\\s]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XIAOYA_PLAY_URL = Pattern.compile(
            "(?:href|data-url|data-href|data-play|url|play_url|playUrl)\\s*[:=]\\s*[\"']([^\"']*/vod/play/id/[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XIAOYA_PP_START = Pattern.compile(
            "var\\s+pp\\s*=\\s*\\{",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GGJAV_EMBED_URL = Pattern.compile(
            "((?:https?:)?//(?:www\\.)?ggjav\\.com/main/embed\\?[^\\s\"'<>\\\\]+|/main/embed\\?[^\\s\"'<>\\\\]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GGJAV_OBFUSCATED_LINKS = Pattern.compile(
            "\\bvar\\s+l\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GIMY_PARSE_API_URL = Pattern.compile(
            "((?:https?:)?//[^\\s\"'<>\\\\]+?parse\\.php\\?[^\\s\"'<>\\\\]+|(?:/[^\\s\"'<>\\\\]*)?parse\\.php\\?[^\\s\"'<>\\\\]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GIMY_PLAY_PAGE_ID = Pattern.compile(
            "/(?:(?:vod)?play|watch|eps)/([A-Za-z0-9]+)-\\d+(?:-\\d+)?\\.html|/video/([A-Za-z0-9]+)-\\d+\\.html",
            Pattern.CASE_INSENSITIVE);
    private static final String[] PLAYER_KEYS = new String[]{
            "url", "src", "play_url", "playUrl", "playurl", "urls", "backup", "backup_urls",
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
        addGimyParseCandidates(candidates, labels, pageText, pageUrl, site);
        addGimyDetailCandidates(candidates, labels, pageUrl, site);
        addGgJavEmbedCandidates(candidates, labels, pageText, pageUrl);
        addMovieFfmExternalCandidates(candidates, labels, pageText, pageUrl, site);
        addMovieFfmEpisodeCandidates(candidates, labels, pageText, pageUrl, site);
        addXiaoyaPlayCandidates(candidates, labels, pageText, pageUrl, site);
        addXiaoyaPpCandidates(candidates, labels, pageText, pageUrl, site);
        addSitePlayLinkCandidates(candidates, labels, pageText, pageUrl, site);
        addAniGamerCandidates(candidates, labels, pageText, pageUrl, site);
        addSocialCandidates(candidates, labels, pageText, pageUrl, site);
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
        if (lowered.contains("mixdrop.ag") || lowered.contains("m1xdrop.click")) {
            return "mixdrop";
        }
        if (isDoodFamilyHost(lowered)) {
            return "dood";
        }
        if (lowered.contains("evoload.io")) {
            return "evoload";
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
        if (lowered.contains("instagram.com")) {
            return "instagram";
        }
        if (lowered.contains("facebook.com") || lowered.contains("fb.watch")) {
            return "facebook";
        }
        if (lowered.contains("twitter.com") || lowered.contains("x.com")) {
            return "twitter";
        }
        String adultSite = adultSourceSite(lowered);
        if (!adultSite.isEmpty()) {
            return adultSite;
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
        addRegexCandidates(out, labels, MIXDROP_WURL_MEDIA_URL.matcher(pageText), pageUrl, true, "Mixdrop wurl media");
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

    private static void addGgJavEmbedCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl) {
        String site = sourceSite(pageUrl);
        if (!("goodav17".equals(site) || "hohoj".equals(site) || "ggjav".equals(site))) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>(out);
        String text = htmlDecoded(pageText);
        Matcher matcher = GGJAV_EMBED_URL.matcher(text);
        while (matcher.find()) {
            addGgJavMediaCandidates(out, labels, seen, resolveUrl(pageUrl, matcher.group(1)), "GGJAV embed media");
        }
        Matcher obfuscatedMatcher = GGJAV_OBFUSCATED_LINKS.matcher(text);
        while (obfuscatedMatcher.find()) {
            for (String value : decodeGgJavObfuscatedLinks(obfuscatedMatcher.group(1))) {
                addGgJavMediaCandidates(out, labels, seen, resolveUrl(pageUrl, value), "GGJAV player links");
            }
        }
    }

    private static void addGgJavMediaCandidates(List<String> out, Map<String, String> labels, Set<String> seen, String rawUrl, String label) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return;
        }
        String decoded = decodeGgJavEmbedMediaUrl(rawUrl);
        List<String> seeds = new ArrayList<>();
        seeds.add(rawUrl);
        if (decoded != null && !decoded.trim().isEmpty()) {
            seeds.add(decoded);
        }
        for (String seed : seeds) {
            for (String candidate : expandGgJavMediaVariants(seed)) {
                if (candidate != null && isMediaUrl(candidate) && seen.add(candidate)) {
                    out.add(candidate);
                    putLabel(labels, candidate, label);
                }
            }
        }
    }

    private static void addGimyParseCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!"gimy".equals(site)) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>(out);
        String text = htmlDecoded(pageText);
        Matcher parseMatcher = GIMY_PARSE_API_URL.matcher(text);
        while (parseMatcher.find()) {
            String candidate = resolveUrl(pageUrl, parseMatcher.group(1));
            if (candidate != null && looksLikeMediaOrPlayer(candidate) && seen.add(candidate)) {
                out.add(candidate);
                putLabel(labels, candidate, "Gimy parse API");
            }
        }
        Matcher iframeMatcher = IFRAME_URL.matcher(text);
        while (iframeMatcher.find()) {
            addGimyIframeDerivedCandidates(out, labels, seen, resolveUrl(pageUrl, iframeMatcher.group(1)));
        }
        List<String> existing = new ArrayList<>(out);
        for (String candidate : existing) {
            if ("gimy".equals(sourceSite(candidate))) {
                addGimyIframeDerivedCandidates(out, labels, seen, candidate);
            }
        }
    }

    private static void addGimyIframeDerivedCandidates(List<String> out, Map<String, String> labels, Set<String> seen, String iframeUrl) {
        if (iframeUrl == null || iframeUrl.trim().isEmpty() || !"gimy".equals(sourceSite(iframeUrl))) {
            return;
        }
        String playSource = queryParameter(iframeUrl, "url");
        if (playSource.isEmpty()) {
            return;
        }
        String decoded = decodeMacCmsUrl(playSource, 0);
        for (String value : splitCandidateValue(decoded)) {
            String resolved = resolveUrl(iframeUrl, value);
            if (resolved != null && isMediaUrl(resolved) && seen.add(resolved)) {
                out.add(resolved);
                putLabel(labels, resolved, "Gimy iframe direct media");
            }
        }
        String parseApi = joinUrl(iframeUrl, "parse.php?url=" + urlEncode(playSource));
        if (parseApi != null && looksLikeMediaOrPlayer(parseApi) && seen.add(parseApi)) {
            out.add(parseApi);
            putLabel(labels, parseApi, "Gimy parse API");
        }
    }

    private static void addGimyDetailCandidates(List<String> out, Map<String, String> labels, String pageUrl, String site) {
        if (!"gimy".equals(site)) {
            return;
        }
        String vodId = gimyPlayVodId(pageUrl);
        if (vodId.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>(out);
        String base = siteRoot(pageUrl, "https://gimy.tube");
        for (String path : new String[]{"/title/", "/vod/", "/detail/", "/voddetail/"}) {
            String candidate = joinUrl(base, path + vodId + ".html");
            if (candidate != null && !sameUrlWithoutFragment(pageUrl, candidate) && seen.add(candidate)) {
                out.add(candidate);
                putLabel(labels, candidate, "Gimy detail fallback");
            }
        }
    }

    private static String gimyPlayVodId(String rawUrl) {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher matcher = GIMY_PLAY_PAGE_ID.matcher(path);
        if (!matcher.find()) {
            return "";
        }
        String first = matcher.group(1);
        String second = matcher.group(2);
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }

    private static void addMovieFfmExternalCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!"movieffm".equals(site)) {
            return;
        }
        String text = htmlDecoded(pageText);
        Set<String> seen = new LinkedHashSet<>(out);
        Matcher blockMatcher = MOVIEFFM_VIDEOS_BLOCK.matcher(text);
        while (blockMatcher.find()) {
            Matcher urlMatcher = MOVIEFFM_JSON_URL.matcher(blockMatcher.group(1));
            while (urlMatcher.find()) {
                addMovieFfmExternalCandidate(out, labels, seen, pageUrl, urlMatcher.group(1), "MovieFFM external source");
            }
        }
        addMovieFfmExternalRegex(out, labels, seen, MOVIEFFM_DOWNLOAD_HREF.matcher(text), pageUrl, "MovieFFM download link");
        addMovieFfmExternalRegex(out, labels, seen, MOVIEFFM_SHORTCODE_URL.matcher(text), pageUrl, "MovieFFM shortcode source");
        addMovieFfmExternalRegex(out, labels, seen, MOVIEFFM_VIDEOURL.matcher(text), pageUrl, "MovieFFM video URL");
        addMovieFfmExternalRegex(out, labels, seen, MOVIEFFM_IFRAME_JSON_URL.matcher(text), pageUrl, "MovieFFM iframe source");
    }

    private static void addMovieFfmExternalRegex(List<String> out, Map<String, String> labels, Set<String> seen, Matcher matcher, String pageUrl, String label) {
        while (matcher.find()) {
            addMovieFfmExternalCandidate(out, labels, seen, pageUrl, matcher.group(1), label);
        }
    }

    private static void addMovieFfmExternalCandidate(List<String> out, Map<String, String> labels, Set<String> seen, String pageUrl, String rawUrl, String label) {
        String candidate = normalizeMovieFfmExternalUrl(resolveUrl(pageUrl, rawUrl));
        if (candidate != null && isMovieFfmExternalHost(candidate) && seen.add(candidate)) {
            out.add(candidate);
            putLabel(labels, candidate, label);
        }
    }

    private static void addMovieFfmEpisodeCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!"movieffm".equals(site)) {
            return;
        }
        String text = htmlDecoded(pageText);
        Set<String> seen = new LinkedHashSet<>(out);
        Matcher detailMatcher = MOVIEFFM_DRAMA_DETAIL_URL.matcher(text);
        while (detailMatcher.find()) {
            addMovieFfmPageCandidate(out, labels, seen, pageUrl, detailMatcher.group(1), "MovieFFM drama detail");
        }
        Matcher episodeMatcher = MOVIEFFM_EPISODE_JSON.matcher(text);
        while (episodeMatcher.find()) {
            addMovieFfmPageCandidate(out, labels, seen, pageUrl, episodeMatcher.group(2), "MovieFFM episode " + normalizeMovieFfmEpisodeName(episodeMatcher.group(1)));
        }
        Matcher absoluteMatcher = MOVIEFFM_EPISODE_ABSOLUTE.matcher(text);
        while (absoluteMatcher.find()) {
            String value = absoluteMatcher.group();
            String lowered = value.toLowerCase(Locale.US);
            if (lowered.contains("/play/") || lowered.contains("/vodplay/") || lowered.contains("/episode/")) {
                addMovieFfmPageCandidate(out, labels, seen, pageUrl, value, "MovieFFM episode page");
            }
        }
    }

    private static void addMovieFfmPageCandidate(List<String> out, Map<String, String> labels, Set<String> seen, String pageUrl, String rawUrl, String label) {
        String candidate = resolveUrl(pageUrl, rawUrl);
        if (candidate != null && sameOrKnownMediaHost(pageUrl, candidate) && seen.add(candidate)) {
            out.add(candidate);
            putLabel(labels, candidate, label);
        }
    }

    private static String normalizeMovieFfmEpisodeName(String rawName) {
        String cleaned = cleanLabel(rawName);
        Matcher numbered = Pattern.compile("^0*(\\d{1,3})$").matcher(cleaned);
        if (numbered.find()) {
            try {
                int number = Integer.parseInt(numbered.group(1));
                if (number > 0 && number < 100) {
                    return String.format(Locale.US, "%02d", number);
                }
            } catch (NumberFormatException ignored) {
                return cleaned;
            }
        }
        return cleaned;
    }

    private static void addXiaoyaPlayCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!"xiaoyakankan".equals(site)) {
            return;
        }
        String text = htmlDecoded(pageText);
        Set<String> seen = new LinkedHashSet<>(out);
        Matcher matcher = XIAOYA_PLAY_URL.matcher(text);
        while (matcher.find()) {
            String candidate = resolveUrl(pageUrl, matcher.group(1));
            if (candidate != null && isXiaoyaPlayUrl(candidate) && seen.add(candidate)) {
                out.add(candidate);
                putLabel(labels, candidate, "XiaoyaKankan play page");
            }
        }
    }

    private static void addXiaoyaPpCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!"xiaoyakankan".equals(site)) {
            return;
        }
        JSONObject pp = extractXiaoyaPpObject(pageText);
        if (pp == null) {
            return;
        }
        JSONArray lines = pp.optJSONArray("lines");
        if (lines == null || lines.length() == 0) {
            return;
        }
        String vod = queryParameter(pageUrl, "vod");
        String vodKey = "";
        int episodeIndex = 0;
        if (!vod.isEmpty()) {
            int dash = vod.indexOf('-');
            vodKey = dash >= 0 ? vod.substring(0, dash) : vod;
            if (dash >= 0) {
                try {
                    episodeIndex = Math.max(0, Integer.parseInt(vod.substring(dash + 1)));
                } catch (NumberFormatException ignored) {
                    episodeIndex = 0;
                }
            }
        }
        Set<String> seen = new LinkedHashSet<>(out);
        for (int i = 0; i < lines.length(); i++) {
            JSONArray row = lines.optJSONArray(i);
            if (row == null || row.length() < 4) {
                continue;
            }
            if (!vodKey.isEmpty() && !vodKey.equals(String.valueOf(row.opt(0)))) {
                continue;
            }
            addXiaoyaPpRowCandidates(out, labels, seen, row, episodeIndex, pageUrl);
        }
        if (vodKey.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.length(); i++) {
            JSONArray row = lines.optJSONArray(i);
            if (row != null && row.length() >= 4 && !vodKey.equals(String.valueOf(row.opt(0)))) {
                addXiaoyaPpRowCandidates(out, labels, seen, row, episodeIndex, pageUrl);
            }
        }
    }

    private static void addXiaoyaPpRowCandidates(List<String> out, Map<String, String> labels, Set<String> seen, JSONArray row, int episodeIndex, String pageUrl) {
        JSONArray candidates = row.optJSONArray(3);
        if (candidates == null || candidates.length() == 0) {
            return;
        }
        int index = Math.min(Math.max(0, episodeIndex), candidates.length() - 1);
        String candidate = resolveUrl(pageUrl, String.valueOf(candidates.opt(index)));
        if (candidate != null && isMediaUrl(candidate) && seen.add(candidate)) {
            out.add(candidate);
            putLabel(labels, candidate, "XiaoyaKankan pp line " + firstNonEmpty(String.valueOf(row.opt(0)), String.valueOf(out.size())));
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

    private static void addSocialCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl, String site) {
        if (!isSocialSite(site)) {
            return;
        }
        String text = pageText == null ? "" : pageText;
        Set<String> seen = new LinkedHashSet<>(out);
        addSocialRegexCandidates(out, labels, seen, META_MEDIA_URL.matcher(text), pageUrl, site, "social metadata");
        addSocialRegexCandidates(out, labels, seen, SOCIAL_JSON_MEDIA_URL.matcher(text), pageUrl, site, "social json");
        if ("twitter".equals(site)) {
            addSocialRegexCandidates(out, labels, seen, TWITTER_VARIANT_URL.matcher(text), pageUrl, site, "twitter variant");
        }
    }

    private static void addSocialRegexCandidates(List<String> out, Map<String, String> labels, Set<String> seen, Matcher matcher, String pageUrl, String site, String label) {
        while (matcher.find()) {
            String value = decodeJsonUrl(matcher.group(1));
            String resolved = resolveUrl(pageUrl, value);
            if (resolved != null && isSocialMediaCandidate(site, resolved) && seen.add(resolved)) {
                out.add(resolved);
                putLabel(labels, resolved, label);
            }
        }
    }

    private static void addPlayerObjectCandidates(List<String> out, Map<String, String> labels, String pageText, String pageUrl) {
        Set<String> seen = new LinkedHashSet<>(out);
        String site = sourceSite(pageUrl);
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
            if ("gimy".equals(site)) {
                addGimyIframeCandidates(out, labels, seen, object, pageUrl, objectLabel);
            }
        }
    }

    private static void addGimyIframeCandidates(List<String> out, Map<String, String> labels, Set<String> seen, JSONObject object, String pageUrl, String objectLabel) {
        String playUrl = object == null ? "" : decodeMacCmsUrl(object.optString("url", ""), object.optInt("encrypt", 0));
        playUrl = playUrl == null ? "" : playUrl.trim();
        if (playUrl.isEmpty() || isMediaUrl(playUrl)) {
            return;
        }
        String playFrom = object.optString("from", "").trim();
        String linkNext = object.optString("link_next", "").trim();
        String base = siteRoot(pageUrl, "https://gimy01.tv");
        String normalIframe = joinUrl(base, "/aiplayer/dp/");
        String iframeBase = normalIframe;
        String jctype = "normal";
        if (isOneOf(playFrom, "JD4K", "JD2K", "JDQM", "JDHG")) {
            iframeBase = "https://play.gimy01.tv/dp/";
            jctype = playFrom;
        } else if (isOneOf(playFrom, "JSYBL", "JSYMG", "JSYQY", "JSYDJ", "JSYHS", "JSYRR", "JSYYK", "JSYTX")) {
            iframeBase = "https://play.gimy01.tv/i/";
            jctype = playFrom;
        } else if ("djplayer".equals(playFrom) || playUrl.startsWith("JinLiDj-")) {
            iframeBase = joinUrl(base, "/aiplayer/jin.php");
            jctype = playFrom.isEmpty() ? "djplayer" : playFrom;
        } else if ("JK2".equals(playFrom) || playUrl.startsWith("JK2-")) {
            iframeBase = joinUrl(base, "/aiplayer/");
            jctype = playFrom.isEmpty() ? "JK2" : playFrom;
        } else if ("Disney".equals(playFrom) || "qingshan".equals(playFrom)) {
            iframeBase = joinUrl(base, "/gimyplayer/");
            jctype = playFrom;
        }
        addGimyIframeCandidate(out, labels, seen, iframeBase, playUrl, jctype, linkNext, base, objectLabel);
        if (!iframeBase.equals(normalIframe)) {
            addGimyIframeCandidate(out, labels, seen, normalIframe, playUrl, "normal", "", base, objectLabel);
        }
    }

    private static void addGimyIframeCandidate(List<String> out, Map<String, String> labels, Set<String> seen, String iframeBase, String playUrl, String jctype, String linkNext, String base, String objectLabel) {
        StringBuilder builder = new StringBuilder(iframeBase);
        builder.append(iframeBase.contains("?") ? "&" : "?");
        builder.append("url=").append(urlEncode(playUrl));
        builder.append("&jctype=").append(urlEncode(jctype));
        if (linkNext != null && !linkNext.trim().isEmpty()) {
            builder.append("&next=").append(urlEncode(joinUrl(base, linkNext)));
        }
        String candidate = builder.toString();
        if (seen.add(candidate)) {
            out.add(candidate);
            putLabel(labels, candidate, firstNonEmpty(objectLabel, "Gimy player iframe"));
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

    private static JSONObject extractXiaoyaPpObject(String pageText) {
        String text = pageText == null ? "" : pageText;
        Matcher matcher = XIAOYA_PP_START.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String blob = balancedObject(text, matcher.end() - 1);
        return blob == null ? null : parseLooseJsonObject(blob);
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

    private static String decodeJsonUrl(String rawUrl) {
        String decoded = rawUrl == null ? "" : rawUrl.trim();
        if (decoded.isEmpty()) {
            return decoded;
        }
        return decoded
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003F", "?")
                .replace("\\u0025", "%")
                .replace("&amp;", "&");
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
            out.add(enrichedLabel(candidate, labels.containsKey(candidate) ? labels.get(candidate) : ""));
        }
        return out;
    }

    private static String enrichedLabel(String url, String resolverLabel) {
        StringBuilder builder = new StringBuilder();
        appendLabelPart(builder, cleanLabel(resolverLabel));
        appendLabelPart(builder, mediaKindLabel(url));
        appendLabelPart(builder, qualityLabel(url));
        appendLabelPart(builder, episodeLabel(url));
        appendLabelPart(builder, sourceHostLabel(url));
        String label = builder.toString();
        return label.length() > 72 ? label.substring(0, 72) : label;
    }

    private static void appendLabelPart(StringBuilder builder, String value) {
        String cleaned = cleanLabel(value);
        if (cleaned.isEmpty()) {
            return;
        }
        String lower = builder.toString().toLowerCase(Locale.US);
        if (lower.contains(cleaned.toLowerCase(Locale.US))) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(cleaned);
    }

    private static String mediaKindLabel(String url) {
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        if (lowered.contains(".m3u8")) return "HLS";
        if (lowered.contains(".mpd")) return "DASH";
        if (lowered.contains(".mp4")) return "MP4";
        if (lowered.contains(".webm")) return "WEBM";
        if (lowered.contains(".m4v")) return "M4V";
        if (lowered.contains("/player") || lowered.contains("/parse") || lowered.contains("/vodplay/")) return "PLAYER";
        return "";
    }

    private static String qualityLabel(String url) {
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        Matcher pMatcher = Pattern.compile("(?<!\\d)(2160|1440|1080|720|480|360)p?(?!\\d)").matcher(lowered);
        if (pMatcher.find()) {
            return pMatcher.group(1) + "p";
        }
        Matcher sizeMatcher = Pattern.compile("(?<!\\d)(3840x2160|2560x1440|1920x1080|1280x720|854x480|640x360)(?!\\d)").matcher(lowered);
        if (!sizeMatcher.find()) {
            return "";
        }
        String size = sizeMatcher.group(1);
        if (size.endsWith("2160")) return "2160p";
        if (size.endsWith("1440")) return "1440p";
        if (size.endsWith("1080")) return "1080p";
        if (size.endsWith("720")) return "720p";
        if (size.endsWith("480")) return "480p";
        if (size.endsWith("360")) return "360p";
        return "";
    }

    private static String episodeLabel(String url) {
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        Matcher epMatcher = Pattern.compile("(?:/|[-_])ep(?:isode)?[-_ ]?(\\d+)(?:\\D|$)").matcher(lowered);
        if (epMatcher.find()) {
            return "EP" + epMatcher.group(1);
        }
        Matcher playMatcher = Pattern.compile("/(?:vod)?play/(?:id/)?\\d+/(?:sid/)?\\d+/(?:nid/)?(\\d+)").matcher(lowered);
        if (playMatcher.find()) {
            return "EP" + playMatcher.group(1);
        }
        Matcher htmlMatcher = Pattern.compile("/(?:play/)?\\d+/(?:\\d+[-_])?(\\d+)\\.html").matcher(lowered);
        return htmlMatcher.find() ? "EP" + htmlMatcher.group(1) : "";
    }

    private static String sourceHostLabel(String url) {
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        String[][] markers = new String[][]{
                {"xluuss", "XLU"}, {"lzcdn", "LZ"}, {"hhuus", "HH"},
                {"qsstvw", "QSS"}, {"gsuus", "GS"}, {"bfllvip", "BFL"},
                {"ppqrrs", "PPQ"}, {"qqqrst", "QQQ"}, {"vodcnd", "VODCND"},
                {"phimgood", "PHIM"}, {"ryiplay", "RYI"}, {"huyall", "HUYA"},
                {"ijycnd", "IJY"}, {"jisuzyv", "JISU"}, {"taopianplay1", "TAOPIAN"},
                {"dmcdn.net", "DMCDN"}, {"googlevideo", "GoogleVideo"},
                {"bilivideo", "BiliVideo"}, {"cdninstagram", "InstagramCDN"},
                {"fbcdn", "FacebookCDN"}, {"video.twimg.com", "TwitterVideo"}
        };
        for (String[] marker : markers) {
            if (lowered.contains(marker[0])) {
                return marker[1];
            }
        }
        return "";
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

    private static boolean isOneOf(String value, String... choices) {
        for (String choice : choices) {
            if (choice.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String htmlDecoded(String raw) {
        return (raw == null ? "" : raw)
                .replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
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
                || target.contains("instagram")
                || target.contains("cdninstagram")
                || target.contains("facebook")
                || target.contains("fbcdn")
                || target.contains("twitter")
                || target.contains("twimg")
                || isAdultMediaHost(target)
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

    private static String siteRoot(String pageUrl, String fallback) {
        try {
            URL url = new URL(pageUrl);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String joinUrl(String base, String value) {
        try {
            return new URL(new URL(base), value == null ? "" : value).toString();
        } catch (MalformedURLException ignored) {
            return value == null ? "" : value;
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private static String queryParameter(String rawUrl, String key) {
        try {
            Uri uri = Uri.parse(rawUrl);
            String value = uri.getQueryParameter(key);
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeMovieFfmExternalUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return null;
        }
        String decoded = htmlDecoded(rawUrl).trim();
        Uri uri = Uri.parse(decoded);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        if (!(host.contains("mixdrop.ag") || host.contains("m1xdrop.click"))) {
            return decoded;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.split("/");
        if (parts.length >= 3 && ("e".equals(parts[1]) || "f".equals(parts[1]))) {
            return (uri.getScheme() == null ? "https" : uri.getScheme()) + "://" + uri.getHost() + "/e/" + parts[2];
        }
        return decoded;
    }

    private static boolean isMovieFfmExternalHost(String rawUrl) {
        String host = Uri.parse(rawUrl).getHost();
        String lowered = host == null ? "" : host.toLowerCase(Locale.US);
        return lowered.contains("mixdrop.ag")
                || lowered.contains("m1xdrop.click")
                || isDoodFamilyHost(lowered)
                || lowered.contains("evoload.io");
    }

    private static boolean isDoodFamilyHost(String loweredHost) {
        String host = loweredHost == null ? "" : loweredHost.toLowerCase(Locale.US);
        return host.contains("dood.video")
                || host.contains("doodstream.")
                || host.contains("d000d.")
                || host.contains("do7go.com")
                || host.contains("dooood.")
                || host.contains("dood.")
                || host.contains("dood.so")
                || host.contains("dood.pm")
                || host.contains("dood.wf")
                || host.contains("dood.re")
                || host.contains("dood.yt");
    }

    private static boolean isXiaoyaPlayUrl(String rawUrl) {
        Uri uri = Uri.parse(rawUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
        return host.contains("xiaoyakankan") && path.contains("/vod/play/id/");
    }

    private static String decodeGgJavEmbedMediaUrl(String embedUrl) {
        if (embedUrl == null || embedUrl.trim().isEmpty()) {
            return "";
        }
        try {
            String encoded = Uri.parse(embedUrl).getQueryParameter("u");
            if (encoded == null || encoded.trim().isEmpty()) {
                return "";
            }
            String padded = encoded + repeat("=", (4 - encoded.length() % 4) % 4);
            byte[] bytes;
            try {
                bytes = Base64.decode(padded, Base64.URL_SAFE);
            } catch (IllegalArgumentException ignored) {
                bytes = Base64.decode(padded, Base64.DEFAULT);
            }
            return new String(bytes, "UTF-8").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<String> decodeGgJavObfuscatedLinks(String encodedValue) {
        List<String> urls = new ArrayList<>();
        if (encodedValue == null || encodedValue.trim().isEmpty()) {
            return urls;
        }
        try {
            String encoded = encodedValue.trim();
            String padded = encoded + repeat("=", (4 - encoded.length() % 4) % 4);
            byte[] payload = Base64.decode(padded, Base64.DEFAULT);
            StringBuilder decodedBuilder = new StringBuilder();
            for (byte rawByte : payload) {
                decodedBuilder.append((char) ((rawByte & 0xff) - 0x58));
            }
            JSONObject object = new JSONObject(decodedBuilder.toString());
            JSONArray names = object.names();
            if (names == null) {
                return urls;
            }
            for (int i = 0; i < names.length(); i++) {
                Object raw = object.opt(String.valueOf(names.opt(i)));
                collectGgJavObfuscatedValue(urls, raw);
            }
        } catch (Exception ignored) {
            return urls;
        }
        return urls;
    }

    private static void collectGgJavObfuscatedValue(List<String> urls, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                collectGgJavObfuscatedValue(urls, array.opt(i));
            }
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty() && !urls.contains(value)) {
            urls.add(value);
        }
    }

    private static List<String> expandGgJavMediaVariants(String rawUrl) {
        List<String> variants = new ArrayList<>();
        String normalized = rawUrl == null ? "" : rawUrl.trim().replace("\\/", "/").replace("&amp;", "&");
        if (normalized.isEmpty()) {
            return variants;
        }
        addUnique(variants, normalized);
        Uri uri = Uri.parse(normalized);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.toLowerCase(Locale.US).endsWith(".mp4")) {
            addUnique(variants, rebuildUri(uri, path + "/index.m3u8"));
        } else if (path.toLowerCase(Locale.US).endsWith(".mp4/index.m3u8")) {
            addUnique(variants, rebuildUri(uri, path.substring(0, path.length() - "/index.m3u8".length())));
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        Matcher hostMatcher = Pattern.compile("video-(\\d+)\\.ggjav\\.com", Pattern.CASE_INSENSITIVE).matcher(host);
        if (!hostMatcher.matches()) {
            return variants;
        }
        List<String> current = new ArrayList<>(variants);
        for (String candidate : current) {
            Uri candidateUri = Uri.parse(candidate);
            for (int i = 1; i <= 8; i++) {
                String altHost = "video-" + i + ".ggjav.com";
                if (!altHost.equals(host)) {
                    addUnique(variants, rebuildUri(candidateUri, candidateUri.getPath(), altHost));
                }
            }
        }
        return variants;
    }

    private static void addUnique(List<String> out, String value) {
        if (value != null && !value.trim().isEmpty() && !out.contains(value)) {
            out.add(value);
        }
    }

    private static String rebuildUri(Uri uri, String path) {
        return rebuildUri(uri, path, uri.getHost());
    }

    private static String rebuildUri(Uri uri, String path, String host) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(uri.getScheme() == null ? "https" : uri.getScheme())
                .encodedAuthority(host == null ? "" : host)
                .encodedPath(path == null ? "" : path);
        if (uri.getEncodedQuery() != null) {
            builder.encodedQuery(uri.getEncodedQuery());
        }
        if (uri.getEncodedFragment() != null) {
            builder.encodedFragment(uri.getEncodedFragment());
        }
        return builder.build().toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static boolean looksLikeMediaOrPlayer(String url) {
        String lowered = url.toLowerCase(Locale.US);
        return isMediaUrl(url)
                || isMovieFfmExternalHost(url)
                || lowered.contains("/parse")
                || lowered.contains("/player")
                || lowered.contains("/dp/")
                || lowered.contains("/ap/")
                || lowered.contains("/play/")
                || lowered.contains("/vodplay/")
                || lowered.contains("/watch/")
                || lowered.contains("/video/")
                || lowered.contains("/videos/")
                || lowered.contains("/embed/")
                || lowered.contains("/amateurjav_content/")
                || lowered.contains("/get/video/")
                || lowered.contains("/eps/")
                || lowered.contains("/episode/")
                || lowered.contains("/vod/detail/")
                || lowered.contains("/voddetail/")
                || lowered.contains("/voddetail2/")
                || lowered.contains("/detail/")
                || lowered.contains("/title/")
                || lowered.contains("/index.php/vod/play/")
                || lowered.contains("/index.php/vod/detail/")
                || lowered.contains("/dianying/")
                || lowered.contains("/dianshiju/")
                || lowered.contains("/zongyi/")
                || lowered.contains("/dongman/")
                || lowered.contains("animevideo.php?sn=");
    }

    private static boolean isSocialSite(String site) {
        return "instagram".equals(site) || "facebook".equals(site) || "twitter".equals(site);
    }

    private static boolean isSocialMediaCandidate(String site, String url) {
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        if (isMediaUrl(lowered)) {
            return true;
        }
        if ("instagram".equals(site)) {
            return lowered.contains("cdninstagram") || lowered.contains("fbcdn") || lowered.contains("/video/");
        }
        if ("facebook".equals(site)) {
            return lowered.contains("fbcdn") || lowered.contains("fbsbx") || lowered.contains("/video/");
        }
        if ("twitter".equals(site)) {
            return lowered.contains("video.twimg.com") || lowered.contains("/ext_tw_video/");
        }
        return false;
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
            if (isMovieFfmExternalHost(url)) {
                score -= 150;
            }
        } else if ("gimy".equals(sourceSite)) {
            score += hostPenalty(lowered, new String[]{"xluuss", "phimgood", "ppqrrs", "ryplay", "ryiplay", "yzzy", "hhiklm", "jisuziyuanbf", "dytt-cinema", "dytt-kan", "modujx", "jisutian", "jisuzyv"});
        } else if ("xiaoyakankan".equals(sourceSite)) {
            score += hostPenalty(lowered, new String[]{"huyall", "ijycnd", "jisuzyv", "gsuus", "qsstvw", "taopianplay1"});
        } else if ("mixdrop".equals(sourceSite) || "dood".equals(sourceSite) || "evoload".equals(sourceSite)) {
            if (isMediaUrl(lowered)) {
                score -= 160;
            }
            if (isMovieFfmExternalHost(url)) {
                score -= 80;
            }
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
        } else if ("instagram".equals(sourceSite)) {
            if (lowered.contains("cdninstagram") || lowered.contains("fbcdn")) {
                score -= 80;
            }
        } else if ("facebook".equals(sourceSite)) {
            if (lowered.contains("fbcdn") || lowered.contains("fbsbx")) {
                score -= 80;
            }
        } else if ("twitter".equals(sourceSite)) {
            if (lowered.contains("video.twimg.com")) {
                score -= 100;
            }
        } else if (isAdultLikeSite(sourceSite)) {
            if (isMediaUrl(lowered)) {
                score -= 100;
            }
            if (lowered.contains("/video/")
                    || lowered.contains("/videos/")
                    || lowered.contains("/embed/")
                    || lowered.contains("/amateurjav_content/")
                    || lowered.contains("/get/video/")) {
                score -= 90;
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
                    || lowered.contains("/voddetail/")
                    || lowered.contains("/voddetail2/")
                    || lowered.contains("/index.php/vod/detail/")
                    || lowered.contains("/detail/")
                    || lowered.contains("/title/")
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
                || isAdultLikeSite(site)
                || isMacCmsLikeSite(site);
    }

    private static String adultSourceSite(String host) {
        String lowered = host == null ? "" : host.toLowerCase(Locale.US);
        if (lowered.contains("missav")) return "missav";
        if (lowered.contains("jable.tv")) return "jable";
        if (lowered.contains("njavtv")) return "njavtv";
        if (lowered.contains("njav")) return "njav";
        if (lowered.contains("supjav")) return "supjav";
        if (lowered.contains("hanime1")) return "hanime1";
        if (lowered.contains("18jav")) return "18jav";
        if (lowered.contains("18av")) return "18av";
        if (lowered.contains("85xvideo")) return "85xvideo";
        if (lowered.contains("avbebe")) return "avbebe";
        if (lowered.contains("avjoy")) return "avjoy";
        if (lowered.contains("bestjavporn")) return "bestjavporn";
        if (lowered.contains("javdock")) return "javdock";
        if (lowered.contains("javfilms")) return "javfilms";
        if (lowered.contains("tinyavideo")) return "tinyavideo";
        if (lowered.contains("goodav17")) return "goodav17";
        if (lowered.contains("hohoj")) return "hohoj";
        if (lowered.contains("ggjav")) return "ggjav";
        if (lowered.contains("tktube")) return "tktube";
        return "";
    }

    private static boolean isAdultMediaHost(String host) {
        String lowered = host == null ? "" : host.toLowerCase(Locale.US);
        return !adultSourceSite(lowered).isEmpty()
                || lowered.contains("mushroomtrack")
                || lowered.contains("cdnlab")
                || lowered.contains("sb-cd.com")
                || lowered.contains("cdn77.org")
                || lowered.contains("cc3001.dmm.co.jp")
                || lowered.contains("dmm.co.jp");
    }

    private static boolean isAdultLikeSite(String site) {
        return "missav".equals(site)
                || "jable".equals(site)
                || "njav".equals(site)
                || "njavtv".equals(site)
                || "supjav".equals(site)
                || "hanime1".equals(site)
                || "18jav".equals(site)
                || "18av".equals(site)
                || "85xvideo".equals(site)
                || "avbebe".equals(site)
                || "avjoy".equals(site)
                || "bestjavporn".equals(site)
                || "javdock".equals(site)
                || "javfilms".equals(site)
                || "tinyavideo".equals(site)
                || "goodav17".equals(site)
                || "hohoj".equals(site)
                || "ggjav".equals(site)
                || "tktube".equals(site);
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
