package tw.wajay.aitestdownloader;

import android.app.Activity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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
    private Spinner sourceSpinner;
    private TextView statusText;
    private TaskStore taskStore;
    private List<TaskStore.CandidateOption> sourceOptions = new ArrayList<>();

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
        setContentView(createContentView());
        hydrateSharedText(getIntent());
        requestNotificationPermission();
        requestLegacyStoragePermission();
        refreshStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        hydrateSharedText(intent);
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

        sourceSpinner = new Spinner(this);
        queueCard.addView(sourceSpinner, matchWrap());

        Button selectedSourceButton = new Button(this);
        selectedSourceButton.setText(getString(R.string.action_queue_selected_source));
        styleSecondaryButton(selectedSourceButton);
        selectedSourceButton.setOnClickListener(view -> retrySelectedSource());
        queueCard.addView(selectedSourceButton, matchWrap());

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
        menu.getMenu().add(0, 3, 2, getString(R.string.action_clear_finished));
        menu.getMenu().add(0, 4, 3, getString(R.string.action_export_logs));
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
            startDownloaderService(DownloadService.startIntent(this, VideoSearchResolver.searchUri(query), fileName, "", "", "{}"));
            statusText.setText(getString(R.string.status_search_queued, query));
            return;
        }

        String requestedName = fileNameInput.getText().toString();
        int queued = 0;
        String firstName = "";
        for (String rawUrl : urls) {
            Uri uri = Uri.parse(rawUrl);
            String fileName = urls.size() == 1 ? FileNames.choose(uri, requestedName) : FileNames.choose(uri, "");
            if (queued == 0) {
                firstName = fileName;
            }
            startDownloaderService(DownloadService.startIntent(this, rawUrl, fileName, requestContext.referer, requestContext.cookieHeader, requestContext.headersJson));
            queued++;
        }

        if (urls.size() == 1) {
            fileNameInput.setText(firstName);
            statusText.setText(getString(R.string.status_download_queued, firstName));
        } else {
            fileNameInput.setText("");
            statusText.setText(getString(R.string.status_queued_downloads, queued));
        }
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
        statusText.setText(getString(R.string.status_retry_queued, task.optString("fileName", "download.bin")));
    }

    private void retryNextSource() {
        org.json.JSONObject task = taskStore.retryNextCandidate();
        if (task == null) {
            Toast.makeText(this, getString(R.string.toast_no_alternate_source), Toast.LENGTH_SHORT).show();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        statusText.setText(getString(R.string.status_alternate_source_queued, task.optString("fileName", "download.bin"), task.optString("url", "")));
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
        statusText.setText(getString(R.string.status_selected_source_queued, task.optString("fileName", "download.bin"), option.url));
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
        refreshCandidateOptions();
    }

    private void refreshCandidateOptions() {
        if (sourceSpinner == null) {
            return;
        }
        sourceOptions = taskStore.latestCandidateOptions();
        List<TaskStore.CandidateOption> adapterItems = sourceOptions;
        if (adapterItems.isEmpty()) {
            adapterItems = new ArrayList<>();
            adapterItems.add(new TaskStore.CandidateOption("", -1, "", getString(R.string.source_none)));
        }
        ArrayAdapter<TaskStore.CandidateOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                adapterItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(adapter);
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
}
