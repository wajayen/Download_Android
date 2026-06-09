package tw.wajay.aitestdownloader;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Pattern ESCAPED_MEDIA_URL = Pattern.compile(
            "https?:\\\\/\\\\/[^\\s\"'<>]+?\\.(?:m3u8|mp4|mpd|webm|m4v)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXDROP_PLAY_ID = Pattern.compile("/(?:e|f|embed)/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOOD_PASS_MD5_PATH = Pattern.compile("(/pass_md5/[^\"'\\s<>\\\\]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOOD_ANY_HTTP_URL = Pattern.compile("https?://[^\\s\"'<>\\\\]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOOD_TOKEN_VALUE = Pattern.compile("(?:[?&]|\\b)token\\s*[:=]\\s*['\"]?([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOOD_EXPIRY_VALUE = Pattern.compile("(?:[?&]|\\b)expiry\\s*[:=]\\s*['\"]?(\\d{10,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern STREAMTAPE_GET_VIDEO_PATH = Pattern.compile("(/get_video\\?[^\"'\\s<>\\\\]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE_PAIR = Pattern.compile("([A-Z0-9-]+)=((?:\"[^\"]+\")|[^,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANIME1_API_REQ = Pattern.compile("data-apireq=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern BESTJAVPORN_VIDEO_ID = Pattern.compile("\\bvideo-id=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern BESTJAVPORN_VIDEO_VER = Pattern.compile("\\bvideo_ver=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern BESTJAVPORN_DATA_MPU = Pattern.compile("id=[\"']video-player[\"'][^>]+data-mpu=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLAYER_DATA_CONFIG = Pattern.compile("\\bdata-config=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JAVDOCK_PLAYER_WRAPPER = Pattern.compile("id=[\"']player-wrapper[\"'][^>]*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JAVDOCK_DATA_TS_ID = Pattern.compile("\\bdata-ts-id=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVDOCK_DATA_TS_LIVE = Pattern.compile("\\bdata-ts-live=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JAVDOCK_DATA_TS_EP = Pattern.compile("\\bdata-ts-ep=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NJAV_VIDEO_ID = Pattern.compile(
            "Video\\(\\{id:\\s*[\"']?(\\d+)|v-scope=[\"']Video\\(\\{id:\\s*[\"']?(\\d+)|data-id=[\"'](\\d+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NJAV_POSTER = Pattern.compile("data-poster=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NJAV_VIDEO_FRAME_SRC = Pattern.compile("videoFrame\\.src\\s*=\\s*[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NJAV_PLAYER_M3U8 = Pattern.compile(
            "PLAYER_CONFIG\\s*=\\s*\\{.*?m3u8\\s*:\\s*[\"']([^\"']+)[\"']|\\bm3u8\\s*:\\s*[\"']([^\"']+)[\"']|\\bfile\\s*:\\s*[\"']([^\"']+(?:\\.m3u8|/play/token_hash)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NJAVTV_SURRIT_PLAYLIST = Pattern.compile(
            "(https://surrit\\.com/[^\\s\"']+/playlist\\.m3u8)|source\\s*=\\s*[\"'](https://surrit\\.com/[^\"']+/playlist\\.m3u8)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JABLE_HLS = Pattern.compile(
            "(https://[^\\s\"'\\\\]+\\.m3u8[^\\s\"'\\\\]*)|hlsUrl\\s*=\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCE_SRC = Pattern.compile("<source\\b[^>]+\\bsrc=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SUPJAV_BG = Pattern.compile("<div[^>]+id=[\"']dz_video[\"'][^>]*\\bbg=[\"']([^\"']*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPJAV_SERVER_LINK = Pattern.compile(
            "<a\\b[^>]*\\bclass=[\"'][^\"']*\\bbtn-server\\b[^\"']*[\"'][^>]*\\bdata-link=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SUPJAV_CHILD_SRC = Pattern.compile("src=[\"'](\\?c=[^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPJAV_HLS_FIELD = Pattern.compile("[\"']hls\\d+[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVOLOAD_REDIRECT_LINK = Pattern.compile("redirect_link\\s*=\\s*['\"]([^'\"]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TKTUBE_QUALITY = Pattern.compile("_(\\d{3,4})p\\.mp4", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVFILMS_DMM_VIDEO_ID = Pattern.compile("/get/video/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITTV_DETAIL_PATH = Pattern.compile("/(?:detail|voddetail)/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITTV_PLAY_PATH = Pattern.compile("/(?:vodplay|play)/", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITTV_PLAY_ATTR = Pattern.compile(
            "(?:href|data-href|data-url)=[\"']([^\"']*(?:/(?:vodplay|play)/)[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITTV_PLAY_PATH_TEXT = Pattern.compile(
            "/(?:vodplay|play)/\\d+(?:-\\d+){0,3}\\.html",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TV777_DETAIL_PATH = Pattern.compile("/vod/detail/id/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TV777_PLAY_PATH = Pattern.compile("/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TV777_EPISODE_LINK = Pattern.compile(
            "href=[\"']((?:(?:https?:)?//play\\.777tv\\.ai)?/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern THANJU_PAGE_PATH = Pattern.compile("/(?:detail|play)/\\d+(?:/\\d+-\\d+)?\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern THANJU_PLAY_LINK = Pattern.compile(
            "href=[\"']([^\"']*/play/\\d+/\\d+-\\d+\\.html)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OLEVOD_DETAIL_PATH = Pattern.compile("/index\\.php/vod/detail/id/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OLEVOD_PLAY_PATH = Pattern.compile("/index\\.php/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OLEVOD_PLAY_LINK = Pattern.compile(
            "href=[\"']([^\"']*/index\\.php/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IQIYI_PLAY_LINK = Pattern.compile(
            "href=[\"']([^\"']*(?:/(?:play|album)/)[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAMASQ_DETAIL_PATH = Pattern.compile("/detail/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAMASQ_PLAY_PATH = Pattern.compile("/vodplay/(\\d+)/(ep\\d+)\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAMASQ_PLAY_LINK = Pattern.compile(
            "href=[\"']([^\"']*/vodplay/\\d+/ep\\d+\\.html)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_PATH_ID = Pattern.compile("/(?:movie|tv|drama|anime)/(\\d+)/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_FFM_ID = Pattern.compile("/ffm/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_JS_ID = Pattern.compile(
            "replace\\(['\"]\\{0\\}['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVIEFFM_EP_SLUG = Pattern.compile(
            "\\bep_slug=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TITLE = Pattern.compile(
            "<title\\b[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_TITLE = Pattern.compile(
            "<meta\\b[^>]+(?:property|name)=[\"'](?:og:title|twitter:title|title)[\"'][^>]+content=[\"']([^\"']+)[\"']|<meta\\b[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:title|twitter:title|title)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern THREEKOR_LIST_PATH = Pattern.compile("/list/\\d+[^/]*\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREEKOR_DETAIL_PATH = Pattern.compile("/detail/\\d+\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREEKOR_DETAIL_LINK = Pattern.compile(
            "href=[\"']((?:https?:)?//3kor\\.com/detail/\\d+\\.html|/detail/\\d+\\.html)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern THREEKOR_PLAY_ENTRY = Pattern.compile(
            "bb_a\\(['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]*)['\"]\\s*,\\s*event\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NNYY_PAGE_PATH = Pattern.compile("/(?:dianying|dianshiju|zongyi|dongman)/(\\d+)\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NNYY_DEFAULT_EP = Pattern.compile("on_ep\\(['\"]([^'\"]+)['\"]\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NNYY_EP_SLUG_TAG = Pattern.compile(
            "<([a-z0-9]+)\\b[^>]*\\bep_slug=['\"]([^'\"]+)['\"][^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NNYY_EP_SLUG_ATTR = Pattern.compile(
            "\\bep_slug=['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IKANBOT_PLAY_PATH = Pattern.compile("/play/\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern IKANBOT_WINDOW_TOKEN = Pattern.compile("window\\.v_tks\\s*=\\s*['\"]([^'\"]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IKANBOT_URL_FIELD = Pattern.compile(
            "(?:url|src|playurl|playUrl)\\s*[:=]\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YFSP_PLAY_PATH = Pattern.compile("/play/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_STATUS_PATH = Pattern.compile("/([^/?#]+)/status/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_SHORTCODE_PATH = Pattern.compile("/(?:reel|p)/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACEBOOK_VIDEO_PATH = Pattern.compile("/(?:reel|watch|videos)(?:/|\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACEBOOK_LSD_FIELD = Pattern.compile("\"lsd\"\\s*:\\s*\\{\"name\":\"lsd\",\"value\":\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACEBOOK_REELS_QUERY = Pattern.compile(
            "FBReelsRootWithEntrypointQueryRelayPreloader_[^\"]+\",\"queryID\":\"(\\d+)\",\"variables\":(\\{.*?\\}),\"queryName\":\"FBReelsRootWithEntrypointQuery\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DAILYMOTION_VIDEO_PATH = Pattern.compile(
            "(?:/video/|/embed/video/|^/)([A-Za-z0-9]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BILIBILI_BVID = Pattern.compile("(BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BILIBILI_AID_PATH = Pattern.compile("/video/av(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_PLAYER_RESPONSE_START = Pattern.compile(
            "(?:var\\s+)?ytInitialPlayerResponse\\s*=\\s*\\{",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern THREADS_VIDEO_VERSIONS_START = Pattern.compile(
            "\"video_versions\"\\s*:\\s*\\[",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHTEEN_AV_QUALITY_SRC = Pattern.compile(
            "src\\s*:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"][^{};\\n]{0,240}?size\\s*:\\s*['\"]?(\\d{3,4})['\"]?|size\\s*:\\s*['\"]?(\\d{3,4})['\"]?[^{};\\n]{0,240}?src\\s*:\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EIGHTEEN_AV_IFRAME_PREFIX = Pattern.compile(
            "//18av\\.mm-cg\\.com/js/player/play\\.php\\?numresolution=\\d+&id=",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHTEEN_AV_ENCODED_PLAYER_ID = Pattern.compile(
            "mvarr\\['[^']+'\\]\\s*=\\s*\\[\\[['\"](?:a_iframe_id_[^'\"]+)['\"],['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHTEEN_AV_XOR_VALUE = Pattern.compile("\\bhadeedg252\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHTEEN_AV_BASE_VALUE = Pattern.compile("\\bhcdeedg252\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHTEEN_AV_AES_KEY = Pattern.compile("\\bargdeqweqweqwe\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHTEEN_AV_AES_IV = Pattern.compile("\\bhdddedg252\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern AVJOY_HLS_FIELD = Pattern.compile(
            "[\"'](hls\\d+)['\"]\\s*:\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_IFRAME_SRC = Pattern.compile(
            "<iframe\\b[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HAYAV_DATA_SECRET = Pattern.compile("data-secret=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_LINK_QUOTED = Pattern.compile("(?:src|href)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_LINK_UNQUOTED = Pattern.compile("(?:src|href)=([^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
    private static final String HAYAV_SECRET_KEY = "MySuperSecretKey2026";
    private static final Pattern AVBEBE_ARCHIVE_LINK = Pattern.compile(
            "(?:href|data-href|data-url)=[\"']([^\"']*/archives/\\d+[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AVBEBE_IFRAME_SRC = Pattern.compile(
            "<iframe\\b[^>]+\\bsrc=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern AVBEBE_HLS_FIELD = Pattern.compile(
            "[\"'](hls\\d+)[\"']\\s*:\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
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

    private static final class SiteMediaResult {
        final String sourceSite;
        final String primaryUrl;
        final List<String> candidates;
        final String refererUrl;

        SiteMediaResult(String sourceSite, String primaryUrl, List<String> candidates, String refererUrl) {
            this.sourceSite = sourceSite;
            this.primaryUrl = primaryUrl;
            this.candidates = candidates;
            this.refererUrl = refererUrl;
        }
    }

    private static final class TwitterStatus {
        final String screenName;
        final String statusId;

        TwitterStatus(String screenName, String statusId) {
            this.screenName = screenName;
            this.statusId = statusId;
        }
    }

    private static final class FacebookGraphqlPayload {
        final String lsd;
        final String docId;
        final String variables;

        FacebookGraphqlPayload(String lsd, String docId, String variables) {
            this.lsd = lsd;
            this.docId = docId;
            this.variables = variables;
        }
    }

    private static final class BilibiliVideoInfo {
        final String bvid;
        final String aid;
        final String cid;

        BilibiliVideoInfo(String bvid, String aid, String cid) {
            this.bvid = bvid == null ? "" : bvid;
            this.aid = aid == null ? "" : aid;
            this.cid = cid == null ? "" : cid;
        }
    }

    private static final class ThreadsVideoCandidate {
        final int area;
        final String url;

        ThreadsVideoCandidate(int area, String url) {
            this.area = area;
            this.url = url;
        }
    }

    private static final class QualityCandidate {
        final int quality;
        final String url;

        QualityCandidate(int quality, String url) {
            this.quality = quality;
            this.url = url;
        }
    }

    private static final class Protected18AvPlayer {
        final String iframePrefix;
        final String encodedId;
        final String decodedPayload;

        Protected18AvPlayer(String iframePrefix, String encodedId, String decodedPayload) {
            this.iframePrefix = iframePrefix == null ? "" : iframePrefix;
            this.encodedId = encodedId == null ? "" : encodedId;
            this.decodedPayload = decodedPayload == null ? "" : decodedPayload;
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
        default void onResolved(String sourceSite, String targetUrl, List<String> candidates, List<String> candidateLabels, List<String> candidateReferers) {
            onResolved(sourceSite, targetUrl, candidates, candidateLabels);
        }
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
                if (VideoSearchResolver.isSearchUri(rawUrl)) {
                    downloadSearchResult(rawUrl, requestedName, callback);
                    return;
                }
                Uri uri = Uri.parse(rawUrl);
                String fileName = FileNames.choose(uri, requestedName);
                String targetUrl = rawUrl;
                String sourceSite = MediaResolver.sourceSite(rawUrl);
                List<String> fallbackUrls = new ArrayList<>();
                String refererUrl = firstNonEmpty(providedReferer, "");
                if (!isMediaUrl(rawUrl)) {
                    ResolvedTarget resolvedTarget = resolvePageToMedia(rawUrl, refererUrl, callback);
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

    private void downloadSearchResult(String rawUrl, String requestedName, Callback callback) throws IOException {
        String query = VideoSearchResolver.queryFromUri(rawUrl);
        String selectedUrl = VideoSearchResolver.selectedUrlFromUri(rawUrl);
        callback.onStatus(context.getString(R.string.engine_searching_video, query));
        List<VideoSearchResolver.Result> results = VideoSearchResolver.search(query);
        prioritizeSelectedSearchResult(results, selectedUrl);
        if (results.isEmpty()) {
            throw new IOException(context.getString(R.string.error_search_no_results, query));
        }
        List<String> urls = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (VideoSearchResolver.Result result : results) {
            urls.add(result.url);
            labels.add(result.title);
        }
        callback.onResolved("search", results.get(0).url, urls, labels);

        IOException lastError = null;
        for (int i = 0; i < results.size(); i++) {
            VideoSearchResolver.Result result = results.get(i);
            if (cancelled.get()) {
                callback.onStatus(context.getString(R.string.engine_cancelled_keep_partial));
                return;
            }
            try {
                callback.onStatus(context.getString(R.string.engine_search_try_result, i + 1, results.size(), result.sourceSite));
                String sourceSite = MediaResolver.sourceSite(result.url);
                String targetUrl = result.url;
                List<String> fallbackUrls = new ArrayList<>();
                String refererUrl = result.refererUrl;
                if (!isMediaUrl(result.url)) {
                    ResolvedTarget resolvedTarget = resolvePageToMedia(result.url, refererUrl, callback);
                    targetUrl = resolvedTarget.primaryUrl;
                    sourceSite = resolvedTarget.sourceSite;
                    fallbackUrls = resolvedTarget.fallbackUrls;
                    refererUrl = firstNonEmpty(resolvedTarget.refererUrl, refererUrl);
                }
                callback.onResolved("search", targetUrl,
                        mergedSearchCandidates(results, i, targetUrl, fallbackUrls),
                        mergedSearchCandidateLabels(results, i, targetUrl, fallbackUrls),
                        mergedSearchCandidateReferers(results, i, targetUrl, fallbackUrls, refererUrl));
                String fileName = requestedName == null || requestedName.trim().isEmpty()
                        ? FileNames.choose(Uri.parse(targetUrl), FileNames.sanitize(query) + ".mp4")
                        : FileNames.choose(Uri.parse(targetUrl), requestedName);
                downloadWithFallbacks(targetUrl, fallbackUrls, sourceSite, fileName, refererUrl, callback);
                return;
            } catch (IOException error) {
                lastError = error;
                callback.onStatus(context.getString(R.string.engine_search_result_failed, shortMessage(error)));
            }
        }
        throw lastError == null
                ? new IOException(context.getString(R.string.error_search_no_results, query))
                : lastError;
    }

    private void prioritizeSelectedSearchResult(List<VideoSearchResolver.Result> results, String selectedUrl) {
        if (results == null || results.size() < 2 || selectedUrl == null || selectedUrl.trim().isEmpty()) {
            return;
        }
        String selected = selectedUrl.trim();
        for (int i = 0; i < results.size(); i++) {
            VideoSearchResolver.Result result = results.get(i);
            if (result != null && selected.equals(result.url)) {
                if (i > 0) {
                    results.remove(i);
                    results.add(0, result);
                }
                return;
            }
        }
    }

    private List<String> mergedSearchCandidates(
            List<VideoSearchResolver.Result> results,
            int selectedIndex,
            String targetUrl,
            List<String> fallbackUrls) {
        List<String> urls = new ArrayList<>();
        addUnique(urls, targetUrl);
        for (String fallbackUrl : fallbackUrls) {
            addUnique(urls, fallbackUrl);
        }
        for (int i = selectedIndex + 1; i < results.size(); i++) {
            addUnique(urls, results.get(i).url);
        }
        for (int i = 0; i < selectedIndex; i++) {
            addUnique(urls, results.get(i).url);
        }
        return urls;
    }

    private List<String> mergedSearchCandidateLabels(
            List<VideoSearchResolver.Result> results,
            int selectedIndex,
            String targetUrl,
            List<String> fallbackUrls) {
        List<String> labels = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        addUniqueWithLabel(urls, labels, targetUrl, context.getString(R.string.search_candidate_selected, results.get(selectedIndex).title));
        for (String fallbackUrl : fallbackUrls) {
            addUniqueWithLabel(urls, labels, fallbackUrl, context.getString(R.string.search_candidate_media_fallback));
        }
        for (int i = selectedIndex + 1; i < results.size(); i++) {
            addUniqueWithLabel(urls, labels, results.get(i).url, context.getString(R.string.search_candidate_alternate, results.get(i).title));
        }
        for (int i = 0; i < selectedIndex; i++) {
            addUniqueWithLabel(urls, labels, results.get(i).url, context.getString(R.string.search_candidate_previous, results.get(i).title));
        }
        return labels;
    }

    private List<String> mergedSearchCandidateReferers(
            List<VideoSearchResolver.Result> results,
            int selectedIndex,
            String targetUrl,
            List<String> fallbackUrls,
            String selectedRefererUrl) {
        List<String> referers = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        addUniqueWithLabel(urls, referers, targetUrl, selectedRefererUrl);
        for (String fallbackUrl : fallbackUrls) {
            addUniqueWithLabel(urls, referers, fallbackUrl, selectedRefererUrl);
        }
        for (int i = selectedIndex + 1; i < results.size(); i++) {
            addUniqueWithLabel(urls, referers, results.get(i).url, results.get(i).refererUrl);
        }
        for (int i = 0; i < selectedIndex; i++) {
            addUniqueWithLabel(urls, referers, results.get(i).url, results.get(i).refererUrl);
        }
        return referers;
    }

    private void addUnique(List<String> urls, String url) {
        if (url != null && !url.trim().isEmpty() && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private void addUniqueWithLabel(List<String> urls, List<String> labels, String url, String label) {
        if (url == null || url.trim().isEmpty() || urls.contains(url)) {
            return;
        }
        urls.add(url);
        labels.add(label == null ? "" : label);
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
                || "Accept-Encoding".equals(name)
                || "Authorization".equals(name)
                || "Cache-Control".equals(name)
                || "DNT".equals(name)
                || "Origin".equals(name)
                || "Pragma".equals(name)
                || "Priority".equals(name)
                || "X-Requested-With".equals(name)
                || "Sec-Fetch-Site".equals(name)
                || "Sec-Fetch-Mode".equals(name)
                || "Sec-Fetch-Dest".equals(name)
                || "Sec-Fetch-User".equals(name);
    }
    void cancel() {
        cancelled.set(true);
    }

    private ResolvedTarget resolvePageToMedia(String rawUrl, Callback callback) throws IOException {
        return resolvePageToMedia(rawUrl, "", callback);
    }

    private ResolvedTarget resolvePageToMedia(String rawUrl, String initialRefererUrl, Callback callback) throws IOException {
        return resolvePageToMedia(rawUrl, initialRefererUrl, callback, 0, new LinkedHashSet<String>());
    }

    private ResolvedTarget resolvePageToMedia(
            String rawUrl,
            String initialRefererUrl,
            Callback callback,
            int depth,
            Set<String> seenPages) throws IOException {
        String currentUrl = rawUrl == null ? "" : rawUrl.trim();
        String currentReferer = initialRefererUrl == null ? "" : initialRefererUrl.trim();
        if (currentUrl.isEmpty()) {
            throw new IOException(context.getString(R.string.error_no_media_candidate));
        }
        if (depth >= PAGE_RESOLVE_DEPTH_LIMIT) {
            throw new IOException(context.getString(R.string.error_resolve_depth_exceeded));
        }
        if (!seenPages.add(currentUrl)) {
            throw new IOException(context.getString(R.string.error_no_media_candidate));
        }

        String pageText = readText(currentUrl, currentReferer);
        if (looksLikeHlsManifest(pageText)) {
            callback.onStatus(context.getString(R.string.engine_hls_manifest_detected));
            callback.onResolved(
                    MediaResolver.sourceSite(currentUrl),
                    currentUrl,
                    Collections.singletonList(currentUrl),
                    Collections.singletonList("HLS manifest"),
                    Collections.singletonList(currentReferer));
            return new ResolvedTarget(currentUrl, MediaResolver.sourceSite(currentUrl), new ArrayList<>(), currentReferer);
        }

        String anime1Url = resolveAnime1ApiMedia(currentUrl, pageText);
        if (anime1Url != null) {
            callback.onStatus(context.getString(R.string.engine_resolved_anime1));
            callback.onResolved(
                    "anime1",
                    anime1Url,
                    Collections.singletonList(anime1Url),
                    Collections.singletonList("Anime1 API"),
                    Collections.singletonList(currentUrl));
            return new ResolvedTarget(anime1Url, "anime1", new ArrayList<>(), currentUrl);
        }
        SiteMediaResult siteMedia = resolveAdultSiteMedia(currentUrl, pageText);
        if (siteMedia != null && siteMedia.primaryUrl != null) {
            callback.onStatus(context.getString(R.string.engine_resolving_page_candidate, siteMedia.sourceSite, depth));
            callback.onResolved(
                    siteMedia.sourceSite,
                    siteMedia.primaryUrl,
                    siteMedia.candidates,
                    siteCandidateLabels(siteMedia.sourceSite, siteMedia.candidates),
                    sameReferers(siteMedia.candidates, siteMedia.refererUrl));
            return new ResolvedTarget(siteMedia.primaryUrl, siteMedia.sourceSite, mediaFallbacks(siteMedia.candidates, siteMedia.primaryUrl), siteMedia.refererUrl);
        }
        MediaResolver.Result resolved = MediaResolver.resolve(pageText, currentUrl);
        callback.onStatus(context.getString(R.string.engine_resolving_page_candidate, resolved.sourceSite, depth));
        if (resolved.primaryUrl == null) {
            throw new IOException(context.getString(R.string.error_no_media_candidate));
        }
        callback.onResolved(
                resolved.sourceSite,
                resolved.primaryUrl,
                resolved.candidates,
                resolved.candidateLabels,
                sameReferers(resolved.candidates, currentUrl));
        if (resolved.primaryIsMedia) {
            return new ResolvedTarget(resolved.primaryUrl, resolved.sourceSite, mediaFallbacks(resolved.candidates, resolved.primaryUrl), currentUrl);
        }

        IOException lastError = null;
        for (String candidate : pageFallbacks(resolved.candidates)) {
            if (cancelled.get()) {
                throw new IOException(context.getString(R.string.engine_cancelled_keep_partial));
            }
            try {
                return resolvePageToMedia(candidate, currentUrl, callback, depth + 1, new LinkedHashSet<>(seenPages));
            } catch (IOException error) {
                lastError = error;
                callback.onStatus(context.getString(R.string.engine_source_failed_switching, shortMessage(error)));
            }
        }
        List<String> directMediaFallbacks = mediaFallbacks(resolved.candidates, "");
        if (!directMediaFallbacks.isEmpty()) {
            String primaryMedia = directMediaFallbacks.get(0);
            return new ResolvedTarget(
                    primaryMedia,
                    resolved.sourceSite,
                    mediaFallbacks(directMediaFallbacks, primaryMedia),
                    currentUrl);
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException(context.getString(R.string.error_no_media_candidate));
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
                    List<String> remainingTargets = targets.subList(i, targets.size());
                    callback.onResolved(
                            sourceSite,
                            targetUrl,
                            remainingTargets,
                            Collections.emptyList(),
                            sameReferers(remainingTargets, refererUrl));
                }
                if (isDashUrl(targetUrl)) {
                    callback.onStatus(context.getString(R.string.engine_resolving_dash));
                    downloadDash(targetUrl, FileNames.replaceExtension(fileName, ".mp4"), refererUrl, callback);
                } else if (isHlsUrl(targetUrl) || (targetUrl.equals(primaryUrl) && !isMediaUrl(targetUrl))) {
                    callback.onStatus(context.getString(R.string.engine_resolving_hls));
                    downloadHls(targetUrl, fileName, refererUrl, callback);
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

    private List<String> pageFallbacks(List<String> candidates) {
        List<String> fallbacks = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty() && !isMediaUrl(candidate) && !fallbacks.contains(candidate)) {
                fallbacks.add(candidate);
            }
        }
        return fallbacks;
    }

    private List<String> sameReferers(List<String> candidates, String refererUrl) {
        List<String> referers = new ArrayList<>();
        int count = candidates == null ? 0 : candidates.size();
        String referer = refererUrl == null ? "" : refererUrl.trim();
        for (int i = 0; i < count; i++) {
            referers.add(referer);
        }
        return referers;
    }

    private SiteMediaResult resolveAdultSiteMedia(String pageUrl, String pageText) throws IOException {
        String site = MediaResolver.sourceSite(pageUrl);
        if ("bestjavporn".equals(site)) {
            return resolveBestJavPornMedia(pageUrl, pageText);
        }
        if ("javdock".equals(site)) {
            return resolveJavDockMedia(pageUrl, pageText);
        }
        if ("njav".equals(site)) {
            return resolveNjavMedia(pageUrl, pageText);
        }
        if ("njavtv".equals(site)) {
            return resolveNjavTvMedia(pageUrl, pageText);
        }
        if ("jable".equals(site)) {
            return resolveJableMedia(pageUrl, pageText);
        }
        if ("85xvideo".equals(site)) {
            return resolve85xVideoMedia(pageUrl, pageText);
        }
        if ("tinyavideo".equals(site)) {
            return resolveTinyAVideoMedia(pageUrl, pageText);
        }
        if ("supjav".equals(site)) {
            return resolveSupJavMedia(pageUrl, pageText);
        }
        if ("evoload".equals(site)) {
            return resolveEvoLoadMedia(pageUrl, pageText);
        }
        if ("mixdrop".equals(site)) {
            return resolveMixdropMedia(pageUrl, pageText);
        }
        if ("dood".equals(site)) {
            return resolveDoodMedia(pageUrl, pageText);
        }
        if ("streamtape".equals(site)) {
            return resolveStreamtapeMedia(pageUrl, pageText);
        }
        if ("filehost".equals(site)) {
            return resolveFileHostMedia(pageUrl, pageText);
        }
        if ("sbhost".equals(site)) {
            return resolveSbHostMedia(pageUrl, pageText);
        }
        if ("mirrorhost".equals(site)) {
            return resolveMirrorHostMedia(pageUrl, pageText);
        }
        if ("cdnsource".equals(site)) {
            return resolveCdnSourceMedia(pageUrl, pageText);
        }
        if ("tktube".equals(site)) {
            return resolveTkTubeMedia(pageUrl, pageText);
        }
        if ("18jav".equals(site)) {
            return resolve18JavMedia(pageUrl, pageText);
        }
        if ("18av".equals(site)) {
            return resolve18AvMedia(pageUrl, pageText);
        }
        if ("avbebe".equals(site)) {
            return resolveAvbebeMedia(pageUrl, pageText);
        }
        if ("avjoy".equals(site)) {
            return resolveAvJoyMedia(pageUrl, pageText);
        }
        if ("goodav17".equals(site) || "hohoj".equals(site) || "ggjav".equals(site)) {
            return resolveGgJavBackedMedia(site, pageUrl, pageText);
        }
        if ("hayav".equals(site)) {
            return resolveHayAvMedia(pageUrl, pageText);
        }
        if ("hanime1".equals(site)) {
            return resolveHanime1Media(pageUrl, pageText);
        }
        if ("ppp.porn".equals(site)) {
            return resolvePppPornMedia(pageUrl, pageText);
        }
        if ("javfilms".equals(site)) {
            return resolveJavFilmsMedia(pageUrl, pageText);
        }
        if ("missav".equals(site)) {
            return resolveMissAvMedia(pageUrl, pageText);
        }
        if ("777tv".equals(site)) {
            return resolve777TvMedia(pageUrl, pageText);
        }
        if ("99itv".equals(site)) {
            return resolve99ItvMedia(pageUrl, pageText);
        }
        if ("thanju".equals(site)) {
            return resolveThanjuMedia(pageUrl, pageText);
        }
        if ("olevod".equals(site)) {
            return resolveOlevodMedia(pageUrl, pageText);
        }
        if ("movieffm".equals(site)) {
            return resolveMovieFfmMedia(pageUrl, pageText);
        }
        if ("dramasq".equals(site)) {
            return resolveDramaSqMedia(pageUrl, pageText);
        }
        if ("3kor".equals(site)) {
            return resolve3KorMedia(pageUrl, pageText);
        }
        if ("nnyy".equals(site)) {
            return resolveNnyyMedia(pageUrl, pageText);
        }
        if ("ikanbot".equals(site)) {
            return resolveIkanbotMedia(pageUrl, pageText);
        }
        if ("yfsp".equals(site)) {
            return resolveYfspMedia(pageUrl, pageText);
        }
        if ("twitter".equals(site)) {
            return resolveTwitterMedia(pageUrl, pageText);
        }
        if ("instagram".equals(site)) {
            return resolveInstagramMedia(pageUrl, pageText);
        }
        if ("facebook".equals(site)) {
            return resolveFacebookMedia(pageUrl, pageText);
        }
        if ("threads".equals(site)) {
            return resolveThreadsMedia(pageUrl, pageText);
        }
        if ("dailymotion".equals(site)) {
            return resolveDailymotionMedia(pageUrl, pageText);
        }
        if ("bilibili".equals(site)) {
            return resolveBilibiliMedia(pageUrl, pageText);
        }
        if ("iqiyi".equals(site)) {
            return resolveIqiyiMedia(pageUrl, pageText);
        }
        if ("youtube".equals(site)) {
            return resolveYoutubeMedia(pageUrl, pageText);
        }
        return null;
    }

    private SiteMediaResult resolveBestJavPornMedia(String pageUrl, String pageText) throws IOException {
        Matcher videoIdMatcher = BESTJAVPORN_VIDEO_ID.matcher(pageText == null ? "" : pageText);
        Matcher dataMpuMatcher = BESTJAVPORN_DATA_MPU.matcher(pageText == null ? "" : pageText);
        if (!videoIdMatcher.find() || !dataMpuMatcher.find()) {
            return null;
        }
        String videoId = htmlDecoded(videoIdMatcher.group(1)).trim();
        String videoVer = "2";
        Matcher videoVerMatcher = BESTJAVPORN_VIDEO_VER.matcher(pageText);
        if (videoVerMatcher.find()) {
            videoVer = firstNonEmpty(htmlDecoded(videoVerMatcher.group(1)), "2");
        }
        String origin = originFromUrl(pageUrl);
        String sources = bestJavPornDex(videoId, htmlDecoded(dataMpuMatcher.group(1)));
        String apiBody = "sources=" + urlEncode(sources) + "&ver=" + urlEncode(videoVer);
        String apiText = postForm(origin + "/api/play/", apiBody, pageUrl);
        JSONObject payload = parseJsonObject(apiText);
        if (payload == null || !payloadStatus(payload) || payload.optString("data", "").isEmpty()) {
            return null;
        }
        String playerPath = bestJavPornDex(videoId, payload.optString("data", ""));
        String playerUrl = joinUrl(origin + "/", playerPath);
        String playerText = readText(playerUrl, pageUrl);
        List<String> candidates = decodeBestJavPornPlayerSources(playerText, playerUrl);
        if (candidates.isEmpty()) {
            candidates.addAll(extractMediaCandidates(playerText, playerUrl));
        }
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("bestjavporn", candidates.get(0), candidates, playerUrl);
    }

    private SiteMediaResult resolveJavDockMedia(String pageUrl, String pageText) throws IOException {
        Matcher wrapperMatcher = JAVDOCK_PLAYER_WRAPPER.matcher(pageText == null ? "" : pageText);
        if (!wrapperMatcher.find()) {
            return null;
        }
        String playerHtml = wrapperMatcher.group(0);
        Matcher videoIdMatcher = JAVDOCK_DATA_TS_ID.matcher(playerHtml);
        Matcher dataLiveMatcher = JAVDOCK_DATA_TS_LIVE.matcher(playerHtml);
        if (!videoIdMatcher.find() || !dataLiveMatcher.find()) {
            return null;
        }
        String videoId = htmlDecoded(videoIdMatcher.group(1)).trim();
        String dataLive = htmlDecoded(dataLiveMatcher.group(1)).trim();
        int epValue = 1;
        Matcher epMatcher = JAVDOCK_DATA_TS_EP.matcher(playerHtml);
        if (epMatcher.find()) {
            epValue = Math.max(1, parseInt(htmlDecoded(epMatcher.group(1)), 2) - 1);
        }
        String origin = originFromUrl(pageUrl);
        String sources = javDockFme(videoId, dataLive, true);
        String apiBody = "sources=" + urlEncode(sources) + "&ep=" + epValue + "&ver=2";
        String apiText = postForm(origin + "/api/play/", apiBody, pageUrl);
        JSONObject payload = parseJsonObject(apiText);
        if (payload == null || !payloadStatus(payload) || payload.optString("data", "").isEmpty()) {
            return null;
        }
        String playerPath = javDockFme(videoId, payload.optString("data", ""), false);
        String playerUrl = joinUrl(origin + "/", playerPath);
        String playerText = readText(playerUrl, pageUrl);
        List<String> candidates = decodeBestJavPornPlayerSources(playerText, playerUrl);
        candidates.addAll(extractMediaCandidates(playerText, playerUrl));
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("javdock", candidates.get(0), candidates, playerUrl);
    }

    private SiteMediaResult resolveMixdropMedia(String pageUrl, String pageText) throws IOException {
        List<String> candidates = new ArrayList<>();
        String watchUrl = mixdropWatchUrl(pageUrl);
        String referer = firstNonEmpty(watchUrl, pageUrl);
        SiteMediaResult pageMedia = mediaFromMediaResolver("mixdrop", pageUrl, pageText, referer);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        if (!watchUrl.isEmpty() && !watchUrl.equals(pageUrl)) {
            try {
                String watchText = readText(watchUrl, pageUrl);
                SiteMediaResult watchMedia = mediaFromMediaResolver("mixdrop", watchUrl, watchText, watchUrl);
                if (watchMedia != null) {
                    candidates.addAll(watchMedia.candidates);
                }
            } catch (IOException ignored) {
                // Keep candidates from the original page; some mirrors block direct embed fetches.
            }
        }
        if (isMediaUrl(pageUrl)) {
            addUnique(candidates, pageUrl);
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("mixdrop", candidates.get(0), candidates, referer);
    }

    private String mixdropWatchUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        String normalized = htmlDecoded(rawUrl).trim();
        try {
            URL parsed = new URL(normalized);
            String host = parsed.getHost();
            if (host == null) {
                return normalized;
            }
            String loweredHost = host.toLowerCase(Locale.US);
            if (!(loweredHost.contains("mixdrop.ag") || loweredHost.contains("m1xdrop.click"))) {
                return normalized;
            }
            Matcher idMatcher = MIXDROP_PLAY_ID.matcher(parsed.getPath() == null ? "" : parsed.getPath());
            if (!idMatcher.find()) {
                return normalized;
            }
            return parsed.getProtocol() + "://" + host + "/e/" + idMatcher.group(1);
        } catch (MalformedURLException ignored) {
            return normalized;
        }
    }

    private SiteMediaResult resolveDoodMedia(String pageUrl, String pageText) throws IOException {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("dood", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        for (String passUrl : extractDoodPassUrls(pageText, pageUrl)) {
            if (cancelled.get()) {
                return null;
            }
            try {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("X-Requested-With", "XMLHttpRequest");
                String responseText = readText(passUrl, pageUrl, headers);
                candidates.addAll(extractDoodResponseCandidates(responseText, passUrl, pageText));
            } catch (IOException ignored) {
                // Try the next pass_md5 endpoint or fall back to page-level media candidates.
            }
        }
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate) && isDoodTransientMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("dood", candidates.get(0), candidates, pageUrl);
    }

    private List<String> extractDoodPassUrls(String pageText, String pageUrl) {
        List<String> out = new ArrayList<>();
        Matcher matcher = DOOD_PASS_MD5_PATH.matcher((pageText == null ? "" : pageText).replace("\\/", "/"));
        while (matcher.find()) {
            addUnique(out, joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim()));
        }
        return out;
    }

    private List<String> extractDoodResponseCandidates(String responseText, String passUrl, String pageText) {
        List<String> out = new ArrayList<>();
        String text = htmlDecoded(responseText == null ? "" : responseText).replace("\\/", "/").trim();
        addDoodCandidate(out, text, pageText);
        if (isMediaUrl(text) && isDoodTransientMediaUrl(text)) {
            addUnique(out, text);
        }
        Matcher matcher = DOOD_ANY_HTTP_URL.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            addDoodCandidate(out, candidate, pageText);
        }
        out.addAll(extractMediaCandidates(text, passUrl));
        return out;
    }

    private void addDoodCandidate(List<String> out, String rawCandidate, String pageText) {
        String candidate = cleanDoodCandidate(rawCandidate);
        if (candidate.isEmpty()) {
            return;
        }
        if (isMediaUrl(candidate) && isDoodTransientMediaUrl(candidate)) {
            addUnique(out, candidate);
        }
        String finalized = doodTokenizedMediaUrl(candidate, pageText);
        if (isMediaUrl(finalized) && isDoodTransientMediaUrl(finalized)) {
            addUnique(out, finalized);
        }
    }

    private String doodTokenizedMediaUrl(String rawCandidate, String pageText) {
        String candidate = cleanDoodCandidate(rawCandidate);
        if (candidate.isEmpty() || candidate.contains("token=")) {
            return "";
        }
        String token = firstMatch(pageText, DOOD_TOKEN_VALUE);
        if (token.isEmpty()) {
            return "";
        }
        String expiry = firstMatch(pageText, DOOD_EXPIRY_VALUE);
        if (expiry.isEmpty()) {
            expiry = String.valueOf(System.currentTimeMillis() + 6L * 60L * 60L * 1000L);
        }
        String separator = candidate.contains("?") ? "&" : "?";
        return candidate + doodRandomSuffix(candidate) + separator + "token=" + urlEncode(token) + "&expiry=" + expiry;
    }

    private String cleanDoodCandidate(String rawCandidate) {
        return htmlDecoded(rawCandidate == null ? "" : rawCandidate)
                .replace("\\/", "/")
                .trim()
                .replaceAll("[);,]+$", "");
    }

    private String doodRandomSuffix(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        long seed = Math.abs((long) (value == null ? 0 : value.hashCode())) + System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            out.append(alphabet.charAt((int) (seed % alphabet.length())));
            seed = seed / alphabet.length() + 17L;
        }
        return out.toString();
    }

    private boolean isDoodTransientMediaUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return false;
        }
        try {
            String host = new URL(rawUrl).getHost();
            String lowered = host == null ? "" : host.toLowerCase(Locale.US);
            return lowered.contains("cloudatacdn.com")
                    || isDoodFamilyHost(lowered);
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private boolean isDoodFamilyHost(String loweredHost) {
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

    private SiteMediaResult resolveStreamtapeMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("streamtape", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        for (String candidate : extractStreamtapeGetVideoCandidates(pageText, pageUrl)) {
            if (isMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("streamtape", candidates.get(0), candidates, pageUrl);
    }

    private List<String> extractStreamtapeGetVideoCandidates(String pageText, String pageUrl) {
        List<String> out = new ArrayList<>();
        Matcher matcher = STREAMTAPE_GET_VIDEO_PATH.matcher((pageText == null ? "" : pageText).replace("\\/", "/"));
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).replace("&amp;", "&").trim());
            if (isStreamtapeMediaUrl(candidate)) {
                addUnique(out, candidate);
            }
        }
        return out;
    }

    private boolean isStreamtapeMediaUrl(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return (lowered.contains("streamtape.") || lowered.contains("streamta.pe") || lowered.contains("strtape."))
                && lowered.contains("/get_video?");
    }

    private SiteMediaResult resolveFileHostMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("filehost", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("filehost", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolveSbHostMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("sbhost", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("sbhost", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolveMirrorHostMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("mirrorhost", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("mirrorhost", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolveCdnSourceMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("cdnsource", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("cdnsource", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolveNjavMedia(String pageUrl, String pageText) throws IOException {
        String path = "";
        try {
            path = new URL(pageUrl).getPath().toLowerCase(Locale.US);
        } catch (MalformedURLException ignored) {
            return null;
        }
        if (!path.contains("/xvideos/")) {
            return null;
        }
        String videoId = extractNjavVideoId(pageText);
        if (videoId.isEmpty()) {
            return null;
        }
        String origin = firstNonEmpty(originFromUrl(pageUrl), "https://www.njav.com");
        String apiText = readText(origin + "/api/v/" + videoId + "/videos", pageUrl);
        JSONObject payload = parseJsonObject(apiText);
        JSONArray entries = payload == null ? null : payload.optJSONArray("data");
        if (entries == null || entries.length() == 0) {
            return null;
        }
        String poster = "";
        Matcher posterMatcher = NJAV_POSTER.matcher(pageText == null ? "" : pageText);
        if (posterMatcher.find()) {
            poster = htmlDecoded(posterMatcher.group(1)).trim();
        }
        List<String> candidates = new ArrayList<>();
        String firstPlayerUrl = "";
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String vvUrl = joinUrl(origin + "/", entry.optString("url", ""));
            if (vvUrl.isEmpty()) {
                continue;
            }
            if (!poster.isEmpty() && !vvUrl.contains("poster=")) {
                vvUrl = vvUrl + (vvUrl.contains("?") ? "&" : "?") + "poster=" + urlEncode(poster);
            }
            String vvText = readText(vvUrl, pageUrl);
            Matcher frameMatcher = NJAV_VIDEO_FRAME_SRC.matcher(vvText);
            if (!frameMatcher.find()) {
                continue;
            }
            String playerUrl = joinUrl(vvUrl, htmlDecoded(frameMatcher.group(1)));
            if (playerUrl.isEmpty()) {
                continue;
            }
            String playerText = readText(playerUrl, vvUrl);
            String mediaUrl = extractNjavPlayerM3u8(playerText, playerUrl);
            if (!mediaUrl.isEmpty()) {
                candidates.add(mediaUrl);
                if (firstPlayerUrl.isEmpty()) {
                    firstPlayerUrl = playerUrl;
                }
            }
        }
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("njav", candidates.get(0), candidates, firstNonEmpty(firstPlayerUrl, pageUrl));
    }

    private String extractNjavVideoId(String pageText) {
        Matcher matcher = NJAV_VIDEO_ID.matcher(pageText == null ? "" : pageText);
        if (!matcher.find()) {
            return "";
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractNjavPlayerM3u8(String playerText, String playerUrl) {
        Matcher matcher = NJAV_PLAYER_M3U8.matcher(playerText == null ? "" : playerText);
        if (!matcher.find()) {
            return "";
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String candidate = joinUrl(playerUrl, htmlDecoded(value).replace("\\/", "/").trim());
            if (isMediaUrl(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private SiteMediaResult resolveNjavTvMedia(String pageUrl, String pageText) {
        String mediaUrl = extractNjavTvPlaylist(pageText);
        if (mediaUrl.isEmpty()) {
            return null;
        }
        List<String> candidates = dedupeMediaCandidates(Collections.singletonList(mediaUrl));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("njavtv", candidates.get(0), candidates, pageUrl);
    }

    private String extractNjavTvPlaylist(String pageText) {
        Matcher matcher = NJAVTV_SURRIT_PLAYLIST.matcher(pageText == null ? "" : pageText);
        if (!matcher.find()) {
            return "";
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String candidate = htmlDecoded(value).replace("\\/", "/").trim();
            if (isHlsUrl(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private SiteMediaResult resolveJableMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = JABLE_HLS.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String value = matcher.group(i);
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                String candidate = joinUrl(pageUrl, htmlDecoded(value).replace("\\/", "/").trim());
                if (isHlsUrl(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("jable", candidates.get(0), candidates, "https://jable.tv/");
    }

    private SiteMediaResult resolve85xVideoMedia(String pageUrl, String pageText) {
        return resolvePageSourceMedia("85xvideo", pageUrl, pageText, pageUrl);
    }

    private SiteMediaResult resolveTinyAVideoMedia(String pageUrl, String pageText) {
        return resolvePageSourceMedia("tinyavideo", pageUrl, pageText, pageUrl);
    }

    private SiteMediaResult resolvePageSourceMedia(String sourceSite, String pageUrl, String pageText, String refererUrl) {
        List<String> candidates = new ArrayList<>(extractMediaCandidates(pageText == null ? "" : pageText, pageUrl));
        Matcher sourceMatcher = SOURCE_SRC.matcher(pageText == null ? "" : pageText);
        while (sourceMatcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(sourceMatcher.group(1)).replace("\\/", "/").trim());
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                candidates.add(candidate);
            }
        }
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult(sourceSite, candidates.get(0), candidates, refererUrl);
    }

    private SiteMediaResult resolveSupJavMedia(String pageUrl, String pageText) throws IOException {
        String text = pageText == null ? "" : pageText;
        String bg = "";
        Matcher bgMatcher = SUPJAV_BG.matcher(text);
        if (bgMatcher.find()) {
            bg = htmlDecoded(bgMatcher.group(1)).trim();
        }
        Matcher serverMatcher = SUPJAV_SERVER_LINK.matcher(text);
        List<String> candidates = new ArrayList<>();
        String firstReferer = "";
        while (serverMatcher.find()) {
            String token = htmlDecoded(serverMatcher.group(1)).trim();
            if (token.isEmpty()) {
                continue;
            }
            String playerUrl = "https://lk1.supremejav.com/supjav.php?l=" + urlEncode(token) + "&bg=" + urlEncode(bg);
            String childUrl = "https://lk1.supremejav.com/supjav.php?c=" + urlEncode(reverse(token));
            String playerText;
            try {
                playerText = readText(playerUrl, pageUrl);
            } catch (IOException ignored) {
                continue;
            }
            Matcher childMatcher = SUPJAV_CHILD_SRC.matcher(playerText);
            if (childMatcher.find()) {
                childUrl = joinUrl(playerUrl, htmlDecoded(childMatcher.group(1)));
            }
            String childText;
            try {
                childText = readText(childUrl, playerUrl);
            } catch (IOException ignored) {
                continue;
            }
            candidates.addAll(extractMediaCandidates(childText, childUrl));
            Matcher hlsMatcher = SUPJAV_HLS_FIELD.matcher(childText);
            while (hlsMatcher.find()) {
                String candidate = joinUrl(childUrl, htmlDecoded(hlsMatcher.group(1)).replace("\\/", "/").trim());
                if (isMediaUrl(candidate)) {
                    candidates.add(candidate);
                }
            }
            if (firstReferer.isEmpty()) {
                firstReferer = childUrl;
            }
        }
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("supjav", candidates.get(0), candidates, firstNonEmpty(firstReferer, pageUrl));
    }

    private SiteMediaResult resolveEvoLoadMedia(String pageUrl, String pageText) throws IOException {
        List<String> pageVariants = new ArrayList<>();
        pageVariants.add(pageText == null ? "" : pageText);
        Matcher redirectMatcher = EVOLOAD_REDIRECT_LINK.matcher(pageText == null ? "" : pageText);
        if (redirectMatcher.find()) {
            String redirectBase = htmlDecoded(redirectMatcher.group(1)).trim();
            String redirectUrl = joinUrl(pageUrl, redirectBase + "fp=-5");
            if (!redirectUrl.isEmpty() && !redirectUrl.equals(pageUrl)) {
                try {
                    pageVariants.add(readText(redirectUrl, pageUrl));
                } catch (IOException ignored) {
                    // The original Evoload page can still expose media candidates.
                }
            }
        }
        if (isParkedEvoLoadPage(pageVariants)) {
            throw new IOException("EvoLoad source unavailable");
        }
        List<String> candidates = new ArrayList<>();
        for (String variant : pageVariants) {
            candidates.addAll(extractMediaCandidates(variant, pageUrl));
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("evoload", candidates.get(0), candidates, pageUrl);
    }

    private boolean isParkedEvoLoadPage(List<String> pageVariants) {
        for (String variant : pageVariants) {
            String text = variant == null ? "" : variant.toLowerCase(Locale.US);
            if (text.contains("assets.abovedomains.com")
                    || text.contains("forsale.min.js")
                    || text.contains("domain may be for sale")
                    || text.contains("this domain is for sale")
                    || text.contains("buy this domain")) {
                return true;
            }
        }
        return false;
    }

    private List<String> prioritizeManifestCandidates(List<String> candidates) {
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (isHlsUrl(candidate) || isDashUrl(candidate)) {
                out.add(candidate);
            }
        }
        for (String candidate : candidates) {
            if (!out.contains(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private SiteMediaResult resolveTkTubeMedia(String pageUrl, String pageText) {
        List<String> candidates = extractTkTubeMediaCandidates(pageUrl, pageText);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("tktube", candidates.get(0), candidates, pageUrl);
    }

    private List<String> extractTkTubeMediaCandidates(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            String lowered = candidate == null ? "" : candidate.toLowerCase(Locale.US);
            if (!lowered.contains("/get_file/") || !lowered.contains(".mp4")) {
                continue;
            }
            if (lowered.contains("preview")
                    || lowered.contains("screenshot")
                    || lowered.contains(".jpg")
                    || lowered.contains(".jpeg")
                    || lowered.contains(".png")
                    || lowered.contains(".webp")) {
                continue;
            }
            addUnique(candidates, candidate);
        }
        Collections.sort(candidates, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Integer.compare(tkTubeMediaPriority(left), tkTubeMediaPriority(right));
            }
        });
        return candidates;
    }

    private int tkTubeMediaPriority(String rawUrl) {
        Matcher matcher = TKTUBE_QUALITY.matcher(rawUrl == null ? "" : rawUrl);
        if (!matcher.find()) {
            return 0;
        }
        return -parseInt(matcher.group(1), 0);
    }

    private SiteMediaResult resolve18JavMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("18jav", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolve18AvMedia(String pageUrl, String pageText) {
        String text = htmlDecoded(pageText == null ? "" : pageText).replace("\\/", "/").replace("\\u002F", "/");
        List<String> candidates = new ArrayList<>();
        List<QualityCandidate> qualityCandidates = new ArrayList<>();
        Matcher qualityMatcher = EIGHTEEN_AV_QUALITY_SRC.matcher(text);
        while (qualityMatcher.find()) {
            String rawUrl = firstNonEmpty(qualityMatcher.group(1), qualityMatcher.group(4));
            int quality = parseInt(firstNonEmpty(qualityMatcher.group(2), qualityMatcher.group(3)), 0);
            String candidate = joinUrl(pageUrl, rawUrl);
            if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                qualityCandidates.add(new QualityCandidate(quality, candidate));
            }
        }
        Collections.sort(qualityCandidates, Comparator.comparingInt((QualityCandidate item) -> item.quality).reversed());
        for (QualityCandidate candidate : qualityCandidates) {
            addUnique(candidates, candidate.url);
        }
        try {
            for (String candidate : resolve18AvProtectedPlayerCandidates(pageUrl, text)) {
                if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                    addUnique(candidates, candidate);
                }
            }
        } catch (IOException ignored) {
            // Keep page/player-script candidates available when the protected endpoint rejects mobile requests.
        }
        for (String candidate : extractMediaCandidates(text, pageUrl)) {
            if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("18av", candidates.get(0), candidates, pageUrl);
    }

    private List<String> resolve18AvProtectedPlayerCandidates(String pageUrl, String pageText) throws IOException {
        Protected18AvPlayer player = extract18AvProtectedPlayer(pageText);
        if (player.iframePrefix.isEmpty()) {
            return new ArrayList<>();
        }
        String siteRoot = originFromUrl(pageUrl);
        String playerBase = joinUrl(firstNonEmpty(siteRoot, "https://18av.mm-cg.com") + "/", player.iframePrefix);
        List<String> playerIds = new ArrayList<>();
        addUnique(playerIds, player.decodedPayload);
        addUnique(playerIds, player.encodedId);
        addUnique(playerIds, "");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cookie", "javascript_cookie_AgreeTOS_save1=javascript_cookie_AgreeTOS_save2");
        List<String> candidates = new ArrayList<>();
        for (String playerId : playerIds) {
            String probeUrl = playerBase + urlEncode(playerId);
            try {
                String probeText = readText(probeUrl, pageUrl, headers);
                for (String candidate : extract18AvPlayerCandidates(probeText, probeUrl)) {
                    addUnique(candidates, candidate);
                }
            } catch (IOException ignored) {
                // Try the next decoded id form.
            }
            if (!candidates.isEmpty()) {
                break;
            }
        }
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private Protected18AvPlayer extract18AvProtectedPlayer(String pageText) {
        String text = pageText == null ? "" : pageText;
        String iframePrefix = "";
        Matcher prefixMatcher = EIGHTEEN_AV_IFRAME_PREFIX.matcher(text);
        if (prefixMatcher.find()) {
            iframePrefix = prefixMatcher.group(0);
        }
        String encodedId = firstMatch(text, EIGHTEEN_AV_ENCODED_PLAYER_ID);
        String decodedPayload = decrypt18AvPlayerId(
                encodedId,
                firstMatch(text, EIGHTEEN_AV_BASE_VALUE),
                firstMatch(text, EIGHTEEN_AV_XOR_VALUE),
                firstMatch(text, EIGHTEEN_AV_AES_KEY),
                firstMatch(text, EIGHTEEN_AV_AES_IV));
        return new Protected18AvPlayer(iframePrefix, encodedId, decodedPayload);
    }

    private String decrypt18AvPlayerId(String encodedValue, String baseValue, String xorValue, String aesKey, String aesIv) {
        String stage1 = decode18AvPayload(encodedValue, baseValue, xorValue);
        if (stage1.isEmpty() || aesKey == null || aesIv == null || aesKey.isEmpty() || aesIv.isEmpty()) {
            return stage1;
        }
        try {
            byte[] encrypted = Base64.decode(stage1, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(aesIv.getBytes(StandardCharsets.UTF_8)));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).trim();
        } catch (GeneralSecurityException | IllegalArgumentException ignored) {
            return stage1;
        }
    }

    private String decode18AvPayload(String encodedValue, String baseValue, String xorValue) {
        String encoded = encodedValue == null ? "" : encodedValue.trim();
        if (encoded.isEmpty()) {
            return "";
        }
        int base = parseInt(baseValue, 0);
        int xor = parseInt(xorValue, -1);
        if (base < 2 || base > 36 || xor < 0) {
            return "";
        }
        String[] parts = encoded.split(Pattern.quote(String.valueOf((char) (base + 97))));
        StringBuilder builder = new StringBuilder();
        try {
            for (String part : parts) {
                if (!part.isEmpty()) {
                    builder.append((char) (Integer.parseInt(part, base) ^ xor));
                }
            }
        } catch (NumberFormatException ignored) {
            return "";
        }
        return builder.toString();
    }

    private List<String> extract18AvPlayerCandidates(String playerText, String playerUrl) {
        String text = htmlDecoded(playerText == null ? "" : playerText).replace("\\/", "/").replace("\\u002F", "/");
        List<String> candidates = new ArrayList<>();
        List<QualityCandidate> qualityCandidates = new ArrayList<>();
        Matcher qualityMatcher = EIGHTEEN_AV_QUALITY_SRC.matcher(text);
        while (qualityMatcher.find()) {
            String rawUrl = firstNonEmpty(qualityMatcher.group(1), qualityMatcher.group(4));
            int quality = parseInt(firstNonEmpty(qualityMatcher.group(2), qualityMatcher.group(3)), 0);
            String candidate = joinUrl(playerUrl, rawUrl);
            if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                qualityCandidates.add(new QualityCandidate(quality, candidate));
            }
        }
        Collections.sort(qualityCandidates, Comparator.comparingInt((QualityCandidate item) -> item.quality).reversed());
        for (QualityCandidate candidate : qualityCandidates) {
            addUnique(candidates, candidate.url);
        }
        for (String candidate : extractMediaCandidates(text, playerUrl)) {
            if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private SiteMediaResult resolveAvbebeMedia(String pageUrl, String pageText) throws IOException {
        if (isAvbebeCategoryPage(pageUrl)) {
            for (String detailUrl : extractAvbebeArchiveLinks(pageUrl, pageText)) {
                try {
                    SiteMediaResult detailMedia = resolveAvbebeMedia(detailUrl, readText(detailUrl, pageUrl));
                    if (detailMedia != null) {
                        return detailMedia;
                    }
                } catch (IOException ignored) {
                    // Try the next archive item; category pages often contain stale entries.
                }
            }
        }

        List<String> candidates = new ArrayList<>();
        String firstReferer = "";
        for (String candidate : extractAvbebeStreamCandidates(pageText, pageUrl)) {
            addUnique(candidates, candidate);
            if (firstReferer.isEmpty()) {
                firstReferer = pageUrl;
            }
        }

        for (String iframeUrl : extractAvbebeIframeUrls(pageUrl, pageText)) {
            String playerUrl = avbebeEmbedUrl(iframeUrl);
            if (playerUrl.isEmpty() || (!isAvbebePlayableIframe(playerUrl) && !isAvbebeRetryableIframe(playerUrl))) {
                continue;
            }
            try {
                String playerText = readText(playerUrl, pageUrl);
                for (String candidate : extractAvbebeStreamCandidates(playerText, playerUrl)) {
                    if (candidates.isEmpty() || firstReferer.isEmpty()) {
                        firstReferer = playerUrl;
                    }
                    addUnique(candidates, candidate);
                }
            } catch (IOException ignored) {
                // Protected Avbebe mirrors rotate often; keep other iframe mirrors alive.
            }
        }

        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("avbebe", candidates.get(0), candidates, firstNonEmpty(firstReferer, pageUrl));
    }

    private boolean isAvbebeCategoryPage(String rawUrl) {
        try {
            String path = new URL(rawUrl).getPath();
            return path != null && path.toLowerCase(Locale.US).contains("/archives/category");
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extractAvbebeArchiveLinks(String pageUrl, String pageText) {
        List<String> out = new ArrayList<>();
        Matcher matcher = AVBEBE_ARCHIVE_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if (!candidate.isEmpty()) {
                addUnique(out, candidate);
            }
        }
        return out;
    }

    private List<String> extractAvbebeIframeUrls(String pageUrl, String pageText) {
        List<String> out = new ArrayList<>();
        Matcher matcher = AVBEBE_IFRAME_SRC.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if (candidate.isEmpty() || isAvbebeAdIframe(candidate)) {
                continue;
            }
            addUnique(out, candidate);
        }
        Collections.sort(out, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Integer.compare(avbebeIframePriority(left), avbebeIframePriority(right));
            }
        });
        return out;
    }

    private List<String> extractAvbebeStreamCandidates(String pageText, String pageUrl) {
        String text = htmlDecoded(pageText == null ? "" : pageText)
                .replace("\\/", "/")
                .replace("\\u002F", "/");
        List<QualityCandidate> hlsFields = new ArrayList<>();
        Matcher hlsMatcher = AVBEBE_HLS_FIELD.matcher(text);
        while (hlsMatcher.find()) {
            String fieldName = hlsMatcher.group(1).toLowerCase(Locale.US);
            String candidate = joinUrl(pageUrl, htmlDecoded(hlsMatcher.group(2)).trim());
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                hlsFields.add(new QualityCandidate(1000 - avbebeHlsPriority(fieldName), candidate));
            }
        }
        Collections.sort(hlsFields, Comparator.comparingInt((QualityCandidate item) -> item.quality).reversed());

        List<String> candidates = new ArrayList<>();
        for (QualityCandidate candidate : hlsFields) {
            addUnique(candidates, candidate.url);
        }
        for (String candidate : extractMediaCandidates(text, pageUrl)) {
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private int avbebeHlsPriority(String fieldName) {
        if ("hls4".equals(fieldName)) {
            return 0;
        }
        if ("hls2".equals(fieldName)) {
            return 10;
        }
        return 20;
    }

    private int avbebeIframePriority(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        if (lowered.contains("masukestin.com") || lowered.contains("swdyu.com")
                || lowered.contains("dhcplay.com") || lowered.contains("hglink.to")
                || lowered.contains("hgcloud.to")) {
            return 0;
        }
        if (lowered.contains("turbovidhls") || lowered.contains("turboviplay")) {
            return 10;
        }
        return 50;
    }

    private boolean isAvbebePlayableIframe(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("turbovidhls") || lowered.contains("turboviplay");
    }

    private boolean isAvbebeRetryableIframe(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("masukestin.com") || lowered.contains("swdyu.com")
                || lowered.contains("dhcplay.com") || lowered.contains("hglink.to")
                || lowered.contains("hgcloud.to");
    }

    private boolean isAvbebeAdIframe(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("ads") || lowered.contains("adsterra") || lowered.contains("popads")
                || lowered.contains("doubleclick") || lowered.contains("googlesyndication");
    }

    private String avbebeEmbedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        try {
            URL parsed = new URL(rawUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (host.contains("dhcplay.com") || host.contains("hglink.to") || host.contains("hgcloud.to")) {
                return new URL(parsed.getProtocol(), "masukestin.com", parsed.getPort(),
                        firstNonEmpty(parsed.getFile(), parsed.getPath())).toString();
            }
            return rawUrl.trim();
        } catch (MalformedURLException ignored) {
            return rawUrl.trim();
        }
    }

    private SiteMediaResult resolveAvJoyMedia(String pageUrl, String pageText) {
        if (!pagePathContains(pageUrl, "/video/")) {
            return mediaFromMediaResolver("avjoy", pageUrl, pageText, avJoyReferer(pageUrl));
        }
        String text = htmlDecoded(pageText == null ? "" : pageText)
                .replace("\\/", "/")
                .replace("\\u002F", "/");
        List<String> candidates = new ArrayList<>();
        List<QualityCandidate> hlsCandidates = new ArrayList<>();
        Matcher hlsMatcher = AVJOY_HLS_FIELD.matcher(text);
        while (hlsMatcher.find()) {
            String fieldName = hlsMatcher.group(1).toLowerCase(Locale.US);
            String candidate = joinUrl(pageUrl, htmlDecoded(hlsMatcher.group(2)).trim());
            if (isHlsUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                hlsCandidates.add(new QualityCandidate(1000 - avJoyHlsPriority(fieldName), candidate));
            }
        }
        Collections.sort(hlsCandidates, Comparator.comparingInt((QualityCandidate item) -> item.quality).reversed());
        for (QualityCandidate candidate : hlsCandidates) {
            addUnique(candidates, candidate.url);
        }

        SiteMediaResult pageMedia = mediaFromMediaResolver("avjoy", pageUrl, text, avJoyReferer(pageUrl));
        if (pageMedia != null) {
            for (String candidate : pageMedia.candidates) {
                if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                    addUnique(candidates, candidate);
                }
            }
        }
        for (String candidate : extractMediaCandidates(text, pageUrl)) {
            if (isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !isPreviewMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("avjoy", candidates.get(0), candidates, avJoyReferer(pageUrl));
    }

    private int avJoyHlsPriority(String fieldName) {
        if ("hls4".equals(fieldName)) {
            return 0;
        }
        if ("hls2".equals(fieldName)) {
            return 10;
        }
        return 20;
    }

    private String avJoyReferer(String pageUrl) {
        return firstNonEmpty(originFromUrl(pageUrl), "https://avjoy.me") + "/";
    }

    private SiteMediaResult resolveGgJavBackedMedia(String sourceSite, String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        String firstReferer = pageUrl;
        SiteMediaResult pageMedia = mediaFromMediaResolver(sourceSite, pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }

        for (String iframeUrl : extractPlayerIframeUrls(pageUrl, pageText)) {
            String iframeSite = MediaResolver.sourceSite(iframeUrl);
            if (!"ggjav".equals(iframeSite) && !"goodav17".equals(iframeSite) && !"hohoj".equals(iframeSite)) {
                continue;
            }
            try {
                String iframeText = readText(iframeUrl, pageUrl);
                SiteMediaResult iframeMedia = mediaFromMediaResolver(sourceSite, iframeUrl, iframeText, iframeUrl);
                if (iframeMedia != null) {
                    if (candidates.isEmpty()) {
                        firstReferer = iframeUrl;
                    }
                    candidates.addAll(iframeMedia.candidates);
                }
            } catch (IOException ignored) {
                // Keep page-decoded candidates when a mirror iframe blocks fetching.
            }
        }

        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult(sourceSite, candidates.get(0), candidates, firstReferer);
    }

    private List<String> extractPlayerIframeUrls(String pageUrl, String pageText) {
        List<String> out = new ArrayList<>();
        Matcher matcher = PLAYER_IFRAME_SRC.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String iframeUrl = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            String lowered = iframeUrl.toLowerCase(Locale.US);
            if (lowered.contains("ggjav.com")
                    || lowered.contains("goodav17.com")
                    || lowered.contains("hohoj.tv")
                    || lowered.contains("/embed")) {
                addUnique(out, iframeUrl);
            }
        }
        return out;
    }

    private SiteMediaResult resolveHayAvMedia(String pageUrl, String pageText) {
        if (!pagePathContains(pageUrl, "/video/")) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        String firstReferer = pageUrl;
        for (String candidate : extractHayAvEmbedCandidates(pageText, pageUrl)) {
            String embedUrl = avbebeEmbedUrl(candidate);
            if (isHayAvHgCloudEmbed(embedUrl)) {
                try {
                    String embedText = readText(embedUrl, pageUrl);
                    for (String streamCandidate : extractAvbebeStreamCandidates(embedText, embedUrl)) {
                        if (candidates.isEmpty()) {
                            firstReferer = embedUrl;
                        }
                        addUnique(candidates, streamCandidate);
                    }
                } catch (IOException ignored) {
                    // Keep the original embed/direct candidate as a fallback.
                }
            }
            if (isMediaUrl(embedUrl) && !isPreviewMediaUrl(embedUrl) && !looksLikeImageUrl(embedUrl)) {
                addUnique(candidates, embedUrl);
            }
        }

        SiteMediaResult pageMedia = mediaFromMediaResolver("hayav", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("hayav", candidates.get(0), candidates, firstReferer);
    }

    private List<String> extractHayAvEmbedCandidates(String pageText, String pageUrl) {
        List<String> candidates = new ArrayList<>();
        String text = pageText == null ? "" : pageText;
        Matcher secretMatcher = HAYAV_DATA_SECRET.matcher(text);
        while (secretMatcher.find()) {
            String decoded = decodeHayAvSecret(htmlDecoded(secretMatcher.group(1)));
            collectHayAvEmbedLinks(candidates, decoded, pageUrl);
        }
        for (String candidate : extractMediaCandidates(text, pageUrl)) {
            if (isMediaUrl(candidate) && !isPreviewMediaUrl(candidate) && !looksLikeImageUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        return candidates;
    }

    private void collectHayAvEmbedLinks(List<String> candidates, String decodedHtml, String pageUrl) {
        for (Pattern pattern : new Pattern[]{EMBED_LINK_QUOTED, EMBED_LINK_UNQUOTED}) {
            Matcher matcher = pattern.matcher(decodedHtml == null ? "" : decodedHtml);
            while (matcher.find()) {
                String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).replace("\\/", "/").trim());
                if (!candidate.isEmpty() && !isKnownAdMediaUrl(candidate)) {
                    addUnique(candidates, candidate);
                    String mapped = avbebeEmbedUrl(candidate);
                    if (!mapped.equals(candidate)) {
                        addUnique(candidates, mapped);
                    }
                }
            }
        }
    }

    private String decodeHayAvSecret(String secret) {
        try {
            byte[] payload = Base64.decode(secret == null ? "" : secret.trim(), Base64.DEFAULT);
            byte[] key = HAYAV_SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] decoded = new byte[payload.length];
            for (int i = 0; i < payload.length; i++) {
                decoded[i] = (byte) (payload[i] ^ key[i % key.length]);
            }
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private boolean isHayAvHgCloudEmbed(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return (lowered.contains("masukestin.com")
                || lowered.contains("swdyu.com")
                || lowered.contains("hgcloud.to")
                || lowered.contains("hglink.to")
                || lowered.contains("dhcplay.com"))
                && lowered.contains("/e/");
    }

    private boolean isKnownAdMediaUrl(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("ads")
                || lowered.contains("doubleclick")
                || lowered.contains("googlesyndication")
                || lowered.contains("popads")
                || lowered.contains("adsterra");
    }

    private boolean isPreviewMediaUrl(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("imgstream1.com")
                || lowered.contains("gifb.")
                || lowered.contains("fchost1.")
                || lowered.contains("fbhost1.")
                || lowered.contains("preview")
                || lowered.contains("screenshot");
    }

    private SiteMediaResult resolveHanime1Media(String pageUrl, String pageText) {
        if (!pagePathContains(pageUrl, "watch")) {
            return null;
        }
        List<String> candidates = prioritizeManifestCandidates(dedupeMediaCandidates(
                extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)));
        if (candidates.isEmpty()) {
            return null;
        }
        String origin = firstNonEmpty(originFromUrl(pageUrl), "https://hanime1.me");
        return new SiteMediaResult("hanime1", candidates.get(0), candidates, origin + "/");
    }

    private SiteMediaResult resolvePppPornMedia(String pageUrl, String pageText) {
        if (!pagePathContains(pageUrl, "/v/")) {
            return null;
        }
        List<String> candidates = prioritizeManifestCandidates(dedupeMediaCandidates(
                extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("ppp.porn", candidates.get(0), candidates, pageUrl);
    }

    private boolean pagePathContains(String rawUrl, String marker) {
        try {
            return new URL(rawUrl).getPath().toLowerCase(Locale.US).contains(marker.toLowerCase(Locale.US));
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private SiteMediaResult resolveJavFilmsMedia(String pageUrl, String pageText) {
        if (!pagePathContains(pageUrl, "/video/")) {
            return null;
        }
        List<String> manifestCandidates = new ArrayList<>();
        List<String> directCandidates = new ArrayList<>();
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (!isMediaUrl(candidate) || looksLikeImageUrl(candidate)) {
                continue;
            }
            if (isHlsUrl(candidate) || isDashUrl(candidate)) {
                addUnique(manifestCandidates, candidate);
            } else if (isJavFilmsDirectMedia(candidate)) {
                addUnique(directCandidates, candidate);
            }
        }
        manifestCandidates = prioritizeManifestCandidates(dedupeMediaCandidates(manifestCandidates));
        if (!manifestCandidates.isEmpty()) {
            return new SiteMediaResult("javfilms", manifestCandidates.get(0), manifestCandidates, pageUrl);
        }
        directCandidates = dedupeMediaCandidates(directCandidates);
        if (directCandidates.isEmpty()) {
            return null;
        }
        String referer = javFilmsDirectReferer(pageUrl, pageText, directCandidates.get(0));
        return new SiteMediaResult("javfilms", directCandidates.get(0), directCandidates, referer);
    }

    private boolean isJavFilmsDirectMedia(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("cc3001.dmm.co.jp")
                && lowered.contains("/litevideo/freepv/")
                && lowered.contains(".mp4");
    }

    private String javFilmsDirectReferer(String pageUrl, String pageText, String mediaUrl) {
        if (!isJavFilmsDirectMedia(mediaUrl)) {
            return pageUrl;
        }
        String videoId = extractJavFilmsDmmVideoId(pageUrl, pageText);
        if (videoId.isEmpty()) {
            return pageUrl;
        }
        return "https://video.dmm.co.jp/av/content/?id=" + urlEncode(videoId);
    }

    private String extractJavFilmsDmmVideoId(String pageUrl, String pageText) {
        Matcher matcher = JAVFILMS_DMM_VIDEO_ID.matcher(pageText == null ? "" : pageText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        try {
            String path = new URL(pageUrl).getPath();
            String[] parts = path == null ? new String[0] : path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i] == null ? "" : parts[i].trim();
                if (!part.isEmpty()) {
                    return part;
                }
            }
        } catch (MalformedURLException ignored) {
            // Fall through to no DMM referer.
        }
        return "";
    }

    private SiteMediaResult resolveMissAvMedia(String pageUrl, String pageText) throws IOException {
        List<String> candidates = extractMissAvMediaCandidates(pageUrl, pageText);
        String referer = pageUrl;
        if (candidates.isEmpty()) {
            for (String alternateUrl : missAvAlternatePageUrls(pageUrl)) {
                if (alternateUrl.equals(pageUrl)) {
                    continue;
                }
                try {
                    String alternateText = readText(alternateUrl, originFromUrl(pageUrl) + "/");
                    candidates = extractMissAvMediaCandidates(alternateUrl, alternateText);
                    if (!candidates.isEmpty()) {
                        referer = alternateUrl;
                        break;
                    }
                } catch (IOException ignored) {
                    // Try the next localized or dm mirror-style page.
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("missav", candidates.get(0), candidates, referer);
    }

    private List<String> extractMissAvMediaCandidates(String pageUrl, String pageText) {
        List<String> manifests = new ArrayList<>();
        List<String> direct = new ArrayList<>();
        for (String candidate : extractMediaCandidates(pageText == null ? "" : pageText, pageUrl)) {
            if (!isMediaUrl(candidate) || looksLikeImageUrl(candidate)) {
                continue;
            }
            if (isHlsUrl(candidate) || isDashUrl(candidate)) {
                addUnique(manifests, candidate);
            } else {
                addUnique(direct, candidate);
            }
        }
        manifests = filterMissAvManifestCandidates(prioritizeManifestCandidates(dedupeMediaCandidates(manifests)));
        direct = dedupeMediaCandidates(direct);
        List<String> out = new ArrayList<>(manifests);
        for (String candidate : direct) {
            addUnique(out, candidate);
        }
        return out;
    }

    private List<String> filterMissAvManifestCandidates(List<String> candidates) {
        Set<String> playlistPrefixes = new HashSet<>();
        for (String candidate : candidates) {
            String lowered = candidate == null ? "" : candidate.toLowerCase(Locale.US);
            if (lowered.endsWith("/playlist.m3u8")) {
                playlistPrefixes.add(lowered.substring(0, lowered.length() - "/playlist.m3u8".length()));
            }
        }
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            String lowered = candidate == null ? "" : candidate.toLowerCase(Locale.US);
            if (lowered.endsWith("/source/video.m3u8")) {
                String prefix = lowered.substring(0, lowered.length() - "/source/video.m3u8".length());
                if (playlistPrefixes.contains(prefix)) {
                    continue;
                }
            }
            addUnique(out, candidate);
        }
        return out;
    }

    private List<String> missAvAlternatePageUrls(String pageUrl) {
        List<String> alternates = new ArrayList<>();
        addUnique(alternates, pageUrl);
        try {
            URL parsed = new URL(pageUrl);
            String root = parsed.getProtocol() + "://" + parsed.getHost();
            String cleanPath = parsed.getPath() == null ? "" : parsed.getPath().replaceFirst("^/+", "");
            if (cleanPath.isEmpty()) {
                return alternates;
            }
            List<String> localized = new ArrayList<>();
            Matcher dmMatcher = Pattern.compile("^dm\\d+/(.+)$", Pattern.CASE_INSENSITIVE).matcher(cleanPath);
            String strippedDmPath = "";
            if (dmMatcher.find() && !dmMatcher.group(1).trim().isEmpty()) {
                strippedDmPath = dmMatcher.group(1).replaceFirst("^/+", "");
                localized.add(strippedDmPath);
            }
            if (!Pattern.compile("^(?:en|ja|ko|zh)(?:/|$)", Pattern.CASE_INSENSITIVE).matcher(cleanPath).find()) {
                localized.add("en/" + cleanPath);
                if (!strippedDmPath.isEmpty()) {
                    localized.add("en/" + strippedDmPath);
                }
            }
            if (!Pattern.compile("^dm\\d+/", Pattern.CASE_INSENSITIVE).matcher(cleanPath).find()) {
                for (String suffix : new String[]{"39", "85", "58", "55", "45", "43", "41", "31", "22"}) {
                    localized.add("dm" + suffix + "/" + cleanPath);
                }
            }
            for (String path : localized) {
                addUnique(alternates, root + "/" + path);
            }
        } catch (MalformedURLException ignored) {
            // Keep only the original page URL.
        }
        return alternates;
    }

    private SiteMediaResult resolve777TvMedia(String pageUrl, String pageText) throws IOException {
        if (is777TvPlayPage(pageUrl)) {
            return mediaFromMediaResolver("777tv", pageUrl, pageText, pageUrl);
        }
        if (!is777TvDetailPage(pageUrl)) {
            return null;
        }
        SiteMediaResult pageMedia = mediaFromMediaResolver("777tv", pageUrl, pageText, "https://777tv.ai/");
        if (pageMedia != null) {
            return pageMedia;
        }
        for (String playUrl : extract777TvPlayCandidates(pageText)) {
            if (cancelled.get()) {
                return null;
            }
            try {
                String playText = readText(playUrl, pageUrl);
                SiteMediaResult playMedia = mediaFromMediaResolver("777tv", playUrl, playText, playUrl);
                if (playMedia != null) {
                    return playMedia;
                }
            } catch (IOException ignored) {
                // Try the next episode/play-page candidate.
            }
        }
        return null;
    }

    private boolean is777TvDetailPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return TV777_DETAIL_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private boolean is777TvPlayPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return TV777_PLAY_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extract777TvPlayCandidates(String pageText) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = TV777_EPISODE_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String raw = htmlDecoded(matcher.group(1)).trim();
            String candidate = joinUrl("https://play.777tv.ai/", raw);
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                String host = new URL(candidate).getHost();
                if (host != null && host.toLowerCase(Locale.US).contains("777tv.ai")) {
                    addUnique(candidates, candidate);
                }
            } catch (MalformedURLException ignored) {
                // Ignore malformed episode links.
            }
        }
        return candidates;
    }

    private SiteMediaResult resolve99ItvMedia(String pageUrl, String pageText) throws IOException {
        if (is99ItvPlayPage(pageUrl)) {
            return mediaFromMediaResolver("99itv", pageUrl, pageText, pageUrl);
        }
        if (!is99ItvDetailPage(pageUrl)) {
            return null;
        }
        SiteMediaResult pageMedia = mediaFromMediaResolver("99itv", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            return pageMedia;
        }
        for (String playUrl : extract99ItvPlayCandidates(pageUrl, pageText)) {
            if (cancelled.get()) {
                return null;
            }
            try {
                String playText = readText(playUrl, pageUrl);
                SiteMediaResult playMedia = mediaFromMediaResolver("99itv", playUrl, playText, playUrl);
                if (playMedia != null) {
                    return playMedia;
                }
            } catch (IOException ignored) {
                // Try the next play-page candidate, mirroring the desktop resolver fallback flow.
            }
        }
        return null;
    }

    private SiteMediaResult mediaFromMediaResolver(String sourceSite, String pageUrl, String pageText, String refererUrl) {
        MediaResolver.Result resolved = MediaResolver.resolve(pageText == null ? "" : pageText, pageUrl);
        if (resolved.primaryUrl == null || !resolved.primaryIsMedia) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        for (String candidate : resolved.candidates) {
            if (isMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult(sourceSite, candidates.get(0), candidates, refererUrl);
    }

    private boolean is99ItvDetailPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return ITTV_DETAIL_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private boolean is99ItvPlayPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return ITTV_PLAY_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extract99ItvPlayCandidates(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        String text = pageText == null ? "" : pageText;
        Matcher attrMatcher = ITTV_PLAY_ATTR.matcher(text);
        while (attrMatcher.find()) {
            add99ItvPlayCandidate(candidates, pageUrl, attrMatcher.group(1));
        }
        Matcher pathMatcher = ITTV_PLAY_PATH_TEXT.matcher(text);
        while (pathMatcher.find()) {
            add99ItvPlayCandidate(candidates, pageUrl, pathMatcher.group(0));
        }
        return candidates;
    }

    private void add99ItvPlayCandidate(List<String> candidates, String pageUrl, String rawCandidate) {
        String candidate = joinUrl(pageUrl, htmlDecoded(rawCandidate).trim());
        if (candidate.isEmpty()) {
            return;
        }
        try {
            String host = new URL(candidate).getHost();
            if (host == null || !host.toLowerCase(Locale.US).contains("99itv.net")) {
                return;
            }
        } catch (MalformedURLException ignored) {
            return;
        }
        addUnique(candidates, candidate);
    }

    private SiteMediaResult resolveThanjuMedia(String pageUrl, String pageText) throws IOException {
        if (!isThanjuPage(pageUrl)) {
            return null;
        }
        if (pagePathContains(pageUrl, "/play/")) {
            return mediaFromMediaResolver("thanju", pageUrl, pageText, pageUrl);
        }
        SiteMediaResult pageMedia = mediaFromMediaResolver("thanju", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            return pageMedia;
        }
        for (String playUrl : extractThanjuPlayCandidates(pageUrl, pageText)) {
            if (cancelled.get()) {
                return null;
            }
            try {
                String playText = readText(playUrl, pageUrl);
                SiteMediaResult playMedia = mediaFromMediaResolver("thanju", playUrl, playText, playUrl);
                if (playMedia != null) {
                    return playMedia;
                }
            } catch (IOException ignored) {
                // Try the next play-page candidate.
            }
        }
        return null;
    }

    private boolean isThanjuPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return THANJU_PAGE_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extractThanjuPlayCandidates(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = THANJU_PLAY_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                String host = new URL(candidate).getHost();
                if (host != null && host.toLowerCase(Locale.US).contains("thanju.com")) {
                    addUnique(candidates, candidate);
                }
            } catch (MalformedURLException ignored) {
                // Ignore malformed play links.
            }
        }
        return candidates;
    }

    private SiteMediaResult resolveOlevodMedia(String pageUrl, String pageText) throws IOException {
        if (isOlevodPlayPage(pageUrl)) {
            return mediaFromMediaResolver("olevod", pageUrl, pageText, pageUrl);
        }
        if (!isOlevodDetailPage(pageUrl)) {
            return null;
        }
        SiteMediaResult pageMedia = mediaFromMediaResolver("olevod", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            return pageMedia;
        }
        for (String playUrl : extractOlevodPlayCandidates(pageUrl, pageText)) {
            if (cancelled.get()) {
                return null;
            }
            try {
                String playText = readText(playUrl, pageUrl);
                SiteMediaResult playMedia = mediaFromMediaResolver("olevod", playUrl, playText, playUrl);
                if (playMedia != null) {
                    return playMedia;
                }
            } catch (IOException ignored) {
                // Try the next play-page candidate.
            }
        }
        return null;
    }

    private boolean isOlevodDetailPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return OLEVOD_DETAIL_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private boolean isOlevodPlayPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return OLEVOD_PLAY_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extractOlevodPlayCandidates(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = OLEVOD_PLAY_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                String host = new URL(candidate).getHost();
                String loweredHost = host == null ? "" : host.toLowerCase(Locale.US);
                if (loweredHost.contains("olevod") || loweredHost.contains("olehdtv")) {
                    addUnique(candidates, candidate);
                }
            } catch (MalformedURLException ignored) {
                // Ignore malformed play links.
            }
        }
        return candidates;
    }

    private SiteMediaResult resolveMovieFfmMedia(String pageUrl, String pageText) throws IOException {
        return resolveMovieFfmMedia(pageUrl, pageText, true);
    }

    private SiteMediaResult resolveMovieFfmMedia(String pageUrl, String pageText, boolean allowSearchFallback) throws IOException {
        List<String> candidates = new ArrayList<>();
        String origin = firstNonEmpty(originFromUrl(pageUrl), "https://www.movieffm.me");
        String contentId = movieFfmContentId(pageUrl, pageText);
        if (!contentId.isEmpty()) {
            for (String slug : movieFfmEpisodeSlugs(pageText)) {
                if (cancelled.get()) {
                    return null;
                }
                String apiUrl = origin + "/ffm/" + urlEncode(contentId);
                if (!slug.isEmpty()) {
                    apiUrl += "/" + urlEncode(slug);
                }
                try {
                    candidates.addAll(extractMovieFfmApiCandidates(readText(apiUrl, pageUrl, movieFfmAjaxHeaders(pageUrl)), apiUrl));
                } catch (IOException ignored) {
                    // Try the next MovieFFM source tab or fallback parser.
                }
            }
        }
        SiteMediaResult pageMedia = mediaFromMediaResolver("movieffm", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return allowSearchFallback ? resolveMovieFfmAlternateFromSearch(pageUrl, pageText) : null;
        }
        return new SiteMediaResult("movieffm", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolveMovieFfmAlternateFromSearch(String pageUrl, String pageText) throws IOException {
        String title = movieFfmSearchTitle(pageText);
        if (title.isEmpty()) {
            return null;
        }
        List<VideoSearchResolver.Result> results;
        try {
            results = VideoSearchResolver.search(title);
        } catch (IOException ignored) {
            return null;
        }
        for (VideoSearchResolver.Result result : results) {
            if (cancelled.get()) {
                return null;
            }
            if (result == null || result.url == null || result.url.isEmpty() || sameUrlWithoutFragment(pageUrl, result.url)) {
                continue;
            }
            if (!"movieffm".equals(MediaResolver.sourceSite(result.url))) {
                continue;
            }
            try {
                String referer = firstNonEmpty(result.refererUrl, pageUrl);
                SiteMediaResult media = resolveMovieFfmMedia(result.url, readText(result.url, referer), false);
                if (media != null) {
                    return media;
                }
            } catch (IOException ignored) {
                // Try the next same-title MovieFFM candidate.
            }
        }
        return null;
    }

    private String movieFfmSearchTitle(String pageText) {
        String text = pageText == null ? "" : pageText;
        Matcher metaMatcher = META_TITLE.matcher(text);
        while (metaMatcher.find()) {
            String title = cleanMovieFfmTitle(firstNonEmpty(metaMatcher.group(1), metaMatcher.group(2)));
            if (!title.isEmpty()) {
                return title;
            }
        }
        Matcher titleMatcher = HTML_TITLE.matcher(text);
        if (titleMatcher.find()) {
            String title = cleanMovieFfmTitle(titleMatcher.group(1));
            if (!title.isEmpty()) {
                return title;
            }
        }
        return "";
    }

    private String cleanMovieFfmTitle(String rawTitle) {
        String title = htmlDecoded(rawTitle == null ? "" : rawTitle)
                .replaceAll("<[^>]+>", " ")
                .replaceFirst("\\s*[-|].*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (title.length() < 2 || title.length() > 80) {
            return "";
        }
        return title;
    }

    private boolean sameUrlWithoutFragment(String left, String right) {
        try {
            URL leftUrl = new URL(left == null ? "" : left);
            URL rightUrl = new URL(right == null ? "" : right);
            String leftPort = leftUrl.getPort() <= 0 ? "" : ":" + leftUrl.getPort();
            String rightPort = rightUrl.getPort() <= 0 ? "" : ":" + rightUrl.getPort();
            String leftNormalized = leftUrl.getProtocol().toLowerCase(Locale.US) + "://"
                    + leftUrl.getHost().toLowerCase(Locale.US) + leftPort
                    + firstNonEmpty(leftUrl.getPath(), "/") + (leftUrl.getQuery() == null ? "" : "?" + leftUrl.getQuery());
            String rightNormalized = rightUrl.getProtocol().toLowerCase(Locale.US) + "://"
                    + rightUrl.getHost().toLowerCase(Locale.US) + rightPort
                    + firstNonEmpty(rightUrl.getPath(), "/") + (rightUrl.getQuery() == null ? "" : "?" + rightUrl.getQuery());
            return leftNormalized.equals(rightNormalized);
        } catch (MalformedURLException ignored) {
            return (left == null ? "" : left).equals(right == null ? "" : right);
        }
    }

    private String movieFfmContentId(String pageUrl, String pageText) {
        String path = "";
        try {
            path = new URL(pageUrl).getPath();
        } catch (MalformedURLException ignored) {
            // Continue with page-script fallbacks.
        }
        Matcher ffmMatcher = MOVIEFFM_FFM_ID.matcher(path == null ? "" : path);
        if (ffmMatcher.find()) {
            return htmlDecoded(ffmMatcher.group(1)).trim();
        }
        Matcher pathMatcher = MOVIEFFM_PATH_ID.matcher(path == null ? "" : path);
        String pathId = pathMatcher.find() ? htmlDecoded(pathMatcher.group(1)).trim() : "";
        Matcher jsMatcher = MOVIEFFM_JS_ID.matcher(pageText == null ? "" : pageText);
        String jsId = jsMatcher.find() ? htmlDecoded(jsMatcher.group(1)).trim() : "";
        return firstNonEmpty(jsId, pathId);
    }

    private List<String> movieFfmEpisodeSlugs(String pageText) {
        List<String> slugs = new ArrayList<>();
        Matcher matcher = MOVIEFFM_EP_SLUG.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            addUnique(slugs, htmlDecoded(matcher.group(1)).trim());
        }
        if (slugs.isEmpty()) {
            slugs.add("");
        }
        return slugs;
    }

    private Map<String, String> movieFfmAjaxHeaders(String pageUrl) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Sec-Fetch-Dest", "empty");
        String origin = originFromUrl(pageUrl);
        if (!origin.isEmpty()) {
            headers.put("Origin", origin);
        }
        return headers;
    }

    private List<String> extractMovieFfmApiCandidates(String apiText, String apiUrl) {
        List<String> candidates = new ArrayList<>();
        try {
            JSONObject payload = parseJsonObject(apiText);
            JSONArray plays = payload == null ? null : payload.optJSONArray("video_plays");
            if (plays != null) {
                for (int i = 0; i < plays.length(); i++) {
                    JSONObject row = plays.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    for (String key : new String[]{"play_data", "v_data", "url", "play_url", "src"}) {
                        String candidate = joinUrl(apiUrl, row.optString(key, ""));
                        if (isMediaUrl(candidate)) {
                            addUnique(candidates, candidate);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Regex fallback below handles partial JSON and HTML-wrapped payloads.
        }
        candidates.addAll(extractMediaCandidates(apiText == null ? "" : apiText, apiUrl));
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private SiteMediaResult resolveDramaSqMedia(String pageUrl, String pageText) throws IOException {
        Matcher playMatcher = dramasqPlayMatcher(pageUrl);
        if (playMatcher.find()) {
            String apiUrl = "https://dramasq.io/drq/" + playMatcher.group(1) + "/" + playMatcher.group(2);
            List<String> candidates = new ArrayList<>();
            try {
                candidates.addAll(extractDramaSqApiCandidates(readText(apiUrl, pageUrl), apiUrl));
            } catch (IOException ignored) {
                // Fall back to media candidates embedded in the play page.
            }
            candidates.addAll(extractMediaCandidates(pageText == null ? "" : pageText, pageUrl));
            candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
            if (candidates.isEmpty()) {
                return null;
            }
            return new SiteMediaResult("dramasq", candidates.get(0), candidates, pageUrl);
        }
        if (!isDramaSqDetailPage(pageUrl)) {
            return null;
        }
        for (String playUrl : extractDramaSqPlayCandidates(pageUrl, pageText)) {
            if (cancelled.get()) {
                return null;
            }
            try {
                String playText = readText(playUrl, pageUrl);
                SiteMediaResult playMedia = resolveDramaSqMedia(playUrl, playText);
                if (playMedia != null) {
                    return playMedia;
                }
            } catch (IOException ignored) {
                // Try the next episode/play-page candidate.
            }
        }
        return null;
    }

    private Matcher dramasqPlayMatcher(String pageUrl) {
        String path = "";
        try {
            path = new URL(pageUrl).getPath();
        } catch (MalformedURLException ignored) {
            // Return a matcher that will not match.
        }
        return DRAMASQ_PLAY_PATH.matcher(path == null ? "" : path);
    }

    private boolean isDramaSqDetailPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return DRAMASQ_DETAIL_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extractDramaSqPlayCandidates(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = DRAMASQ_PLAY_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                String host = new URL(candidate).getHost();
                if (host != null && host.toLowerCase(Locale.US).contains("dramasq")) {
                    addUnique(candidates, candidate);
                }
            } catch (MalformedURLException ignored) {
                // Ignore malformed play links.
            }
        }
        return candidates;
    }

    private List<String> extractDramaSqApiCandidates(String apiText, String apiUrl) {
        List<String> candidates = new ArrayList<>();
        try {
            JSONObject payload = parseJsonObject(apiText);
            JSONArray plays = payload == null ? null : payload.optJSONArray("video_plays");
            if (plays != null) {
                for (int i = 0; i < plays.length(); i++) {
                    JSONObject entry = plays.optJSONObject(i);
                    if (entry == null) {
                        continue;
                    }
                    for (String key : new String[]{"play_data", "v_data", "url", "play_url", "src"}) {
                        String candidate = joinUrl(apiUrl, entry.optString(key, ""));
                        if (isMediaUrl(candidate)) {
                            addUnique(candidates, candidate);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep regex fallback alive for API shape changes.
        }
        candidates.addAll(extractMediaCandidates(apiText == null ? "" : apiText, apiUrl));
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private SiteMediaResult resolve3KorMedia(String pageUrl, String pageText) throws IOException {
        if (is3KorListPage(pageUrl)) {
            for (String detailUrl : extract3KorDetailCandidates(pageUrl, pageText)) {
                if (cancelled.get()) {
                    return null;
                }
                try {
                    String detailText = readText(detailUrl, pageUrl);
                    SiteMediaResult detailMedia = resolve3KorMedia(detailUrl, detailText);
                    if (detailMedia != null) {
                        return detailMedia;
                    }
                } catch (IOException ignored) {
                    // Try the next detail candidate.
                }
            }
            return null;
        }
        if (!is3KorDetailPage(pageUrl)) {
            return null;
        }
        String detailUrl = stripQueryAndFragment(pageUrl);
        List<String> playIds = extract3KorPlayIds(pageText);
        if (playIds.isEmpty()) {
            return mediaFromMediaResolver("3kor", pageUrl, pageText, detailUrl);
        }
        String requestedPlayId = queryParameter(pageUrl, "play");
        String selectedPlayId = playIds.contains(requestedPlayId) ? requestedPlayId : playIds.get(0);
        String apiUrl = "https://3kor.com/u/u1.php?ud=" + urlEncode(selectedPlayId);
        String encryptedText = readText(apiUrl, detailUrl).trim();
        String directStream = decrypt3KorStreamUrl(encryptedText);
        if (directStream.isEmpty()) {
            return null;
        }
        String streamUrl = "https://3kor.com/m3/edit-down.php?url=" + urlEncode(directStream);
        List<String> candidates = new ArrayList<>();
        addUnique(candidates, streamUrl);
        for (String playId : playIds) {
            if (!playId.equals(selectedPlayId)) {
                addUnique(candidates, detailUrl + "?play=" + urlEncode(playId));
            }
        }
        return new SiteMediaResult("3kor", streamUrl, candidates, detailUrl);
    }

    private boolean is3KorListPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return THREEKOR_LIST_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private boolean is3KorDetailPage(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            return THREEKOR_DETAIL_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private List<String> extract3KorDetailCandidates(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = THREEKOR_DETAIL_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if (!candidate.isEmpty()) {
                addUnique(candidates, candidate);
            }
        }
        return candidates;
    }

    private List<String> extract3KorPlayIds(String pageText) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = THREEKOR_PLAY_ENTRY.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            addUnique(ids, htmlDecoded(matcher.group(1)).trim());
        }
        return ids;
    }

    private String decrypt3KorStreamUrl(String encryptedText) throws IOException {
        try {
            byte[] encryptedBytes = Base64.decode(encryptedText == null ? "" : encryptedText.trim(), Base64.DEFAULT);
            if (encryptedBytes.length <= 16) {
                throw new IOException("3KOR encrypted stream invalid");
            }
            byte[] key = Arrays.copyOf("my-to-newhan-2025".getBytes(StandardCharsets.UTF_8), 32);
            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(encryptedBytes, 16, encryptedBytes.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            String streamUrl = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8).trim();
            return isMediaUrl(streamUrl) ? streamUrl : "";
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IOException("3KOR decrypt failed", error);
        }
    }

    private String stripQueryAndFragment(String rawUrl) {
        try {
            URL parsed = new URL(rawUrl);
            return parsed.getProtocol() + "://" + parsed.getHost() + parsed.getPath();
        } catch (MalformedURLException ignored) {
            return rawUrl == null ? "" : rawUrl;
        }
    }

    private String queryParameter(String rawUrl, String name) {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        String value = uri.getQueryParameter(name);
        return value == null ? "" : value.trim();
    }

    private SiteMediaResult resolveNnyyMedia(String pageUrl, String pageText) throws IOException {
        String pageId = nnyyPageId(pageUrl);
        if (pageId.isEmpty()) {
            return null;
        }
        List<String> slugs = extractNnyyEpisodeSlugs(pageText);
        String selectedSlug = queryParameter(pageUrl, "ep");
        if (selectedSlug.isEmpty()) {
            selectedSlug = extractNnyyDefaultEpisodeSlug(pageText);
        }
        if (selectedSlug.isEmpty() && !slugs.isEmpty()) {
            selectedSlug = slugs.get(0);
        }
        if (selectedSlug.isEmpty()) {
            return null;
        }
        String apiUrl = "https://nnyy.in/_gp/" + pageId + "/" + selectedSlug;
        List<String> candidates = extractNnyyApiCandidates(readText(apiUrl, pageUrl), apiUrl);
        if (candidates.isEmpty()) {
            return null;
        }
        for (String slug : slugs) {
            if (!slug.equals(selectedSlug)) {
                addUnique(candidates, stripQueryAndFragment(pageUrl) + "?ep=" + urlEncode(slug));
            }
        }
        return new SiteMediaResult("nnyy", candidates.get(0), candidates, pageUrl);
    }

    private String nnyyPageId(String pageUrl) {
        try {
            String path = new URL(pageUrl).getPath();
            Matcher matcher = NNYY_PAGE_PATH.matcher(path == null ? "" : path);
            return matcher.find() ? matcher.group(1) : "";
        } catch (MalformedURLException ignored) {
            return "";
        }
    }

    private String extractNnyyDefaultEpisodeSlug(String pageText) {
        Matcher matcher = NNYY_DEFAULT_EP.matcher(pageText == null ? "" : pageText);
        return matcher.find() ? htmlDecoded(matcher.group(1)).trim() : "";
    }

    private List<String> extractNnyyEpisodeSlugs(String pageText) {
        List<String> slugs = new ArrayList<>();
        String text = pageText == null ? "" : pageText;
        Matcher tagMatcher = NNYY_EP_SLUG_TAG.matcher(text);
        while (tagMatcher.find()) {
            addUnique(slugs, htmlDecoded(tagMatcher.group(2)).trim());
        }
        Matcher attrMatcher = NNYY_EP_SLUG_ATTR.matcher(text);
        while (attrMatcher.find()) {
            addUnique(slugs, htmlDecoded(attrMatcher.group(1)).trim());
        }
        return slugs;
    }

    private List<String> extractNnyyApiCandidates(String apiText, String apiUrl) {
        List<String> candidates = new ArrayList<>();
        try {
            JSONObject payload = parseJsonObject(apiText);
            JSONArray plays = payload == null ? null : payload.optJSONArray("video_plays");
            if (plays != null) {
                for (int i = 0; i < plays.length(); i++) {
                    JSONObject row = plays.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    String candidate = joinUrl(apiUrl, row.optString("play_data", ""));
                    if (isMediaUrl(candidate)) {
                        addUnique(candidates, candidate);
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep regex fallback alive for API shape changes.
        }
        candidates.addAll(extractMediaCandidates(apiText == null ? "" : apiText, apiUrl));
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private SiteMediaResult resolveIkanbotMedia(String pageUrl, String pageText) throws IOException {
        if (!isIkanbotPlayPage(pageUrl)) {
            return mediaFromMediaResolver("ikanbot", pageUrl, pageText, pageUrl);
        }
        String origin = firstNonEmpty(originFromUrl(pageUrl), "https://www1.ikanbot.com");
        List<String> candidates = new ArrayList<>(extractIkanbotMediaCandidates(pageText, pageUrl));
        String videoId = extractIkanbotHiddenValue(pageText, "current_id");
        String mtype = firstNonEmpty(extractIkanbotHiddenValue(pageText, "mtype"), "0");
        if (!videoId.isEmpty()) {
            List<String> tokenCandidates = ikanbotTokenCandidates(pageText, videoId);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
            headers.put("X-Requested-With", "XMLHttpRequest");
            headers.put("Sec-Fetch-Site", "same-origin");
            headers.put("Sec-Fetch-Mode", "cors");
            headers.put("Sec-Fetch-Dest", "empty");
            for (String token : tokenCandidates) {
                String apiUrl = origin + "/api/getResN?videoId=" + urlEncode(videoId)
                        + "&mtype=" + urlEncode(mtype)
                        + "&token=" + urlEncode(token);
                try {
                    candidates.addAll(extractIkanbotMediaCandidates(readText(apiUrl, pageUrl, headers), pageUrl));
                } catch (IOException ignored) {
                    // Try the next token form; Ikanbot changes accepted token shapes over time.
                }
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return mediaFromMediaResolver("ikanbot", pageUrl, pageText, pageUrl);
        }
        return new SiteMediaResult("ikanbot", candidates.get(0), candidates, pageUrl);
    }

    private boolean isIkanbotPlayPage(String pageUrl) {
        try {
            String host = new URL(pageUrl).getHost();
            String path = new URL(pageUrl).getPath();
            return host != null && host.toLowerCase(Locale.US).contains("ikanbot")
                    && IKANBOT_PLAY_PATH.matcher(path == null ? "" : path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private String extractIkanbotHiddenValue(String pageText, String fieldId) {
        String field = Pattern.quote(fieldId == null ? "" : fieldId);
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("id=[\"']" + field + "[\"'][^>]*value=[\"']([^\"']*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                Pattern.compile("value=[\"']([^\"']*)[\"'][^>]*id=[\"']" + field + "[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(pageText == null ? "" : pageText);
            if (matcher.find()) {
                return htmlDecoded(matcher.group(1)).trim();
            }
        }
        return "";
    }

    private List<String> ikanbotTokenCandidates(String pageText, String videoId) {
        List<String> tokens = new ArrayList<>();
        Matcher windowMatcher = IKANBOT_WINDOW_TOKEN.matcher(pageText == null ? "" : pageText);
        if (windowMatcher.find()) {
            addUnique(tokens, htmlDecoded(windowMatcher.group(1)).trim());
        }
        String eToken = extractIkanbotHiddenValue(pageText, "e_token");
        String derived = deriveIkanbotApiToken(videoId, eToken);
        addUnique(tokens, derived);
        addUnique(tokens, "");
        addUnique(tokens, eToken);
        if (eToken.startsWith("wa") && eToken.contains("ve")) {
            addUnique(tokens, eToken.substring(2));
            if (eToken.length() > 4) {
                addUnique(tokens, eToken.substring(2, eToken.length() - 2));
            }
            if (eToken.length() >= 34) {
                addUnique(tokens, eToken.substring(2, 34));
            }
        }
        return tokens;
    }

    private String deriveIkanbotApiToken(String videoId, String eToken) {
        String digits = videoId == null ? "" : videoId.replaceAll("\\D+", "");
        String remaining = eToken == null ? "" : eToken.trim();
        if (digits.isEmpty() || remaining.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String tail = digits.length() > 4 ? digits.substring(digits.length() - 4) : digits;
        for (int i = 0; i < tail.length(); i++) {
            int offset = (tail.charAt(i) - '0') % 3 + 1;
            if (remaining.length() < offset + 8) {
                return "";
            }
            builder.append(remaining, offset, offset + 8);
            remaining = remaining.substring(offset + 8);
        }
        return builder.toString().trim();
    }

    private List<String> extractIkanbotMediaCandidates(String value, String baseUrl) {
        List<String> candidates = new ArrayList<>(extractMediaCandidates(value == null ? "" : value, baseUrl));
        Matcher fieldMatcher = IKANBOT_URL_FIELD.matcher(value == null ? "" : value);
        while (fieldMatcher.find()) {
            String candidate = joinUrl(baseUrl, htmlDecoded(fieldMatcher.group(1)).replace("\\/", "/").trim());
            if (isMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
        String text = value == null ? "" : value.trim();
        if (text.length() < 200000 && (text.startsWith("{") || text.startsWith("["))) {
            try {
                if (text.startsWith("{")) {
                    collectIkanbotJsonCandidates(new JSONObject(text), baseUrl, candidates);
                } else {
                    collectIkanbotJsonCandidates(new JSONArray(text), baseUrl, candidates);
                }
            } catch (Exception ignored) {
                // Keep regex candidates when the response is JavaScript-like rather than strict JSON.
            }
        }
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private void collectIkanbotJsonCandidates(Object node, String baseUrl, List<String> candidates) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                collectIkanbotJsonCandidates(object.opt(keys.next()), baseUrl, candidates);
            }
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectIkanbotJsonCandidates(array.opt(i), baseUrl, candidates);
            }
            return;
        }
        if (node instanceof String) {
            candidates.addAll(extractIkanbotMediaCandidates((String) node, baseUrl));
        }
    }

    private SiteMediaResult resolveYfspMedia(String pageUrl, String pageText) {
        String playKey = extractYfspPlayKey(pageUrl);
        List<String> candidates = new ArrayList<>();
        if (!playKey.isEmpty()) {
            addUnique(candidates, "https://upload.yfsp.tv/api/video/MasterPlayList?id=" + urlEncode(playKey));
            addUnique(candidates, "https://upload.yfsp.tv/api/video/MasterPlayList?id=" + playKey);
            addUnique(candidates, "https://upload.yfsp.tv/api/video/Playlist?id=" + urlEncode(playKey));
        }
        SiteMediaResult pageMedia = mediaFromMediaResolver("yfsp", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            for (String candidate : pageMedia.candidates) {
                addUnique(candidates, candidate);
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("yfsp", candidates.get(0), candidates, pageUrl);
    }

    private SiteMediaResult resolveTwitterMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("twitter", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        TwitterStatus status = twitterStatusFromUrl(pageUrl);
        if (status != null) {
            String apiUrl = "https://api.vxtwitter.com/" + urlEncode(status.screenName) + "/status/" + urlEncode(status.statusId);
            try {
                candidates.addAll(extractJsonMediaCandidates(readText(apiUrl, pageUrl), apiUrl));
            } catch (IOException ignored) {
                // Keep page metadata candidates when the public helper API is unavailable.
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("twitter", candidates.get(0), candidates, pageUrl);
    }

    private TwitterStatus twitterStatusFromUrl(String pageUrl) {
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (!host.contains("twitter.com") && !host.equals("x.com") && !host.endsWith(".x.com")
                    && !host.contains("vxtwitter.com") && !host.contains("fxtwitter.com")) {
                return null;
            }
            Matcher matcher = TWITTER_STATUS_PATH.matcher(parsed.getPath() == null ? "" : parsed.getPath());
            if (!matcher.find()) {
                return null;
            }
            String screenName = htmlDecoded(matcher.group(1)).trim();
            String statusId = htmlDecoded(matcher.group(2)).trim();
            return screenName.isEmpty() || statusId.isEmpty() ? null : new TwitterStatus(screenName, statusId);
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private SiteMediaResult resolveInstagramMedia(String pageUrl, String pageText) {
        String shortcode = instagramShortcodeFromUrl(pageUrl);
        if (shortcode.isEmpty()) {
            return mediaFromMediaResolver("instagram", pageUrl, pageText, pageUrl);
        }
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("instagram", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        String embedUrl = "https://www.instagram.com/reel/" + urlEncode(shortcode) + "/embed/captioned/";
        try {
            candidates.addAll(extractJsonMediaCandidates(readText(embedUrl, pageUrl, instagramHeaders(pageUrl)), embedUrl));
        } catch (IOException ignored) {
            // Some posts disable embed access; continue with API fallback.
        }
        String mediaId = instagramMediaIdFromShortcode(shortcode);
        if (!mediaId.isEmpty()) {
            for (String apiUrl : new String[]{
                    "https://i.instagram.com/api/v1/media/" + mediaId + "/info/",
                    "https://www.instagram.com/api/v1/media/" + mediaId + "/info/"
            }) {
                try {
                    candidates.addAll(extractJsonMediaCandidates(readText(apiUrl, pageUrl, instagramApiHeaders()), apiUrl));
                } catch (IOException ignored) {
                    // Try the alternate Instagram API host.
                }
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("instagram", candidates.get(0), candidates, "https://www.instagram.com/");
    }

    private String instagramShortcodeFromUrl(String pageUrl) {
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (!host.contains("instagram.com")) {
                return "";
            }
            Matcher matcher = INSTAGRAM_SHORTCODE_PATH.matcher(parsed.getPath() == null ? "" : parsed.getPath());
            return matcher.find() ? htmlDecoded(matcher.group(1)).trim() : "";
        } catch (MalformedURLException ignored) {
            return "";
        }
    }

    private String instagramMediaIdFromShortcode(String shortcode) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        BigInteger mediaId = BigInteger.ZERO;
        String code = shortcode == null ? "" : shortcode.trim();
        if (code.isEmpty()) {
            return "";
        }
        for (int i = 0; i < code.length(); i++) {
            int index = alphabet.indexOf(code.charAt(i));
            if (index < 0) {
                return "";
            }
            mediaId = mediaId.multiply(BigInteger.valueOf(64L)).add(BigInteger.valueOf(index));
        }
        return String.valueOf(mediaId);
    }

    private Map<String, String> instagramHeaders(String refererUrl) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "*/*");
        headers.put("Referer", firstNonEmpty(refererUrl, "https://www.instagram.com/"));
        headers.put("Origin", "https://www.instagram.com");
        return headers;
    }

    private Map<String, String> instagramApiHeaders() {
        Map<String, String> headers = instagramHeaders("https://www.instagram.com/");
        headers.put("X-IG-App-ID", "936619743392459");
        headers.put("X-ASBD-ID", "198387");
        headers.put("X-IG-WWW-Claim", "0");
        return headers;
    }

    private SiteMediaResult resolveFacebookMedia(String pageUrl, String pageText) {
        if (!isFacebookVideoPage(pageUrl)) {
            return mediaFromMediaResolver("facebook", pageUrl, pageText, pageUrl);
        }
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("facebook", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        FacebookGraphqlPayload graphqlPayload = extractFacebookGraphqlPayload(pageText);
        if (graphqlPayload != null) {
            try {
                String body = facebookGraphqlBody(graphqlPayload);
                candidates.addAll(extractJsonMediaCandidates(postForm("https://www.facebook.com/api/graphql/", body, pageUrl), pageUrl));
            } catch (IOException ignored) {
                // Keep static page candidates when GraphQL requires an authenticated/browser session.
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("facebook", candidates.get(0), candidates, pageUrl);
    }

    private boolean isFacebookVideoPage(String pageUrl) {
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (!host.contains("facebook.com") && !host.equals("fb.watch")) {
                return false;
            }
            String path = parsed.getPath() == null ? "" : parsed.getPath();
            return host.equals("fb.watch") || FACEBOOK_VIDEO_PATH.matcher(path).find();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }

    private FacebookGraphqlPayload extractFacebookGraphqlPayload(String pageText) {
        String text = pageText == null ? "" : pageText;
        Matcher lsdMatcher = FACEBOOK_LSD_FIELD.matcher(text);
        Matcher queryMatcher = FACEBOOK_REELS_QUERY.matcher(text);
        if (!lsdMatcher.find() || !queryMatcher.find()) {
            return null;
        }
        String lsd = htmlDecoded(lsdMatcher.group(1)).trim();
        String docId = htmlDecoded(queryMatcher.group(1)).trim();
        String variables = htmlDecoded(queryMatcher.group(2)).trim();
        if (lsd.isEmpty() || docId.isEmpty() || variables.isEmpty()) {
            return null;
        }
        return new FacebookGraphqlPayload(lsd, docId, variables);
    }

    private String facebookGraphqlBody(FacebookGraphqlPayload payload) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("av", "0");
        form.put("__aaid", "0");
        form.put("__user", "0");
        form.put("__a", "1");
        form.put("__comet_req", "15");
        form.put("fb_api_caller_class", "RelayModern");
        form.put("fb_api_req_friendly_name", "FBReelsRootWithEntrypointQuery");
        form.put("variables", payload.variables);
        form.put("doc_id", payload.docId);
        form.put("server_timestamps", "true");
        form.put("lsd", payload.lsd);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return builder.toString();
    }

    private SiteMediaResult resolveThreadsMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("threads", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        candidates.addAll(extractThreadsVideoVersionCandidates(pageText));
        candidates = dedupeMediaCandidates(candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("threads", candidates.get(0), candidates, pageUrl);
    }

    private List<String> extractThreadsVideoVersionCandidates(String pageText) {
        List<ThreadsVideoCandidate> ranked = new ArrayList<>();
        String text = htmlDecoded(pageText == null ? "" : pageText)
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\u0026", "&");
        Matcher matcher = THREADS_VIDEO_VERSIONS_START.matcher(text);
        while (matcher.find()) {
            String blob = balancedArray(text, matcher.end() - 1);
            if (blob == null) {
                continue;
            }
            try {
                JSONArray versions = new JSONArray(blob);
                for (int i = 0; i < versions.length(); i++) {
                    JSONObject version = versions.optJSONObject(i);
                    if (version == null) {
                        continue;
                    }
                    String url = firstNonEmpty(version.optString("url", ""), version.optString("video_url", ""));
                    String candidate = url == null ? "" : url.replace("\\/", "/").replace("\\u0026", "&").trim();
                    if (!isMediaUrl(candidate)) {
                        continue;
                    }
                    int width = Math.max(0, version.optInt("width", 0));
                    int height = Math.max(0, version.optInt("height", 0));
                    ranked.add(new ThreadsVideoCandidate(width * height, candidate));
                }
            } catch (Exception ignored) {
                // Try the next embedded video_versions block.
            }
        }
        Collections.sort(ranked, Comparator.comparingInt((ThreadsVideoCandidate item) -> item.area).reversed());
        List<String> candidates = new ArrayList<>();
        for (ThreadsVideoCandidate candidate : ranked) {
            addUnique(candidates, candidate.url);
        }
        return candidates;
    }

    private SiteMediaResult resolveDailymotionMedia(String pageUrl, String pageText) {
        String videoId = dailymotionVideoIdFromUrl(pageUrl);
        if (videoId.isEmpty()) {
            return mediaFromMediaResolver("dailymotion", pageUrl, pageText, pageUrl);
        }
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("dailymotion", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        String metadataUrl = "https://www.dailymotion.com/player/metadata/video/" + urlEncode(videoId);
        try {
            candidates.addAll(extractJsonMediaCandidates(readText(metadataUrl, pageUrl), metadataUrl));
        } catch (IOException ignored) {
            // Fall back to page candidates when player metadata is unavailable.
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("dailymotion", candidates.get(0), candidates, pageUrl);
    }

    private String dailymotionVideoIdFromUrl(String pageUrl) {
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (!host.contains("dailymotion.com") && !host.equals("dai.ly")) {
                return "";
            }
            Matcher matcher = DAILYMOTION_VIDEO_PATH.matcher(parsed.getPath() == null ? "" : parsed.getPath());
            if (!matcher.find()) {
                return "";
            }
            String videoId = htmlDecoded(matcher.group(1)).trim();
            return videoId.matches("[A-Za-z0-9]+") ? videoId : "";
        } catch (MalformedURLException ignored) {
            return "";
        }
    }

    private SiteMediaResult resolveBilibiliMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("bilibili", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        BilibiliVideoInfo info = bilibiliVideoInfoFromUrl(pageUrl, pageText);
        if (info != null) {
            try {
                BilibiliVideoInfo apiInfo = fetchBilibiliVideoInfo(info, pageUrl);
                if (apiInfo != null) {
                    info = apiInfo;
                }
            } catch (IOException ignored) {
                // Page-embedded candidates still remain available.
            }
            if (!info.cid.isEmpty()) {
                for (int qn : new int[]{80, 64, 32, 16}) {
                    String playUrl = "https://api.bilibili.com/x/player/playurl?"
                            + (info.bvid.isEmpty() ? "avid=" + urlEncode(info.aid) : "bvid=" + urlEncode(info.bvid))
                            + "&cid=" + urlEncode(info.cid)
                            + "&qn=" + qn
                            + "&type=&otype=json&fnval=0&fourk=1";
                    try {
                        candidates.addAll(extractBilibiliPlayurlCandidates(readText(playUrl, "https://www.bilibili.com/"), playUrl));
                    } catch (IOException ignored) {
                        // Try the next quality level.
                    }
                }
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("bilibili", candidates.get(0), candidates, "https://www.bilibili.com/");
    }

    private BilibiliVideoInfo fetchBilibiliVideoInfo(BilibiliVideoInfo seed, String pageUrl) throws IOException {
        String viewUrl = "https://api.bilibili.com/x/web-interface/view?"
                + (seed.bvid.isEmpty() ? "aid=" + urlEncode(seed.aid) : "bvid=" + urlEncode(seed.bvid));
        JSONObject payload = parseJsonObject(readText(viewUrl, pageUrl));
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data == null) {
            return null;
        }
        String bvid = firstNonEmpty(data.optString("bvid", ""), seed.bvid);
        String aid = firstNonEmpty(data.optString("aid", ""), seed.aid);
        String cid = data.optString("cid", "");
        JSONArray pages = data.optJSONArray("pages");
        if (cid.isEmpty() && pages != null && pages.length() > 0) {
            JSONObject firstPage = pages.optJSONObject(0);
            cid = firstPage == null ? "" : firstPage.optString("cid", "");
        }
        if ((bvid.isEmpty() && aid.isEmpty()) || cid.isEmpty()) {
            return null;
        }
        return new BilibiliVideoInfo(bvid, aid, cid);
    }

    private BilibiliVideoInfo bilibiliVideoInfoFromUrl(String pageUrl, String pageText) {
        String bvid = "";
        String aid = "";
        String text = firstNonEmpty(pageUrl, "") + "\n" + firstNonEmpty(pageText, "");
        Matcher bvidMatcher = BILIBILI_BVID.matcher(text);
        if (bvidMatcher.find()) {
            bvid = bvidMatcher.group(1);
        }
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (!host.contains("bilibili.com") && !host.equals("b23.tv")) {
                return null;
            }
            Matcher aidMatcher = BILIBILI_AID_PATH.matcher(parsed.getPath() == null ? "" : parsed.getPath());
            if (aidMatcher.find()) {
                aid = aidMatcher.group(1);
            }
        } catch (MalformedURLException ignored) {
            return null;
        }
        if (bvid.isEmpty() && aid.isEmpty()) {
            return null;
        }
        return new BilibiliVideoInfo(bvid, aid, "");
    }

    private List<String> extractBilibiliPlayurlCandidates(String apiText, String apiUrl) {
        List<String> candidates = new ArrayList<>();
        JSONObject payload = parseJsonObject(apiText);
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        JSONArray durl = data == null ? null : data.optJSONArray("durl");
        if (durl != null) {
            for (int i = 0; i < durl.length(); i++) {
                JSONObject entry = durl.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                addBilibiliMediaCandidate(candidates, entry.optString("url", ""));
                JSONArray backups = entry.optJSONArray("backup_url");
                if (backups != null) {
                    for (int j = 0; j < backups.length(); j++) {
                        addBilibiliMediaCandidate(candidates, backups.optString(j, ""));
                    }
                }
            }
        }
        candidates.addAll(extractJsonMediaCandidates(apiText, apiUrl));
        return dedupeMediaCandidates(candidates);
    }

    private void addBilibiliMediaCandidate(List<String> candidates, String rawUrl) {
        String candidate = rawUrl == null ? "" : rawUrl.replace("\\u0026", "&").replace("\\/", "/").trim();
        if (isMediaUrl(candidate)) {
            addUnique(candidates, candidate);
        }
    }

    private SiteMediaResult resolveIqiyiMedia(String pageUrl, String pageText) {
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("iqiyi", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        candidates.addAll(extractJsonMediaCandidates(pageText == null ? "" : pageText, pageUrl));

        String firstReferer = pageUrl;
        for (String playUrl : extractIqiyiPlayLinks(pageUrl, pageText)) {
            if (playUrl.equals(pageUrl)) {
                continue;
            }
            try {
                String playText = readText(playUrl, pageUrl);
                SiteMediaResult playMedia = mediaFromMediaResolver("iqiyi", playUrl, playText, playUrl);
                if (playMedia != null) {
                    candidates.addAll(playMedia.candidates);
                }
                candidates.addAll(extractJsonMediaCandidates(playText, playUrl));
                if (firstReferer.equals(pageUrl)) {
                    firstReferer = playUrl;
                }
            } catch (IOException ignored) {
                // Keep direct page candidates when iQIYI blocks a play page fetch.
            }
            if (!candidates.isEmpty()) {
                break;
            }
        }

        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("iqiyi", candidates.get(0), candidates, firstReferer);
    }

    private List<String> extractIqiyiPlayLinks(String pageUrl, String pageText) {
        List<String> out = new ArrayList<>();
        Matcher matcher = IQIYI_PLAY_LINK.matcher(pageText == null ? "" : pageText);
        while (matcher.find()) {
            String candidate = joinUrl(pageUrl, htmlDecoded(matcher.group(1)).trim());
            if ("iqiyi".equals(MediaResolver.sourceSite(candidate)) && (pagePathContains(candidate, "/play/") || pagePathContains(candidate, "/album/"))) {
                addUnique(out, candidate);
            }
            if (out.size() >= 4) {
                break;
            }
        }
        Collections.sort(out, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Boolean.compare(!pagePathContains(left, "/play/"), !pagePathContains(right, "/play/"));
            }
        });
        return out;
    }

    private SiteMediaResult resolveYoutubeMedia(String pageUrl, String pageText) {
        String videoId = youtubeVideoIdFromUrl(pageUrl);
        if (videoId.isEmpty()) {
            return mediaFromMediaResolver("youtube", pageUrl, pageText, pageUrl);
        }
        List<String> candidates = new ArrayList<>();
        SiteMediaResult pageMedia = mediaFromMediaResolver("youtube", pageUrl, pageText, pageUrl);
        if (pageMedia != null) {
            candidates.addAll(pageMedia.candidates);
        }
        candidates.addAll(extractYoutubePlayerCandidates(pageText, pageUrl));
        if (candidates.isEmpty()) {
            try {
                String watchUrl = "https://www.youtube.com/watch?v=" + urlEncode(videoId);
                candidates.addAll(extractYoutubePlayerCandidates(readText(watchUrl, "https://www.youtube.com/"), watchUrl));
            } catch (IOException ignored) {
                // Fall back to page candidates only.
            }
        }
        candidates = prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
        if (candidates.isEmpty()) {
            return null;
        }
        return new SiteMediaResult("youtube", candidates.get(0), candidates, "https://www.youtube.com/");
    }

    private String youtubeVideoIdFromUrl(String pageUrl) {
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            String path = parsed.getPath() == null ? "" : parsed.getPath();
            if (host.equals("youtu.be")) {
                String id = path.startsWith("/") ? path.substring(1) : path;
                int slash = id.indexOf('/');
                return youtubeValidVideoId(slash >= 0 ? id.substring(0, slash) : id);
            }
            if (!host.contains("youtube.com")) {
                return "";
            }
            String fromQuery = Uri.parse(pageUrl).getQueryParameter("v");
            if (fromQuery != null && !fromQuery.trim().isEmpty()) {
                return youtubeValidVideoId(fromQuery);
            }
            if (path.startsWith("/shorts/") || path.startsWith("/embed/")) {
                String[] parts = path.split("/");
                if (parts.length >= 3) {
                    return youtubeValidVideoId(parts[2]);
                }
            }
            return "";
        } catch (MalformedURLException ignored) {
            return "";
        }
    }

    private String youtubeValidVideoId(String value) {
        String id = value == null ? "" : value.trim();
        return id.matches("[A-Za-z0-9_-]{11}") ? id : "";
    }

    private List<String> extractYoutubePlayerCandidates(String pageText, String pageUrl) {
        List<String> candidates = new ArrayList<>();
        JSONObject player = extractYoutubePlayerResponse(pageText);
        JSONObject streaming = player == null ? null : player.optJSONObject("streamingData");
        if (streaming == null) {
            return candidates;
        }
        addYoutubeFormatCandidates(candidates, streaming.optJSONArray("formats"));
        String hlsManifest = streaming.optString("hlsManifestUrl", "").trim();
        if (isMediaUrl(hlsManifest)) {
            addUnique(candidates, hlsManifest);
        }
        return dedupeMediaCandidates(candidates);
    }

    private JSONObject extractYoutubePlayerResponse(String pageText) {
        String text = pageText == null ? "" : pageText;
        Matcher matcher = YOUTUBE_PLAYER_RESPONSE_START.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String blob = balancedObject(text, matcher.end() - 1);
        if (blob == null) {
            return null;
        }
        return parseJsonObject(blob);
    }

    private void addYoutubeFormatCandidates(List<String> candidates, JSONArray formats) {
        if (formats == null) {
            return;
        }
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (format == null) {
                continue;
            }
            String mimeType = format.optString("mimeType", "").toLowerCase(Locale.US);
            if (!mimeType.contains("video/")) {
                continue;
            }
            String candidate = format.optString("url", "").trim();
            if (candidate.isEmpty()) {
                candidate = youtubeUrlFromSignatureCipher(format.optString("signatureCipher", ""));
            }
            if (isMediaUrl(candidate)) {
                addUnique(candidates, candidate);
            }
        }
    }

    private String youtubeUrlFromSignatureCipher(String cipher) {
        if (cipher == null || cipher.trim().isEmpty()) {
            return "";
        }
        String signature = queryParameterFromForm(cipher, "s");
        if (!signature.isEmpty()) {
            return "";
        }
        return queryParameterFromForm(cipher, "url");
    }

    private String queryParameterFromForm(String query, String name) {
        try {
            Uri uri = Uri.parse("https://local.invalid/?" + query);
            String value = uri.getQueryParameter(name);
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractYfspPlayKey(String pageUrl) {
        try {
            URL parsed = new URL(pageUrl);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (!host.contains("yfsp.tv")) {
                return "";
            }
            Matcher matcher = YFSP_PLAY_PATH.matcher(parsed.getPath() == null ? "" : parsed.getPath());
            return matcher.find() ? htmlDecoded(matcher.group(1)).trim() : "";
        } catch (MalformedURLException ignored) {
            return "";
        }
    }

    private List<String> decodeBestJavPornPlayerSources(String playerText, String playerUrl) {
        List<String> candidates = new ArrayList<>();
        Matcher configMatcher = PLAYER_DATA_CONFIG.matcher(playerText == null ? "" : playerText);
        if (!configMatcher.find()) {
            return candidates;
        }
        try {
            String configJson = decodeBestJavPornPlayerConfig(htmlDecoded(configMatcher.group(1)), playerUrl);
            JSONObject config = new JSONObject(configJson);
            String sourceBlob = config.optString("src", "");
            if (!sourceBlob.isEmpty()) {
                String sourceJson = new String(base64Decode(sourceBlob), StandardCharsets.UTF_8);
                JSONArray sources = new JSONArray(sourceJson);
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject entry = sources.optJSONObject(i);
                    if (entry == null) {
                        continue;
                    }
                    String candidate = firstNonEmpty(entry.optString("file", ""), firstNonEmpty(entry.optString("src", ""), entry.optString("url", "")));
                    candidate = joinUrl(playerUrl, candidate);
                    if (isMediaUrl(candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep the generic media extraction fallback alive when a player config changes.
        }
        return candidates;
    }

    private String bestJavPornDex(String videoId, String encrypted) {
        String key = reverse(base64EncodeLatin1(videoId + "_0x58fe15"));
        byte[] decrypted = rc4(base64Decode(encrypted), key);
        return new String(base64Decode(new String(decrypted, StandardCharsets.ISO_8859_1)), StandardCharsets.UTF_8);
    }

    private String javDockFme(String videoId, String encrypted, boolean encodeSources) {
        String suffix = encodeSources ? "_0x48107a" : "undefined";
        String key = reverse(base64EncodeLatin1(videoId + suffix));
        byte[] decrypted = rc4(base64Decode(encrypted), key);
        return new String(base64Decode(new String(decrypted, StandardCharsets.ISO_8859_1)), StandardCharsets.UTF_8);
    }

    private String decodeBestJavPornPlayerConfig(String dataConfig, String playerUrl) {
        String pathQuery = "";
        try {
            URL parsed = new URL(playerUrl);
            pathQuery = parsed.getPath() + (parsed.getQuery() == null ? "" : "?" + parsed.getQuery());
        } catch (MalformedURLException ignored) {
            pathQuery = playerUrl == null ? "" : playerUrl;
        }
        String seed = base64EncodeLatin1(pathQuery);
        String keySeed = seed.length() > 4 ? seed.substring(4, Math.min(20, seed.length())) : seed;
        String key = reverse(base64EncodeLatin1(keySeed + "_0x59a0e4"));
        byte[] decrypted = rc4(base64Decode(dataConfig), key);
        return new String(base64Decode(new String(decrypted, StandardCharsets.ISO_8859_1)), StandardCharsets.UTF_8);
    }

    private String postForm(String rawUrl, String body, String refererUrl) throws IOException {
        HttpURLConnection connection = open(rawUrl, refererUrl);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (BufferedOutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
            output.write(bytes);
        }
        int code = connection.getResponseCode();
        if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw httpStatusException(connection, rawUrl, code);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), charsetFromContentType(connection.getContentType())))) {
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

    private JSONObject parseJsonObject(String text) {
        try {
            return new JSONObject(text == null ? "{}" : text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String balancedObject(String text, int start) {
        if (text == null || start < 0 || start >= text.length() || text.charAt(start) != '{') {
            return null;
        }
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
            if (ch == '"' || ch == '\'') {
                inString = true;
                quote = ch;
                continue;
            }
            if (ch == '{') {
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

    private String balancedArray(String text, int start) {
        if (text == null || start < 0 || start >= text.length() || text.charAt(start) != '[') {
            return null;
        }
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
            if (ch == '"' || ch == '\'') {
                inString = true;
                quote = ch;
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private List<String> extractJsonMediaCandidates(String value, String baseUrl) {
        List<String> candidates = new ArrayList<>(extractMediaCandidates(value == null ? "" : value, baseUrl));
        String text = value == null ? "" : value.trim();
        if (text.length() < 200000 && (text.startsWith("{") || text.startsWith("["))) {
            try {
                if (text.startsWith("{")) {
                    collectJsonMediaCandidates(new JSONObject(text), baseUrl, candidates);
                } else {
                    collectJsonMediaCandidates(new JSONArray(text), baseUrl, candidates);
                }
            } catch (Exception ignored) {
                // Regex extraction above still handles JavaScript-like or partial JSON payloads.
            }
        }
        return prioritizeManifestCandidates(dedupeMediaCandidates(candidates));
    }

    private void collectJsonMediaCandidates(Object node, String baseUrl, List<String> candidates) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                collectJsonMediaCandidates(object.opt(keys.next()), baseUrl, candidates);
            }
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectJsonMediaCandidates(array.opt(i), baseUrl, candidates);
            }
            return;
        }
        if (node instanceof String) {
            candidates.addAll(extractMediaCandidates((String) node, baseUrl));
        }
    }

    private boolean payloadStatus(JSONObject payload) {
        Object value = payload == null ? null : payload.opt("status");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "ok".equalsIgnoreCase(text);
    }

    private List<String> siteCandidateLabels(String sourceSite, List<String> candidates) {
        List<String> labels = new ArrayList<>();
        for (String candidate : candidates) {
            labels.add(sourceSite + " player " + (isHlsUrl(candidate) ? "HLS" : isDashUrl(candidate) ? "DASH" : "media"));
        }
        return labels;
    }

    private List<String> dedupeMediaCandidates(List<String> candidates) {
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && isMediaUrl(candidate) && !looksLikeImageUrl(candidate) && !out.contains(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private boolean looksLikeImageUrl(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.matches(".*\\.(?:jpg|jpeg|png|gif|webp)(?:[?#].*)?$");
    }

    private byte[] base64Decode(String value) {
        String text = value == null ? "" : value.trim().replace('-', '+').replace('_', '/');
        while (text.length() % 4 != 0) {
            text += "=";
        }
        return Base64.decode(text, Base64.DEFAULT);
    }

    private String base64EncodeLatin1(String value) {
        return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.ISO_8859_1), Base64.NO_WRAP);
    }

    private byte[] rc4(byte[] data, String key) {
        byte[] keyBytes = (key == null ? "" : key).getBytes(StandardCharsets.ISO_8859_1);
        if (keyBytes.length == 0) {
            return new byte[0];
        }
        int[] state = new int[256];
        for (int i = 0; i < state.length; i++) {
            state[i] = i;
        }
        int j = 0;
        for (int i = 0; i < state.length; i++) {
            j = (j + state[i] + (keyBytes[i % keyBytes.length] & 0xff)) & 0xff;
            int tmp = state[i];
            state[i] = state[j];
            state[j] = tmp;
        }
        byte[] output = new byte[data == null ? 0 : data.length];
        int i = 0;
        j = 0;
        for (int n = 0; n < output.length; n++) {
            i = (i + 1) & 0xff;
            j = (j + state[i]) & 0xff;
            int tmp = state[i];
            state[i] = state[j];
            state[j] = tmp;
            output[n] = (byte) ((data[n] & 0xff) ^ state[(state[i] + state[j]) & 0xff]);
        }
        return output;
    }

    private String reverse(String value) {
        return new StringBuilder(value == null ? "" : value).reverse().toString();
    }

    private String htmlDecoded(String value) {
        String decoded = value == null ? "" : value;
        decoded = decoded.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return decoded;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
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
        if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw httpStatusException(connection, rawUrl, code);
        }
        boolean canResume = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL;
        if (existing > 0L && !canResume) {
            existing = 0L;
        }
        long contentLength = connection.getContentLengthLong();
        long total = contentLength > 0L ? existing + contentLength : -1L;
        String contentDisposition = connection.getHeaderField("Content-Disposition");
        validateHttpContentType(connection.getContentType(), rawUrl, callback);

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
        validateHttpCompleteSize(part, total, callback);
        rejectTextErrorFile(part, rawUrl);
        String headerName = FileNames.fromContentDisposition(contentDisposition);
        if (!headerName.isEmpty()) {
            output = uniqueOutputFile(headerName);
        }
        replace(part, output);
        if (state.exists()) {
            state.delete();
        }
        validateCompletedMedia(output, callback);
        callback.onDone(output);
    }

    private void validateHttpContentType(String contentType, String rawUrl, Callback callback) throws IOException {
        rejectNonMediaContentType(contentType, rawUrl);
        callback.onStatus(context.getString(R.string.engine_http_content_type_validated));
    }

    private void rejectNonMediaContentType(String contentType, String rawUrl) throws IOException {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.US).trim();
        if (normalized.isEmpty()) {
            return;
        }
        String mediaType = normalized.split(";", 2)[0].trim();
        if (mediaType.equals("text/html")
                || mediaType.equals("text/plain")
                || mediaType.equals("application/json")
                || mediaType.equals("application/xml")
                || mediaType.equals("text/xml")) {
            throw new IOException("HTTP response is not media content: " + mediaType);
        }
    }

    private void validateHttpCompleteSize(File part, long expectedBytes, Callback callback) throws IOException {
        if (expectedBytes <= 0L) {
            return;
        }
        long actualBytes = part == null || !part.exists() ? 0L : part.length();
        if (actualBytes != expectedBytes) {
            throw new IOException("HTTP download size mismatch: expected " + expectedBytes + " bytes, got " + actualBytes + " bytes");
        }
        callback.onStatus(context.getString(R.string.engine_http_size_validated));
    }

    private void downloadHls(String rawUrl, String requestedFileName, String refererUrl, Callback callback) throws IOException {
        HlsPlaylist playlist = resolveHlsPlaylist(rawUrl, refererUrl, callback);
        List<HlsSegment> segments = playlist.segments;
        if (segments.isEmpty()) {
            throw new IOException("HLS manifest did not contain downloadable segments");
        }

        File output = outputFile(FileNames.replaceExtension(requestedFileName, ".ts"));
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
        File completed = remuxHlsOutput(output, requestedFileName, callback);
        validateCompletedMedia(completed, callback);
        callback.onDone(completed);
    }

    private void downloadDash(String rawUrl, String fileName, String refererUrl, Callback callback) throws IOException {
        DashPlan plan = resolveDashPlan(rawUrl, refererUrl);
        if (plan.initUrl == null || plan.initUrl.isEmpty() || plan.segments.isEmpty()) {
            throw new IOException("DASH manifest did not contain a supported SegmentTemplate, SegmentList, or SegmentBase");
        }

        File output = outputFile(fileName);
        File part = new File(output.getParentFile(), output.getName() + ".part");
        File checkpoint = new File(output.getParentFile(), output.getName() + ".dashstate");
        callback.onStatus(context.getString(R.string.engine_dash_representation, dashPlanDescription(plan)));
        if (plan.audioPlan != null) {
            callback.onStatus(context.getString(R.string.engine_dash_audio_representation, dashPlanDescription(plan.audioPlan)));
        } else if (plan.audioTrackCount > 0) {
            callback.onStatus(context.getString(R.string.engine_dash_video_only_audio_pending, dashAudioSummary(plan)));
        }
        if (!downloadDashTrack(plan, part, checkpoint, refererUrl, callback, true, R.string.engine_downloading_dash_segment)) {
            return;
        }
        replace(part, output);
        if (checkpoint.exists()) {
            checkpoint.delete();
        }

        File completed = muxDashAudioIfAvailable(output, plan, refererUrl, callback);
        if (completed == null) {
            return;
        }
        validateCompletedMedia(completed, callback);
        callback.onDone(completed);
    }

    private boolean downloadDashTrack(DashPlan plan, File part, File checkpoint, String refererUrl, Callback callback, boolean reportProgress, int statusResId) throws IOException {
        int startIndex = loadDashCheckpoint(checkpoint, plan, part);
        boolean append = startIndex > 0 && part.exists();
        try (FileOutputStream fos = new FileOutputStream(part, append);
            BufferedOutputStream outputStream = new BufferedOutputStream(fos)) {
            if (startIndex == 0) {
                byte[] init = readBytesWithRetry(plan.initUrl, plan.initRange, effectiveReferer(refererUrl, plan.manifestUrl), "empty DASH init segment");
                outputStream.write(init);
                outputStream.flush();
            }
            for (int index = startIndex; index < plan.segments.size(); index++) {
                if (cancelled.get()) {
                    callback.onStatus(context.getString(R.string.engine_cancelled_keep_dash_partial));
                    return false;
                }
                callback.onStatus(context.getString(statusResId, index + 1, plan.segments.size()));
                DashSegment segment = plan.segments.get(index);
                byte[] data = readBytesWithRetry(segment.url, segment.byteRange, effectiveReferer(refererUrl, plan.manifestUrl), "empty DASH segment");
                outputStream.write(data);
                outputStream.flush();
                saveDashCheckpoint(checkpoint, plan, index + 1);
                if (reportProgress) {
                    callback.onProgress(index + 1L, plan.segments.size());
                }
            }
        }
        return true;
    }

    private File muxDashAudioIfAvailable(File videoOutput, DashPlan plan, String refererUrl, Callback callback) throws IOException {
        if (plan.audioPlan == null) {
            return videoOutput;
        }
        File parent = videoOutput.getParentFile();
        File audioPart = new File(parent, videoOutput.getName() + ".audio.part");
        File audioOutput = new File(parent, videoOutput.getName() + ".audio.m4a");
        File audioCheckpoint = new File(parent, videoOutput.getName() + ".audio.dashstate");
        File muxed = new File(parent, videoOutput.getName() + ".muxed.mp4");
        try {
            callback.onStatus(context.getString(R.string.engine_downloading_dash_audio));
            if (!downloadDashTrack(plan.audioPlan, audioPart, audioCheckpoint, refererUrl, callback, false, R.string.engine_downloading_dash_audio_segment)) {
                return null;
            }
            replace(audioPart, audioOutput);
            if (audioCheckpoint.exists()) {
                audioCheckpoint.delete();
            }
            callback.onStatus(context.getString(R.string.engine_muxing_dash_audio));
            File merged = MediaRemuxer.muxVideoAudioToMp4(videoOutput, audioOutput, muxed);
            replace(merged, videoOutput);
            if (audioOutput.exists()) {
                audioOutput.delete();
            }
            callback.onStatus(context.getString(R.string.engine_muxed_dash_audio));
        } catch (Exception error) {
            callback.onStatus(context.getString(R.string.engine_mux_dash_audio_failed_keep_video, shortMessage(error)));
            if (muxed.exists()) {
                muxed.delete();
            }
            if (audioPart.exists()) {
                audioPart.delete();
            }
            if (audioOutput.exists()) {
                audioOutput.delete();
            }
        }
        return videoOutput;
    }

    private void validateCompletedMedia(File output, Callback callback) throws IOException {
        if (output == null || !output.isFile() || output.length() <= 0L) {
            throw new IOException("download output file is missing or empty");
        }
        if (!shouldProbeMediaTracks(output.getName())) {
            return;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(output.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            int mediaTrackIndex = -1;
            boolean hasDuration = false;
            boolean hasPositiveDuration = false;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.containsKey(MediaFormat.KEY_MIME) ? format.getString(MediaFormat.KEY_MIME) : "";
                if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                    if (mediaTrackIndex < 0) {
                        mediaTrackIndex = i;
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        hasDuration = true;
                        if (format.getLong(MediaFormat.KEY_DURATION) > 0L) {
                            hasPositiveDuration = true;
                        }
                    }
                }
            }
            if (mediaTrackIndex < 0) {
                throw new IOException("download output is not a valid media file");
            }
            if (hasDuration && !hasPositiveDuration) {
                throw new IOException("download output media duration is invalid");
            }
            extractor.selectTrack(mediaTrackIndex);
            ByteBuffer sampleBuffer = ByteBuffer.allocate(64 * 1024);
            if (extractor.readSampleData(sampleBuffer, 0) <= 0) {
                throw new IOException("download output media track is unreadable");
            }
            if (hasPositiveDuration) {
                callback.onStatus(context.getString(R.string.engine_media_duration_validated));
            }
            callback.onStatus(context.getString(R.string.engine_media_validated));
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("download output is not a valid media file", error);
        } finally {
            extractor.release();
        }
    }

    private boolean shouldProbeMediaTracks(String fileName) {
        String lowered = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        return lowered.endsWith(".mp4")
                || lowered.endsWith(".m4v")
                || lowered.endsWith(".webm")
                || lowered.endsWith(".mkv")
                || lowered.endsWith(".mov");
    }

    private File remuxHlsOutput(File transportOutput, String requestedFileName, Callback callback) {
        File mp4Output = outputFile(FileNames.replaceExtension(requestedFileName, ".mp4"));
        if (transportOutput.equals(mp4Output)) {
            return transportOutput;
        }
        try {
            callback.onStatus(context.getString(R.string.engine_remuxing_mp4));
            File remuxed = MediaRemuxer.remuxToMp4(transportOutput, mp4Output);
            if (transportOutput.exists() && !transportOutput.delete()) {
                callback.onStatus(context.getString(R.string.engine_remuxed_mp4_keep_source));
            } else {
                callback.onStatus(context.getString(R.string.engine_remuxed_mp4));
            }
            return remuxed;
        } catch (Exception error) {
            callback.onStatus(context.getString(R.string.engine_remux_mp4_failed_keep_ts, shortMessage(error)));
            return transportOutput;
        }
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
        connection.setRequestProperty("Accept-Encoding", "identity");
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
        return readText(connection, rawUrl);
    }

    private String readText(String rawUrl, String refererUrl, Map<String, String> extraHeaders) throws IOException {
        HttpURLConnection connection = open(rawUrl, refererUrl);
        if (extraHeaders != null) {
            for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                if (header.getKey() != null && header.getValue() != null) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
        }
        return readText(connection, rawUrl);
    }

    private String readText(HttpURLConnection connection, String rawUrl) throws IOException {
        int code = connection.getResponseCode();
        if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw httpStatusException(connection, rawUrl, code);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), charsetFromContentType(connection.getContentType())))) {
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
        final boolean videoLike;
        final String initUrl;
        final String initRange;
        final List<DashSegment> segments;
        final int audioTrackCount;
        final String audioSummary;
        final DashPlan audioPlan;

        DashPlan(String manifestUrl, String representationId, int bandwidth, int width, int height, String codecs, boolean videoLike, String initUrl, String initRange, List<DashSegment> segments) {
            this(manifestUrl, representationId, bandwidth, width, height, codecs, videoLike, initUrl, initRange, segments, 0, "", null);
        }

        DashPlan(String manifestUrl, String representationId, int bandwidth, int width, int height, String codecs, boolean videoLike, String initUrl, String initRange, List<DashSegment> segments, int audioTrackCount, String audioSummary, DashPlan audioPlan) {
            this.manifestUrl = manifestUrl;
            this.representationId = representationId;
            this.bandwidth = bandwidth;
            this.width = width;
            this.height = height;
            this.codecs = codecs == null ? "" : codecs;
            this.videoLike = videoLike;
            this.initUrl = initUrl;
            this.initRange = initRange;
            this.segments = segments;
            this.audioTrackCount = Math.max(0, audioTrackCount);
            this.audioSummary = audioSummary == null ? "" : audioSummary;
            this.audioPlan = audioPlan;
        }

        DashPlan withAudioTracks(int audioTrackCount, String audioSummary, DashPlan audioPlan) {
            return new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, videoLike, initUrl, initRange, segments, audioTrackCount, audioSummary, audioPlan);
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
        int code = connection.getResponseCode();
        if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw httpStatusException(connection, "https://v.anime1.me/api", code);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), charsetFromContentType(connection.getContentType())))) {
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

    private Charset charsetFromContentType(String contentType) {
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

    private IOException httpStatusException(HttpURLConnection connection, String rawUrl, int code) {
        String message = "HTTP " + code + " for " + shortUrlForError(rawUrl);
        String snippet = readErrorSnippet(connection);
        if (!snippet.isEmpty()) {
            message += ": " + snippet;
        }
        return new IOException(message);
    }

    private String readErrorSnippet(HttpURLConnection connection) {
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            return "";
        }
        try (InputStream input = new BufferedInputStream(errorStream)) {
            byte[] buffer = new byte[1024];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int read;
            int remaining = 4096;
            while (remaining > 0 && (read = input.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                output.write(buffer, 0, read);
                remaining -= read;
            }
            String text = new String(output.toByteArray(), charsetFromContentType(connection.getContentType()));
            text = text.replaceAll("\\s+", " ").trim();
            return text.length() > 160 ? text.substring(0, 160) : text;
        } catch (IOException ignored) {
            return "";
        }
    }

    private String shortUrlForError(String rawUrl) {
        try {
            URL url = new URL(rawUrl);
            StringBuilder builder = new StringBuilder();
            builder.append(url.getProtocol()).append("://").append(url.getHost());
            String path = url.getPath();
            if (path != null && !path.isEmpty()) {
                builder.append(path);
            }
            if (url.getQuery() != null && !url.getQuery().isEmpty()) {
                builder.append("?");
            }
            String value = builder.toString();
            return value.length() > 140 ? value.substring(0, 140) : value;
        } catch (MalformedURLException ignored) {
            String value = rawUrl == null ? "" : rawUrl;
            return value.length() > 140 ? value.substring(0, 140) : value;
        }
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
            DashPlan bestAudio = null;
            int audioTrackCount = 0;
            List<String> audioSummaries = new ArrayList<>();
            for (Element period : childElements(root, "Period")) {
                String periodBase = joinUrl(mpdBase, firstBaseUrl(period));
                for (Element adaptation : childElements(period, "AdaptationSet")) {
                    String adaptationBase = joinUrl(periodBase, firstBaseUrl(adaptation));
                    if (isDashAudioElement(adaptation)) {
                        List<Element> representations = childElements(adaptation, "Representation");
                        audioTrackCount += Math.max(1, representations.size());
                        if (representations.isEmpty()) {
                            addDashAudioSummary(audioSummaries, adaptation, null);
                        }
                        for (Element representation : representations) {
                            addDashAudioSummary(audioSummaries, adaptation, representation);
                            DashPlan candidate = parseDashRepresentation(
                                    rawUrl,
                                    adaptation,
                                    representation,
                                    joinUrl(adaptationBase, firstBaseUrl(representation)),
                                    periodDuration);
                            if (candidate != null && (bestAudio == null || compareDashAudioPlan(candidate, bestAudio) > 0)) {
                                bestAudio = candidate;
                            }
                        }
                        continue;
                    }
                    if (isDashNonVideoElement(adaptation)) {
                        continue;
                    }
                    for (Element representation : childElements(adaptation, "Representation")) {
                        if (isDashAudioElement(representation)) {
                            audioTrackCount++;
                            addDashAudioSummary(audioSummaries, adaptation, representation);
                            DashPlan candidate = parseDashRepresentation(
                                    rawUrl,
                                    adaptation,
                                    representation,
                                    joinUrl(adaptationBase, firstBaseUrl(representation)),
                                    periodDuration);
                            if (candidate != null && (bestAudio == null || compareDashAudioPlan(candidate, bestAudio) > 0)) {
                                bestAudio = candidate;
                            }
                            continue;
                        }
                        if (isDashNonVideoElement(representation)) {
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
            return best.withAudioTracks(audioTrackCount, joinLimitedParts(audioSummaries, 3), bestAudio);
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

    private boolean isDashNonVideoElement(Element element) {
        String mediaType = firstNonEmpty(attrOrEmpty(element, "mimeType"), attrOrEmpty(element, "contentType")).toLowerCase(Locale.US);
        String codecs = attrOrEmpty(element, "codecs").toLowerCase(Locale.US);
        return mediaType.contains("audio")
                || mediaType.contains("text")
                || mediaType.contains("subtitle")
                || mediaType.contains("application/ttml")
                || mediaType.contains("image")
                || codecs.startsWith("mp4a")
                || codecs.startsWith("ac-3")
                || codecs.startsWith("ec-3")
                || codecs.startsWith("opus")
                || codecs.startsWith("stpp")
                || codecs.startsWith("wvtt");
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

    private int countDashAudioRepresentations(Element adaptation) {
        List<Element> representations = childElements(adaptation, "Representation");
        return Math.max(1, representations.size());
    }

    private void addDashAudioSummary(List<String> summaries, Element adaptation, Element representation) {
        String id = representation == null ? attrOrEmpty(adaptation, "id") : firstNonEmpty(attrOrEmpty(representation, "id"), attrOrEmpty(adaptation, "id"));
        String bandwidth = representation == null ? attrOrEmpty(adaptation, "bandwidth") : firstNonEmpty(attrOrEmpty(representation, "bandwidth"), attrOrEmpty(adaptation, "bandwidth"));
        String codecs = representation == null ? attrOrEmpty(adaptation, "codecs") : firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
        List<String> parts = new ArrayList<>();
        if (!id.isEmpty()) {
            parts.add(id);
        }
        if (!bandwidth.isEmpty()) {
            parts.add(bandwidth + " bps");
        }
        if (!codecs.isEmpty()) {
            parts.add(codecs);
        }
        String summary = parts.isEmpty() ? "audio" : joinParts(parts);
        if (!summaries.contains(summary)) {
            summaries.add(summary);
        }
    }

    private String dashAudioSummary(DashPlan plan) {
        if (plan == null || plan.audioTrackCount <= 0) {
            return "";
        }
        if (!plan.audioSummary.isEmpty()) {
            return plan.audioSummary;
        }
        return String.valueOf(plan.audioTrackCount);
    }

    private String joinLimitedParts(List<String> parts, int limit) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        List<String> limited = new ArrayList<>();
        int count = Math.min(Math.max(1, limit), parts.size());
        for (int i = 0; i < count; i++) {
            limited.add(parts.get(i));
        }
        if (parts.size() > count) {
            limited.add("+" + (parts.size() - count));
        }
        return joinParts(limited);
    }

    private boolean isDashVideoLike(Element adaptation, Element representation, int width, int height, String codecs) {
        String mediaType = (attrOrEmpty(adaptation, "mimeType") + " "
                + attrOrEmpty(adaptation, "contentType") + " "
                + attrOrEmpty(representation, "mimeType") + " "
                + attrOrEmpty(representation, "contentType")).toLowerCase(Locale.US);
        String codecText = codecs == null ? "" : codecs.toLowerCase(Locale.US);
        return mediaType.contains("video")
                || width > 0
                || height > 0
                || codecText.startsWith("avc")
                || codecText.startsWith("hev")
                || codecText.startsWith("hvc")
                || codecText.startsWith("vp8")
                || codecText.startsWith("vp9")
                || codecText.startsWith("av01");
    }

    private DashPlan parseDashSegmentTemplate(String manifestUrl, Element adaptation, Element representation, Element template, String baseUrl, String periodDuration) throws IOException {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        int width = parseInt(firstNonEmpty(attrOrEmpty(representation, "width"), attrOrEmpty(adaptation, "width")), 0);
        int height = parseInt(firstNonEmpty(attrOrEmpty(representation, "height"), attrOrEmpty(adaptation, "height")), 0);
        String codecs = firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
        boolean videoLike = isDashVideoLike(adaptation, representation, width, height, codecs);
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
            List<Element> timelineSegments = childElements(timeline, "S");
            for (int entryIndex = 0; entryIndex < timelineSegments.size(); entryIndex++) {
                Element s = timelineSegments.get(entryIndex);
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
                int count = dashTimelineRepeatCount(repeat, duration, time, entryIndex, timelineSegments, periodDuration, timescale);
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
        return new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, videoLike, initUrl, null, segments);
    }

    private int dashTimelineRepeatCount(
            int repeat,
            long duration,
            long time,
            int entryIndex,
            List<Element> timelineSegments,
            String periodDuration,
            int timescale) {
        if (repeat >= 0) {
            return Math.max(1, repeat + 1);
        }
        long nextTime = nextDashTimelineTime(entryIndex, timelineSegments);
        if (nextTime > time && duration > 0L) {
            return Math.max(1, (int) Math.min(MAX_DASH_DURATION_SEGMENTS, (nextTime - time) / duration));
        }
        long totalMillis = parseIsoDurationMillis(periodDuration);
        if (totalMillis > 0L && timescale > 0 && duration > 0L) {
            double totalTicks = totalMillis * (timescale / 1000.0);
            if (totalTicks > time) {
                return Math.max(1, (int) Math.min(MAX_DASH_DURATION_SEGMENTS, Math.ceil((totalTicks - time) / duration)));
            }
        }
        return 1;
    }

    private long nextDashTimelineTime(int entryIndex, List<Element> timelineSegments) {
        for (int i = entryIndex + 1; i < timelineSegments.size(); i++) {
            String rawTime = attrOrEmpty(timelineSegments.get(i), "t");
            if (!rawTime.isEmpty()) {
                return parseLong(rawTime, -1L);
            }
        }
        return -1L;
    }

    private DashPlan parseDashSegmentList(String manifestUrl, Element adaptation, Element representation, Element list, String baseUrl) {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        int width = parseInt(firstNonEmpty(attrOrEmpty(representation, "width"), attrOrEmpty(adaptation, "width")), 0);
        int height = parseInt(firstNonEmpty(attrOrEmpty(representation, "height"), attrOrEmpty(adaptation, "height")), 0);
        String codecs = firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
        boolean videoLike = isDashVideoLike(adaptation, representation, width, height, codecs);
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
        return initUrl.isEmpty() || segments.isEmpty() ? null : new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, videoLike, initUrl, initRange, segments);
    }

    private DashPlan parseDashSegmentBase(String manifestUrl, Element adaptation, Element representation, Element segmentBase, String baseUrl) {
        String representationId = firstNonEmpty(attrOrEmpty(representation, "id"), "video");
        int bandwidth = parseInt(attrOrEmpty(representation, "bandwidth"), 0);
        int width = parseInt(firstNonEmpty(attrOrEmpty(representation, "width"), attrOrEmpty(adaptation, "width")), 0);
        int height = parseInt(firstNonEmpty(attrOrEmpty(representation, "height"), attrOrEmpty(adaptation, "height")), 0);
        String codecs = firstNonEmpty(attrOrEmpty(representation, "codecs"), attrOrEmpty(adaptation, "codecs"));
        boolean videoLike = isDashVideoLike(adaptation, representation, width, height, codecs);
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
        return new DashPlan(manifestUrl, representationId, bandwidth, width, height, codecs, videoLike, initUrl, initRange, segments);
    }

    private int compareDashPlan(DashPlan left, DashPlan right) {
        int typeCompare = Boolean.compare(left != null && left.videoLike, right != null && right.videoLike);
        if (typeCompare != 0) {
            return typeCompare;
        }
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

    private int compareDashAudioPlan(DashPlan left, DashPlan right) {
        int bandwidthCompare = Integer.compare(left == null ? 0 : left.bandwidth, right == null ? 0 : right.bandwidth);
        if (bandwidthCompare != 0) {
            return bandwidthCompare;
        }
        return Integer.compare(left == null ? 0 : left.segments.size(), right == null ? 0 : right.segments.size());
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
        boolean hasEndList = false;
        boolean playlistTypeVod = false;
        String pendingByteRange = null;
        Map<String, Long> nextByteRangeOffsets = new LinkedHashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if ("#EXT-X-ENDLIST".equalsIgnoreCase(line)) {
                hasEndList = true;
                continue;
            }
            if (line.toUpperCase(Locale.US).startsWith("#EXT-X-PLAYLIST-TYPE:")) {
                playlistTypeVod = line.toUpperCase(Locale.US).contains("VOD");
                continue;
            }
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
        return new HlsPlaylist(manifestUrl, segments, hasEndList || playlistTypeVod);
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
        byte[] mapBytes = readBytesWithRetry(segment.map.uri, segment.map.byteRange, refererUrl, "empty HLS init map");
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

    private byte[] readBytesWithRetry(String rawUrl, String byteRange, String refererUrl, String emptyMessage) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= SEGMENT_RETRY_LIMIT; attempt++) {
            try {
                byte[] data = readBytes(rawUrl, byteRange, refererUrl);
                if (data.length == 0) {
                    throw new IOException(emptyMessage);
                }
                return data;
            } catch (IOException error) {
                last = error;
                if (cancelled.get()) {
                    throw error;
                }
            }
        }
        throw last == null ? new IOException(emptyMessage) : last;
    }

    private byte[] readHlsKeyWithRetry(HlsKey key, String refererUrl) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= SEGMENT_RETRY_LIMIT; attempt++) {
            try {
                byte[] data = readBytes(key.uri, null, refererUrl);
                if (data.length == 0) {
                    throw new IOException("empty HLS key");
                }
                if (data.length != 16) {
                    throw new IOException(context.getString(R.string.error_invalid_hls_key_length, data.length));
                }
                return data;
            } catch (IOException error) {
                last = error;
                if (cancelled.get()) {
                    throw error;
                }
            }
        }
        throw last == null ? new IOException("HLS key failed") : last;
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
            key.bytes = readHlsKeyWithRetry(key, refererUrl);
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
        boolean requiresPartial = false;
        long expectedRangeLength = expectedByteRangeLength(byteRange);
        if (byteRange != null && !byteRange.isEmpty()) {
            String rangeHeader = hlsByteRangeToHttpRange(byteRange);
            if (rangeHeader != null) {
                connection.setRequestProperty("Range", rangeHeader);
                requiresPartial = true;
            }
        }
        int code = connection.getResponseCode();
        if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw httpStatusException(connection, rawUrl, code);
        }
        if (requiresPartial && code != HttpURLConnection.HTTP_PARTIAL) {
            throw new IOException("HTTP " + code + " did not honor byte range for " + shortUrlForError(rawUrl));
        }
        if (code < HttpURLConnection.HTTP_OK || code >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw httpStatusException(connection, rawUrl, code);
        }
        rejectNonMediaContentType(connection.getContentType(), rawUrl);
        long expectedContentLength = connection.getContentLengthLong();
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
            byte[] bytes = output.toByteArray();
            if (expectedRangeLength > 0L && bytes.length != expectedRangeLength) {
                throw new IOException("byte-range response size mismatch: expected " + expectedRangeLength + " bytes, got " + bytes.length + " bytes");
            }
            if (expectedRangeLength <= 0L && expectedContentLength > 0L && bytes.length != expectedContentLength) {
                throw new IOException("byte response size mismatch: expected " + expectedContentLength + " bytes, got " + bytes.length + " bytes");
            }
            rejectTextErrorPayload(bytes, rawUrl);
            return bytes;
        } finally {
            connection.disconnect();
        }
    }

    private void rejectTextErrorPayload(byte[] bytes, String rawUrl) throws IOException {
        if (bytes == null || bytes.length < 32) {
            return;
        }
        int length = Math.min(bytes.length, 512);
        int printable = 0;
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            if (value == 9 || value == 10 || value == 13 || (value >= 32 && value <= 126)) {
                printable++;
            }
        }
        if (printable < length * 0.85) {
            return;
        }
        String prefix = new String(bytes, 0, length, StandardCharsets.UTF_8).trim().toLowerCase(Locale.US);
        if (prefix.startsWith("<!doctype")
                || prefix.startsWith("<html")
                || prefix.startsWith("<?xml")
                || prefix.startsWith("{\"error")
                || prefix.startsWith("{\"message")
                || prefix.startsWith("{\"status")
                || prefix.startsWith("[{\"error")
                || prefix.contains("<body")
                || prefix.contains("<title>")) {
            throw new IOException("byte response looks like a text error page for " + shortUrlForError(rawUrl));
        }
    }

    private void rejectTextErrorFile(File file, String rawUrl) throws IOException {
        if (file == null || !file.isFile() || file.length() < 32L) {
            return;
        }
        int length = (int) Math.min(file.length(), 512L);
        byte[] prefix = new byte[length];
        int read;
        try (FileInputStream input = new FileInputStream(file)) {
            read = input.read(prefix);
        }
        if (read <= 0) {
            return;
        }
        if (read < prefix.length) {
            prefix = Arrays.copyOf(prefix, read);
        }
        rejectTextErrorPayload(prefix, rawUrl);
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

    private long expectedByteRangeLength(String byteRange) {
        String trimmed = byteRange == null ? "" : byteRange.trim();
        if (trimmed.isEmpty()) {
            return -1L;
        }
        String value = trimmed.toLowerCase(Locale.US).startsWith("bytes=") ? trimmed.substring(6).trim() : trimmed;
        if (value.contains("@")) {
            try {
                long length = Long.parseLong(value.split("@", 2)[0].trim());
                return length > 0L ? length : -1L;
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        String[] range = value.split("-", 2);
        if (range.length != 2 || range[0].trim().isEmpty() || range[1].trim().isEmpty()) {
            return -1L;
        }
        try {
            long start = Long.parseLong(range[0].trim());
            long end = Long.parseLong(range[1].trim());
            return end >= start ? end - start + 1L : -1L;
        } catch (NumberFormatException ignored) {
            return -1L;
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

    private String firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? htmlDecoded(matcher.group(1)).trim() : "";
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
        addRegexCandidates(candidates, ESCAPED_MEDIA_URL.matcher(pageText), pageUrl, false);
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
                || lowered.contains(".webm") || lowered.contains(".m4v") || isYfspHlsManifest(rawUrl)
                || isYoutubeHlsManifest(rawUrl) || isStreamtapeMediaUrl(rawUrl);
    }

    private boolean isHlsUrl(String rawUrl) {
        return rawUrl.toLowerCase(Locale.US).contains(".m3u8") || isYfspHlsManifest(rawUrl)
                || isYoutubeHlsManifest(rawUrl);
    }

    private boolean isYfspHlsManifest(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("upload.yfsp.tv/api/video/masterplaylist")
                || lowered.contains("upload.yfsp.tv/api/video/playlist");
    }

    private boolean isYoutubeHlsManifest(String rawUrl) {
        String lowered = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        return lowered.contains("googlevideo.com") && lowered.contains("/api/manifest/hls");
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

    private File uniqueOutputFile(String fileName) {
        File target = outputFile(fileName);
        if (!target.exists()) {
            return target;
        }
        String safeName = target.getName();
        int dot = safeName.lastIndexOf('.');
        String stem = dot > 0 ? safeName.substring(0, dot) : safeName;
        String extension = dot > 0 ? safeName.substring(dot) : "";
        File directory = target.getParentFile();
        for (int i = 2; i < 1000; i++) {
            File candidate = new File(directory, stem + " (" + i + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return new File(directory, stem + " (" + System.currentTimeMillis() + ")" + extension);
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
        if (!playlist.resumable) {
            checkpoint.delete();
            return 0;
        }
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
        if (!playlist.resumable) {
            checkpoint.delete();
            return;
        }
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
        final boolean resumable;

        HlsPlaylist(String manifestUrl, List<HlsSegment> segments, boolean resumable) {
            this.manifestUrl = manifestUrl;
            this.segments = segments;
            this.resumable = resumable;
        }
    }
}
