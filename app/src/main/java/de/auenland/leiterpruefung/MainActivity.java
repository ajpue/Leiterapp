package de.auenland.leiterpruefung;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 1001;
    private static final int REQ_RESTORE = 1002;
    private static final int REQ_PHOTO = 1003;
    private static final int REQ_CAMERA_PERMISSION = 1004;

    private DbHelper db;
    private EditText ladderId;
    private EditText inspector;
    private EditText location;
    private EditText ladderType;
    private EditText intervalMonths;
    private EditText defect;
    private TextView photoInfo;
    private final Spinner[] stepSpinners = new Spinner[6];
    private String currentPhotoPath;

    private static final String[] STEP_TITLES = {
            "1. Holme und Sprossen/Stufen",
            "2. Schrauben, Nieten und Verbindungen",
            "3. Füße, Gelenke und Spreizsicherung",
            "4. Verriegelungen / bewegliche Teile",
            "5. Kennzeichnung / allgemeiner Zustand",
            "6. Gesamtprüfung / sicher verwendbar"
    };

    private static final String[] STEP_VALUES = {
            "i.O.", "Mangel", "nicht zutreffend"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box);

        TextView title = new TextView(this);
        title.setText("Leiterprüfung");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, dp(12));
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("300 Leitern: L-0001 bis L-0300 • offline");
        info.setPadding(0, 0, 0, dp(12));
        box.addView(info);

        ladderId = addText(box, "Leiter-ID, z. B. L-0001", false);
        ladderId.setAllCaps(true);

        Button scan = addButton(box, "Barcode / QR scannen");
        scan.setOnClickListener(v -> startScan());

        inspector = addText(box, "Prüfer", false);
        location = addText(box, "Standort", false);
        ladderType = addText(box, "Leiterart", false);
        intervalMonths = addText(box, "Prüfintervall", true);
        intervalMonths.setText("12");
        intervalMonths.setEnabled(false);

        for (int i = 0; i < STEP_TITLES.length; i++) {
            TextView label = new TextView(this);
            label.setText(STEP_TITLES[i]);
            label.setPadding(0, dp(12), 0, dp(4));
            box.addView(label);

            Spinner sp = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, STEP_VALUES);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sp.setAdapter(adapter);
            stepSpinners[i] = sp;
            box.addView(sp);
        }

        defect = addText(box, "Mangelbeschreibung (bei Mangel Pflicht)", false);
        defect.setMinLines(2);

        Button photo = addButton(box, "Optionales Foto aufnehmen");
        photo.setOnClickListener(v -> takePhoto());

        photoInfo = new TextView(this);
        photoInfo.setText("Kein Foto");
        photoInfo.setPadding(0, dp(4), 0, dp(10));
        box.addView(photoInfo);

        Button save = addButton(box, "Prüfung speichern");
        save.setOnClickListener(v -> saveInspection());

        Button inventory = addButton(box, "Leiterbestand / Prüffälligkeit");
        inventory.setOnClickListener(v ->
                startActivity(new Intent(this, InventoryActivity.class)));

        Button history = addButton(box, "Historie der Leiter");
        history.setOnClickListener(v -> showHistory());

        Button exportOne = addButton(box, "Excel: ausgewählte Leiter");
        exportOne.setOnClickListener(v -> exportXlsx(false));

        Button exportAll = addButton(box, "Excel: alle Prüfungen");
        exportAll.setOnClickListener(v -> exportXlsx(true));

        Button chooseSd = addButton(box, "SD-/Backup-Ordner wählen");
        chooseSd.setOnClickListener(v -> chooseBackupTree());

        Button restore = addButton(box, "Backup wiederherstellen");
        restore.setOnClickListener(v -> chooseRestoreFile());

        Button share = addButton(box, "Backup teilen / per Mail senden");
        share.setOnClickListener(v -> shareBackup());

        TextView backupState = new TextView(this);
        Uri tree = BackupManager.getTreeUri(this);
        backupState.setText(tree == null
                ? "Hinweis: Für automatische Sicherungen zuerst einen SD-/Backup-Ordner wählen."
                : "Automatische Sicherung ist eingerichtet.");
        backupState.setPadding(0, dp(10), 0, dp(20));
        box.addView(backupState);

        return scroll;
    }

    private EditText addText(LinearLayout box, String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(e, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button addButton(LinearLayout box, String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        box.addView(b, lp);
        return b;
    }

    private void startScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Barcode der Leiter scannen");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void saveInspection() {
        String id = normalizeLadderId(ladderId.getText().toString());
        if (!db.isValidLadder(id)) {
            toast("Ungültige Leiter-ID. Erlaubt: L-0001 bis L-0300.");
            return;
        }
        String inspectorText = inspector.getText().toString().trim();
        if (inspectorText.isEmpty()) {
            toast("Bitte Prüfer eintragen.");
            return;
        }

        int interval = 12;

        boolean hasDefect = false;
        String[] steps = new String[6];
        for (int i = 0; i < 6; i++) {
            steps[i] = String.valueOf(stepSpinners[i].getSelectedItem());
            if ("Mangel".equals(steps[i])) hasDefect = true;
        }

        String defectText = defect.getText().toString().trim();
        if (hasDefect && defectText.isEmpty()) {
            toast("Bei Mangel ist eine Mangelbeschreibung Pflicht.");
            return;
        }

        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.add(Calendar.MONTH, interval);

        ContentValues cv = new ContentValues();
        cv.put("ladder_id", id);
        cv.put("inspector", inspectorText);
        cv.put("ts", now);
        cv.put("location", location.getText().toString().trim());
        cv.put("ladder_type", ladderType.getText().toString().trim());
        cv.put("interval_months", interval);
        cv.put("next_ts", cal.getTimeInMillis());
        cv.put("status", hasDefect ? "MANGEL / GESPERRT" : "i.O.");
        cv.put("defect", defectText);
        cv.put("photo_path", currentPhotoPath == null ? "" : currentPhotoPath);
        for (int i = 0; i < 6; i++) {
            cv.put("s" + (i + 1), steps[i]);
        }

        try {
            db.insertInspection(cv);
            boolean backedUp = BackupManager.backupAfterInspection(this, db);
            ladderId.setText(id);
            String status = hasDefect ? "MANGEL / GESPERRT" : "Prüfung bestanden";
            String backup = backedUp ? "\nSD-Backup erstellt." :
                    "\nKein automatisches SD-Backup (Ordner noch nicht gewählt oder nicht erreichbar).";
            new AlertDialog.Builder(this)
                    .setTitle("Gespeichert")
                    .setMessage(status + backup)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            toast("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private void showHistory() {
        String id = normalizeLadderId(ladderId.getText().toString());
        if (!db.isValidLadder(id)) {
            toast("Bitte gültige Leiter-ID eingeben oder scannen.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Historie " + id)
                .setMessage(db.historyText(id))
                .setPositiveButton("OK", null)
                .show();
    }

    private void exportXlsx(boolean all) {
        String id = normalizeLadderId(ladderId.getText().toString());
        if (!all && !db.isValidLadder(id)) {
            toast("Bitte gültige Leiter-ID für den Einzel-Export eingeben.");
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Export-Ordner fehlt");
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMANY).format(new Date());
            String name = all
                    ? "Leiterpruefung_alle_" + stamp + ".xlsx"
                    : "Leiterpruefung_" + id + "_" + stamp + ".xlsx";
            File out = new File(dir, name);
            XlsxExporter.write(all ? db.allInspections() : db.inspectionsForLadder(id), out);
            shareFile(out, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            toast("Excel-Export fehlgeschlagen: " + e.getMessage());
        }
    }

    private void chooseBackupTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    private void chooseRestoreFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_RESTORE);
    }

    private void shareBackup() {
        try {
            File f = BackupManager.createShareBackup(this, db);
            shareFile(f, "application/json");
        } catch (Exception e) {
            toast("Backup konnte nicht erstellt werden: " + e.getMessage());
        }
    }

    private void takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) throw new IllegalStateException("Kein Foto-Ordner");
            File photo = File.createTempFile("leiter_", ".jpg", dir);
            currentPhotoPath = photo.getAbsolutePath();
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".files", photo);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_PHOTO);
        } catch (Exception e) {
            currentPhotoPath = null;
            toast("Kamera konnte nicht gestartet werden: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                toast("Kamerazugriff wurde nicht erlaubt.");
            }
        }
    }

    private void shareFile(File f, String mime) {
        Uri uri = FileProvider.getUriForFile(
                this, getPackageName() + ".files", f);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Datei teilen"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(
                requestCode, resultCode, data);
        if (scanResult != null && scanResult.getContents() != null) {
            ladderId.setText(normalizeLadderId(scanResult.getContents()));
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                final int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                         Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, flags);
                    BackupManager.setTreeUri(this, uri);
                    toast("Backup-Ordner gespeichert.");
                } catch (Exception e) {
                    toast("Ordnerfreigabe fehlgeschlagen: " + e.getMessage());
                }
            }
        } else if (requestCode == REQ_RESTORE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) restoreWithConfirmation(uri);
        } else if (requestCode == REQ_PHOTO) {
            if (resultCode == RESULT_OK && currentPhotoPath != null) {
                photoInfo.setText("Foto gespeichert");
            } else {
                currentPhotoPath = null;
                photoInfo.setText("Kein Foto");
            }
        }
    }

    private void restoreWithConfirmation(Uri uri) {
        try {
            JSONObject backup = BackupManager.readJson(this, uri);
            int count = backup.getJSONArray("inspections").length();
            new AlertDialog.Builder(this)
                    .setTitle("Backup wiederherstellen?")
                    .setMessage("Dabei werden die aktuell gespeicherten Prüfungen durch " +
                            count + " Prüfungen aus dem Backup ersetzt.\n\n" +
                            "Nur fortfahren, wenn dieses Backup wirklich verwendet werden soll.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Wiederherstellen", (d, w) -> {
                        try {
                            db.replaceInspectionsFromJson(backup);
                            toast("Backup wiederhergestellt: " + db.inspectionCount() + " Prüfungen.");
                        } catch (Exception e) {
                            toast("Restore fehlgeschlagen: " + e.getMessage());
                        }
                    })
                    .show();
        } catch (Exception e) {
            toast("Backup konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    private String normalizeLadderId(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.GERMANY);
        if (s.matches("\\d{1,4}")) {
            try {
                int n = Integer.parseInt(s);
                return String.format(Locale.GERMANY, "L-%04d", n);
            } catch (Exception ignored) {}
        }
        if (s.matches("L-?\\d{1,4}")) {
            String digits = s.replaceAll("\\D", "");
            try {
                int n = Integer.parseInt(digits);
                return String.format(Locale.GERMANY, "L-%04d", n);
            } catch (Exception ignored) {}
        }
        return s;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
