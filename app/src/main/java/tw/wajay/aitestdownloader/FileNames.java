package tw.wajay.aitestdownloader;

import android.net.Uri;

import java.util.Locale;

final class FileNames {
    private FileNames() {
    }

    static String choose(Uri uri, String requestedName) {
        String clean = sanitize(requestedName);
        if (!clean.isEmpty()) {
            return clean;
        }
        clean = sanitize(queryFileName(uri));
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
        String clean = value
                .replaceAll("[\\p{Cntrl}]+", "_")
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("[. ]+$", "");
        if (clean.isEmpty()) {
            return "";
        }
        String base = clean;
        int dot = clean.indexOf('.');
        if (dot > 0) {
            base = clean.substring(0, dot);
        }
        String normalizedBase = base.toUpperCase(Locale.US);
        if (normalizedBase.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            clean = "_" + clean;
        }
        return clean;
    }

    private static String queryFileName(Uri uri) {
        if (uri == null || uri.isOpaque()) {
            return "";
        }
        for (String key : new String[]{"response-content-disposition", "filename", "file", "name"}) {
            String value = uri.getQueryParameter(key);
            String candidate = "response-content-disposition".equals(key)
                    ? contentDispositionFileName(value)
                    : value;
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate;
            }
        }
        return "";
    }

    private static String contentDispositionFileName(String value) {
        if (value == null) {
            return "";
        }
        String[] parts = value.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = trimmed.substring(0, equals).trim().toLowerCase(Locale.US);
            String raw = unquote(trimmed.substring(equals + 1).trim());
            if ("filename*".equals(name)) {
                int marker = raw.indexOf("''");
                return marker >= 0 ? raw.substring(marker + 2) : raw;
            }
            if ("filename".equals(name) && !raw.isEmpty()) {
                return raw;
            }
        }
        return "";
    }

    private static String unquote(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
