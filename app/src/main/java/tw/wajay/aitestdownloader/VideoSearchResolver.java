package tw.wajay.aitestdownloader;

import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    private static final Pattern DATA_LINK = Pattern.compile(
            "\\b(?:data-url|data-href|data-src|data-play|data-link|data-video|data-clipboard-text)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_SRC = Pattern.compile(
            "<(?:img|source|video)\\b[^>]+(?:src|poster|data-src|data-original|data-lazy-src|data-thumb|data-thumbnail|data-image|data-cover|data-poster|data-background-image)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMAGE_SRCSET = Pattern.compile(
            "<img\\b[^>]+(?:srcset|data-srcset)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMAGE_ATTR_URL = Pattern.compile(
            "\\b(?:src|poster|data-src|data-original|data-lazy-src|data-thumb|data-thumbnail|data-image|data-cover|data-poster|data-background-image)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_IMAGE = Pattern.compile(
            "(?:background|background-image)\\s*:\\s*url\\((['\"]?)(.*?)\\1\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JSON_IMAGE = Pattern.compile(
            "[\"'](?:thumbnailUrl|thumbnail|thumb|image|imageUrl|poster|posterUrl|cover|coverUrl|pic|vod_pic)[\"']\\s*:\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_IMAGE = Pattern.compile(
            "<meta\\b[^>]+(?:property|name)=[\"'](?:og:image|twitter:image|twitter:image:src)[\"'][^>]+content=[\"']([^\"']+)[\"']|<meta\\b[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:image|twitter:image|twitter:image:src)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_TITLE = Pattern.compile(
            "<meta\\b[^>]+(?:property|name)=[\"'](?:og:title|twitter:title|title)[\"'][^>]+content=[\"']([^\"']+)[\"']|<meta\\b[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:title|twitter:title|title)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE_ATTR = Pattern.compile(
            "\\b(?:title|alt|aria-label|data-title|data-name)=[\"']([^\"']{2,160})[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_RESULT_ITEM = Pattern.compile(
            "<div\\b[^>]*class=[\"'][^\"']*result-item[^\"']*[\"'][^>]*>(.*?)</article>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MOVIEFFM_RESULT_TITLE = Pattern.compile(
            "<div\\b[^>]*class=[\"'][^\"']*title[^\"']*[\"'][^>]*>\\s*<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MOVIEFFM_EP_SLUG = Pattern.compile(
            "\\bep_slug=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_MEDIA_HINT = Pattern.compile(
            "(?:https?:\\\\?/\\\\?/|\\\\?/\\\\?/)[^\"'<>\\s\\\\]+(?:\\.m3u8|\\.mp4|\\.mpd)|\\b(?:player_aaaa|videourl|video_plays|play_data)\\b|/ffm/\\d+/[A-Za-z0-9_-]+|wp-json/dooplayer",
            Pattern.CASE_INSENSITIVE);
    private VideoSearchResolver() {
    }

    static final class Result {
        final String url;
        final String title;
        final String sourceSite;
        final String refererUrl;
        final String thumbnailUrl;
        final String thumbnailRefererUrl;

        Result(String url, String title, String sourceSite, String refererUrl) {
            this(url, title, sourceSite, refererUrl, "", "");
        }

        Result(String url, String title, String sourceSite, String refererUrl, String thumbnailUrl) {
            this(url, title, sourceSite, refererUrl, thumbnailUrl, refererUrl);
        }

        Result(String url, String title, String sourceSite, String refererUrl, String thumbnailUrl, String thumbnailRefererUrl) {
            this.url = url;
            this.title = title;
            this.sourceSite = sourceSite;
            this.refererUrl = refererUrl == null ? "" : refererUrl;
            this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
            this.thumbnailRefererUrl = thumbnailRefererUrl == null ? "" : thumbnailRefererUrl;
        }
    }

    static String searchUri(String query) {
        return searchUri(query, "");
    }

    static String searchUri(String query, String selectedUrl) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(SEARCH_SCHEME)
                .authority(SEARCH_HOST)
                .appendQueryParameter("q", query == null ? "" : query.trim());
        if (selectedUrl != null && !selectedUrl.trim().isEmpty()) {
            builder.appendQueryParameter("selected", selectedUrl.trim());
        }
        return builder
                .build()
                .toString();
    }

    static String selectedUrlFromUri(String rawUrl) {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        String selected = uri.getQueryParameter("selected");
        return selected == null ? "" : selected.trim();
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
        String searchable = filenameStem(text);
        if (!extractJavCode(searchable).isEmpty()) {
            return true;
        }
        if (containsCjkKanaHangul(searchable) && searchable.length() >= 2) {
            return true;
        }
        int alnum = 0;
        for (int i = 0; i < searchable.length(); i++) {
            if (Character.isLetterOrDigit(searchable.charAt(i))) {
                alnum++;
            }
        }
        return alnum >= 3 && Pattern.compile("[A-Za-z]").matcher(searchable).find();
    }

    private static String filenameStem(String value) {
        String text = value == null ? "" : value.trim();
        int slash = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < text.length()) {
            text = text.substring(slash + 1);
        }
        return text.replaceFirst("(?i)\\.(?:mp4|m4v|webm|mkv|avi|mov|ts|m3u8|mpd|part)$", "").trim();
    }

    static List<Result> search(String query) throws IOException {
        String cleanQuery = filenameStem(query == null ? "" : query.trim());
        if (cleanQuery.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Result> results = new ArrayList<>();
        addDirectSiteSearchResults(cleanQuery, seen, results);
        if (results.size() >= 3) {
            rankSearchResults(results, cleanQuery);
            return results;
        }
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
        rankSearchResults(results, cleanQuery);
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
                if (out.size() >= SITE_SEARCH_LINK_LIMIT) {
                    return;
                }
            }
        }
    }

    private static String[][] generalTemplates() {
        return new String[][]{
                {"MovieFFM", "https://www.movieffm.me/xssearch?q=%s"},
                {"MovieFFM", "https://www.movieffm.net/xssearch?q=%s"},
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
                {"iQIYI", "https://www.iq.com/search?query=%s&lang=zh_tw"},
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
                {"85xVideo", "https://85xvideo.com/search/%s"},
                {"TinyAVideo", "https://tinyavideo.com/search/%s"},
                {"GoodAV17", "https://goodav17.com/search/%s"},
                {"HoHoJ", "https://hohoj.tv/search/%s"},
                {"HayAV", "https://hayav.com/?s=%s"},
                {"GGJAV", "https://ggjav.com/search/%s"},
                {"TKTube", "https://tktube.com/search/%s"}
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
                {"85xVideo", "https://85xvideo.com/search/%s"},
                {"TinyAVideo", "https://tinyavideo.com/search/%s"},
                {"GoodAV17", "https://goodav17.com/search/%s"},
                {"HoHoJ", "https://hohoj.tv/search/%s"},
                {"HayAV", "https://hayav.com/?s=%s"},
                {"GGJAV", "https://ggjav.com/search/%s"},
                {"TKTube", "https://tktube.com/search/%s"},
                {"MovieFFM", "https://www.movieffm.me/xssearch?q=%s"},
                {"MovieFFM", "https://www.movieffm.net/xssearch?q=%s"},
                {"XiaoyaKankan", "https://tw.xiaoyakankan.com/vod/search.html?wd=%s"},
                {"Gimy", "https://gimy.ai/search.html?wd=%s"},
                {"Gimy", "https://gimy.tw/search.html?wd=%s"}
        };
    }

    private static void addJavDirectResults(String javCode, Set<String> seen, List<Result> out) {
        String slug = javCode.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String compact = javCode.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        String upperSlug = slug.toUpperCase(Locale.US);
        String[][] direct = new String[][]{
                {"MissAV", "https://missav.ws/" + slug},
                {"MissAV", "https://missav.ai/" + slug},
                {"MissAV", "https://missav.ws/dm13/en/" + slug},
                {"Jable", "https://jable.tv/videos/" + slug + "/"},
                {"Jable", "https://jable.tv/videos/" + compact + "/"},
                {"NJAV", "https://www.njav.com/tw/xvideos/" + slug},
                {"NJAV", "https://www.njav.com/tw/xvideos/" + slug + "-uncensored-leak"},
                {"NJAVTV", "https://njavtv.com/video/" + slug + "/"},
                {"SupJAV", "https://supjav.com/" + compact + ".html"},
                {"AVBebe", "https://avbebe.com/video/" + upperSlug},
                {"AVJoy", "https://avjoy.me/video/" + slug},
                {"HayAV", "https://hayav.com/video/chinese-subtitles/" + slug + "c/"},
                {"JavDock", "https://www.javdock.com/video/" + slug},
                {"BestJavPorn", "https://bestjavporn.com/video/" + slug + "/"},
                {"JavFilms", "https://javfilms.com/video/" + slug + "/"},
                {"18JAV", "https://18jav.tv/videos/" + slug + "/"},
                {"HoHoJ", "https://hohoj.tv/" + slug + "/"},
                {"GGJAV", "https://ggjav.com/video/" + slug + "/"}
        };
        for (String[] item : direct) {
            if (out.size() >= MAX_RESULTS) {
                return;
            }
            RankedResult enriched = enrichSearchResult(
                    new RankedResult(item[1], item[0] + ": " + javCode, 120, "", ""),
                    item[1]);
            addResult(enriched.url, enriched.title, "", seen, out, enriched.thumbnailUrl, enriched.thumbnailRefererUrl);
        }
    }

    private static List<String> searchQueries(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query + " download video");
        queries.add(query + " m3u8 mp4");
        String[] sites = new String[]{
                "movieffm.me", "movieffm.net", "gimy", "xiaoyakankan.com", "iq.com", "dramasq", "olevod.com",
                "3kor.com", "99itv.net", "nnyy.in", "missav", "jable.tv", "njav",
                "supjav.com", "bestjavporn.com", "javdock.com", "javfilms.com",
                "18jav.tv", "85xvideo.com", "avbebe.com", "avjoy.me", "tinyavideo.com",
                "goodav17.com", "hohoj.tv", "hayav.com", "ggjav.com", "tktube.com"
        };
        for (String site : sites) {
            queries.add(query + " site:" + site);
        }
        return queries;
    }

    private static void collectDuckDuckGoResults(String query, Set<String> seen, List<Result> out) throws IOException {
        String html = fetch("https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"));
        collectMatches(DDG_RESULT.matcher(html), query, seen, out);
        if (out.size() < MAX_RESULTS) {
            collectMatches(LINK.matcher(html), query, seen, out);
        }
    }

    private static void collectSiteSearchResults(
            String searchUrl,
            String sourceLabel,
            String query,
            Set<String> seen,
            List<Result> out) {
        try {
            String html = fetch(searchUrl, 5000, 6000);
            List<RankedResult> ranked = extractSearchPageLinks(html, searchUrl, sourceLabel, query);
            for (RankedResult result : ranked) {
                if (out.size() >= MAX_RESULTS) {
                    return;
                }
                RankedResult enriched = enrichSearchResult(result, searchUrl);
                if (resultMatchScore(enriched.title, enriched.url, query) <= 0) {
                    continue;
                }
                addResult(enriched.url, enriched.title, searchUrl, seen, out, enriched.thumbnailUrl, enriched.thumbnailRefererUrl);
            }
        } catch (IOException ignored) {
            // Skip blocked search pages; the picker should only show concrete video candidates.
        }
    }

    private static List<RankedResult> extractSearchPageLinks(String html, String baseUrl, String sourceLabel, String query) {
        return extractSearchPageLinks(html, baseUrl, sourceLabel, query, 0);
    }

    private static List<RankedResult> extractSearchPageLinks(String html, String baseUrl, String sourceLabel, String query, int depth) {
        List<RankedResult> ranked = new ArrayList<>();
        Set<String> localSeen = new LinkedHashSet<>();
        collectMovieFfmSearchCards(ranked, localSeen, html, baseUrl, sourceLabel, query);
        Matcher matcher = LINK.matcher(html == null ? "" : html);
        while (matcher.find() && ranked.size() < SITE_SEARCH_LINK_LIMIT * 3) {
            String url = normalizeResultUrl(matcher.group(1), baseUrl);
            if (url.isEmpty() || !localSeen.add(url)) {
                continue;
            }
            if (isNonVideoListingUrl(url)) {
                appendNestedListingResults(ranked, localSeen, url, sourceLabel, query, depth);
                continue;
            }
            if (!looksLikeSearchResultPath(url)) {
                continue;
            }
            String nearbyHtml = htmlWindow(html, matcher.start(), matcher.end());
            String cardHtml = resultCardHtml(html, matcher.start(), matcher.end());
            String title = firstNonEmptyTitle(extractNearbyTitle(cardHtml), extractNearbyTitle(nearbyHtml), cleanTitle(matcher.group(2)), titleFromUrl(url));
            int score = resultMatchScore(title, url, query);
            String thumbnailUrl = firstNonEmptyTitle(extractThumbnailUrl(cardHtml, baseUrl), extractThumbnailUrl(nearbyHtml, baseUrl));
            ranked.add(new RankedResult(url, sourceLabel + ": " + title, score, thumbnailUrl, baseUrl));
        }
        Matcher dataMatcher = DATA_LINK.matcher(html == null ? "" : html);
        while (dataMatcher.find() && ranked.size() < SITE_SEARCH_LINK_LIMIT * 4) {
            String url = normalizeResultUrl(dataMatcher.group(1), baseUrl);
            if (url.isEmpty() || !localSeen.add(url)) {
                continue;
            }
            if (isNonVideoListingUrl(url)) {
                appendNestedListingResults(ranked, localSeen, url, sourceLabel, query, depth);
                continue;
            }
            if (!looksLikeSearchResultPath(url)) {
                continue;
            }
            String nearbyHtml = htmlWindow(html, dataMatcher.start(), dataMatcher.end());
            String cardHtml = resultCardHtml(html, dataMatcher.start(), dataMatcher.end());
            String title = firstNonEmptyTitle(extractNearbyTitle(cardHtml), extractNearbyTitle(nearbyHtml), titleFromUrl(url), "embedded link");
            int score = resultMatchScore(title, url, query);
            String thumbnailUrl = firstNonEmptyTitle(extractThumbnailUrl(cardHtml, baseUrl), extractThumbnailUrl(nearbyHtml, baseUrl));
            ranked.add(new RankedResult(url, sourceLabel + ": " + title, score, thumbnailUrl, baseUrl));
        }
        Collections.sort(ranked, new Comparator<RankedResult>() {
            @Override
            public int compare(RankedResult left, RankedResult right) {
                return Integer.compare(right.score, left.score);
            }
        });
        int visibleLimit = SITE_SEARCH_LINK_LIMIT * 3;
        if (ranked.size() > visibleLimit) {
            return new ArrayList<>(ranked.subList(0, visibleLimit));
        }
        return ranked;
    }

    private static void collectMovieFfmSearchCards(
            List<RankedResult> ranked,
            Set<String> seen,
            String html,
            String baseUrl,
            String sourceLabel,
            String query) {
        if (baseUrl == null || !baseUrl.toLowerCase(Locale.US).contains("movieffm.")) {
            return;
        }
        Matcher cardMatcher = MOVIEFFM_RESULT_ITEM.matcher(html == null ? "" : html);
        while (cardMatcher.find() && ranked.size() < SITE_SEARCH_LINK_LIMIT * 3) {
            String cardHtml = cardMatcher.group(1);
            Matcher titleMatcher = MOVIEFFM_RESULT_TITLE.matcher(cardHtml);
            if (!titleMatcher.find()) {
                continue;
            }
            String url = normalizeResultUrl(titleMatcher.group(1), baseUrl);
            if (url.isEmpty() || !seen.add(url) || !looksLikeSearchResultPath(url)) {
                continue;
            }
            String title = firstNonEmptyTitle(cleanTitle(titleMatcher.group(2)), extractNearbyTitle(cardHtml), titleFromUrl(url));
            String thumbnailUrl = extractThumbnailUrl(cardHtml, baseUrl);
            ranked.add(new RankedResult(url, sourceLabel + ": " + title, resultMatchScore(title, url, query), thumbnailUrl, baseUrl));
        }
    }

    private static void appendNestedListingResults(
            List<RankedResult> ranked,
            Set<String> seen,
            String listingUrl,
            String sourceLabel,
            String query,
            int depth) {
        if (depth >= 1 || ranked.size() >= SITE_SEARCH_LINK_LIMIT) {
            return;
        }
        try {
            String html = fetch(listingUrl, 5000, 7000);
            List<RankedResult> nested = extractSearchPageLinks(html, listingUrl, sourceLabel, query, depth + 1);
            for (RankedResult result : nested) {
                if (ranked.size() >= SITE_SEARCH_LINK_LIMIT * 4) {
                    return;
                }
                if (result.url == null || result.url.isEmpty() || !seen.add(result.url)) {
                    continue;
                }
                ranked.add(result);
            }
        } catch (IOException ignored) {
            // Listing pages often block direct fetches; keep the visible picker to concrete pages only.
        }
    }

    private static void collectMatches(Matcher matcher, String query, Set<String> seen, List<Result> out) {
        while (matcher.find() && out.size() < MAX_RESULTS) {
            String url = normalizeResultUrl(matcher.group(1));
            if (isNonVideoListingUrl(url)) {
                addNestedListingResults(url, query, seen, out);
                continue;
            }
            String title = cleanTitle(matcher.group(2));
            RankedResult enriched = enrichSearchResult(new RankedResult(url, title, 1), "");
            if (resultMatchScore(enriched.title, enriched.url, query) <= 0) {
                continue;
            }
            addResult(enriched.url, enriched.title, "", seen, out, enriched.thumbnailUrl, enriched.thumbnailRefererUrl);
        }
    }

    private static void addNestedListingResults(String listingUrl, String query, Set<String> seen, List<Result> out) {
        if (listingUrl == null || listingUrl.isEmpty() || out.size() >= MAX_RESULTS) {
            return;
        }
        try {
            String html = fetch(listingUrl, 5000, 7000);
            String site = MediaResolver.sourceSite(listingUrl);
            List<RankedResult> nested = extractSearchPageLinks(html, listingUrl, site == null ? "" : site, query, 1);
            for (RankedResult result : nested) {
                if (out.size() >= MAX_RESULTS) {
                    return;
                }
                RankedResult enriched = enrichSearchResult(result, listingUrl);
                if (resultMatchScore(enriched.title, enriched.url, query) <= 0) {
                    continue;
                }
                addResult(enriched.url, enriched.title, listingUrl, seen, out, enriched.thumbnailUrl, enriched.thumbnailRefererUrl);
            }
        } catch (IOException ignored) {
            // External search engines often return category pages; blocked listings are simply not visible choices.
        }
    }

    private static void addResult(String url, String title, String refererUrl, Set<String> seen, List<Result> out) {
        addResult(url, title, refererUrl, seen, out, "");
    }

    private static void addResult(String url, String title, String refererUrl, Set<String> seen, List<Result> out, String thumbnailUrl) {
        addResult(url, title, refererUrl, seen, out, thumbnailUrl, refererUrl);
    }

    private static void addResult(String url, String title, String refererUrl, Set<String> seen, List<Result> out, String thumbnailUrl, String thumbnailRefererUrl) {
        if (url == null
                || url.isEmpty()
                || isNonVideoListingUrl(url)
                || looksLikeListingTitle(title)
                || !isSupportedCandidate(url)
                || !seen.add(url)) {
            return;
        }
        out.add(new Result(url, cleanTitle(title), MediaResolver.sourceSite(url), refererUrl, thumbnailUrl, thumbnailRefererUrl));
    }

    private static void rankSearchResults(List<Result> results, String query) {
        Collections.sort(results, new Comparator<Result>() {
            @Override
            public int compare(Result left, Result right) {
                int leftRank = resultRank(left, query);
                int rightRank = resultRank(right, query);
                if (leftRank != rightRank) {
                    return Integer.compare(leftRank, rightRank);
                }
                int leftSite = sitePriority(left.url);
                int rightSite = sitePriority(right.url);
                if (leftSite != rightSite) {
                    return Integer.compare(leftSite, rightSite);
                }
                return Integer.compare(cleanTitle(left.title).length(), cleanTitle(right.title).length());
            }
        });
    }

    private static int resultRank(Result result, String query) {
        String title = normalizeSearchText(result == null ? "" : result.title);
        String url = normalizeSearchText(result == null ? "" : result.url);
        String needle = normalizeSearchText(query);
        if (!needle.isEmpty() && title.equals(needle)) {
            return 0;
        }
        if (!needle.isEmpty() && title.startsWith(needle)) {
            return 1;
        }
        if (!needle.isEmpty() && title.contains(needle)) {
            return 2;
        }
        if (!needle.isEmpty() && url.contains(needle)) {
            return 3;
        }
        return 9;
    }

    private static int sitePriority(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        if (url.contains("movieffm.net") || url.contains("movieffm.me")) {
            return 8;
        }
        if (url.contains("tw.xiaoyakankan.tv")) {
            return 5;
        }
        if (url.contains("tw.xiaoyakankan.io")) {
            return 6;
        }
        if (url.contains("xiaoyakankan")) {
            return 7;
        }
        if (url.contains("gimy")) {
            return 14;
        }
        if (url.contains("iq.com")) {
            return 18;
        }
        if (url.contains("nnyy.in")) {
            return 20;
        }
        return 40;
    }

    private static RankedResult enrichSearchResult(RankedResult result, String refererUrl) {
        if (result == null || result.url == null || result.url.isEmpty()) {
            return result;
        }
        String displayTitle = result.title == null ? "" : result.title.trim();
        String titleWithoutSite = displayTitle.contains(": ")
                ? displayTitle.substring(displayTitle.indexOf(": ") + 2).trim()
                : displayTitle;
        boolean needsTitle = !looksLikeUsableTitle(titleWithoutSite)
                || "embedded link".equalsIgnoreCase(titleWithoutSite)
                || looksLikeCodeOnlyTitle(titleWithoutSite);
        boolean needsThumbnail = result.thumbnailUrl == null || result.thumbnailUrl.trim().isEmpty();
        boolean needsMovieFfmValidation = isMovieFfmResultUrl(result.url);
        boolean needsResolvableValidation = needsResolvableSearchValidation(MediaResolver.sourceSite(result.url));
        if (!needsTitle && !needsThumbnail && !needsMovieFfmValidation && !needsResolvableValidation) {
            return result;
        }
        try {
            String html = fetch(result.url, 5000, 7000);
            if (needsMovieFfmValidation && !movieFfmPageLooksDownloadable(html)) {
                return new RankedResult(result.url, "", -1000, result.thumbnailUrl, result.thumbnailRefererUrl);
            }
            if (needsResolvableValidation && !searchPageLooksResolvable(html, result.url)) {
                return new RankedResult(result.url, "", -1000, result.thumbnailUrl, result.thumbnailRefererUrl);
            }
            String pageTitle = extractPageTitle(html);
            String pageThumb = extractThumbnailUrl(html, result.url);
            String sourcePrefix = "";
            if (displayTitle.contains(": ")) {
                sourcePrefix = displayTitle.substring(0, displayTitle.indexOf(": ") + 2);
            }
            String title = needsTitle && looksLikeUsableTitle(pageTitle)
                    ? sourcePrefix + pageTitle
                    : result.title;
            String thumb = needsThumbnail && !pageThumb.isEmpty() ? pageThumb : result.thumbnailUrl;
            String thumbReferer = needsThumbnail && !pageThumb.isEmpty() ? result.url : result.thumbnailRefererUrl;
            return new RankedResult(result.url, title, result.score, thumb, thumbReferer);
        } catch (IOException ignored) {
            return result;
        }
    }

    private static boolean isMovieFfmResultUrl(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return url.contains("movieffm.me") || url.contains("movieffm.net");
    }

    private static boolean needsResolvableSearchValidation(String sourceSite) {
        if (sourceSite == null || sourceSite.isEmpty()) {
            return false;
        }
        switch (sourceSite) {
            case "movieffm":
            case "gimy":
            case "xiaoyakankan":
            case "nnyy":
            case "thanju":
            case "3kor":
            case "dramasq":
            case "olevod":
            case "777tv":
            case "99itv":
            case "ikanbot":
            case "yfsp":
            case "anime1":
            case "ani_gamer":
                return true;
            default:
                return false;
        }
    }

    private static boolean searchPageLooksResolvable(String html, String pageUrl) {
        MediaResolver.Result media = MediaResolver.resolve(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        return media != null && media.candidates != null && !media.candidates.isEmpty();
    }

    private static boolean movieFfmPageLooksDownloadable(String html) {
        String text = html == null ? "" : html;
        Matcher slugMatcher = MOVIEFFM_EP_SLUG.matcher(text);
        while (slugMatcher.find()) {
            if (slugMatcher.group(1) != null && !slugMatcher.group(1).trim().isEmpty()) {
                return true;
            }
        }
        return MOVIEFFM_MEDIA_HINT.matcher(text.replace("\\/", "/")).find();
    }

    private static String htmlWindow(String html, int start, int end) {
        String text = html == null ? "" : html;
        int from = Math.max(0, start - 2600);
        int to = Math.min(text.length(), end + 3600);
        return from < to ? text.substring(from, to) : "";
    }

    private static String resultCardHtml(String html, int start, int end) {
        String text = html == null ? "" : html;
        int from = Math.max(0, start - 1600);
        int to = Math.min(text.length(), end + 2200);
        String[] blockStarts = new String[]{"<article", "<li", "<div", "<section"};
        for (String marker : blockStarts) {
            int pos = text.toLowerCase(Locale.US).lastIndexOf(marker, start);
            if (pos >= 0 && pos > from) {
                from = pos;
                break;
            }
        }
        String lowered = text.toLowerCase(Locale.US);
        int close = firstPositive(
                lowered.indexOf("</article>", end),
                lowered.indexOf("</li>", end),
                lowered.indexOf("</div>", end),
                lowered.indexOf("</section>", end));
        if (close >= 0 && close + 12 < to) {
            to = close + 12;
        }
        return from < to ? text.substring(from, to) : "";
    }

    private static int firstPositive(int... values) {
        int out = -1;
        for (int value : values) {
            if (value >= 0 && (out < 0 || value < out)) {
                out = value;
            }
        }
        return out;
    }

    private static String extractPageTitle(String html) {
        String text = html == null ? "" : html;
        Matcher metaMatcher = META_TITLE.matcher(text);
        while (metaMatcher.find()) {
            String title = cleanTitle(firstNonEmptyTitle(metaMatcher.group(1), metaMatcher.group(2)));
            if (looksLikeUsableTitle(title)) {
                return title;
            }
        }
        Matcher titleMatcher = Pattern.compile("<title\\b[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        if (titleMatcher.find()) {
            String title = cleanTitle(titleMatcher.group(1)).replaceFirst("\\s*[-|].*$", "").trim();
            if (looksLikeUsableTitle(title)) {
                return title;
            }
        }
        return extractNearbyTitle(text);
    }

    private static String extractThumbnailUrl(String html, String baseUrl) {
        Matcher metaMatcher = META_IMAGE.matcher(html == null ? "" : html);
        while (metaMatcher.find()) {
            String raw = firstNonEmptyTitle(metaMatcher.group(1), metaMatcher.group(2));
            String url = normalizeResultUrl(raw, baseUrl);
            if (looksLikeThumbnailUrl(url, true)) {
                return url;
            }
        }
        Matcher matcher = IMAGE_SRC.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String url = normalizeResultUrl(raw, baseUrl);
            if (looksLikeThumbnailUrl(url, false)) {
                return url;
            }
        }
        Matcher attrMatcher = IMAGE_ATTR_URL.matcher(html == null ? "" : html);
        while (attrMatcher.find()) {
            String url = normalizeResultUrl(attrMatcher.group(1), baseUrl);
            if (looksLikeThumbnailUrl(url, false)) {
                return url;
            }
        }
        Matcher srcsetMatcher = IMAGE_SRCSET.matcher(html == null ? "" : html);
        while (srcsetMatcher.find()) {
            for (String candidate : srcsetMatcher.group(1).split(",")) {
                String raw = candidate.trim().split("\\s+")[0];
                String url = normalizeResultUrl(raw, baseUrl);
                if (looksLikeThumbnailUrl(url, false)) {
                    return url;
                }
            }
        }
        Matcher styleMatcher = STYLE_IMAGE.matcher(html == null ? "" : html);
        while (styleMatcher.find()) {
            String url = normalizeResultUrl(styleMatcher.group(2), baseUrl);
            if (looksLikeThumbnailUrl(url, false)) {
                return url;
            }
        }
        Matcher jsonMatcher = JSON_IMAGE.matcher(html == null ? "" : html);
        while (jsonMatcher.find()) {
            String url = normalizeResultUrl(unescapeScriptString(jsonMatcher.group(1)), baseUrl);
            if (looksLikeThumbnailUrl(url, true)) {
                return url;
            }
        }
        return "";
    }

    private static String extractNearbyTitle(String html) {
        String text = html == null ? "" : html;
        Matcher attrMatcher = TITLE_ATTR.matcher(text);
        while (attrMatcher.find()) {
            String title = cleanTitle(attrMatcher.group(1));
            if (looksLikeUsableTitle(title)) {
                return title;
            }
        }
        Matcher headingMatcher = Pattern.compile("<(?:h1|h2|h3|h4|strong|span|p)\\b[^>]*>(.*?)</(?:h1|h2|h3|h4|strong|span|p)>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        while (headingMatcher.find()) {
            String title = cleanTitle(headingMatcher.group(1));
            if (looksLikeUsableTitle(title)) {
                return title;
            }
        }
        return "";
    }

    private static String firstNonEmptyTitle(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String title = value == null ? "" : value.trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        return "";
    }

    private static boolean looksLikeUsableTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.length() < 2 || value.length() > 120) {
            return false;
        }
        String lowered = value.toLowerCase(Locale.US);
        return !lowered.equals("play")
                && !lowered.equals("download")
                && !lowered.equals("watch")
                && !lowered.equals("more")
                && !lowered.contains("javascript:");
    }

    private static boolean looksLikeListingTitle(String title) {
        String value = cleanTitle(title == null ? "" : title).toLowerCase(Locale.US);
        if (value.isEmpty()) {
            return false;
        }
        return value.equals("category")
                || value.equals("categories")
                || value.equals("tag")
                || value.equals("tags")
                || value.equals("genre")
                || value.equals("genres")
                || value.equals("search")
                || value.equals("list")
                || value.equals("channel")
                || value.equals("channels")
                || value.equals("series")
                || value.equals("studio")
                || value.equals("studios")
                || value.equals("actress")
                || value.equals("actresses")
                || value.equals("actor")
                || value.equals("actors")
                || value.contains("search results")
                || value.contains("category:")
                || value.contains("分類")
                || value.contains("類別")
                || value.contains("タグ")
                || value.contains("カテゴリー");
    }

    private static boolean looksLikeCodeOnlyTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.length() < 4 || value.length() > 24) {
            return false;
        }
        return !extractJavCode(value).isEmpty()
                || Pattern.compile("^[A-Za-z]{2,10}[-_\\s]?\\d{2,8}[A-Za-z]?$", Pattern.CASE_INSENSITIVE).matcher(value).matches()
                || Pattern.compile("^FC2[-_\\s]?PPV[-_\\s]?\\d{3,10}$", Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    private static String titleFromUrl(String rawUrl) {
        try {
            String path = Uri.parse(rawUrl == null ? "" : rawUrl).getLastPathSegment();
            String title = path == null ? "" : URLDecoder.decode(path, "UTF-8");
            title = title.replaceFirst("(?i)\\.(?:html?|php|mp4|m4v|webm|m3u8|mpd)$", "");
            title = title.replace('-', ' ').replace('_', ' ').trim();
            return cleanTitle(title);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean looksLikeThumbnailUrl(String rawUrl, boolean trustedMetadata) {
        String url = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        if (url.startsWith("data:")
                || url.contains("blank.")
                || url.contains("placeholder")
                || url.contains("loading")) {
            return false;
        }
        if (url.endsWith(".svg")
                || hasSupportedMediaExtension(url)
                || url.contains(".m3u8")
                || url.contains(".mpd")) {
            return false;
        }
        return trustedMetadata
                || url.contains(".jpg")
                || url.contains(".jpeg")
                || url.contains(".png")
                || url.contains(".webp")
                || url.contains(".gif")
                || url.contains("thumb")
                || url.contains("cover")
                || url.contains("poster")
                || url.contains("image")
                || url.contains("pic");
    }

    private static String unescapeScriptString(String value) {
        return (value == null ? "" : value)
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003f", "?")
                .replace("\\u0025", "%");
    }

    private static String normalizeResultUrl(String raw) {
        String value = htmlDecode(raw == null ? "" : raw.trim());
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        value = unwrapRedirectUrl(value);
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
            return normalizeResultUrl(new URL(new URL(baseUrl), value).toString());
        } catch (Exception ignored) {
            return normalizeResultUrl(value);
        }
    }

    private static String unwrapRedirectUrl(String value) {
        String current = value == null ? "" : value.trim();
        for (int depth = 0; depth < 3; depth++) {
            try {
                Uri uri = Uri.parse(current);
                String encoded = firstQueryParameter(uri, new String[]{"uddg", "url", "u", "target", "to", "dest", "destination", "redirect", "q"});
                if (encoded == null || encoded.trim().isEmpty()) {
                    return current;
                }
                String decoded = URLDecoder.decode(encoded, "UTF-8").trim();
                if (decoded.startsWith("//")) {
                    decoded = "https:" + decoded;
                }
                if (!decoded.startsWith("http://") && !decoded.startsWith("https://")) {
                    return current;
                }
                if (decoded.equals(current)) {
                    return current;
                }
                current = decoded;
            } catch (Exception ignored) {
                return current;
            }
        }
        return current;
    }

    private static String firstQueryParameter(Uri uri, String[] keys) {
        for (String key : keys) {
            String value = uri.getQueryParameter(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isSupportedCandidate(String url) {
        String site = MediaResolver.sourceSite(url);
        if (!"generic".equals(site)) {
            return true;
        }
        return hasSupportedMediaExtension(url);
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
        connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6");
        connection.setRequestProperty("Accept-Encoding", "identity");
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
            return new String(output.toByteArray(), charsetFromContentType(connection.getContentType()));
        } finally {
            connection.disconnect();
        }
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        Matcher matcher = Pattern.compile("charset\\s*=\\s*['\"]?([^;\\s'\"]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
        if (!matcher.find()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(matcher.group(1).trim());
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean looksLikeSearchResultPath(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        if (isNonVideoListingUrl(url)) {
            return false;
        }
        if (hasSupportedMediaExtension(url)) {
            return true;
        }
        String[] markers = new String[]{
                "/voddetail/", "/voddetail2/", "/vodplay/", "/vod/",
                "/index.php/vod/detail/", "/index.php/vod/play/",
                "/detail/", "/details/", "/title/", "/play/", "/watch/",
                "/video/", "/videos/", "/movie/", "/tv/", "/anime/", "/drama/", "/album/", "/episode/",
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

    private static boolean isNonVideoListingUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isEmpty()) {
            return true;
        }
        Uri uri = Uri.parse(value);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
        String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.US);
        if (path.isEmpty() || "/".equals(path)) {
            return true;
        }
        String[] listingMarkers = new String[]{
                "/category/", "/categories/", "/cat/", "/tag/", "/tags/",
                "/genre/", "/genres/", "/type/", "/types/", "/label/",
                "/channel/", "/channels/", "/series/", "/studio/", "/studios/",
                "/maker/", "/makers/", "/actress/", "/actresses/", "/actor/", "/actors/",
                "/pornstar/", "/pornstars/", "/models/", "/model/",
                "/author/", "/page/", "/feed/", "/rss/", "/wp-",
                "/search/", "/search.html", "/vodsearch/", "/vod/search",
                "/vodtype/", "/vodshow/", "/vodlist/", "/list/", "/lists/",
                "/index.php/vod/search", "/index.php/vod/type",
                "/privacy", "/contact", "/about", "/dmca"
        };
        for (String marker : listingMarkers) {
            if (path.contains(marker)) {
                return true;
            }
        }
        if (path.endsWith("/search")
                || path.endsWith("/category")
                || path.endsWith("/categories")
                || path.endsWith("/tag")
                || path.endsWith("/tags")
                || path.endsWith("/genre")
                || path.endsWith("/genres")
                || path.endsWith("/channel")
                || path.endsWith("/channels")
                || path.endsWith("/series")
                || path.endsWith("/studio")
                || path.endsWith("/studios")
                || path.endsWith("/actress")
                || path.endsWith("/actresses")
                || path.endsWith("/actor")
                || path.endsWith("/actors")) {
            return true;
        }
        String[] searchParams = new String[]{
                "wd=", "keyword=", "search_query=", "query=", "q=", "s="
        };
        for (String marker : searchParams) {
            if (query.startsWith(marker) || query.contains("&" + marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSupportedMediaExtension(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains(".m3u8")
                || lowered.contains(".mp4")
                || lowered.contains(".mpd")
                || lowered.contains(".webm")
                || lowered.contains(".m4v");
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
        return score;
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
        final String thumbnailUrl;
        final String thumbnailRefererUrl;

        RankedResult(String url, String title, int score) {
            this(url, title, score, "", "");
        }

        RankedResult(String url, String title, int score, String thumbnailUrl) {
            this(url, title, score, thumbnailUrl, "");
        }

        RankedResult(String url, String title, int score, String thumbnailUrl, String thumbnailRefererUrl) {
            this.url = url;
            this.title = title;
            this.score = score;
            this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
            this.thumbnailRefererUrl = thumbnailRefererUrl == null ? "" : thumbnailRefererUrl;
        }
    }
}
