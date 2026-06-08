# 下載者 Android 版

`Download_Android` 是 Windows 版「下載者」的 Android 原生移植專案。目標是在手機上提供接近桌面版的影片搜尋、解析、下載、續傳、播放與診斷能力，同時維持 Android 友善的操作方式。

目前 Android 版本：`0.150.0`（`versionCode 150`）。

## App 特色

- Android 原生介面，採用左上角選單、右上角設定、中央下載隊列的手機操作配置。
- 介面風格採用淡雅、清爽的日系配色，主要操作集中在新增下載、播放與下載隊列。
- App 名稱依 Windows 版「下載者」為準，並提供繁體中文、簡體中文、英文、日文四語系資源。
- Launcher 圖示沿用 Windows 版的台灣圖示。
- Android 下載佇列只顯示目前未完成的下載檔案與進度，重新啟動時會清除已完成項目。
- 右上角設定選單提供下載目錄設定，可選擇完成檔匯出的資料夾。
- 完成影片清單只顯示實際存在於下載目錄的可播放檔案，最多顯示三筆，並可用右側上下按鈕切換更多檔案。
- 當已設定下載目錄時，播放清單只讀取該目錄；如果該目錄是空的，就不顯示任何可播放檔案。

- v0.136.0: 右上角設定選單新增「關於」，顯示下載者版本與編譯年月日。
- v0.136.0: GoodAV17/HohoJ/GGJAV 會解碼 `ggjav.com/main/embed?u=...` iframe，加入直接媒體與 video-N.ggjav.com 備援候選。
- v0.136.0: UI 底部下載隊列改為按鈕，預設不顯示隊列內容，按下按鈕才以視窗顯示目前下載檔案與進度。
- v0.137.0: GGJAV 的 `var l = ...` 混淆播放器資料會被解碼成直接媒體候選，並套用 MP4/HLS 與 video-N.ggjav.com 備援。
- v0.138.0: Gimy iframe/player 頁會展開 `parse.php?url=...` API 候選與 iframe `url=` 內的直接媒體候選，並套用接近 Windows 版的 Gimy 串流排序。
- v0.139.0: Gimy 播放頁會反推出 `/title/`、`/vod/`、`/detail/`、`/voddetail/` 詳情頁備援候選，parse 回應也會辨識 `playurl` 媒體欄位。
- v0.140.0: MovieFFM 劇集頁會提取 `/drama/` 季/詳情頁、JSON `name`/`url` 集數，以及頁面中的 `/play/`、`/vodplay/`、`/episode/` 播放頁候選。
- v0.141.0: 下載通知不再於通知內容內重複顯示「下載器/Downloader」，通知標題直接顯示目前下載狀態與進度。
- v0.142.0: AVJoy 頁面會依照 Windows 版提取 `hls1`、`hls2`、`hls4` 等 HLS 欄位，並優先選擇較高順位的播放候選。
- v0.143.0: BestJavPorn 與 JavDock 影片頁新增 Windows 版同源的 RC4/Base64 播放器設定解碼，解析成功時可直接取得 HLS/MP4 候選。
- v0.144.0: NJAV 影片頁新增 Windows 版同源的 video id、AJAX 影片清單、iframe player 與 `PLAYER_CONFIG` HLS 解析流程。
- v0.145.0: NJAVTV 影片頁新增 Windows 版同源的 `surrit.com/.../playlist.m3u8` 與 `source =` HLS 解析路徑。
- v0.146.0: Jable 影片頁新增 Windows 版同源的直接 `.m3u8` 與 `hlsUrl` HLS 解析路徑，並套用 Jable referer。
- v0.147.0: 85xVideo 影片頁新增 Windows 版同源的媒體 URL 與 `<source src>` 候選抽取，並排除圖片誤判。
- v0.148.0: TinyAVideo 影片頁新增 Windows 版同源的媒體 URL 候選抽取，讓番號搜尋命中頁面後可直接取得 HLS/MP4 候選。
- v0.149.0: SupJAV 影片頁新增 Windows 版同源的播放 server 追蹤，會由 `btn-server`/`data-link` 進入 supremejav player 與 child page 抽取 HLS/MP4 候選。
- v0.150.0: 搜尋影片名稱或番號時不再自動下載第一個結果，改為先列出縮圖、檔名/番號、站台名稱，由使用者選擇要下載的網址。
## 下載功能

