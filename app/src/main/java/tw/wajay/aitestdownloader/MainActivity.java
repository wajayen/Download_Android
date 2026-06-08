package tw.wajay.aitestdownloader;

import android.app.Activity;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(250, 248, 245);
    private static final int SURFACE = Color.rgb(255, 255, 253);
    private static final int SURFACE_TINT = Color.rgb(246, 241, 238);
    private static final int BORDER = Color.rgb(226, 219, 213);
    private static final int TEXT_PRIMARY = Color.rgb(39, 43, 50);
    private static final int TEXT_SECONDARY = Color.rgb(92, 99, 108);
    private static final int ACCENT = Color.rgb(158, 83, 101);
    private static final int ACCENT_DARK = Color.rgb(119, 63, 78);
    private static final int INDIGO = Color.rgb(62, 78, 113);
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_LINE = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9-]*)\\s*:\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURL_HEADER = Pattern.compile("-H\\s+['\"]([A-Za-z][A-Za-z0-9-]*)\\s*:\\s*([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURL_URL = Pattern.compile("(?:^|\\s)(?:curl|--url)\\s+(?:--location\\s+|--request\\s+\\S+\\s+|--compressed\\s+)*['\"]?(https?://[^\\s'\"<>]+)['\"]?", Pattern.CASE_INSENSITIVE);
    private EditText urlInput;
    private EditText fileNameInput;
    private LinearLayout completedVideoList;
    private LinearLayout sourcePanel;
    private Spinner sourceSpinner;
    private Button selectedSourceButton;
    private Button copySourceButton;
    private Button copyAllSourcesButton;
    private Button shareAllSourcesButton;
    private Button shareSourceButton;
    private Button copySourceCurlButton;
    private Button shareSourceCurlButton;
    private Button openSourceButton;
    private Button openRefererButton;
    private TextView sourceDetailText;
    private TextView statusText;
    private TaskStore taskStore;
    private List<TaskStore.CandidateOption> sourceOptions = new ArrayList<>();
    private List<CompletedVideo> completedVideos = new ArrayList<>();
    private int selectedCompletedVideoIndex = 0;
    private String selectedSourceUrl = "";
    private boolean sourcePanelVisible = false;

    private static final class BrowserRequestContext {
        final List<String> urls;
        final String referer;
        final String cookieHeader;
        final String headersJson;

        BrowserRequestContext(List<String> urls, String referer, String cookieHeader, String headersJson) {
            this.urls = urls;
            this.referer = referer;
            this.cookieHeader = cookieHeader;
            this.headersJson = headersJson;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageSettings.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskStore = new TaskStore(this);
        taskStore.clearCompletedTasks();
        setContentView(createContentView());
        hydrateSharedText(getIntent());
        requestNotificationPermission();
        requestMediaVideoPermission();
        requestLegacyStoragePermission();
        refreshStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        hydrateSharedText(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 || requestCode == 1002 || requestCode == 1003) {
            refreshStatus();
        }
    }

    private ScrollView createContentView() {
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(12), pad, pad);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar, matchWrap());

        Button menuButton = toolbarButton(getString(R.string.action_open_menu), "\u2630");
        menuButton.setOnClickListener(view -> showNavigationMenu(view));
        toolbar.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(22);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button settingsButton = toolbarButton(getString(R.string.action_open_settings), "...");
        settingsButton.setOnClickListener(view -> showSettingsMenu(view));
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView addTitle = new TextView(this);
        addTitle.setText(getString(R.string.section_new_download));
        styleSectionTitle(addTitle);
        addTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(addTitle, matchWrap());

        LinearLayout inputPanel = contentPanel();
        root.addView(inputPanel, matchWrap());

        urlInput = new EditText(this);
        urlInput.setHint(getString(R.string.hint_url));
        urlInput.setSingleLine(false);
        urlInput.setMinLines(3);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        styleInput(urlInput);
        inputPanel.addView(urlInput, matchWrap());

        fileNameInput = new EditText(this);
        fileNameInput.setHint(getString(R.string.hint_file_name));
        fileNameInput.setSingleLine(true);
        fileNameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        styleInput(fileNameInput);
        inputPanel.addView(fileNameInput, matchWrap());

        Button downloadButton = new Button(this);
        downloadButton.setText(getString(R.string.action_download));
        stylePrimaryButton(downloadButton);
        downloadButton.setOnClickListener(view -> startDownload());
        inputPanel.addView(downloadButton, matchWrap());

        completedVideoList = new LinearLayout(this);
        completedVideoList.setOrientation(LinearLayout.VERTICAL);
        inputPanel.addView(completedVideoList, matchWrap());

        Button playSelectedButton = new Button(this);
        playSelectedButton.setText(getString(R.string.action_play_selected_video));
        styleSecondaryButton(playSelectedButton);
        playSelectedButton.setOnClickListener(view -> playSelectedCompletedVideo());
        inputPanel.addView(playSelectedButton, matchWrap());

        Button playAfterButton = new Button(this);
        playAfterButton.setText(getString(R.string.action_play_after_50mb));
        styleSecondaryButton(playAfterButton);
        playAfterButton.setOnClickListener(view -> startDownload(true));
        inputPanel.addView(playAfterButton, matchWrap());

        TextView queueTitle = new TextView(this);
        queueTitle.setText(getString(R.string.section_download_queue));
        styleSectionTitle(queueTitle);
        queueTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(queueTitle, matchWrap());

        LinearLayout queueCard = contentPanel();
        root.addView(queueCard, matchWrap());

        statusText = new TextView(this);
        statusText.setText(getString(R.string.status_idle));
        statusText.setTextSize(15);
        statusText.setTextColor(TEXT_SECONDARY);
        statusText.setMinLines(6);
        statusText.setGravity(Gravity.TOP | Gravity.START);
        statusText.setLineSpacing(dp(2), 1.0f);
        statusText.setPadding(dp(2), 0, dp(2), dp(10));
        queueCard.addView(statusText, matchWrap());

        sourcePanel = contentPanel();
        sourcePanel.setVisibility(View.GONE);
        root.addView(sourcePanel, matchWrap());

        TextView sourceTitle = new TextView(this);
        sourceTitle.setText(getString(R.string.section_source_candidates));
        styleSectionTitle(sourceTitle);
        sourceTitle.setPadding(0, 0, 0, dp(4));
        sourcePanel.addView(sourceTitle, matchWrap());

        sourceSpinner = new Spinner(this);
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < sourceOptions.size()) {
                    selectedSourceUrl = sourceOptions.get(position).url;
                }
                updateSourceDetail();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateSourceDetail();
            }
        });
        sourcePanel.addView(sourceSpinner, matchWrap());

        sourceDetailText = new TextView(this);
        sourceDetailText.setText(getString(R.string.source_detail_empty));
        sourceDetailText.setTextSize(13);
        sourceDetailText.setTextColor(TEXT_SECONDARY);
        sourceDetailText.setMinLines(2);
        sourceDetailText.setPadding(dp(10), dp(8), dp(10), dp(8));
        sourceDetailText.setBackground(roundedBackground(SURFACE_TINT, BORDER, 1, 8));
        sourcePanel.addView(sourceDetailText, matchWrap());

        selectedSourceButton = new Button(this);
        selectedSourceButton.setText(getString(R.string.action_queue_selected_source));
        styleSecondaryButton(selectedSourceButton);
        selectedSourceButton.setOnClickListener(view -> retrySelectedSource());
        sourcePanel.addView(selectedSourceButton, matchWrap());

        copySourceButton = new Button(this);
        copySourceButton.setText(getString(R.string.action_copy_source_detail));
        styleSecondaryButton(copySourceButton);
        copySourceButton.setOnClickListener(view -> copySelectedSourceDetail());
        sourcePanel.addView(copySourceButton, matchWrap());

        copyAllSourcesButton = new Button(this);
        copyAllSourcesButton.setText(getString(R.string.action_copy_all_source_details));
        styleSecondaryButton(copyAllSourcesButton);
        copyAllSourcesButton.setOnClickListener(view -> copyAllSourceDetails());
        sourcePanel.addView(copyAllSourcesButton, matchWrap());

        shareAllSourcesButton = new Button(this);
        shareAllSourcesButton.setText(getString(R.string.action_share_all_source_details));
        styleSecondaryButton(shareAllSourcesButton);
        shareAllSourcesButton.setOnClickListener(view -> shareAllSourceDetails());
        sourcePanel.addView(shareAllSourcesButton, matchWrap());

        shareSourceButton = new Button(this);
        shareSourceButton.setText(getString(R.string.action_share_source_detail));
        styleSecondaryButton(shareSourceButton);
        shareSourceButton.setOnClickListener(view -> shareSelectedSourceDetail());
        sourcePanel.addView(shareSourceButton, matchWrap());

        copySourceCurlButton = new Button(this);
        copySourceCurlButton.setText(getString(R.string.action_copy_source_curl));
        styleSecondaryButton(copySourceCurlButton);
        copySourceCurlButton.setOnClickListener(view -> copySelectedSourceCurl());
        sourcePanel.addView(copySourceCurlButton, matchWrap());

        shareSourceCurlButton = new Button(this);
        shareSourceCurlButton.setText(getString(R.string.action_share_source_curl));
        styleSecondaryButton(shareSourceCurlButton);
        shareSourceCurlButton.setOnClickListener(view -> shareSelectedSourceCurl());
        sourcePanel.addView(shareSourceCurlButton, matchWrap());

        openSourceButton = new Button(this);
        openSourceButton.setText(getString(R.string.action_open_source_in_browser));
        styleSecondaryButton(openSourceButton);
        openSourceButton.setOnClickListener(view -> openSelectedSourceInBrowser());
        sourcePanel.addView(openSourceButton, matchWrap());

        openRefererButton = new Button(this);
        openRefererButton.setText(getString(R.string.action_open_referer_in_browser));
        styleSecondaryButton(openRefererButton);
        openRefererButton.setOnClickListener(view -> openSelectedRefererInBrowser());
        sourcePanel.addView(openRefererButton, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(root);
        return scroll;
    }

    private Button toolbarButton(String description, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(22);
        button.setTextColor(INDIGO);
        button.setAllCaps(false);
        button.setContentDescription(description);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(dp(48));
        button.setBackground(roundedBackground(SURFACE_TINT, BORDER, 1, 8));
        return button;
    }

    private LinearLayout contentPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(8));
        panel.setBackground(roundedBackground(SURFACE, BORDER, 1, 8));
        return panel;
    }

    private void styleSectionTitle(TextView textView) {
        textView.setTextSize(16);
        textView.setTextColor(TEXT_PRIMARY);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private void styleInput(EditText editText) {
        editText.setTextColor(TEXT_PRIMARY);
        editText.setHintTextColor(Color.rgb(143, 137, 132));
        editText.setTextSize(15);
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        editText.setBackground(roundedBackground(Color.rgb(253, 252, 250), Color.rgb(218, 210, 204), 1, 8));
    }

    private void stylePrimaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setMinHeight(dp(48));
        button.setBackground(roundedBackground(ACCENT, ACCENT_DARK, 1, 8));
    }

    private void styleSecondaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(INDIGO);
        button.setTextSize(15);
        button.setMinHeight(dp(44));
        button.setBackground(roundedBackground(Color.rgb(244, 247, 248), Color.rgb(204, 212, 219), 1, 8));
    }

    private GradientDrawable roundedBackground(int color, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private void showNavigationMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.action_retry_failed));
        menu.getMenu().add(0, 2, 1, getString(R.string.action_try_next_source));
        menu.getMenu().add(0, 5, 2, getString(R.string.action_source_candidates));
        menu.getMenu().add(0, 3, 3, getString(R.string.action_clear_finished));
        menu.getMenu().add(0, 4, 4, getString(R.string.action_export_logs));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                retryLatestFailed();
                return true;
            }
            if (item.getItemId() == 2) {
                retryNextSource();
                return true;
            }
            if (item.getItemId() == 3) {
                clearFinishedTasks();
                return true;
            }
            if (item.getItemId() == 4) {
                exportLogs();
                return true;
            }
            if (item.getItemId() == 5) {
                toggleSourcePanel();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.action_refresh));
        menu.getMenu().add(0, 2, 1, getString(R.string.action_cancel));
        LanguageSettings.Option[] options = LanguageSettings.options();
        for (int i = 0; i < options.length; i++) {
            menu.getMenu().add(1, 1000 + i, 10 + i, getString(options[i].labelResId));
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                refreshStatus();
                return true;
            }
            if (item.getItemId() == 2) {
                startService(DownloadService.cancelIntent(this));
                statusText.setText(getString(R.string.status_cancelling));
                return true;
            }
            int languageIndex = item.getItemId() - 1000;
            if (languageIndex >= 0 && languageIndex < options.length) {
                String selected = options[languageIndex].code;
                if (!selected.equals(LanguageSettings.current(this))) {
                    LanguageSettings.set(this, selected);
                    Toast.makeText(this, getString(R.string.toast_language_changed), Toast.LENGTH_SHORT).show();
                    recreate();
                }
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void startDownload() {
        startDownload(false);
    }

    private void startDownload(boolean playAfterThreshold) {
        BrowserRequestContext requestContext = parseBrowserRequestContext(urlInput.getText().toString());
        List<String> urls = requestContext.urls;
        if (urls.isEmpty()) {
            String query = urlInput.getText().toString().trim();
            if (!VideoSearchResolver.looksLikeSearchText(query)) {
                Toast.makeText(this, getString(R.string.toast_enter_url_or_search), Toast.LENGTH_SHORT).show();
                return;
            }
            String fileName = FileNames.sanitize(fileNameInput.getText().toString());
            if (fileName.isEmpty()) {
                fileName = FileNames.sanitize(query) + ".mp4";
            }
            startDownloaderService(DownloadService.startIntent(this, VideoSearchResolver.searchUri(query), fileName, "", "", "{}", playAfterThreshold));
            statusText.setText(queueLine(fileName, 0L, -1L));
            return;
        }

        String requestedName = fileNameInput.getText().toString();
        int queued = 0;
        String firstName = "";
        StringBuilder queuedPreview = new StringBuilder();
        for (String rawUrl : urls) {
            Uri uri = Uri.parse(rawUrl);
            String fileName = urls.size() == 1 ? FileNames.choose(uri, requestedName) : FileNames.choose(uri, "");
            if (queued == 0) {
                firstName = fileName;
            }
            if (queuedPreview.length() > 0) {
                queuedPreview.append('\n');
            }
            queuedPreview.append(queueLine(fileName, 0L, -1L));
            startDownloaderService(DownloadService.startIntent(this, rawUrl, fileName, requestContext.referer, requestContext.cookieHeader, requestContext.headersJson, playAfterThreshold));
            queued++;
        }

        if (urls.size() == 1) {
            fileNameInput.setText(firstName);
        } else {
            fileNameInput.setText("");
        }
        statusText.setText(queuedPreview.toString());
    }

    private BrowserRequestContext parseBrowserRequestContext(String text) {
        String raw = text == null ? "" : text;
        String referer = "";
        String cookieHeader = "";
        JSONObject headers = new JSONObject();
        StringBuilder urlText = new StringBuilder();
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            Matcher header = HEADER_LINE.matcher(line);
            if (header.matches()) {
                String name = canonicalHeaderName(header.group(1));
                String value = header.group(2).trim();
                if (isRefererHeader(name)) {
                    referer = value;
                } else if ("Cookie".equals(name)) {
                    cookieHeader = value;
                } else if (isAllowedRequestHeader(name)) {
                    putHeader(headers, name, value);
                }
                continue;
            }
            urlText.append(line).append('\n');
        }
        Matcher curlHeader = CURL_HEADER.matcher(raw);
        while (curlHeader.find()) {
            String name = canonicalHeaderName(curlHeader.group(1));
            String value = curlHeader.group(2).trim();
            if (isRefererHeader(name)) {
                referer = value;
            } else if ("Cookie".equals(name)) {
                cookieHeader = value;
            } else if (isAllowedRequestHeader(name)) {
                putHeader(headers, name, value);
            }
        }
        List<String> urls = extractCurlTargetUrls(raw);
        if (urls.isEmpty()) {
            urls = extractUrls(removeCurlHeaderArguments(urlText.toString()));
        }
        if (urls.isEmpty()) {
            urls = extractUrls(removeCurlHeaderArguments(raw));
        }
        return new BrowserRequestContext(urls, firstUrl(referer), cookieHeader, headers.toString());
    }

    private List<String> extractCurlTargetUrls(String text) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = CURL_URL.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String url = trimTrailingPunctuation(matcher.group(1));
            if (!url.isEmpty() && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private String removeCurlHeaderArguments(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return CURL_HEADER.matcher(text).replaceAll(" ");
    }

    private boolean isAllowedRequestHeader(String name) {
        return "User-Agent".equals(name)
                || "Accept".equals(name)
                || "Accept-Language".equals(name)
                || "Accept-Encoding".equals(name)
                || "Authorization".equals(name)
                || "Cache-Control".equals(name)
                || "DNT".equals(name)
                || "Pragma".equals(name)
                || "Priority".equals(name)
                || "X-Requested-With".equals(name)
                || "Sec-Fetch-Site".equals(name)
                || "Sec-Fetch-Mode".equals(name)
                || "Sec-Fetch-Dest".equals(name)
                || "Sec-Fetch-User".equals(name);
    }

    private boolean isRefererHeader(String name) {
        return "Referer".equals(name) || "Referrer".equals(name);
    }

    private String canonicalHeaderName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.US);
        if ("user-agent".equals(name)) return "User-Agent";
        if ("accept".equals(name)) return "Accept";
        if ("accept-language".equals(name)) return "Accept-Language";
        if ("accept-encoding".equals(name)) return "Accept-Encoding";
        if ("authorization".equals(name)) return "Authorization";
        if ("cache-control".equals(name)) return "Cache-Control";
        if ("cookie".equals(name)) return "Cookie";
        if ("dnt".equals(name)) return "DNT";
        if ("pragma".equals(name)) return "Pragma";
        if ("priority".equals(name)) return "Priority";
        if ("referer".equals(name)) return "Referer";
        if ("referrer".equals(name)) return "Referrer";
        if ("x-requested-with".equals(name)) return "X-Requested-With";
        if ("sec-fetch-site".equals(name)) return "Sec-Fetch-Site";
        if ("sec-fetch-mode".equals(name)) return "Sec-Fetch-Mode";
        if ("sec-fetch-dest".equals(name)) return "Sec-Fetch-Dest";
        if ("sec-fetch-user".equals(name)) return "Sec-Fetch-User";
        return rawName == null ? "" : rawName.trim();
    }

    private void putHeader(JSONObject headers, String name, String value) {
        try {
            if (value != null && !value.trim().isEmpty()) {
                String cleaned = sanitizeRequestHeaderValue(name, value);
                if (!cleaned.isEmpty()) {
                    headers.put(name, cleaned);
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed pasted header values.
        }
    }

    private String sanitizeRequestHeaderValue(String name, String value) {
        String cleaned = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        if ("Accept-Encoding".equals(name)) {
            return cleaned.equalsIgnoreCase("identity") ? "identity" : "";
        }
        return cleaned;
    }

    private String firstUrl(String value) {
        Matcher matcher = HTTP_URL.matcher(value == null ? "" : value);
        return matcher.find() ? trimTrailingPunctuation(matcher.group()) : "";
    }

    private List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = HTTP_URL.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String url = trimTrailingPunctuation(matcher.group());
            if (!url.isEmpty() && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private String trimTrailingPunctuation(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        while (value.endsWith(".") || value.endsWith(",") || value.endsWith(";") || value.endsWith(")") || value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
    private void retryLatestFailed() {
        org.json.JSONObject task = taskStore.retryLatestFailedOrCancelled();
        if (task == null) {
            Toast.makeText(this, getString(R.string.toast_no_retry_task), Toast.LENGTH_SHORT).show();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        refreshStatus();
    }

    private void retryNextSource() {
        org.json.JSONObject task = taskStore.retryNextCandidate();
        if (task == null) {
            Toast.makeText(this, getString(R.string.toast_no_alternate_source), Toast.LENGTH_SHORT).show();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        refreshStatus();
    }

    private void retrySelectedSource() {
        if (sourceOptions.isEmpty() || sourceSpinner == null) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        int position = sourceSpinner.getSelectedItemPosition();
        if (position < 0 || position >= sourceOptions.size()) {
            Toast.makeText(this, getString(R.string.toast_select_source_first), Toast.LENGTH_SHORT).show();
            return;
        }
        TaskStore.CandidateOption option = sourceOptions.get(position);
        org.json.JSONObject task = taskStore.retryCandidate(option.taskId, option.url);
        if (task == null) {
            Toast.makeText(this, getString(R.string.toast_selected_source_unavailable), Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        refreshCandidateOptions();
        refreshStatus();
    }

    private void toggleSourcePanel() {
        sourcePanelVisible = !sourcePanelVisible;
        if (sourcePanel != null) {
            sourcePanel.setVisibility(sourcePanelVisible ? View.VISIBLE : View.GONE);
        }
        refreshCandidateOptions();
    }

    private void clearFinishedTasks() {
        int cleared = taskStore.clearFinishedTasks();
        Toast.makeText(this, getString(R.string.toast_cleared_finished, cleared), Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void exportLogs() {
        EventLog eventLog = new EventLog(this);
        eventLog.write("log_export", "", "manual export requested");
        try {
            String exported = OutputExporter.exportToPublicDownloads(this, eventLog.file());
            Toast.makeText(this, getString(R.string.toast_logs_exported), Toast.LENGTH_SHORT).show();
            statusText.setText(getString(R.string.status_logs_exported, exported, eventLog.path()));
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            Toast.makeText(this, getString(R.string.toast_log_export_failed), Toast.LENGTH_SHORT).show();
            statusText.setText(getString(R.string.status_log_export_failed, message, eventLog.path()));
        }
    }

    private void playSelectedCompletedVideo() {
        CompletedVideo selected = selectedCompletedVideo();
        if (selected == null || !selected.exists(this)) {
            Toast.makeText(this, getString(R.string.toast_no_completed_video), Toast.LENGTH_SHORT).show();
            refreshCompletedVideos();
            return;
        }
        Uri uri = selected.uri == null
                ? PartialFileProvider.contentUriFor(this, selected.file, selected.name)
                : selected.uri;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType(selected.name));
        intent.setClipData(ClipData.newUri(getContentResolver(), selected.name, uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_playback_unavailable), Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownloaderService(Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void hydrateSharedText(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            urlInput.setText(text.toString().trim());
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return String.format(Locale.US, "%.1f KiB", kib);
        }
        return String.format(Locale.US, "%.1f MiB", kib / 1024.0);
    }

    private String queueLine(String fileName, long downloaded, long total) {
        if (total > 0L) {
            return fileName + "  " + String.format(Locale.US, "%.1f%%", downloaded * 100.0 / total);
        }
        if (downloaded > 0L) {
            return fileName + "  " + formatBytes(downloaded);
        }
        return fileName + "  0%";
    }

    private void refreshCompletedVideos() {
        if (completedVideoList == null) {
            return;
        }
        completedVideos = latestCompletedVideos();
        if (selectedCompletedVideoIndex >= completedVideos.size()) {
            selectedCompletedVideoIndex = Math.max(0, completedVideos.size() - 1);
        }
        if (selectedCompletedVideoIndex < 0) {
            selectedCompletedVideoIndex = 0;
        }
        completedVideoList.removeAllViews();
        if (completedVideos.isEmpty()) {
            completedVideoList.addView(completedVideoRow(getString(R.string.completed_video_none), false, null), matchWrap());
            return;
        }
        completedVideoList.addView(videoSelectButton("\u25b2", selectedCompletedVideoIndex > 0, view -> {
            if (selectedCompletedVideoIndex > 0) {
                selectedCompletedVideoIndex--;
                refreshCompletedVideos();
            }
        }), matchWrap());
        int start = Math.max(0, Math.min(selectedCompletedVideoIndex - 1, completedVideos.size() - 3));
        int end = Math.min(completedVideos.size(), start + 3);
        for (int i = start; i < end; i++) {
            final int index = i;
            completedVideoList.addView(
                    completedVideoRow(completedVideos.get(i).label, i == selectedCompletedVideoIndex, view -> {
                        selectedCompletedVideoIndex = index;
                        refreshCompletedVideos();
                    }),
                    matchWrap());
        }
        completedVideoList.addView(videoSelectButton("\u25bc", selectedCompletedVideoIndex < completedVideos.size() - 1, view -> {
            if (selectedCompletedVideoIndex < completedVideos.size() - 1) {
                selectedCompletedVideoIndex++;
                refreshCompletedVideos();
            }
        }), matchWrap());
    }

    private CompletedVideo selectedCompletedVideo() {
        if (completedVideos.isEmpty() || selectedCompletedVideoIndex < 0 || selectedCompletedVideoIndex >= completedVideos.size()) {
            return null;
        }
        return completedVideos.get(selectedCompletedVideoIndex);
    }

    private TextView completedVideoRow(String text, boolean selected, View.OnClickListener listener) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextSize(14);
        row.setSingleLine(true);
        row.setTextColor(selected ? Color.WHITE : TEXT_PRIMARY);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(roundedBackground(selected ? INDIGO : Color.rgb(253, 252, 250), selected ? INDIGO : BORDER, 1, 8));
        if (listener != null) {
            row.setOnClickListener(listener);
        }
        return row;
    }

    private Button videoSelectButton(String text, boolean enabled, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setSingleLine(true);
        styleSecondaryButton(button);
        button.setEnabled(enabled);
        button.setOnClickListener(listener);
        return button;
    }

    private List<CompletedVideo> latestCompletedVideos() {
        List<CompletedVideo> videos = new ArrayList<>();
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null || !dir.isDirectory()) {
            dir = getFilesDir();
        }
        addCompletedVideos(videos, dir);
        if (Build.VERSION.SDK_INT >= 29) {
            addCompletedVideosFromMediaStore(videos);
        } else {
            File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AI Test Downloader");
            addCompletedVideos(videos, publicDir);
        }
        videos.sort((left, right) -> Long.compare(right.updatedAt, left.updatedAt));
        return labeledCompletedVideos(videos);
    }

    private void addCompletedVideos(List<CompletedVideo> videos, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles(file -> file != null
                && file.isFile()
                && file.length() > 0L
                && !file.getName().endsWith(".part")
                && isPlayableVideo(file.getName()));
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        for (File file : files) {
            if (!containsCompletedVideo(videos, file)) {
                videos.add(CompletedVideo.fromFile(file, ""));
            }
        }
    }

    private void addCompletedVideosFromMediaStore(List<CompletedVideo> videos) {
        String[] projection = new String[]{
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.RELATIVE_PATH
        };
        String selection = MediaStore.Downloads.RELATIVE_PATH + "=? AND " + MediaStore.Downloads.SIZE + ">0";
        String[] selectionArgs = new String[]{Environment.DIRECTORY_DOWNLOADS + "/AI Test Downloader/"};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                if (!isPlayableVideo(name)) {
                    continue;
                }
                Uri uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(cursor.getLong(idColumn)));
                if (canOpenVideoUri(uri) && !containsCompletedVideo(videos, uri)) {
                    videos.add(CompletedVideo.fromUri(uri, name, cursor.getLong(modifiedColumn) * 1000L, ""));
                }
            }
        } catch (Exception ignored) {
            // MediaStore visibility varies by Android version and permissions; private files remain available.
        }
    }

    private List<CompletedVideo> labeledCompletedVideos(List<CompletedVideo> videos) {
        List<CompletedVideo> labeled = new ArrayList<>();
        for (int i = 0; i < videos.size(); i++) {
            CompletedVideo video = videos.get(i);
            labeled.add(video.withLabel(displayVideoName(video.name)));
        }
        return labeled;
    }

    private String displayVideoName(String name) {
        String value = name == null ? "" : name.trim();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }
        int dot = value.lastIndexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot);
        }
        return value.isEmpty() ? "video" : value;
    }

    private boolean canOpenVideoUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        try (android.os.ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
            return descriptor != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean containsCompletedVideo(List<CompletedVideo> videos, File file) {
        String path = file.getAbsolutePath();
        for (CompletedVideo video : videos) {
            if (video.file != null && path.equals(video.file.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCompletedVideo(List<CompletedVideo> videos, Uri uri) {
        String value = uri == null ? "" : uri.toString();
        for (CompletedVideo video : videos) {
            if (video.uri != null && value.equals(video.uri.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlayableVideo(String name) {
        String lowered = name == null ? "" : name.toLowerCase(Locale.US);
        return lowered.endsWith(".mp4")
                || lowered.endsWith(".m4v")
                || lowered.endsWith(".webm")
                || lowered.endsWith(".ts")
                || lowered.endsWith(".mkv")
                || lowered.endsWith(".mov");
    }

    private String mimeType(String name) {
        String lowered = name == null ? "" : name.toLowerCase(Locale.US);
        if (lowered.endsWith(".mp4") || lowered.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (lowered.endsWith(".webm")) {
            return "video/webm";
        }
        if (lowered.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (lowered.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (lowered.endsWith(".mov")) {
            return "video/quicktime";
        }
        return "video/*";
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshStatus() {
        statusText.setText(taskStore.summary());
        refreshCompletedVideos();
        refreshCandidateOptions();
    }

    private void refreshCandidateOptions() {
        if (sourceSpinner == null) {
            return;
        }
        sourceOptions = taskStore.latestCandidateOptions();
        int selectedIndex = selectedSourceIndex();
        List<TaskStore.CandidateOption> adapterItems = sourceOptions;
        if (adapterItems.isEmpty()) {
            adapterItems = new ArrayList<>();
            adapterItems.add(new TaskStore.CandidateOption("", -1, "", getString(R.string.source_none)));
            selectedSourceUrl = "";
        }
        ArrayAdapter<TaskStore.CandidateOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                adapterItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(adapter);
        if (!sourceOptions.isEmpty()) {
            sourceSpinner.setSelection(selectedIndex, false);
            selectedSourceUrl = sourceOptions.get(selectedIndex).url;
        }
        selectedSourceButton.setEnabled(!sourceOptions.isEmpty());
        copySourceButton.setEnabled(!sourceOptions.isEmpty());
        copyAllSourcesButton.setEnabled(!sourceOptions.isEmpty());
        shareAllSourcesButton.setEnabled(!sourceOptions.isEmpty());
        shareSourceButton.setEnabled(!sourceOptions.isEmpty());
        copySourceCurlButton.setEnabled(!sourceOptions.isEmpty());
        shareSourceCurlButton.setEnabled(!sourceOptions.isEmpty());
        openSourceButton.setEnabled(!sourceOptions.isEmpty());
        openRefererButton.setEnabled(false);
        updateSourceDetail();
    }

    private int selectedSourceIndex() {
        if (sourceOptions.isEmpty()) {
            return 0;
        }
        if (selectedSourceUrl == null || selectedSourceUrl.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < sourceOptions.size(); i++) {
            if (selectedSourceUrl.equals(sourceOptions.get(i).url)) {
                return i;
            }
        }
        return 0;
    }

    private TaskStore.CandidateOption selectedSourceOption() {
        if (sourceOptions.isEmpty() || sourceSpinner == null) {
            return null;
        }
        int position = sourceSpinner.getSelectedItemPosition();
        if (position < 0 || position >= sourceOptions.size()) {
            position = 0;
        }
        return sourceOptions.get(position);
    }

    private void updateSourceDetail() {
        if (sourceDetailText == null || sourceSpinner == null) {
            return;
        }
        if (sourceOptions.isEmpty()) {
            sourceDetailText.setText(getString(R.string.source_detail_empty));
            openRefererButton.setEnabled(false);
            return;
        }
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null) {
            sourceDetailText.setText(getString(R.string.source_detail_empty));
            openRefererButton.setEnabled(false);
            return;
        }
        if (option.referer == null || option.referer.trim().isEmpty()) {
            sourceDetailText.setText(sourceDetailText(option));
            openRefererButton.setEnabled(false);
        } else {
            sourceDetailText.setText(sourceDetailText(option));
            openRefererButton.setEnabled(true);
        }
    }

    private String sourceDetailText(TaskStore.CandidateOption option) {
        if (option == null) {
            return getString(R.string.source_detail_empty);
        }
        return option.referer == null || option.referer.trim().isEmpty()
                ? getString(R.string.source_detail_format, option.url)
                : getString(R.string.source_detail_with_referer_format, option.url, option.referer);
    }

    private void copySelectedSourceDetail() {
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        String text = sourceDetailText(option);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.section_source_candidates), text));
            Toast.makeText(this, getString(R.string.toast_source_detail_copied), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyAllSourceDetails() {
        if (sourceOptions.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.section_source_candidates), allSourceDetailsText()));
            Toast.makeText(this, getString(R.string.toast_all_source_details_copied, sourceOptions.size()), Toast.LENGTH_SHORT).show();
        }
    }

    private String allSourceDetailsText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sourceOptions.size(); i++) {
            TaskStore.CandidateOption option = sourceOptions.get(i);
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(i + 1).append(". ").append(option.label).append('\n');
            builder.append(sourceDetailText(option));
        }
        return builder.toString();
    }

    private void shareAllSourceDetails() {
        if (sourceOptions.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, allSourceDetailsText());
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.action_share_all_source_details)));
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_all_source_details_share_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSelectedSourceDetail() {
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sourceDetailText(option));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.action_share_source_detail)));
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_source_share_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void copySelectedSourceCurl() {
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.action_copy_source_curl), sourceCurlText(option)));
            Toast.makeText(this, getString(R.string.toast_source_curl_copied), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSelectedSourceCurl() {
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sourceCurlText(option));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.action_share_source_curl)));
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_source_curl_share_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private String sourceCurlText(TaskStore.CandidateOption option) {
        StringBuilder builder = new StringBuilder();
        builder.append("curl -L \"").append(escapeCurlQuote(option.url)).append("\"");
        if (option.referer != null && !option.referer.trim().isEmpty()) {
            builder.append(" -H \"Referer: ").append(escapeCurlQuote(option.referer.trim())).append("\"");
        }
        return builder.toString();
    }

    private String escapeCurlQuote(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void openSelectedSourceInBrowser() {
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null) {
            Toast.makeText(this, getString(R.string.toast_no_source_candidate), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(option.url));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_source_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSelectedRefererInBrowser() {
        TaskStore.CandidateOption option = selectedSourceOption();
        if (option == null || option.referer == null || option.referer.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_referer_available), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(option.referer));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_referer_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23
                && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
        }
    }

    private void requestMediaVideoPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, 1003);
        }
    }

    private static final class CompletedVideo {
        final File file;
        final Uri uri;
        final String name;
        final long updatedAt;
        final String label;

        private CompletedVideo(File file, Uri uri, String name, long updatedAt, String label) {
            this.file = file;
            this.uri = uri;
            this.name = name == null || name.isEmpty() ? "video.mp4" : name;
            this.updatedAt = updatedAt;
            this.label = label;
        }

        static CompletedVideo fromFile(File file, String label) {
            String name = file == null ? "video.mp4" : file.getName();
            long updatedAt = file == null ? 0L : file.lastModified();
            return new CompletedVideo(file, null, name, updatedAt, label);
        }

        static CompletedVideo fromUri(Uri uri, String name, long updatedAt, String label) {
            return new CompletedVideo(null, uri, name, updatedAt, label);
        }

        CompletedVideo withLabel(String label) {
            return new CompletedVideo(file, uri, name, updatedAt, label);
        }

        boolean exists(Context context) {
            if (file != null) {
                return file.exists() && file.isFile() && file.length() > 0L;
            }
            if (uri == null || context == null) {
                return false;
            }
            try (android.os.ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
                return descriptor != null;
            } catch (Exception ignored) {
                return false;
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
