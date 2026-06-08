package tw.wajay.aitestdownloader;

import android.app.Activity;
import android.Manifest;
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

public final class MainActivity extends Activity {
    private static final int TEAL = Color.rgb(11, 107, 107);
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private EditText urlInput;
    private EditText fileNameInput;
    private Spinner sourceSpinner;
    private TextView statusText;
    private TaskStore taskStore;
    private List<TaskStore.CandidateOption> sourceOptions = new ArrayList<>();

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
            statusText.setText("甇???...");
        });
        root.addView(cancelButton, matchWrap());

        Button refreshButton = new Button(this);
        refreshButton.setText(getString(R.string.action_refresh));
        refreshButton.setOnClickListener(view -> refreshStatus());
        root.addView(refreshButton, matchWrap());

        Button retryButton = new Button(this);
        retryButton.setText("Retry Failed");
        retryButton.setOnClickListener(view -> retryLatestFailed());
        root.addView(retryButton, matchWrap());

        Button nextSourceButton = new Button(this);
        nextSourceButton.setText("Try Next Source");
        nextSourceButton.setOnClickListener(view -> retryNextSource());
        root.addView(nextSourceButton, matchWrap());

        sourceSpinner = new Spinner(this);
        root.addView(sourceSpinner, matchWrap());

        Button selectedSourceButton = new Button(this);
        selectedSourceButton.setText("Queue Selected Source");
        selectedSourceButton.setOnClickListener(view -> retrySelectedSource());
        root.addView(selectedSourceButton, matchWrap());

        Button clearButton = new Button(this);
        clearButton.setText("Clear Finished");
        clearButton.setOnClickListener(view -> clearFinishedTasks());
        root.addView(clearButton, matchWrap());

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

    private void startDownload() {
        List<String> urls = extractUrls(urlInput.getText().toString());
        if (urls.isEmpty()) {
            Toast.makeText(this, "Enter at least one http or https URL", Toast.LENGTH_SHORT).show();
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
            startDownloaderService(DownloadService.startIntent(this, rawUrl, fileName));
            queued++;
        }

        if (urls.size() == 1) {
            fileNameInput.setText(firstName);
            statusText.setText("Download queued\n" + firstName);
        } else {
            fileNameInput.setText("");
            statusText.setText("Queued " + queued + " downloads");
        }
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
            Toast.makeText(this, "No failed or cancelled task to retry", Toast.LENGTH_SHORT).show();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        statusText.setText("Retry queued\n" + task.optString("fileName", "download.bin"));
    }

    private void retryNextSource() {
        org.json.JSONObject task = taskStore.retryNextCandidate();
        if (task == null) {
            Toast.makeText(this, "No alternate source candidate available", Toast.LENGTH_SHORT).show();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        statusText.setText("Alternate source queued\n" + task.optString("fileName", "download.bin") + "\n" + task.optString("url", ""));
    }

    private void retrySelectedSource() {
        if (sourceOptions.isEmpty() || sourceSpinner == null) {
            Toast.makeText(this, "No source candidate available", Toast.LENGTH_SHORT).show();
            return;
        }
        int position = sourceSpinner.getSelectedItemPosition();
        if (position < 0 || position >= sourceOptions.size()) {
            Toast.makeText(this, "Select a source candidate first", Toast.LENGTH_SHORT).show();
            return;
        }
        TaskStore.CandidateOption option = sourceOptions.get(position);
        org.json.JSONObject task = taskStore.retryCandidate(option.taskId, option.url);
        if (task == null) {
            Toast.makeText(this, "Selected source is no longer available", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        startDownloaderService(DownloadService.wakeIntent(this));
        statusText.setText("Selected source queued\n" + task.optString("fileName", "download.bin") + "\n" + option.url);
        refreshCandidateOptions();
    }

    private void clearFinishedTasks() {
        int cleared = taskStore.clearFinishedTasks();
        Toast.makeText(this, "Cleared " + cleared + " finished task(s)", Toast.LENGTH_SHORT).show();
        refreshStatus();
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
            adapterItems.add(new TaskStore.CandidateOption("", -1, "", "No resolved source candidates"));
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