- 支援直接貼上或分享影片網址到 App。
- 支援批次網址解析，貼上多個 HTTP/HTTPS 連結時可一次加入下載。
- 支援一般 HTTP 下載、HLS `.m3u8`、DASH `.mpd`、MP4、WebM、M4V 等常見媒體來源。
- 內建自訂下載核心，不依賴 Android `DownloadManager`。
- 支援前景服務與通知進度，適合長時間下載。
- 支援最多兩個下載任務並行。
- 支援取消任務並保留可續傳的部分檔。
- 支援 app 重啟後恢復未完成任務。
- 支援失敗或取消任務重新排入下載。
- 支援清除已完成、失敗、取消的任務記錄。
- 支援將完成檔匯出到使用者設定的下載目錄；未設定時使用公開下載目錄 `Downloads/AI Test Downloader`。

## 搜尋與解析

- 輸入框可接受影片網址、影片名稱、番號、媒體檔名或瀏覽器 `Copy as cURL` 文字。
- 支援以影片名稱或番號搜尋可下載來源。
- 支援 JAV 番號偵測，會優先嘗試常見 JAV 站點的直接頁面與站內搜尋。
- 支援 MovieFFM、Gimy、XiaoyaKankan、MacCMS 類站點、Anime1、AniGamer、部分社群平台與 JAV/adult 站群的解析基礎。
- MovieFFM 會提取 Mixdrop、Dood、Evoload 類外部播放來源，並將 Mixdrop 檔案頁正規化為播放頁候選。
- Mixdrop 外部播放器頁會額外抽取 `wurl` 腳本媒體欄位，補強 MovieFFM 外部來源遞迴解析。
- Dood 系列外部播放網域比照 Windows 版擴大辨識，涵蓋 `dood.video`、`doodstream`、`d000d`、`do7go`、`dooood` 與常見 `dood.*` 變體。
- Mixdrop、Dood 系列、Evoload 直接網址與 iframe 會被辨識為外部播放器來源，避免落回 generic 頁面標籤。
- MovieFFM 劇集頁會補充季/詳情頁與集數播放頁候選，提升影集、連續劇搜尋後的來源重建能力。
- Gimy 頁面的 `player_data` 會生成常見 `aiplayer` / `play.gimy01.tv` iframe 候選，提升進入真實播放器頁的成功率。
- Gimy iframe/player 頁會提取 `parse.php?url=...` API 候選，並從 iframe `url=` 參數推導直接媒體候選。
- Gimy 播放頁可反推出常見詳情頁備援候選，方便在播放頁失敗時重新建立集數與來源列表。
- XiaoyaKankan 會從連結、資料屬性與 script 欄位提取 `/vod/play/id/` 播放頁候選。
- XiaoyaKankan `.com` 頁面的 `var pp` 線路資料會被解析成同集數的媒體候選來源。
- 支援遞迴追蹤播放頁、iframe、API 頁面，最多追蹤多層播放來源。
- 解析結果會保留候選來源，使用者可從選單中檢視、複製、分享、開啟或指定某個來源重新下載。
- 候選來源會盡量顯示來源站、畫質、集數、媒體類型、Referer 等資訊。

## 瀏覽器請求內容

- 支援貼上 `Cookie:`、`Referer:` 等瀏覽器請求資訊。
- 支援 `Copy as cURL` 文字解析，會提取 URL、Referer、Cookie 與常用安全標頭。
- 支援 `User-Agent`、`Accept`、`Accept-Language`、`Authorization`、`Sec-Fetch-*` 等標頭。
- 解析頁面、HLS、DASH、key、init、segment 下載時會延續 Referer、Origin 與必要請求標頭。
- 內建持久化 Cookie jar，服務或 App 重啟後仍可復用已保存 Cookie。

## HLS / DASH 支援

- HLS 支援 media playlist 下載、segment 重試、空 segment 檢查與合併。
- HLS 支援 master playlist variant 選擇，會依解析度、頻寬、名稱與 codecs 優先選取較佳來源。
- HLS 支援 AES-128 解密，包含 IV 與 media sequence IV fallback。
- HLS 支援 `#EXT-X-MAP` init segment 與 byte-range 片段。
- HLS 支援 VOD checkpoint 續傳，避免 live/event playlist 使用過期 segment 窗口續傳。
- HLS 完成後會嘗試使用 Android `MediaExtractor` / `MediaMuxer` 原生 remux 成 MP4，失敗時保留 TS 輸出。
- DASH 支援 `SegmentTemplate`、`SegmentList`、`SegmentBase` 與 `SegmentTimeline`。
- DASH 會依解析度、頻寬、codecs 選擇影片 representation，並略過 audio-only、subtitle、text、image 軌。
- DASH 會選出獨立音訊軌並嘗試下載後與影片軌合併為 MP4；若裝置不支援該封裝，會保留純影片輸出。

