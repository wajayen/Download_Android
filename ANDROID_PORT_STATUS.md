# Android Port Status

This Android app is a native port track for the desktop downloader in `downloader.py`.

## Implemented

- APK build pipeline with local JDK, Android SDK, and Gradle.
- Native Android UI for entering or sharing a URL.
- Android-friendly main screen with a left hamburger menu, right overflow settings menu, and download queue centered as the primary content.
- Main UI now uses a calm Japanese-inspired Android layout with pale surfaces, subtle borders, the new-download controls above the download queue, and large touch-friendly controls.
- New-download controls now list the latest three completed videos from the app download directory above the playback controls, allowing one to be selected and opened in an external player.
- Completed-video playback now lists only playable files that still exist in the download directory or can be opened through MediaStore, shows filenames without extensions or numbering, and uses up/down controls to select beyond the three visible rows.
- Completed-video playback controls now have English, Traditional Chinese, Simplified Chinese, and Japanese dictionary entries instead of falling back to mixed UI text.
- Completed-video playback now refreshes when returning to the app and includes completed files exported to public Downloads/AI Test Downloader, using scoped read-only content URIs for playback.
- Completed-video playback now queries public Downloads/AI Test Downloader through MediaStore on Android 10+ and plays those entries by MediaStore URI, avoiding scoped-storage file path issues.
- Completed-video playback now requests Android 13+ video media permission so public exported videos are visible in the recent completed-video list when the platform requires it.
- Completed-video playback now refreshes the recent completed-video list immediately after notification, legacy storage, or Android 13+ video media permissions return.
- Download queue now shows only unfinished files with their current progress, hides resolved-source metadata from the queue area, and clears completed tasks on app/service startup.
- Source-candidate review now shows the selected candidate's full URL in the hamburger-menu source panel before queueing that source.
- Source-candidate review now also shows the selected candidate's Referer/source page when one is available, matching protected-site download diagnostics more closely.
- Source-candidate review can now copy the selected URL and Referer details to the Android clipboard for external inspection or troubleshooting.
- Source-candidate review can now copy the complete candidate list with labels, URLs, and Referer details for batch comparison.
- Source-candidate review can now share the complete candidate list through Android's standard share sheet.
- Source-candidate review can now share the selected URL and Referer details through Android's standard share sheet.
- Source-candidate review can now copy the selected candidate as a `curl -L` command, including a Referer header when available.
- Source-candidate review can now share the generated `curl -L` command through Android's standard share sheet.
- Source-candidate review can now open the selected candidate URL in an external browser for quick result inspection.
- Source-candidate review can now open the selected candidate's Referer/source page in an external browser when available.
- Source-candidate review now preserves the selected candidate across status refreshes when that candidate is still available.
- Android UI now includes a Play after 50 MB action below Download; selected tasks keep downloading to completion while the partial file is opened in an external player once it reaches 50 MB.
- Play after 50 MB now has dedicated multi-download queue messaging and a playback-started notification while the download continues.
- Play after 50 MB now persists its playback-attempt state so recovered or restarted tasks do not repeatedly relaunch the external player.
- Play after 50 MB now exposes a playable display filename to external Android players while keeping the underlying `.part` file read-only and app-scoped.
- Play after 50 MB now records a visible four-language task message when Android cannot launch an external player, while keeping the download running.
- Play after 50 MB now includes the playback content URI in both Intent data and ClipData, improving read-permission compatibility with external Android players.
- Android app name and launcher icon match the Windows version: `下載者` with the Taiwan symbol icon.
- Batch URL parsing from pasted/shared text; multiple http(s) links are queued together.
- Download file names now receive desktop-style safety cleanup for control characters, invalid path characters, trailing dots/spaces, and Windows reserved device names before queueing or exporting.
- Download file names now infer desktop-style names from URL query hints such as `response-content-disposition`, `filename`, `file`, and `name` before falling back to the URL path.
- Direct HTTP downloads now use a server-provided `Content-Disposition` filename for the finalized output when available, after applying the same safe filename cleanup.
- `Content-Disposition` `filename*=` values now decode RFC 5987 percent-encoded filenames before safe filename cleanup, improving Traditional Chinese, Japanese, and other non-ASCII output names.
- Direct HTTP downloads avoid overwriting an existing file when a server-provided filename collides, using numbered names such as `video (2).mp4`.
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
- Android input now preserves more safe browser request headers from copied requests, including `Cache-Control`, `DNT`, `Pragma`, `Priority`, `Sec-Fetch-User`, and safe `Accept-Encoding: identity`.
- Android HTTP requests now default to `Accept-Encoding: identity` so HTML, HLS, DASH, and segment reads do not accidentally parse compressed bytes as plain text/media.
- Android text response parsing now honors `Content-Type` charset values for pages, HLS/DASH manifests, and Anime1 API responses, falling back to UTF-8.
- Android HTTP, page, API, HLS, and DASH reads now report clear HTTP status failures and reject byte-range segment responses that are not returned as `206 Partial Content`.
- Android input supports browser `Copy as cURL` text and avoids queuing URLs found only inside pasted header values such as Referer.
- Android input now accepts video titles and JAV-style codes as search queries, searches supported video sites in the background, and queues the first resolvable result for download.
- Android search query detection now treats pasted media filenames by their stem, matching the desktop behavior for inputs such as `title.mp4`, `code.m3u8`, or local-looking paths.
- Android video search now seeds supported site-search URLs before falling back to search-engine results, improving reliability for MovieFFM, Gimy/Xiaoya/MacCMS-like sites, and common JAV site clusters.
- Android video search detects JAV-style codes and prioritizes JAV direct-code URLs plus JAV site-search candidates before general video sites.
- Android JAV code search now seeds more direct detail-page variants for MissAV, Jable, NJAVTV, AVBebe, AVJoy, and GGJAV before falling back to site-search pages.
- Android JAV/adult video search now adds site-search coverage for 85xVideo, TinyAVideo, GoodAV17, and TKTube, matching sites already recognized by the Android resolver.
- Android resolver now recognizes more MacCMS-like search result paths such as `/voddetail/`, `/voddetail2/`, and `/title/`, plus additional Ikanbot/YFSP/Olevod/777TV search entry points.
- Android video search now fetches the first supported site-search pages, extracts ranked detail/play-page links, and keeps the search page itself as a fallback candidate.
- Android video search now also extracts ranked site-search candidates from embedded `data-url`, `data-href`, `data-src`, `data-play`, `data-link`, and related attributes.
- Android video search page fetching now requests identity encoding, sends Traditional Chinese/Japanese-friendly language preferences, and honors `Content-Type` charset values before ranking results.
- Android video search now unwraps common redirect query parameters such as `uddg`, `url`, `u`, `target`, `to`, `dest`, and `redirect` before filtering supported candidates.
- Android video search now accepts generic direct `.webm` and `.m4v` media candidates, matching the Android download core's direct-media support.
- Android video search now preserves each extracted result's search-page Referer while resolving and downloading, improving compatibility with sites that validate navigation origin.
- Android video search now keeps alternate search results in the resolved source candidate list after one result succeeds, so the UI can retry a different search result instead of only the current page's media sources.
- Android source candidate retry now persists per-candidate Referer values and restores them when retrying alternate search results from the UI.
- Pasted or shared Referer values are now applied during page resolution, not only during the final media request.
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
- HLS AES-128 key downloads now retry transient failures and reject empty or invalid-length key responses before decrypting segments.
- HLS segment retry and empty-segment validation.
- HLS init maps now share the same retry and empty-response validation used by media segment downloads.
- HLS checkpoint resume for matching manifest URL and segment count.
- HLS checkpoint resume is now limited to VOD or `#EXT-X-ENDLIST` playlists so live/event manifests do not resume against stale segment windows.
- HLS `#EXT-X-MAP` init segment handling, including byte-range requests.
- HLS media segment `#EXT-X-BYTERANGE` handling for byte-range based playlists and single-file segment layouts.
- HLS byte-range segment parsing now preserves implicit offsets when `#EXT-X-BYTERANGE` omits `@offset`, matching the playlist sequence rules.
- HLS discontinuity markers flush the current output before continuing.
- HLS downloads now attempt an Android-native MediaExtractor/MediaMuxer remux from the merged transport stream into MP4, falling back to the TS output when remux is unsupported.
- Basic DASH `.mpd` support for single-representation fragmented MP4 streams using `SegmentTemplate` or `SegmentList`.
- DASH `SegmentBase` support for single-file MP4 representations with `Initialization` byte ranges.
- DASH representation selection now reads representation or adaptation `width`, `height`, and `codecs`, preferring higher resolution before bandwidth and reporting the selected representation details.
- DASH parsing now honors MPD, Period, AdaptationSet, and Representation `BaseURL` hierarchy and skips audio-only AdaptationSet or Representation candidates before selecting the video stream.
- DASH representation selection now also skips text/subtitle/image tracks and explicitly prefers video-like representations by MIME/content type, codecs, or dimensions.
- DASH manifest parsing now detects separate audio tracks and reports that Android currently downloads the selected video representation while audio muxing remains pending, making the largest FFmpeg/ffprobe parity gap visible during downloads.
- DASH `SegmentTimeline` parsing now expands negative repeat counts such as `S r="-1"` up to the next timeline timestamp or period duration.
- DASH init and media segment downloads now retry transient failures and reject empty responses before writing them to the output.
- DASH downloads write init + media segments into `.mp4` and keep a checkpoint for resumable segment progress.

