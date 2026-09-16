
package de.auenland.leiterpruefung;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends Activity {

    private DbHelper db;
    private LinearLayout list;
    private String ladderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        ladderId = getIntent().getStringExtra("ladder_id");
        if (ladderId == null) ladderId = "";
        setContentView(buildUi());
        refresh();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = new TextView(this);
        title.setText("Historie " + ladderId);
        title.setTextSize(26);
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private void refresh() {
        list.removeAllViews();
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);

        try (Cursor c = db.inspectionsForLadder(ladderId)) {
            if (!c.moveToFirst()) {
                TextView empty = new TextView(this);
                empty.setText("Für diese Leiter ist noch keine Prüfung gespeichert.");
                list.addView(empty);
                return;
            }

            do {
                long inspectionId = c.getLong(c.getColumnIndexOrThrow("id"));
                long ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                String inspector = get(c, "inspector");
                String status = get(c, "status");
                String location = get(c, "location");
                String type = get(c, "ladder_type");
                String defect = get(c, "defect");
                String photoPath = get(c, "photo_path");
                int rungIdx = c.getColumnIndex("rung_count");
                String rungs = (rungIdx >= 0 && !c.isNull(rungIdx))
                        ? String.valueOf(c.getInt(rungIdx)) : "";

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setBackgroundColor("i.O.".equals(status)
                        ? Color.rgb(220, 245, 220)
                        : Color.rgb(255, 220, 220));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);

                TextView header = new TextView(this);
                header.setText(df.format(new Date(ts)) + "   •   " + status);
                header.setTextSize(18);
                header.setTextColor(Color.BLACK);
                card.addView(header);

                StringBuilder details = new StringBuilder();
                details.append("Prüfer: ").append(inspector);
                if (!location.isEmpty()) details.append("\nStandort: ").append(location);
                if (!type.isEmpty()) details.append("\nLeiterart: ").append(type);
                if (!rungs.isEmpty()) details.append("\nSprossen/Stufen: ").append(rungs);
                if (!defect.isEmpty()) details.append("\nMangel: ").append(defect);
                details.append("\nFoto: ").append(hasPhoto(photoPath) ? "vorhanden" : "kein Foto");

                TextView detailView = new TextView(this);
                detailView.setText(details.toString());
                detailView.setTextColor(Color.BLACK);
                card.addView(detailView);

                if (hasPhoto(photoPath)) {
                    Button view = button("Foto ansehen");
                    view.setOnClickListener(v -> viewPhoto(photoPath));
                    card.addView(view);

                    Button bluetooth = button("Foto per Bluetooth senden");
                    bluetooth.setOnClickListener(v ->
                            PhotoShareHelper.sharePhoto(this, new File(photoPath), false));
                    card.addView(bluetooth);

                    Button email = button("Foto per E-Mail senden");
                    email.setOnClickListener(v ->
                            PhotoShareHelper.sharePhoto(this, new File(photoPath), true));
                    card.addView(email);

                    Button delete = button("Foto löschen");
                    delete.setOnClickListener(v ->
                            confirmDeletePhoto(inspectionId, photoPath));
                    card.addView(delete);
                }

                list.addView(card, lp);
            } while (c.moveToNext());
        }
    }

    private void viewPhoto(String photoPath) {
        File f = new File(photoPath);
        if (!f.exists()) {
            toast("Foto wurde nicht gefunden.");
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
        if (bitmap == null) {
            toast("Foto konnte nicht geöffnet werden.");
            return;
        }
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setImageBitmap(bitmap);
        int pad = dp(12);
        image.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Prüffoto")
                .setView(image)
                .setPositiveButton("Schließen", null)
                .show();
    }

    private void confirmDeletePhoto(long inspectionId, String photoPath) {
        new AlertDialog.Builder(this)
                .setTitle("Foto wirklich löschen?")
                .setMessage("Die Prüfung und ihre Historie bleiben erhalten. Nur das Foto wird gelöscht.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, w) -> {
                    File f = new File(photoPath);
                    if (f.exists()) f.delete();
                    db.clearPhotoForInspection(inspectionId);
                    BackupManager.backupAfterInspection(this, db);
                    toast("Foto gelöscht.");
                    refresh();
                })
                .show();
    }

    private boolean hasPhoto(String path) {
        return path != null && !path.trim().isEmpty() && new File(path).exists();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private String get(Cursor c, String col) {
        int idx = c.getColumnIndexOrThrow(col);
        return c.isNull(idx) ? "" : c.getString(idx);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
