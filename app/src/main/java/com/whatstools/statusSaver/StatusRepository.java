package com.whatstools.statusSaver;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/**
 * Single source of truth for WhatsApp status discovery and the save/copy flow.
 * Handles the path differences between WhatsApp versions (legacy /WhatsApp/...
 * vs the newer /Android/media/... location, plus WhatsApp Business) and the
 * storage differences between Android versions (MediaStore on Q+, direct file
 * copy below).
 */
public class StatusRepository {
    private static final String TAG = "StatusRepository";

    public static final String SAVED_FOLDER_NAME = "Status Saver";

    // Legacy app-created save locations (pre-Q installs keep using these).
    private static final String LEGACY_SAVED_IMAGES = "/Status Saver/StatusImages/";
    private static final String LEGACY_SAVED_VIDEOS = "/Status Saver/StatusVideos/";

    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"};
    private static final String[] VIDEO_EXTENSIONS = {".mp4"};

    /** All known WhatsApp status directories, in priority order. */
    private static File[] getStatusDirectories() {
        File external = Environment.getExternalStorageDirectory();
        return new File[]{
                // WhatsApp moved status media here around scoped storage (Android 10+)
                new File(external, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
                new File(external, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
                // Legacy locations used by older WhatsApp builds
                new File(external, "WhatsApp/Media/.Statuses"),
                new File(external, "WhatsApp Business/Media/.Statuses")
        };
    }

    /** Recent (unsaved) statuses of one kind across every known WhatsApp directory, newest first. */
    public static ArrayList<FileModel> getRecentStatuses(boolean videos) {
        ArrayList<File> files = new ArrayList<>();
        HashSet<String> seenNames = new HashSet<>();
        for (File dir : getStatusDirectories()) {
            for (File f : listMediaFiles(dir, videos)) {
                // The same status can show up in more than one directory on
                // upgraded installs — keep the first (newest-path) copy only.
                if (seenNames.add(f.getName())) {
                    files.add(f);
                }
            }
        }
        return toSortedModels(files);
    }

    /** Saved statuses of one kind across the legacy and Q+ save locations, newest first. */
    public static ArrayList<FileModel> getSavedStatuses(boolean videos) {
        ArrayList<File> files = new ArrayList<>();
        HashSet<String> seenNames = new HashSet<>();
        for (File dir : getSavedDirectories(videos)) {
            for (File f : listMediaFiles(dir, videos)) {
                if (seenNames.add(f.getName())) {
                    files.add(f);
                }
            }
        }
        return toSortedModels(files);
    }

    /**
     * Copies one status into the saved area. On Q+ this goes through MediaStore
     * (Pictures/Status Saver or Movies/Status Saver) because direct writes to
     * shared storage are blocked by scoped storage; below Q it is a plain file
     * copy into /Status Saver/ followed by a media scan.
     *
     * @return true when the file was written (or already saved), false on failure.
     */
    public static boolean saveStatus(Context context, File source, boolean video) {
        if (source == null || !source.exists()) {
            return false;
        }
        if (isAlreadySaved(source.getName(), video)) {
            return true;
        }
        if (VERSION.SDK_INT >= 29) {
            return saveViaMediaStore(context, source, video);
        }
        return saveViaFileCopy(context, source, video);
    }

    /** True when a file with this name already exists in any saved directory. */
    public static boolean isAlreadySaved(String fileName, boolean video) {
        for (File dir : getSavedDirectories(video)) {
            if (new File(dir, fileName).exists()) {
                return true;
            }
        }
        return false;
    }

    private static File[] getSavedDirectories(boolean videos) {
        File external = Environment.getExternalStorageDirectory();
        if (videos) {
            return new File[]{
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SAVED_FOLDER_NAME),
                    new File(external, LEGACY_SAVED_VIDEOS)
            };
        }
        return new File[]{
                new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVED_FOLDER_NAME),
                new File(external, LEGACY_SAVED_IMAGES)
        };
    }

    /** Null-safe directory listing filtered to one media kind. */
    private static ArrayList<File> listMediaFiles(File dir, boolean videos) {
        ArrayList<File> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            return result;
        }
        File[] listed = dir.listFiles();
        if (listed == null) {
            // Permission not granted or directory unreadable — not a crash.
            Log.w(TAG, "Unreadable directory: " + dir);
            return result;
        }
        String[] extensions = videos ? VIDEO_EXTENSIONS : IMAGE_EXTENSIONS;
        for (File f : listed) {
            if (!f.isFile()) {
                continue;
            }
            String name = f.getName().toLowerCase();
            for (String ext : extensions) {
                if (name.endsWith(ext)) {
                    result.add(f);
                    break;
                }
            }
        }
        return result;
    }

    private static ArrayList<FileModel> toSortedModels(ArrayList<File> files) {
        Collections.sort(files, new Comparator<File>() {
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        ArrayList<FileModel> models = new ArrayList<>();
        for (File f : files) {
            FileModel model = new FileModel();
            model.setImageFilePath(f.getAbsolutePath());
            model.setImageFileName(f.getName());
            model.setImageChecked(Boolean.FALSE);
            models.add(model);
        }
        return models;
    }

    private static boolean saveViaMediaStore(Context context, File source, boolean video) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(source.getName(), video));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES)
                        + File.separator + SAVED_FOLDER_NAME);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection = video
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri item = null;
        try {
            item = context.getContentResolver().insert(collection, values);
            if (item == null) {
                return false;
            }
            OutputStream out = context.getContentResolver().openOutputStream(item);
            if (out == null) {
                return false;
            }
            copyStream(new FileInputStream(source), out);
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(item, values, null, null);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "MediaStore save failed for " + source, e);
            if (item != null) {
                context.getContentResolver().delete(item, null, null);
            }
            return false;
        }
    }

    private static boolean saveViaFileCopy(Context context, File source, boolean video) {
        File targetDir = new File(Environment.getExternalStorageDirectory(),
                video ? LEGACY_SAVED_VIDEOS : LEGACY_SAVED_IMAGES);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e(TAG, "Cannot create " + targetDir);
            return false;
        }
        File target = new File(targetDir, source.getName());
        try {
            copyStream(new FileInputStream(source), new FileOutputStream(target));
        } catch (IOException e) {
            Log.e(TAG, "File copy failed for " + source, e);
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return false;
        }
        MediaScannerConnection.scanFile(context.getApplicationContext(),
                new String[]{target.getAbsolutePath()},
                new String[]{getMimeType(target.getName(), video)}, null);
        return true;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String getMimeType(String fileName, boolean video) {
        if (video) {
            return "video/mp4";
        }
        String name = fileName.toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
