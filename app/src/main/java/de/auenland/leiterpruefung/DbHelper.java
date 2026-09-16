package de.auenland.leiterpruefung;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "leiterpruefung.db";
    private static final int DB_VERSION = 3;

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ladders (" +
                "id TEXT PRIMARY KEY," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "deactivated_at INTEGER," +
                "deactivation_reason TEXT" +
                ")");
        db.execSQL("CREATE TABLE inspections (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ladder_id TEXT NOT NULL," +
                "inspector TEXT NOT NULL," +
                "ts INTEGER NOT NULL," +
                "location TEXT," +
                "ladder_type TEXT," +
                "rung_count INTEGER," +
                "interval_months INTEGER NOT NULL," +
                "next_ts INTEGER NOT NULL," +
                "status TEXT NOT NULL," +
                "defect TEXT," +
                "photo_path TEXT," +
                "s1 TEXT NOT NULL," +
                "s2 TEXT NOT NULL," +
                "s3 TEXT NOT NULL," +
                "s4 TEXT NOT NULL," +
                "s5 TEXT NOT NULL," +
                "s6 TEXT NOT NULL" +
                ")");
        db.beginTransaction();
        try {
            for (int i = 1; i <= 300; i++) {
                ContentValues cv = new ContentValues();
                cv.put("id", String.format(Locale.GERMANY, "L-%04d", i));
                db.insertOrThrow("ladders", null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE inspections ADD COLUMN rung_count INTEGER");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE ladders ADD COLUMN active INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE ladders ADD COLUMN deactivated_at INTEGER");
            db.execSQL("ALTER TABLE ladders ADD COLUMN deactivation_reason TEXT");
        }
    }

    public boolean isValidLadder(String ladderId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM ladders WHERE id = ? LIMIT 1",
                new String[]{ladderId})) {
            return c.moveToFirst();
        }
    }

    public boolean hasInspections(String ladderId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM inspections WHERE ladder_id = ? LIMIT 1",
                new String[]{ladderId})) {
            return c.moveToFirst();
        }
    }

    public boolean isLadderActive(String ladderId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT active FROM ladders WHERE id = ? LIMIT 1",
                new String[]{ladderId})) {
            return c.moveToFirst() && c.getInt(0) != 0;
        }
    }

    public String ladderDeactivationReason(String ladderId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT deactivation_reason FROM ladders WHERE id = ? LIMIT 1",
                new String[]{ladderId})) {
            if (c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        }
        return "";
    }

    public void deactivateLadder(String ladderId, String reason) {
        ContentValues cv = new ContentValues();
        cv.put("active", 0);
        cv.put("deactivated_at", System.currentTimeMillis());
        cv.put("deactivation_reason", reason == null ? "" : reason.trim());
        getWritableDatabase().update("ladders", cv, "id = ?", new String[]{ladderId});
    }

    public void reactivateLadder(String ladderId) {
        ContentValues cv = new ContentValues();
        cv.put("active", 1);
        cv.putNull("deactivated_at");
        cv.putNull("deactivation_reason");
        getWritableDatabase().update("ladders", cv, "id = ?", new String[]{ladderId});
    }


    public long insertInspection(ContentValues cv) {
        return getWritableDatabase().insertOrThrow("inspections", null, cv);
    }

    public Cursor allInspections() {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM inspections ORDER BY ts DESC", null);
    }

    public void clearPhotoForInspection(long inspectionId) {
        ContentValues cv = new ContentValues();
        cv.put("photo_path", "");
        getWritableDatabase().update(
                "inspections", cv, "id = ?", new String[]{String.valueOf(inspectionId)});
    }


    public Cursor inspectionsForLadder(String ladderId) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM inspections WHERE ladder_id = ? ORDER BY ts DESC",
                new String[]{ladderId});
    }

    public String historyText(String ladderId) {
        StringBuilder sb = new StringBuilder();
        try (Cursor c = inspectionsForLadder(ladderId)) {
            int count = 0;
            while (c.moveToNext() && count < 50) {
                long ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                String status = c.getString(c.getColumnIndexOrThrow("status"));
                String inspector = c.getString(c.getColumnIndexOrThrow("inspector"));
                String defect = c.getString(c.getColumnIndexOrThrow("defect"));
                int rungIdx = c.getColumnIndex("rung_count");
                Integer rungCount = (rungIdx >= 0 && !c.isNull(rungIdx)) ? c.getInt(rungIdx) : null;
                sb.append(DateFormat.getDateTimeInstance(
                                DateFormat.SHORT, DateFormat.SHORT, Locale.GERMANY)
                        .format(new Date(ts)))
                  .append("  •  ").append(status)
                  .append("\nPrüfer: ").append(inspector);
                if (rungCount != null) {
                    sb.append("\nSprossen/Stufen: ").append(rungCount);
                }
                if (defect != null && !defect.trim().isEmpty()) {
                    sb.append("\nMangel: ").append(defect.trim());
                }
                sb.append("\n\n");
                count++;
            }
        }
        if (sb.length() == 0) {
            return "Für diese Leiter ist noch keine Prüfung gespeichert.";
        }
        return sb.toString();
    }

    public Cursor latestInspectionForLadder(String ladderId) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM inspections WHERE ladder_id = ? " +
                "ORDER BY ts DESC, id DESC LIMIT 1",
                new String[]{ladderId});
    }


    public Cursor inventory(boolean onlyRecorded, String search) {
        String base =
                "SELECT l.id AS ladder_id, " +
                "i.location, i.ladder_type, i.status, i.next_ts, i.ts, " +
                "l.active, l.deactivated_at, l.deactivation_reason " +
                "FROM ladders l " +
                "LEFT JOIN inspections i ON i.id = (" +
                "SELECT ii.id FROM inspections ii " +
                "WHERE ii.ladder_id = l.id " +
                "ORDER BY ii.ts DESC, ii.id DESC LIMIT 1" +
                ") ";

        StringBuilder sql = new StringBuilder(base);
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        boolean hasWhere = false;

        if (onlyRecorded) {
            sql.append("WHERE i.id IS NOT NULL ");
            hasWhere = true;
        }

        String q = search == null ? "" : search.trim().toUpperCase(Locale.GERMANY);
        if (!q.isEmpty()) {
            sql.append(hasWhere ? "AND " : "WHERE ");
            sql.append("(UPPER(l.id) LIKE ? OR UPPER(COALESCE(i.location,'')) LIKE ? OR UPPER(COALESCE(i.ladder_type,'')) LIKE ?) ");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        sql.append("ORDER BY l.id");
        return getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
    }

    public int activeRecordedLadderCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT i.ladder_id) " +
                "FROM inspections i JOIN ladders l ON l.id = i.ladder_id " +
                "WHERE l.active = 1", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int inactiveRecordedLadderCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT i.ladder_id) " +
                "FROM inspections i JOIN ladders l ON l.id = i.ladder_id " +
                "WHERE l.active = 0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }


    public int recordedLadderCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT ladder_id) FROM inspections", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }


    public int inspectionCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM inspections", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public JSONObject toBackupJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "leiterpruefung-backup");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());

        JSONArray ladderStates = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, active, deactivated_at, deactivation_reason FROM ladders ORDER BY id", null)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("id", c.getString(0));
                row.put("active", c.getInt(1));
                if (c.isNull(2)) row.put("deactivated_at", JSONObject.NULL);
                else row.put("deactivated_at", c.getLong(2));
                if (c.isNull(3)) row.put("deactivation_reason", JSONObject.NULL);
                else row.put("deactivation_reason", c.getString(3));
                ladderStates.put(row);
            }
        }
        root.put("ladders", ladderStates);

        JSONArray rows = new JSONArray();
        try (Cursor c = allInspections()) {
            String[] cols = c.getColumnNames();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (String col : cols) {
                    int idx = c.getColumnIndexOrThrow(col);
                    switch (c.getType(idx)) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            row.put(col, c.getLong(idx));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            row.put(col, c.getDouble(idx));
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            row.put(col, JSONObject.NULL);
                            break;
                        default:
                            row.put(col, c.getString(idx));
                    }
                }
                rows.put(row);
            }
        }
        root.put("inspections", rows);
        return root;
    }

    public void replaceInspectionsFromJson(JSONObject root) throws JSONException {
        if (!"leiterpruefung-backup".equals(root.optString("format"))) {
            throw new JSONException("Unbekanntes Backup-Format");
        }
        JSONArray rows = root.getJSONArray("inspections");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (root.has("ladders")) {
                JSONArray ladderStates = root.getJSONArray("ladders");
                for (int i = 0; i < ladderStates.length(); i++) {
                    JSONObject ls = ladderStates.getJSONObject(i);
                    ContentValues state = new ContentValues();
                    state.put("active", ls.optInt("active", 1));
                    if (ls.has("deactivated_at") && !ls.isNull("deactivated_at")) {
                        state.put("deactivated_at", ls.optLong("deactivated_at"));
                    } else {
                        state.putNull("deactivated_at");
                    }
                    if (ls.has("deactivation_reason") && !ls.isNull("deactivation_reason")) {
                        state.put("deactivation_reason", ls.optString("deactivation_reason", ""));
                    } else {
                        state.putNull("deactivation_reason");
                    }
                    db.update("ladders", state, "id = ?", new String[]{ls.optString("id", "")});
                }
            }

            db.delete("inspections", null, null);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                ContentValues cv = new ContentValues();
                putLongIfPresent(cv, row, "id");
                putString(cv, row, "ladder_id");
                putString(cv, row, "inspector");
                putLongIfPresent(cv, row, "ts");
                putString(cv, row, "location");
                putString(cv, row, "ladder_type");
                putLongIfPresent(cv, row, "rung_count");
                putLongIfPresent(cv, row, "interval_months");
                putLongIfPresent(cv, row, "next_ts");
                putString(cv, row, "status");
                putString(cv, row, "defect");
                putString(cv, row, "photo_path");
                putString(cv, row, "s1");
                putString(cv, row, "s2");
                putString(cv, row, "s3");
                putString(cv, row, "s4");
                putString(cv, row, "s5");
                putString(cv, row, "s6");
                db.insertOrThrow("inspections", null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void putString(ContentValues cv, JSONObject row, String key) {
        if (row.has(key) && !row.isNull(key)) {
            cv.put(key, row.optString(key, ""));
        } else {
            cv.putNull(key);
        }
    }

    private static void putLongIfPresent(ContentValues cv, JSONObject row, String key) {
        if (row.has(key) && !row.isNull(key)) {
            cv.put(key, row.optLong(key));
        }
    }
}