## Not Yet Ported From Desktop

- Full deep per-site parsers for MovieFFM, Gimy, XiaoyaKankan, YFSP, iQIYI, YouTube, Dailymotion, Bilibili, Ikanbot, social platforms, and adult/JAV sites; NNYY, 3KOR, DramaSQ, Olevod/OleHDTV, Thanju, 99iTV, and 777TV currently have MacCMS-like generic traversal, while Dailymotion/YouTube/Bilibili/iQIYI/Ikanbot/YFSP/social platforms and the first adult/JAV cluster currently have site detection and generic stream/play-page extraction only.
- yt-dlp integration and plugin support.
- ffmpeg/ffprobe based remux, transcode, duration validation, multi-track DASH audio/video muxing, and fallback routing.
- Full browser session reuse and impersonation behavior equivalent to `curl_cffi` and desktop browser workflows; pasted Cookie/Referer plus selected request headers, basic Referer/Origin propagation, and persistent app cookie storage are already ported.
- Full desktop search result review UI, including rich alternate-site fallback prompts; Android now exposes source candidates from the hamburger menu as a separate review panel so the download queue remains file/progress only.

## Next Porting Order

1. Complete FFmpeg/ffprobe parity on Android: multi-track DASH audio/video muxing, unsupported-remux fallback, transcode fallback, and duration/media validation.
2. Port deep per-site parsers in high-value clusters, starting with MovieFFM/Gimy/Xiaoya and JAV/adult sites that currently rely on generic traversal.
3. Add yt-dlp/plugin-equivalent fallback strategy or a constrained Android replacement for unsupported platforms.
4. Expand desktop-style search result review with richer alternate-site fallback prompts while keeping the queue display file/progress-only.
5. Add structured per-site episode/source metadata for major individual sites once the parser clusters are deeper.