## 下載正確性與防呆

- 直接 HTTP 下載會驗證伺服器 `Content-Length` 與完成檔大小，避免短檔被標記完成。
- HTTP、HLS、DASH 完成輸出會進行 Android 原生媒體驗證，確認 MP4/M4V/WebM/MKV/MOV 至少有可讀的音訊或影片軌。
- 媒體軌若提供 duration，中斷或壞掉的零長度輸出會被拒絕；未提供 duration 的格式則保守放行。
- 媒體驗證會實際讀取第一個音訊或影片 sample，避免空軌或壞軌被視為成功。
- HLS/DASH byte-range 下載會驗證回傳 bytes 數是否符合請求範圍。
- HLS/DASH 非 byte-range 下載若伺服器提供 `Content-Length`，也會驗證實際讀取長度。
- HTTP、HLS、DASH 會拒絕明顯的 HTML、JSON、XML、純文字錯誤頁。
- 即使 CDN 把錯誤頁標成二進位內容，App 仍會嗅探前段 payload，避免錯誤頁混入影片片段或直接輸出檔。

## 播放功能

- 支援「播放選取影片」，可從下載目錄中選取已完成且實際存在的影片交給外部播放器播放。
- 支援「下載 50MB 後播放」，下載達到門檻後會啟動外部播放器，檔案仍會繼續完整下載。
- 播放用 URI 會透過 Android content URI 授權，避免直接暴露 app 私有路徑。
- App 會避免列出已刪除但仍殘留在 Android 媒體索引中的舊項目。

## 多語言

App 目前提供：

- 繁體中文
- 簡體中文
- English
- 日本語

可在 App 內選擇系統預設或指定語言，通知與服務文字也會套用已保存語言設定。

## 日誌與診斷

- 內建結構化活動日誌，記錄解析、下載、候選來源、錯誤、重試與併發變更。
- 可從 Android UI 匯出日誌到公開下載目錄，方便檢查手機端問題。
- 來源候選可複製為詳細文字、分享給其他 App，或輸出成 `curl -L` 命令，方便外部比對與除錯。

## 建置 APK

在 PowerShell 執行：

```powershell
C:\antigravity\downloader_android\build_android_apk.ps1
```

Debug APK 會輸出到：

```text
C:\antigravity\downloader_android\dist
```

## GitHub 同步規則

本專案遵循 Windows 下載者的版本同步規則：

- 每個 Android 版本都可正常本地建置 APK。
- 當 `versionCode % 10 == 0` 時，`build_android_apk.ps1` 會呼叫 `publish_github.ps1`。
- 發佈腳本會提交已追蹤的原始碼變更、推送到 GitHub、建立 `v<versionCode>` tag，並上傳該版本 APK 到 GitHub Release。
- 非同步版本只會本地建置 APK，不會自動發佈。

範例：

- `versionCode 110` 會同步為 GitHub tag `v110`。
- `versionCode 111` 到 `119` 只會本地建置。
- `versionCode 120` 會再次同步為 GitHub tag `v120`。

## 目前仍在移植的桌面版功能

- 完整 FFmpeg/ffprobe 等價能力：更廣泛的 DASH mux 相容性、轉碼 fallback、duration/完整性驗證仍需繼續補齊。
- 更深層的單站解析器：MovieFFM、Gimy、Xiaoya、JAV/adult 站群仍需持續移植 Windows 版的深層站點邏輯；MovieFFM 外部播放器已補上 Mixdrop `wurl` 媒體欄位抽取、更完整的 Dood 系列網域辨識、Mixdrop/Dood/Evoload 外部播放器站點標籤，以及劇集/集數候選重建；Gimy 已補上 iframe parse API 候選展開與播放頁反推詳情頁備援；GoodAV17/HohoJ/GGJAV 已補上 GGJAV embed 與 `var l = ...` 混淆播放器解析。
- yt-dlp/plugin 類 fallback：Android 端需要更適合手機環境的替代方案或受控整合策略。
- 更完整的搜尋結果審核 UI：目前 Android 已有候選來源面板，但桌面版的完整替代站提示與結果審核仍在移植中。

更多移植覆蓋範圍與缺口請見 [ANDROID_PORT_STATUS.md](ANDROID_PORT_STATUS.md)。
