package tw.wajay.aitestdownloader;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class PartialFileProvider extends ContentProvider {
    private static final String PUBLIC_SUBDIR = "AI Test Downloader";

    static Uri contentUriFor(android.content.Context context, File file) {
        return contentUriFor(context, file, playableName(file.getName()));
    }

    static Uri contentUriFor(android.content.Context context, File file, String displayName) {
        String cleanDisplayName = FileNames.sanitize(displayName);
        if (cleanDisplayName.isEmpty()) {
            cleanDisplayName = playableName(file.getName());
        }
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".partialfileprovider")
                .appendPath(cleanDisplayName)
                .appendQueryParameter("file", file.getName());
        if (isPublicDownloadFile(file)) {
            builder.appendQueryParameter("scope", "public");
        }
        return builder.build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri == null ? "" : uri.getLastPathSegment();
        String lowered = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        if (lowered.contains(".mp4") || lowered.contains(".m4v")) {
            return "video/mp4";
        }
        if (lowered.contains(".webm")) {
            return "video/webm";
        }
        if (lowered.contains(".ts")) {
            return "video/mp2t";
        }
        return "video/*";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) {
            throw new FileNotFoundException("read only");
        }
        File file = resolve(uri);
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("missing partial file");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{displayName(uri, file), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri == null ? "" : uri.getQueryParameter("file");
        if (name == null || name.isEmpty()) {
            name = uri == null ? "" : uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("bad partial file");
        }
        android.content.Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("missing context");
        }
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if ("public".equals(uri == null ? "" : uri.getQueryParameter("scope"))) {
            dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PUBLIC_SUBDIR);
        } else if (dir == null) {
            dir = context.getFilesDir();
        }
        return new File(dir, name);
    }

    private String displayName(Uri uri, File file) {
        String name = uri == null ? "" : uri.getLastPathSegment();
        String clean = FileNames.sanitize(name);
        return clean.isEmpty() ? playableName(file.getName()) : clean;
    }

    private static String playableName(String name) {
        String clean = FileNames.sanitize(name);
        if (clean.endsWith(".part")) {
            clean = clean.substring(0, clean.length() - 5);
        }
        return clean.isEmpty() ? "preview.mp4" : clean;
    }

    private static boolean isPublicDownloadFile(File file) {
        if (file == null) {
            return false;
        }
        try {
            File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PUBLIC_SUBDIR);
            String parent = file.getParentFile() == null ? "" : file.getParentFile().getCanonicalPath();
            return parent.equals(publicDir.getCanonicalPath());
        } catch (Exception ignored) {
            return false;
        }
    }
}
