package tw.wajay.aitestdownloader;

import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VideoSearchResolver {
    static final String SEARCH_SCHEME = "aitestsearch";
    static final String SEARCH_HOST = "video";

    private static final int MAX_RESULTS = 24;
    private static final int SITE_SEARCH_FETCH_LIMIT = 10;
    private static final int SITE_SEARCH_LINK_LIMIT = 8;
    private static final Pattern DDG_RESULT = Pattern.compile(
            "<a[^>]+class=[\"'][^\"']*result__a[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private VideoSearchResolver() {
    }

    static final class Result {
        final String url;
        final String title;
        final String sourceSite;
        final String refererUrl;

        Result(String url, String title, String sourceSite, String refererUrl) {
            this.url = url;
            this.title = title;
            this.sourceSite = sourceSite;
            this.refererUrl = refererUrl == null ? "" : refererUrl;
        }
    }

    static String searchUri(String query) {
        return new Uri.Builder()
                .scheme(SEARCH_SCHEME)
                .authority(SEARCH_HOST)
                .appendQueryParameter("q", query == null ? "" : query.trim())
                .build()
                .toString();
    }

    static boolean isSearchUri(String rawUrl) {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        return SEARCH_SCHEME.equals(uri.getScheme()) && SEARCH_HOST.equals(uri.getHost());
    }

    static String queryFromUri(String rawUrl) {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        String query = uri.getQueryParameter("q");
        return query == null ? "" : query.trim();
    }

    static boolean looksLikeSearchText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > 160) {
            return false;
        }
        if (Pattern.compile("https?://", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            return false;
        }
        if (!extractJavCode(text).isEmpty()) {
            return true;
        }
        if (containsCjkKanaHangul(text) && text.length() >= 2) {
            return true;
        }
        int alnum = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                alnum++;
            }
        }
        return alnum >= 3 && Pattern.compile("[A-Za-z]").matcher(text).find();
    }

    static List<Result> search(String query) throws IOException {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Result> results = new ArrayList<>();
        addDirectSiteSearchResults(cleanQuery, seen, results);
        for (String searchQuery : searchQueries(cleanQuery)) {
            try {
                collectDuckDuckGoResults(searchQuery, seen, results);
            } catch (IOException ignored) {
                // Site-search candidates above still let the engine try supported sites offline from search-engine availability.
            }
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results;
    }

    private static void addDirectSiteSearchResults(String query, Set<String> seen, List<Result> out) throws IOException {
        String encoded = URLEncoder.encode(query, "UTF-8");
        String pathEncoded = encoded.replace("+", "%20");
        String javCode = extractJavCode(query);
        if (!javCode.isEmpty()) {
            addJavDirectResults(javCode, seen, out);
        }
        String[][] templates = javCode.isEmpty() ? generalTemplates() : javTemplatesFirst();
        int fetchedTemplates = 0;
        for (String[] template : templates) {
            if (out.size() >= MAX_RESULTS) {
                return;
            }
            String value = String.format(Locale.US, template[1], template[1].contains("/search/%s") ? pathEncoded : encoded);
            if (fetchedTemplates < SITE_SEARCH_FETCH_LIMIT) {
                collectSiteSearchResults(value, template[0], query, seen, out);
                fetchedTemplates++;
            }
            addResult(value, template[0] + " site search: " + query, "", seen, out);
        }
    }

    private static String[][] generalTemplates() {
        return new String[][]{
                {"MovieFFM", "https://www.movieffm.net/?s=%s"},
                {"XiaoyaKankan", "https://tw.xiaoyakankan.com/vod/search.html?wd=%s"},
                {"XiaoyaKankan", "https://tw.xiaoyakankan.tv/vod/search.html?wd=%s"},
                {"XiaoyaKankan", "https://tw.xiaoyakankan.io/vod/search.html?wd=%s"},
                {"Gimy", "https://gimy.ai/search.html?wd=%s"},
                {"Gimy", "https://gimy.tw/search.html?wd=%s"},
                {"DramaSQ", "https://dramasq.io/search.html?wd=%s"},
                {"Olevod", "https://olevod.com/search.html?wd=%s"},
                {"Olevod", "https://olevod.com/index.php/vod/search.html?wd=%s"},
                {"3KOR", "https://3kor.com/search.html?wd=%s"},
                {"NNYY", "https://nnyy.in/vodsearch/-------------.html?wd=%s"},
                {"99iTV", "https://99itv.net/vodsearch/-------------.html?wd=%s"},
                {"777TV", "https://777tv.ai/vodsearch/-------------.html?wd=%s"},
                {"777TV", "https://777tv.ai/index.php/vod/search.html?wd=%s"},
                {"Ikanbot", "https://www1.ikanbot.com/vodsearch/-------------.html?wd=%s"},
                {"YFSP", "https://www.yfsp.tv/vodsearch/-------------.html?wd=%s"},
                {"MissAV", "https://missav.ws/search/%s"},
                {"Jable", "https://jable.tv/search/%s/"},
                {"NJAV", "https://www.njav.com/search?keyword=%s"},
                {"NJAVTV", "https://njavtv.com/search/%s"},
                {"SupJAV", "https://supjav.com/?s=%s"},
                {"BestJavPorn", "https://bestjavporn.com/?s=%s"},
                {"JavDock", "https://www.javdock.com/search/%s"},
                {"JavFilms", "https://javfilms.com/search/%s"},
                {"18JAV", "https://18jav.tv/search/%s"},
                {"AVBebe", "https://avbebe.com/search/%s"},
                {"AVJoy", "https://avjoy.me/search/videos?search_query=%s"},
                {"HoHoJ", "https://hohoj.tv/search/%s"},
                {"GGJAV", "https://ggjav.com/search/%s"}
        };
    }

    private static String[][] javTemplatesFirst() {
        return new String[][]{
                {"MissAV", "https://missav.ws/search/%s"},
                {"Jable", "https://jable.tv/search/%s/"},
                {"NJAV", "https://www.njav.com/search?keyword=%s"},
                {"NJAVTV", "https://njavtv.com/search/%s"},
                {"SupJAV", "https://supjav.com/?s=%s"},
                {"BestJavPorn", "https://bestjavporn.com/?s=%s"},
                {"JavDock", "https://www.javdock.com/search/%s"},
                {"JavFilms", "https://javfilms.com/search/%s"},
                {"18JAV", "https://18jav.tv/search/%s"},
                {"AVBebe", "https://avbebe.com/search/%s"},
                {"AVJoy", "https://avjoy.me/search/videos?search_query=%s"},
                {"HoHoJ", "https://hohoj.tv/search/%s"},
                {"GGJAV", "https://ggjav.com/search/%s"},
                {"MovieFFM", "https://www.movieffm.net/?s=%s"},
                {"XiaoyaKankan", "https://tw.xiaoyakankan.com/vod/search.html?wd=%s"},
                {"Gimy", "https://gimy.ai/search.html?wd=%s"},
                {"Gimy", "https://gimy.tw/search.html?wd=%s"}
        };
    }

    private static void addJavDirectResults(String javCode, Set<String> seen, List<Result> out) {
        String slug = javCode.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String compact = javCode.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        String[][] direct = new String[][]{
                {"MissAV", "https://missav.ws/" + slug},
                {"Jable", "https://jable.tv/videos/" + slug + "/"},
                {"NJAV", "https://www.njav.com/tw/xvideos/" + slug},
                {"NJAV", "https://www.njav.com/tw/xvideos/" + slug + "-uncensored-leak"},
                {"SupJAV", "https://supjav.com/" + compact + ".html"},
                {"JavDock", "https://www.javdock.com/video/" + slug},
                {"BestJavPorn", "https://bestjavporn.com/video/" + slug + "/"},
                {"JavFilms", "https://javfilms.com/video/" + slug + "/"},
                {"18JAV", "https://18jav.tv/videos/" + slug + "/"},
                {"HoHoJ", "https://hohoj.tv/" + slug + "/"}
        };
        for (String[] item : direct) {
            if (out.size() >= MAX_RESULTS) {
                return;
            }
            addResult(item[1], item[0] + " direct code: " + javCode, "", seen, out);
        }
    }

    private static List<String> searchQueries(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query + " download video");
        queries.add(query + " m3u8 mp4");
        String[] sites = new String[]{
                "movieffm.net", "gimy", "xiaoyakankan.com", "dramasq", "olevod.com",
                "3kor.com", "99itv.net", "nnyy.in", "missav", "jable.tv", "njav",
                "supjav.com", "bestjavporn.com", "javdock.com", "javfilms.com",
                "18jav.tv", "avbebe.com", "avjoy.me", "hohoj.tv", "ggjav.com"
        };
        for (String site : sites) {
            queries.add(query + " site:" + site);
        }
        return queries;
    }

    private static void collectDuckDuckGoResults(String query, Set<String> seen, List<Result> out) throws IOException {
        String html = fetch("https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"));
        collectMatches(DDG_RESULT.matcher(html), seen, out);
        if (out.size() < MAX_RESULTS) {
            collectMatches(LINK.matcher(html), seen, out);
        }
    }

    private static void collectSiteSearchResults(
            String searchUrl,
            String sourceLabel,
            String query,
            Set<String> seen,
            List<Result> out) {
        try {
            String html = fetch(searchUrl, 7000, 9000);
            List<RankedResult> ranked = extractSearchPageLinks(html, searchUrl, sourceLabel, query);
            for (RankedResult result : ranked) {
                if (out.size() >= MAX_RESULTS) {
                    return;
                }
                addResult(result.url, result.title, searchUrl, seen, out);
            }
        } catch (IOException ignored) {
            // Keep the direct search page as a fallback candidate when a site blocks or times out.
        }
    }

    private static List<RankedResult> extractSearchPageLinks(String html, String baseUrl, String sourceLabel, String query) {
        List<RankedResult> ranked = new ArrayList<>();
        Set<String> localSeen = new LinkedHashSet<>();
        Matcher matcher = LINK.matcher(html == null ? "" : html);
        while (matcher.find() && ranked.size() < SITE_SEARCH_LINK_LIMIT * 3) {
            String url = normalizeResultUrl(matcher.group(1), baseUrl);
            if (url.isEmpty() || !localSeen.add(url) || !looksLikeSearchResultPath(url)) {
                continue;
            }
            String title = cleanTitle(matcher.group(2));
            int score = resultMatchScore(title, url, query);
            if (score < 0) {
                continue;
            }
            ranked.add(new RankedResult(url, sourceLabel + ": " + title, score));
        }
        Collections.sort(ranked, new Comparator<RankedResult>() {
            @Override
            public int compare(RankedResult left, RankedResult right) {
                return Integer.compare(right.score, left.score);
            }
        });
        if (ranked.size() > SITE_SEARCH_LINK_LIMIT) {
            return new ArrayList<>(ranked.subList(0, SITE_SEARCH_LINK_LIMIT));
        }
        return ranked;
    }

    private static void collectMatches(Matcher matcher, Set<String> seen, List<Result> out) {
        while (matcher.find() && out.size() < MAX_RESULTS) {
            String url = normalizeResultUrl(matcher.group(1));
            addResult(url, cleanTitle(matcher.group(2)), "", seen, out);
        }
    }

    private static void addResult(String url, String title, String refererUrl, Set<String> seen, List<Result> out) {
        if (url == null || url.isEmpty() || !isSupportedCandidate(url) || !seen.add(url)) {
            return;
        }
        out.add(new Result(url, cleanTitle(title), MediaResolver.sourceSite(url), refererUrl));
    }

    private static String normalizeResultUrl(String raw) {
        String value = htmlDecode(raw == null ? "" : raw.trim());
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        try {
            Uri uri = Uri.parse(value);
            String encoded = uri.getQueryParameter("uddg");
            if (encoded != null && !encoded.trim().isEmpty()) {
                value = URLDecoder.decode(encoded, "UTF-8");
            }
        } catch (Exception ignored) {
        }
        return value.startsWith("http://") || value.startsWith("https://") ? value : "";
    }

    private static String normalizeResultUrl(String raw, String baseUrl) {
        String value = htmlDecode(raw == null ? "" : raw.trim());
        if (value.isEmpty()
                || value.startsWith("#")
                || value.startsWith("javascript:")
                || value.startsWith("mailto:")
                || value.startsWith("tel:")) {
            return "";
        }
        try {
            return new URL(new URL(baseUrl), value).toString();
        } catch (Exception ignored) {
            return normalizeResultUrl(value);
        }
    }

    private static boolean isSupportedCandidate(String url) {
        String site = MediaResolver.sourceSite(url);
        if (!"generic".equals(site)) {
            return true;
        }
        String lowered = url == null ? "" : url.toLowerCase(Locale.US);
        return lowered.contains(".m3u8") || lowered.contains(".mp4") || lowered.contains(".mpd");
    }

    private static String fetch(String rawUrl) throws IOException {
        return fetch(rawUrl, 15000, 20000);
    }

    private static String fetch(String rawUrl, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("search HTTP " + code);
        }
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    private static boolean looksLikeSearchResultPath(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".mpd")) {
            return true;
        }
        String[] markers = new String[]{
                "/voddetail/", "/voddetail2/", "/vodplay/", "/vod/",
                "/index.php/vod/detail/", "/index.php/vod/play/",
                "/detail/", "/details/", "/title/", "/play/", "/watch/",
                "/video/", "/videos/", "/movie/", "/drama/", "/episode/",
                "/eps/", "/xvideos/", "/archives/", "/html/", "/vr_html/",
                "/dm", "/fc2-ppv-", "/fc2ppv-"
        };
        for (String marker : markers) {
            if (url.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static int resultMatchScore(String title, String url, String query) {
        String haystack = normalizeSearchText(title + " " + url);
        String normalizedQuery = normalizeSearchText(query);
        String javCode = extractJavCode(query);
        int score = 0;
        if (!javCode.isEmpty()) {
            String compactCode = normalizeSearchText(javCode);
            if (haystack.contains(compactCode)) {
                score += 100;
            } else {
                String[] codeParts = compactCode.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
                for (String part : codeParts) {
                    if (part.length() >= 2 && haystack.contains(part)) {
                        score += 18;
                    }
                }
            }
        }
        if (!normalizedQuery.isEmpty() && haystack.contains(normalizedQuery)) {
            score += 60;
        }
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.length() >= 2 && haystack.contains(token)) {
                score += 12;
            }
        }
        return score > 0 ? score : 1;
    }

    private static String normalizeSearchText(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.US)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String cleanTitle(String raw) {
        String title = htmlDecode(raw == null ? "" : raw
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim());
        return title.length() > 80 ? title.substring(0, 80) : title;
    }

    private static String htmlDecode(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String extractJavCode(String value) {
        String text = value == null ? "" : value.trim();
        Matcher fc2 = Pattern.compile("\\bFC2[-_\\s]*PPV[-_\\s]*(\\d{3,10})\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (fc2.find()) {
            return "FC2-PPV-" + fc2.group(1);
        }
        Matcher matcher = Pattern.compile("\\b([A-Za-z]{2,10})[-_. ]?(\\d{2,6})([A-Za-z])?\\b").matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String suffix = matcher.group(3) == null ? "" : matcher.group(3).toUpperCase(Locale.US);
        return matcher.group(1).toUpperCase(Locale.US) + "-" + matcher.group(2) + suffix;
    }

    private static boolean containsCjkKanaHangul(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 0x4E00 && ch <= 0x9FFF)
                    || (ch >= 0x3040 && ch <= 0x30FF)
                    || (ch >= 0xAC00 && ch <= 0xD7AF)) {
                return true;
            }
        }
        return false;
    }

    private static final class RankedResult {
        final String url;
        final String title;
        final int score;

        RankedResult(String url, String title, int score) {
            this.url = url;
            this.title = title;
            this.score = score;
        }
    }
}
