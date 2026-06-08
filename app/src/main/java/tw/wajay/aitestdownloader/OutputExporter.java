package tw.wajay.aitestdownloader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

final class OutputExporter {
    private static final String PUBLIC_SUBDIR = "AI Test Downloader";

    private OutputExporter() {
    }

    static String exportToPublicDownloads(Context context, File source) throws IOException {
        if (source == null || !source.exists()) {
            throw new IOException("Output file missing");
        }
        Uri customTree = DownloadDirectorySettings.treeUri(context);
        if (customTree != null) {
            try {
                return exportToTree(context, customTree, source);
            } catch (Exception ignored) {
                // Persisted tree permissions can disappear when users revoke access or remove storage.
            }
        }
        return exportToDefaultPublicDownloads(context, source);
    }

    private static String exportToDefaultPublicDownloads(Context context, File source) throws IOException {
        if (Build.VERSION.SDK_INT < 29) {
            return exportToLegacyPublicDownloads(source).getAbsolutePath();
        }

        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType(source.getName()));
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_SUBDIR);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        values.put(MediaStore.Downloads.SIZE, source.length());

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Could not create public Downloads entry");
        }

        boolean success = false;
        try (OutputStream rawOutput = resolver.openOutputStream(uri);
             BufferedOutputStream output = rawOutput == null ? null : new BufferedOutputStream(rawOutput);
             BufferedInputStream input = new BufferedInputStream(new FileInputStream(source))) {
            if (output == null) {
                throw new IOException("Could not open public Downloads entry");
            }
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            success = true;
        } finally {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            if (!success) {
                resolver.delete(uri, null, null);
            }
        }

        return uri.toString();
    }

    private static String exportToTree(Context context, Uri treeUri, File source) throws IOException {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        String documentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
        String fileName = uniqueTreeName(resolver, treeUri, source.getName());
        Uri targetUri = DocumentsContract.createDocument(resolver, parentUri, mimeType(source.getName()), fileName);
        if (targetUri == null) {
            throw new IOException("Could not create selected download file");
        }
        boolean success = false;
        try (OutputStream rawOutput = resolver.openOutputStream(targetUri, "w");
             BufferedOutputStream output = rawOutput == null ? null : new BufferedOutputStream(rawOutput);
             BufferedInputStream input = new BufferedInputStream(new FileInputStream(source))) {
            if (output == null) {
                throw new IOException("Could not open selected download file");
            }
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            success = true;
        } finally {
            if (!success) {
                resolver.delete(targetUri, null, null);
            }
        }
        return targetUri.toString();
    }

    private static String uniqueTreeName(ContentResolver resolver, Uri treeUri, String fileName) {
        String safeName = (fileName == null || fileName.trim().isEmpty()) ? "download.bin" : fileName.trim();
        java.util.Set<String> names = existingTreeNames(resolver, treeUri);
        if (!names.contains(safeName)) {
            return safeName;
        }
        int dot = safeName.lastIndexOf('.');
        String stem = dot > 0 ? safeName.substring(0, dot) : safeName;
        String extension = dot > 0 ? safeName.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            String candidate = stem + " (" + i + ")" + extension;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }
        return stem + " (" + System.currentTimeMillis() + ")" + extension;
    }

    private static java.util.Set<String> existingTreeNames(ContentResolver resolver, Uri treeUri) {
        java.util.Set<String> names = new java.util.HashSet<>();
        String documentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        try (Cursor cursor = resolver.query(
                childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor == null) {
                return names;
            }
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (Exception ignored) {
            // If listing fails, let createDocument decide the final behavior.
        }
        return names;
    }

    private static File exportToLegacyPublicDownloads(File source) throws IOException {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File targetDir = new File(downloads, PUBLIC_SUBDIR);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Could not create public Downloads directory");
        }
        File target = uniqueTarget(targetDir, source.getName());
        copyFile(source, target);
        return target;
    }

    private static File uniqueTarget(File directory, String fileName) {
        String safeName = (fileName == null || fileName.trim().isEmpty()) ? "download.bin" : fileName.trim();
        File target = new File(directory, safeName);
        if (!target.exists()) {
            return target;
        }
        int dot = safeName.lastIndexOf('.');
        String stem = dot > 0 ? safeName.substring(0, dot) : safeName;
        String extension = dot > 0 ? safeName.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            target = new File(directory, stem + " (" + i + ")" + extension);
            if (!target.exists()) {
                return target;
            }
        }
        return new File(directory, stem + " (" + System.currentTimeMillis() + ")" + extension);
    }

    private static void copyFile(File source, File target) throws IOException {
        boolean success = false;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            success = true;
        } finally {
            if (!success && target.exists()) {
                target.delete();
            }
        }
    }

    private static String mimeType(String fileName) {
        String lowered = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (lowered.endsWith(".mp4") || lowered.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (lowered.endsWith(".webm")) {
            return "video/webm";
        }
        if (lowered.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (lowered.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowered.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lowered.endsWith(".jsonl")) {
            return "application/x-ndjson";
        }
        if (lowered.endsWith(".json")) {
            return "application/json";
        }
        if (lowered.endsWith(".txt") || lowered.endsWith(".log")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}
