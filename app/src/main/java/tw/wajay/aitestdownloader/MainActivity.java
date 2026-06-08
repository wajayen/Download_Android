package tw.wajay.aitestdownloader;

import android.app.Activity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private static final int TEAL = Color.rgb(11, 107, 107);
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_LINE = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9-]*)\\s*:\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURL_HEADER = Pattern.compile("-H\\s+['\"]([A-Za-z][A-Za-z0-9-]*)\\s*:\\s*([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURL_URL = Pattern.compile("(?:^|\\s)(?:curl|--url)\\s+(?:--location\\s+|--request\\s+\\S+\\s+|--compressed\\s+)*['\"]?(https?://[^\\s'\"<>]+)['\"]?", Pattern.CASE_INSENSITIVE);
    private EditText urlInput;
    private EditText fileNameInput;
    private Spinner languageSpinner;
    private Spinner sourceSpinner;
    private TextView statusText;
    private TaskStore taskStore;
    private List<TaskStore.CandidateOption> sourceOptions = new ArrayList<>();
    private boolean languageSpinnerReady = false;

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
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(247, 248, 250));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(28);
        title.setTextColor(Color.rgb(28, 35, 42));
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.build_ready));
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(88, 96, 105));
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle, matchWrap());

        TextView languageLabel = new TextView(this);
        languageLabel.setText(getString(R.string.label_language));
        languageLabel.setTextSize(14);
        languageLabel.setTextColor(Color.rgb(48, 56, 64));
        root.addView(languageLabel, matchWrap());

        languageSpinner = new Spinner(this);
        configureLanguageSpinner();
        root.addView(languageSpinner, matchWrap());

        urlInput = new EditText(this);
        urlInput.setHint(getString(R.string.hint_url));
        urlInput.setSingleLine(false);
        urlInput.setMinLines(3);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(urlInput, matchWrap());

        fileNameInput = new EditText(this);
        fileNameInput.setHint(getString(R.string.hint_file_name));
        fileNameInput.setSingleLine(true);
        fileNameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        fileNameInput.setPadding(fileNameInput.getPaddingLeft(), dp(12), fileNameInput.getPaddingRight(), dp(12));
        root.addView(fileNameInput, matchWrap());

        Button downloadButton = new Button(this);
        downloadButton.setText(getString(R.string.action_download));
        downloadButton.setTextColor(Color.WHITE);
        downloadButton.setBackgroundColor(TEAL);
        downloadButton.setOnClickListener(view -> startDownload());
        root.addView(downloadButton, matchWrap());

        Button cancelButton = new Button(this);
        cancelButton.setText(getString(R.string.action_cancel));
        cancelButton.setOnClickListener(view -> {
            startService(DownloadService.cancelIntent(this));
            statusText.setText(getString(R.string.status_cancelling));
        });
        root.addView(cancelButton, matchWrap());

        Button refreshButton = new Button(this);
        refreshButton.setText(getString(R.string.action_refresh));
        refreshButton.setOnClickListener(view -> refreshStatus());
        root.addView(refreshButton, matchWrap());

        Button retryButton = new Button(this);
        retryButton.setText(getString(R.string.action_retry_failed));
        retryButton.setOnClickListener(view -> retryLatestFailed());
        root.addView(retryButton, matchWrap());

        Button nextSourceButton = new Button(this);
        nextSourceButton.setText(getString(R.string.action_try_next_source));
        nextSourceButton.setOnClickListener(view -> retryNextSource());
        root.addView(nextSourceButton, matchWrap());

        sourceSpinner = new Spinner(this);
        root.addView(sourceSpinner, matchWrap());

        Button selectedSourceButton = new Button(this);
        selectedSourceButton.setText(getString(R.string.action_queue_selected_source));
        selectedSourceButton.setOnClickListener(view -> retrySelectedSource());
        root.addView(selectedSourceButton, matchWrap());

        Button clearButton = new Button(this);
        clearButton.setText(getString(R.string.action_clear_finished));
        clearButton.setOnClickListener(view -> clearFinishedTasks());
        root.addView(clearButton, matchWrap());

        Button exportLogsButton = new Button(this);
        exportLogsButton.setText(getString(R.string.action_export_logs));
        exportLogsButton.setOnClickListener(view -> exportLogs());
        root.addView(exportLogsButton, matchWrap());

        statusText = new TextView(this);
        statusText.setText(getString(R.string.status_idle));
        statusText.setTextSize(15);
        statusText.setTextColor(Color.rgb(48, 56, 64));
        statusText.setPadding(0, dp(18), 0, 0);
        root.addView(statusText, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void configureLanguageSpinner() {
        LanguageSettings.Option[] options = LanguageSettings.options();
        List<String> labels = new ArrayList<>();
        for (LanguageSettings.Option option : options) {
            labels.add(getString(option.labelResId));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        languageSpinner.setSelection(LanguageSettings.selectedIndex(this));
        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!languageSpinnerReady) {
                    languageSpinnerReady = true;
                    return;
                }
                if (position < 0 || position >= options.length) {
                    return;
                }
                String selected = options[position].code;
                if (selected.equals(LanguageSettings.current(MainActivity.this))) {
                    return;
                }
                LanguageSettings.set(MainActivity.this, selected);
                Toast.makeText(MainActivity.this, getString(R.string.toast_language_changed), Toast.LENGTH_SHORT).show();
                recreate();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void startDownload() {
        BrowserRequestContext requestContext = parseBrowserRequestContext(urlInput.getText().toString());
        List<String> urls = requestContext.urls;
        if (urls.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_enter_url), Toast.LENGTH_SHORT).show();
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
                || "Authorization".equals(name)
                || "X-Requested-With".equals(name)
                || "Sec-Fetch-Site".equals(name)
                || "Sec-Fetch-Mode".equals(name)
                || "Sec-Fetch-Dest".equals(name);
    }

    private boolean isRefererHeader(String name) {
        return "Referer".equals(name) || "Referrer".equals(name);
    }

    private String canonicalHeaderName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.US);
        if ("user-agent".equals(name)) return "User-Agent";
        if ("accept".equals(name)) return "Accept";
        if ("accept-language".equals(name)) return "Accept-Language";
        if ("authorization".equals(name)) return "Authorization";
        if ("cookie".equals(name)) return "Cookie";
        if ("referer".equals(name)) return "Referer";
        if ("referrer".equals(name)) return "Referrer";
        if ("x-requested-with".equals(name)) return "X-Requested-With";
        if ("sec-fetch-site".equals(name)) return "Sec-Fetch-Site";
        if ("sec-fetch-mode".equals(name)) return "Sec-Fetch-Mode";
        if ("sec-fetch-dest".equals(name)) return "Sec-Fetch-Dest";
        return rawName == null ? "" : rawName.trim();
    }

    private void putHeader(JSONObject headers, String name, String value) {
        try {
            if (value != null && !value.trim().isEmpty()) {
                headers.put(name, value.trim());
            }
        } catch (Exception ignored) {
            // Ignore malformed pasted header values.
        }
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
}
