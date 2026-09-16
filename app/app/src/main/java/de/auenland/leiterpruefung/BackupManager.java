package de.auenland.leiterpruefung;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class BackupManager {
    private static final String PREFS = "backup_prefs";
    private static final String KEY_TREE = "tree_uri";

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
            String json = db.toBackupJson().toString(2);
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date());
            writeToTree(context, tree, "leiterpruefung_LATEST.json", json);
            writeToTree(context, tree, "leiterpruefung_" + day + ".json", json);
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
        File out = new File(dir, "leiterpruefung_backup.json");
        try (FileOutputStream fos = new FileOutputStream(out, false)) {
            fos.write(db.toBackupJson().toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    public static JSONObject readJson(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return new JSONObject(sb.toString());
    }

    private static void writeToTree(Context context, Uri treeUri, String name, String text)
            throws Exception {
        DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);
        if (dir == null || !dir.canWrite()) {
            throw new IllegalStateException("Backup-Ordner ist nicht beschreibbar");
        }
        DocumentFile target = dir.findFile(name);
        if (target == null) {
            target = dir.createFile("application/json", name);
        }
        if (target == null) {
            throw new IllegalStateException("Backup-Datei konnte nicht erstellt werden");
        }
        try (OutputStream out = context.getContentResolver()
                .openOutputStream(target.getUri(), "wt")) {
            if (out == null) throw new IllegalStateException("Kein Ausgabestrom");
            out.write(text.getBytes(StandardCharsets.UTF_8));
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
                if (!name.matches("leiterpruefung_\\d{4}-\\d{2}-\\d{2}\\.json")) continue;
                String datePart = name.substring("leiterpruefung_".length(),
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
}
