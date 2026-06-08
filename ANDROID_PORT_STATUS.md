# Android Port Status

This Android app is a native port track for the desktop downloader in `downloader.py`.

## Implemented

- APK build pipeline with local JDK, Android SDK, and Gradle.
- Native Android UI for entering or sharing a URL.
- Android-friendly main screen with a left hamburger menu, right overflow settings menu, and download queue centered as the primary content.
- Main UI now uses a calm Japanese-inspired Android layout with pale surfaces, subtle borders, queue-first structure, and large touch-friendly controls.
- Android app name and launcher icon match the Windows version: `下載者` with the Taiwan symbol icon.
- Batch URL parsing from pasted/shared text; multiple http(s) links are queued together.
- Foreground download service and notification progress for long-running downloads.
- Persistent JSON task queue through SharedPreferences.
- Bounded multi-task queue execution with up to two active downloads.
- Concurrency slot changes are written to the structured runtime activity log.
- Interrupted running tasks are recovered back to queued state after service restart.
- Failed or cancelled tasks can be requeued from the Android UI.
- Finished, failed, and cancelled task history can be cleared from the Android UI.
- Structured runtime activity log at the app-specific Documents `logs/activity.jsonl`.
- Android UI can export the structured runtime activity log to public Downloads for device-side diagnostics.
- Android UI strings now use resource dictionaries with English, Traditional Chinese, Simplified Chinese, and Japanese translations.
- App name is localized from the Traditional Chinese source name `下載者` into English, Simplified Chinese, Traditional Chinese, and Japanese.
- Android UI includes an in-app language selector for system default, English, Traditional Chinese, Simplified Chinese, and Japanese; the saved choice also applies to service notifications.
- Resolved page URLs propagate Referer and Origin headers into HTTP, HLS, and DASH manifest/key/init/segment requests.
- HTTP requests share an app-wide persistent cookie jar so cookies captured during page resolution can be reused by later manifest and media requests after service or app restarts.
- Android input accepts pasted browser request context: `Cookie:` and `Referer:` headers are parsed, persisted, and applied to queued downloads.
- Android input accepts additional safe pasted request headers such as `User-Agent`, `Accept`, `Accept-Language`, `Authorization`, and fetch metadata headers.
- Android input supports browser `Copy as cURL` text and avoids queuing URLs found only inside pasted header values such as Referer.
- Android input now accepts video titles and JAV-style codes as search queries, searches supported video sites in the background, and queues the first resolvable result for download.
- Android video search now seeds supported site-search URLs before falling back to search-engine results, improving reliability for MovieFFM, Gimy/Xiaoya/MacCMS-like sites, and common JAV site clusters.
- Android video search detects JAV-style codes and prioritizes JAV direct-code URLs plus JAV site-search candidates before general video sites.
- Android resolver now recognizes more MacCMS-like search result paths such as `/voddetail/`, `/voddetail2/`, and `/title/`, plus additional Ikanbot/YFSP/Olevod/777TV search entry points.
- Android video search now fetches the first supported site-search pages, extracts ranked detail/play-page links, and keeps the search page itself as a fallback candidate.
- Android video search now preserves each extracted result's search-page Referer while resolving and downloading, improving compatibility with sites that validate navigation origin.
- Android video search now keeps alternate search results in the resolved source candidate list after one result succeeds, so the UI can retry a different search result instead of only the current page's media sources.
- Parser registry foundation through `MediaResolver`.
- First site-aware candidate extraction and ordering for MovieFFM, Gimy, and XiaoyaKankan style pages.
- Recursive player/iframe/API page resolution up to four levels deep.
- Direct media candidates are preferred before player-page fallbacks.
- Resolver decisions are logged with source site, primary URL, and candidate count.
- Resolver decisions are persisted into the task history summary.
- Resolver candidate URL lists are persisted into task history, shown in the Android summary preview, and written to the activity log.
- Android UI can requeue the latest resolved task with the next persisted source candidate via `Try Next Source`.
- Android UI exposes a source candidate picker and can requeue a specific selected resolver candidate.
- Source picker labels include inferred media type, quality, and source site hints when visible in the candidate URL.
- Source picker labels infer episode numbers and friendly names for common CDN/player hosts used by MovieFFM, Gimy, XiaoyaKankan, AniGamer, Anime1, and MacCMS-like sites.
- Resolver candidate metadata now preserves extraction-time labels from anchor text, player object fields, iframe/player context, Anime1, and AniGamer signals.
- Resolver candidate labels now prefer anchor `title`, `aria-label`, and common `data-*` source fields, and combine player object source/server/quality/name fields.
- Resolver candidate labels now infer media type, quality, episode number, and known CDN/source hints even when the page does not provide a readable label.
- Download queue summaries now preview resolved sources with readable candidate labels and short URLs instead of raw long URLs only.
- Alternate direct media candidates are retried automatically when the selected source fails.
- HTTP `.part` resume files are guarded by URL state so alternate sources do not corrupt partial downloads.
- Android 10+ MediaStore export copies completed files into public Downloads/AI Test Downloader.
- Android 6-9 legacy storage export copies completed files and exported logs into public Downloads/AI Test Downloader after requesting write permission.
- MacCMS-style `player_data`, `player_aaaa`, and `player` JavaScript object parsing.
- MacCMS `encrypt=1` URL decode and `encrypt=2` base64 URL decode for player URLs.
- Player source arrays and common MovieFFM/Gimy/Xiaoya episode/play-page links are extracted as resolver candidates.
- Anime1 page parser resolves `data-apireq` through the Anime1 API and extracts direct media URLs.
- Ani Gamer resolver support extracts `animeVideo.php?sn=...` episode links and `animefun.videoSn` page candidates.
- Custom HTTP downloader instead of Android `DownloadManager`.
- Direct media downloads for `mp4`, `webm`, `m4v`, and similar URLs.
- Basic HTTP resume using `.part` files and `Range` requests.
- Cancel support that preserves partial files for resumable HTTP downloads.
- Basic page parsing for common media candidates in `src`, `href`, `file`, `url`, and direct media URLs.
- Generic player-script extraction for `hlsUrl`, `hls`, `m3u8`, `playlist`, `source`, `video_url`, and protocol-relative media URLs.
- MacCMS-like site detection and play/detail traversal for NNYY, 3KOR, DramaSQ, Olevod/OleHDTV, Thanju, 99iTV, and 777TV.
- Large platform/site detection for Dailymotion, YouTube, Bilibili, iQIYI, Ikanbot, and YFSP so resolver logs and task history no longer fall back to generic for those URLs.
- Social platform detection and embedded media extraction for Instagram, Facebook, and X/Twitter metadata, JSON payloads, and Twitter media variants.
- First JAV/adult site cluster detection and play-page candidate traversal for MissAV, Jable, NJAV/NJAVTV, SupJAV, Hanime1, 18JAV/18AV, 85xVideo, AVBebe, AVJoy, BestJavPorn, JavDock, JavFilms, TinyAVideo, GoodAV17, HohoJ, GGJAV, and TKTube.
- Wider player JSON extraction for common manifest and stream keys such as `manifestUrl`, `streamUrl`, `videoUrl`, `mediaUrl`, `dashUrl`, `qualities`, and `streams`.
- HLS `.m3u8` segment download and merge into a `.ts` output.
- HLS master playlist variant selection by bandwidth.
- HLS master playlist variant selection now reads `RESOLUTION`, `NAME`, and `CODECS`, preferring higher resolution before bandwidth and reporting the selected variant details.
- HLS AES-128 segment decryption with explicit IV or media sequence IV fallback.
- HLS segment retry and empty-segment validation.
- HLS checkpoint resume for matching manifest URL and segment count.
- HLS `#EXT-X-MAP` init segment handling, including byte-range requests.
- HLS media segment `#EXT-X-BYTERANGE` handling for byte-range based playlists and single-file segment layouts.
- HLS byte-range segment parsing now preserves implicit offsets when `#EXT-X-BYTERANGE` omits `@offset`, matching the playlist sequence rules.
- HLS discontinuity markers flush the current output before continuing.
- HLS downloads now attempt an Android-native MediaExtractor/MediaMuxer remux from the merged transport stream into MP4, falling back to the TS output when remux is unsupported.
- Basic DASH `.mpd` support for single-representation fragmented MP4 streams using `SegmentTemplate` or `SegmentList`.
- DASH `SegmentBase` support for single-file MP4 representations with `Initialization` byte ranges.
- DASH representation selection now reads representation or adaptation `width`, `height`, and `codecs`, preferring higher resolution before bandwidth and reporting the selected representation details.
- DASH parsing now honors MPD, Period, AdaptationSet, and Representation `BaseURL` hierarchy and skips audio-only AdaptationSet or Representation candidates before selecting the video stream.
- DASH downloads write init + media segments into `.mp4` and keep a checkpoint for resumable segment progress.

