package tw.wajay.aitestdownloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

final class DownloadDirectorySettings {
    private static final String PREFS = "download_directory";
    private static final String KEY_TREE_URI = "tree_uri";

    private DownloadDirectorySettings() {
    }

    static void set(Context context, Uri uri) {
        prefs(context).edit().putString(KEY_TREE_URI, uri == null ? "" : uri.toString()).apply();
    }

    static Uri treeUri(Context context) {
        String value = prefs(context).getString(KEY_TREE_URI, "");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Uri.parse(value);
    }

    static boolean hasCustomDirectory(Context context) {
        return treeUri(context) != null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
