
package de.auenland.leiterpruefung;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final String PREFS = "backup_prefs";
    private static final String KEY_TREE = "tree_uri";
    private static final String JSON_NAME = "backup.json";
    private static final String PHOTO_DIR = "photos/";

    private BackupManager() {}

    public static void setTreeUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TREE, uri.toString()).apply();
    }

    public static Uri getTreeUri(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = p.getString(KEY_TREE, null);
        return s == null ? null : Uri.parse(s);
    }

    public static boolean backupAfterInspection(Context context, DbHelper db) {
        Uri tree = getTreeUri(context);
        if (tree == null) return false;

        try {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
                    .format(new Date());

            byte[] zipBytes = createPortableBackupZipBytes(context, db);

            writeBytesToTree(
                    context, tree,
                    "leiterpruefung_LATEST.zip",
                    "application/zip",
                    zipBytes);

            writeBytesToTree(
                    context, tree,
                    "leiterpruefung_" + day + ".zip",
                    "application/zip",
                    zipBytes);

            cleanupOldDailyBackups(tree, context);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static File createShareBackup(Context context, DbHelper db) throws Exception {
        File dir = new File(context.getCacheDir(), "backup_share");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Backup-Ordner konnte nicht erstellt werden");
        }

        File out = new File(dir, "leiterpruefung_backup.zip");
        try (FileOutputStream fos = new FileOutputStream(out, false)) {
            fos.write(createPortableBackupZipBytes(context, db));
        }
        return out;
    }

    public static JSONObject readBackupForRestore(Context context, Uri uri) throws Exception {
        String name = displayNameFromUri(uri);
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return readLegacyJson(context, uri);
        }

        try {
            return readZipBackupAndRestorePhotos(context, uri);
        } catch (Exception zipError) {
            return readLegacyJson(context, uri);
        }
    }

    private static byte[] createPortableBackupZipBytes(Context context, DbHelper db)
            throws Exception {

        JSONObject root = db.toBackupJson();
        root.put("backupContainer", "zip");
        root.put("backupVersion", 2);

        JSONArray inspections = root.getJSONArray("inspections");
        Map<String, String> pathToZipName = new HashMap<>();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(bos))) {
            for (int i = 0; i < inspections.length(); i++) {
                JSONObject row = inspections.getJSONObject(i);

                String photoPath = row.optString("photo_path", "");
                if (photoPath == null || photoPath.trim().isEmpty()) {
                    row.put("photo_path", "");
                    row.put("backup_photo", JSONObject.NULL);
                    continue;
                }

                File photo = new File(photoPath);
                if (!photo.exists() || !photo.isFile()) {
                    row.put("backup_photo", JSONObject.NULL);
                    continue;
                }

                String zipName = pathToZipName.get(photo.getAbsolutePath());

                if (zipName == null) {
                    String ladder = safePart(row.optString("ladder_id", "leiter"));
                    long inspectionId = row.optLong("id", i + 1L);
                    String ext = extension(photo.getName());
                    if (ext.isEmpty()) ext = ".jpg";

                    zipName = PHOTO_DIR + ladder + "_inspection_" + inspectionId + ext;
                    pathToZipName.put(photo.getAbsolutePath(), zipName);

                    ZipEntry photoEntry = new ZipEntry(zipName);
                    zip.putNextEntry(photoEntry);

                    try (FileInputStream fis = new FileInputStream(photo);
                         BufferedInputStream bis = new BufferedInputStream(fis)) {
                        copy(bis, zip);
                    }

                    zip.closeEntry();
                }

                row.put("backup_photo", zipName);
            }

            ZipEntry jsonEntry = new ZipEntry(JSON_NAME);
            zip.putNextEntry(jsonEntry);
            zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        return bos.toByteArray();
    }

    private static JSONObject readZipBackupAndRestorePhotos(Context context, Uri uri)
            throws Exception {

        byte[] rawZip;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Backup konnte nicht geöffnet werden");
            rawZip = readAll(in);
        }

        JSONObject backupJson = null;
        Map<String, byte[]> photoBytes = new HashMap<>();

        try (ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(new ByteArrayInputStream(rawZip)))) {

            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();

                if (JSON_NAME.equals(name)) {
                    byte[] jsonBytes = readAll(zin);
                    backupJson = new JSONObject(
                            new String(jsonBytes, StandardCharsets.UTF_8));
                } else if (name != null && name.startsWith(PHOTO_DIR) && !e.isDirectory()) {
                    photoBytes.put(name, readAll(zin));
                }

                zin.closeEntry();
            }
        }

        if (backupJson == null) {
            throw new IllegalStateException("backup.json fehlt im ZIP");
        }

        JSONArray inspections = backupJson.getJSONArray("inspections");

        File picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            throw new IllegalStateException("Foto-Ordner auf dem Gerät nicht verfügbar");
        }
        if (!picturesDir.exists() && !picturesDir.mkdirs()) {
            throw new IllegalStateException("Foto-Ordner konnte nicht erstellt werden");
        }

        for (int i = 0; i < inspections.length(); i++) {
            JSONObject row = inspections.getJSONObject(i);

            String backupPhoto = row.optString("backup_photo", "");
            if (backupPhoto == null || backupPhoto.trim().isEmpty()
                    || "null".equalsIgnoreCase(backupPhoto)) {
                row.put("photo_path", "");
                continue;
            }

            byte[] bytes = photoBytes.get(backupPhoto);
            if (bytes == null) {
                row.put("photo_path", "");
                continue;
            }

            String fileName = new File(backupPhoto).getName();
            String ladder = safePart(row.optString("ladder_id", "leiter"));
            long inspectionId = row.optLong("id", i + 1L);

            String ext = extension(fileName);
            if (ext.isEmpty()) ext = ".jpg";

            File out = new File(
                    picturesDir,
                    ladder + "_restored_" + inspectionId + "_" +
                            System.currentTimeMillis() + ext);

            try (FileOutputStream fos = new FileOutputStream(out, false)) {
                fos.write(bytes);
            }

            row.put("photo_path", out.getAbsolutePath());
        }

        return backupJson;
    }

    private static JSONObject readLegacyJson(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();

        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("Backup konnte nicht geöffnet werden");
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
        }

        JSONObject root = new JSONObject(sb.toString());

        JSONArray inspections = root.optJSONArray("inspections");
        if (inspections != null) {
            for (int i = 0; i < inspections.length(); i++) {
                inspections.getJSONObject(i).put("photo_path", "");
            }
        }

        return root;
    }

    private static void writeBytesToTree(
            Context context,
            Uri treeUri,
            String name,
            String mime,
            byte[] bytes) throws Exception {

        DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);

        if (dir == null || !dir.canWrite()) {
            throw new IllegalStateException("Backup-Ordner ist nicht beschreibbar");
        }

        DocumentFile target = dir.findFile(name);
        if (target == null) {
            target = dir.createFile(mime, name);
        }

        if (target == null) {
            throw new IllegalStateException("Backup-Datei konnte nicht erstellt werden");
        }

        try (OutputStream out = context.getContentResolver()
                .openOutputStream(target.getUri(), "wt")) {
            if (out == null) throw new IllegalStateException("Kein Ausgabestrom");
            out.write(bytes);
        }
    }

    private static void cleanupOldDailyBackups(Uri treeUri, Context context) {
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);
            if (dir == null) return;

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -30);
            long cutoff = cal.getTimeInMillis();

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
            fmt.setLenient(false);

            for (DocumentFile f : dir.listFiles()) {
                String name = f.getName();
                if (name == null) continue;

                boolean newZip =
                        name.matches("leiterpruefung_\\d{4}-\\d{2}-\\d{2}\\.zip");
                boolean oldJson =
                        name.matches("leiterpruefung_\\d{4}-\\d{2}-\\d{2}\\.json");

                if (!newZip && !oldJson) continue;

                String datePart = name.substring(
                        "leiterpruefung_".length(),
                        "leiterpruefung_".length() + 10);

                try {
                    Date d = fmt.parse(datePart);
                    if (d != null && d.getTime() < cutoff) {
                        f.delete();
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }

    private static String safePart(String s) {
        if (s == null || s.trim().isEmpty()) return "leiter";
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String extension(String name) {
        if (name == null) return "";
        int p = name.lastIndexOf('.');
        if (p < 0 || p == name.length() - 1) return "";
        String ext = name.substring(p).toLowerCase(Locale.ROOT);
        return ext.length() <= 10 ? ext : "";
    }

    private static String displayNameFromUri(Uri uri) {
        if (uri == null) return null;
        String last = uri.getLastPathSegment();
        return last == null ? null : last;
    }
}