## Not Yet Ported From Desktop

- Full deep per-site parsers for MovieFFM, Gimy, XiaoyaKankan, YFSP, iQIYI, YouTube, Dailymotion, Bilibili, Ikanbot, social platforms, and adult/JAV sites; NNYY, 3KOR, DramaSQ, Olevod/OleHDTV, Thanju, 99iTV, and 777TV currently have MacCMS-like generic traversal, while Dailymotion/YouTube/Bilibili/iQIYI/Ikanbot/YFSP/social platforms and the first adult/JAV cluster currently have site detection and generic stream/play-page extraction only.
- yt-dlp integration and plugin support.
- ffmpeg/ffprobe based remux, transcode, duration validation, multi-track DASH audio/video muxing, and fallback routing.
- Full browser session reuse and impersonation behavior equivalent to `curl_cffi` and desktop browser workflows; pasted Cookie/Referer plus selected request headers, basic Referer/Origin propagation, and persistent app cookie storage are already ported.
- Alternate-site fallback prompts and full desktop search result review UI.

## Next Porting Order

1. Extend the Android-native remux path and evaluate FFmpegKit or equivalent fallback for formats MediaMuxer cannot handle.
2. Add structured per-site episode/source metadata for major individual sites.
3. Port additional site parsers in clusters, starting with JAV/adult sites.
4. Search and alternate-site fallback.
