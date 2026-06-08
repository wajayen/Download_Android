# Android Port Status

This Android app is a native port track for the desktop downloader in `downloader.py`.

## Implemented

- APK build pipeline with local JDK, Android SDK, and Gradle.
- Native Android UI for entering or sharing a URL.
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
- Alternate direct media candidates are retried automatically when the selected source fails.
- HTTP `.part` resume files are guarded by URL state so alternate sources do not corrupt partial downloads.
- Android 10+ MediaStore export copies completed files into public Downloads/AI Test Downloader.
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
- Wider player JSON extraction for common manifest and stream keys such as `manifestUrl`, `streamUrl`, `videoUrl`, `mediaUrl`, `dashUrl`, `qualities`, and `streams`.
- HLS `.m3u8` segment download and merge into a `.ts` output.
- HLS master playlist variant selection by bandwidth.
- HLS AES-128 segment decryption with explicit IV or media sequence IV fallback.
- HLS segment retry and empty-segment validation.
- HLS checkpoint resume for matching manifest URL and segment count.
- HLS `#EXT-X-MAP` init segment handling, including byte-range requests.
- HLS discontinuity markers flush the current output before continuing.
- Basic DASH `.mpd` support for single-representation fragmented MP4 streams using `SegmentTemplate` or `SegmentList`.
- DASH downloads write init + media segments into `.mp4` and keep a checkpoint for resumable segment progress.

## Not Yet Ported From Desktop

- Full deep per-site parsers for MovieFFM, Gimy, XiaoyaKankan, YFSP, iQIYI, YouTube, Dailymotion, Bilibili, Ikanbot, and the adult/JAV sites listed in the desktop README; NNYY, 3KOR, DramaSQ, Olevod/OleHDTV, Thanju, 99iTV, and 777TV currently have MacCMS-like generic traversal, while Dailymotion/YouTube/Bilibili/iQIYI/Ikanbot/YFSP currently have site detection and generic stream extraction only.
- yt-dlp integration and plugin support.
- ffmpeg/ffprobe based remux, transcode, duration validation, multi-track DASH audio/video muxing, and fallback routing.
- Browser/cookie/impersonation behavior equivalent to `curl_cffi` and desktop browser workflows.
- Search workflows and alternate-site fallback prompts.
- Pre-Android 10 public Downloads export; current fallback output is app-specific external Downloads on older devices.

## Next Porting Order

1. FFmpegKit or equivalent media remux path for HLS/DASH to normalized MP4.
2. Add structured per-site episode/source metadata for major individual sites.
3. Port additional site parsers in clusters, starting with JAV/adult sites.
4. Search and alternate-site fallback.
