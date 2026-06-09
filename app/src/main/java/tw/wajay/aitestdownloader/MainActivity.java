package tw.wajay.aitestdownloader;

import android.app.Activity;
import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int REQUEST_DOWNLOAD_DIRECTORY = 2001;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DOWNLOAD_DIRECTORY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
                // Some providers grant access without persistable flags.
            }
            DownloadDirectorySettings.set(this, uri);
            Toast.makeText(this, getString(R.string.toast_download_directory_set), Toast.LENGTH_SHORT).show();
            refreshCompletedVideos();
        }
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

        Button queueButton = new Button(this);
        queueButton.setText(getString(R.string.section_download_queue));
        styleSecondaryButton(queueButton);
        queueButton.setOnClickListener(view -> showDownloadQueueDialog());
        root.addView(queueButton, matchWrap());

        statusText = new TextView(this);
        statusText.setText(getString(R.string.status_idle));
        statusText.setTextSize(15);
        statusText.setTextColor(TEXT_SECONDARY);
        statusText.setMinLines(6);
        statusText.setGravity(Gravity.TOP | Gravity.START);
        statusText.setLineSpacing(dp(2), 1.0f);
        statusText.setPadding(dp(2), 0, dp(2), dp(10));

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
        menu.getMenu().add(0, 3, 2, getString(R.string.action_set_download_directory));
        menu.getMenu().add(0, 4, 3, getString(R.string.action_about));
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
            if (item.getItemId() == 3) {
                openDownloadDirectoryPicker();
                return true;
            }
            if (item.getItemId() == 4) {
                showAboutDialog();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.action_about))
                .setMessage(getString(R.string.about_message, getString(R.string.app_name), BuildConfig.VERSION_NAME, BuildConfig.BUILD_DATE))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showDownloadQueueDialog() {
        CharSequence message = statusText == null ? "" : statusText.getText();
        if (message == null || message.toString().trim().isEmpty()) {
            message = getString(R.string.status_idle);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.section_download_queue))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void openDownloadDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_DOWNLOAD_DIRECTORY);
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.toast_download_directory_picker_failed), Toast.LENGTH_SHORT).show();
        }
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
            showSearchResults(query, fileName, playAfterThreshold);
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

    private void showSearchResults(String query, String fileName, boolean playAfterThreshold) {
        statusText.setText(getString(R.string.status_searching_results));
        new Thread(() -> {
            try {
                List<VideoSearchResolver.Result> results = VideoSearchResolver.search(query);
                runOnUiThread(() -> showSearchResultDialog(query, fileName, playAfterThreshold, results));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.toast_search_failed), Toast.LENGTH_SHORT).show();
                    statusText.setText(getString(R.string.status_idle));
                });
            }
        }, "video-search-ui").start();
    }

    private void showSearchResultDialog(
            String query,
            String fileName,
            boolean playAfterThreshold,
            List<VideoSearchResolver.Result> results) {
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_search_results), Toast.LENGTH_SHORT).show();
            statusText.setText(getString(R.string.status_idle));
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(4));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        final AlertDialog[] holder = new AlertDialog[1];
        for (VideoSearchResolver.Result result : results) {
            list.addView(searchResultRow(result, holder, fileName, playAfterThreshold), matchWrap());
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.section_search_results))
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        holder[0] = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (statusText != null && getString(R.string.status_searching_results).contentEquals(statusText.getText())) {
                statusText.setText(taskStore.summary());
            }
        });
        dialog.show();
    }

    private View searchResultRow(
            VideoSearchResolver.Result result,
            AlertDialog[] holder,
            String fileName,
            boolean playAfterThreshold) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(roundedBackground(Color.rgb(253, 252, 250), BORDER, 1, 8));

        ImageView thumbnail = new ImageView(this);
        thumbnail.setContentDescription(getString(R.string.search_result_thumbnail_description));
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackground(roundedBackground(SURFACE_TINT, BORDER, 1, 6));
        thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
        row.addView(thumbnail, new LinearLayout.LayoutParams(dp(96), dp(64)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(displaySearchTitle(result));
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(15);
        title.setMaxLines(2);
        textColumn.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView site = new TextView(this);
        site.setText(displaySearchSite(result));
        site.setTextColor(TEXT_SECONDARY);
        site.setTextSize(13);
        site.setMaxLines(1);
        textColumn.addView(site, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f));

        row.setOnClickListener(view -> {
            if (holder[0] != null) {
                holder[0].dismiss();
            }
            queueSelectedSearchResult(result, fileName, playAfterThreshold);
        });
        loadThumbnail(thumbnail, result.thumbnailUrl, result.thumbnailRefererUrl);
        return row;
    }

    private void queueSelectedSearchResult(VideoSearchResolver.Result result, String requestedName, boolean playAfterThreshold) {
        String fileName = FileNames.sanitize(requestedName);
        if (fileName.isEmpty()) {
            fileName = FileNames.sanitize(displaySearchTitle(result)) + ".mp4";
        }
        if (fileName.isEmpty() || ".mp4".equals(fileName)) {
            fileName = "video.mp4";
        }
        startDownloaderService(DownloadService.startIntent(this, result.url, fileName, result.refererUrl, "", "{}", playAfterThreshold));
        fileNameInput.setText(fileName);
        statusText.setText(queueLine(fileName, 0L, -1L));
    }

    private String displaySearchTitle(VideoSearchResolver.Result result) {
        String title = result == null || result.title == null ? "" : result.title.trim();
        if (title.contains(": ")) {
            title = title.substring(title.indexOf(": ") + 2).trim();
        }
        title = title.replaceFirst("(?i)^direct\\s+code\\s*:\\s*", "").trim();
        title = title.replaceFirst("(?i)^embedded\\s+link$", "").trim();
        if (!title.isEmpty() && !"embedded link".equalsIgnoreCase(title)) {
            return title;
        }
        String fallback = result == null ? "" : Uri.parse(result.url).getLastPathSegment();
        fallback = fallback == null ? "" : fallback.trim();
        int dot = fallback.lastIndexOf('.');
        if (dot > 0) {
            fallback = fallback.substring(0, dot);
        }
        return fallback.isEmpty() ? getString(R.string.search_result_no_title) : fallback;
    }

    private String displaySearchSite(VideoSearchResolver.Result result) {
        String site = result == null || result.sourceSite == null ? "" : result.sourceSite.trim();
        if (!site.isEmpty() && !"generic".equals(site)) {
            return displaySourceSiteName(site);
        }
        try {
            String host = Uri.parse(result.url).getHost();
            if (host != null && !host.trim().isEmpty()) {
                return host;
            }
        } catch (Exception ignored) {
            // Fall through to the localized unknown-site label.
        }
        return getString(R.string.search_result_site_unknown);
    }

    private String displaySourceSiteName(String site) {
        String value = site == null ? "" : site.trim().toLowerCase(Locale.US);
        if ("movieffm".equals(value)) return "MovieFFM";
        if ("xiaoyakankan".equals(value)) return "XiaoyaKankan";
        if ("gimy".equals(value)) return "Gimy";
        if ("dramasq".equals(value)) return "DramaSQ";
        if ("olevod".equals(value)) return "Olevod";
        if ("3kor".equals(value)) return "3KOR";
        if ("nnyy".equals(value)) return "NNYY";
        if ("777tv".equals(value)) return "777TV";
        if ("ikanbot".equals(value)) return "Ikanbot";
        if ("yfsp".equals(value)) return "YFSP";
        if ("iqiyi".equals(value)) return "iQIYI";
        if ("avjoy".equals(value)) return "AVJoy";
        if ("avbebe".equals(value)) return "AVBebe";
        if ("bestjavporn".equals(value)) return "BestJavPorn";
        if ("javdock".equals(value)) return "JavDock";
        if ("javfilms".equals(value)) return "JavFilms";
        if ("tinyavideo".equals(value)) return "TinyAVideo";
        if ("goodav17".equals(value)) return "GoodAV17";
        if ("hohoj".equals(value)) return "HoHoJ";
        if ("hayav".equals(value)) return "HayAV";
        if ("ggjav".equals(value)) return "GGJAV";
        if ("tktube".equals(value)) return "TKTube";
        if ("18jav".equals(value)) return "18JAV";
        if ("85xvideo".equals(value)) return "85xVideo";
        return site.trim();
    }

    private void loadThumbnail(ImageView target, String rawUrl, String refererUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) {
            return;
        }
        String referer = refererUrl == null ? "" : refererUrl.trim();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(9000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6");
                if (!referer.isEmpty()) {
                    connection.setRequestProperty("Referer", referer);
                }
                int code = connection.getResponseCode();
                if (code >= 400) {
                    return;
                }
                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        runOnUiThread(() -> target.setImageBitmap(bitmap));
                    }
                }
            } catch (Exception ignored) {
                // Keep the built-in gallery placeholder when a search page has no reachable thumbnail.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "search-thumbnail").start();
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
        LinearLayout picker = new LinearLayout(this);
        picker.setOrientation(LinearLayout.HORIZONTAL);
        picker.setGravity(Gravity.CENTER_VERTICAL);
        completedVideoList.addView(picker, matchWrap());

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        picker.addView(rows, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), 0, 0, 0);
        picker.addView(controls, new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));

        Button upButton = videoSelectButton("\u25b2", selectedCompletedVideoIndex > 0, view -> {
            if (selectedCompletedVideoIndex > 0) {
                selectedCompletedVideoIndex--;
                refreshCompletedVideos();
            }
        });
        controls.addView(upButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        int start = Math.max(0, Math.min(selectedCompletedVideoIndex - 1, completedVideos.size() - 3));
        int end = Math.min(completedVideos.size(), start + 3);
        for (int i = start; i < end; i++) {
            final int index = i;
            rows.addView(
                    completedVideoRow(completedVideos.get(i).label, i == selectedCompletedVideoIndex, view -> {
                        selectedCompletedVideoIndex = index;
                        refreshCompletedVideos();
                    }),
                    matchWrap());
        }
        Button downButton = videoSelectButton("\u25bc", selectedCompletedVideoIndex < completedVideos.size() - 1, view -> {
            if (selectedCompletedVideoIndex < completedVideos.size() - 1) {
                selectedCompletedVideoIndex++;
                refreshCompletedVideos();
            }
        });
        controls.addView(downButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
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
        if (DownloadDirectorySettings.hasCustomDirectory(this)) {
            addCompletedVideosFromTree(videos);
            videos.sort((left, right) -> Long.compare(right.updatedAt, left.updatedAt));
            return labeledCompletedVideos(videos);
        }
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null || !dir.isDirectory()) {
            dir = getFilesDir();
        }
        addCompletedVideos(videos, dir);
        File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AI Test Downloader");
        addCompletedVideos(videos, publicDir);
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

    private void addCompletedVideosFromTree(List<CompletedVideo> videos) {
        Uri treeUri = DownloadDirectorySettings.treeUri(this);
        if (treeUri == null) {
            return;
        }
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            int sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                if (!isPlayableVideo(name) || cursor.getLong(sizeColumn) <= 0L) {
                    continue;
                }
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn));
                if (canOpenVideoUri(uri) && !containsCompletedVideo(videos, uri)) {
                    videos.add(CompletedVideo.fromUri(uri, name, cursor.getLong(modifiedColumn), ""));
                }
            }
        } catch (Exception ignored) {
            // The selected provider may be temporarily unavailable; file-based directories still work.
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
