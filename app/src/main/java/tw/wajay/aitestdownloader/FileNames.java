package tw.wajay.aitestdownloader;

import android.net.Uri;

final class FileNames {
    private FileNames() {
    }

    static String choose(Uri uri, String requestedName) {
        String clean = sanitize(requestedName);
        if (!clean.isEmpty()) {
            return clean;
        }
        String last = uri.getLastPathSegment();
        clean = sanitize(last == null ? "" : last);
        return clean.isEmpty() ? "download.bin" : clean;
    }

    static String replaceExtension(String fileName, String extension) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + extension;
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
    }
}
