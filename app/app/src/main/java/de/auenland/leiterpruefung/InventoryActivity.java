
package de.auenland.leiterpruefung;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InventoryActivity extends Activity {

    private DbHelper db;
    private LinearLayout list;
    private TextView count;
    private EditText search;
    private Button modeButton;
    private boolean onlyRecorded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DbHelper(this);
        setContentView(buildUi());
        refresh();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = new TextView(this);
        title.setText("Leiterbestand");
        title.setTextSize(26);
        root.addView(title);

        count = new TextView(this);
        count.setTextSize(16);
        count.setPadding(0, dp(4), 0, dp(8));
        root.addView(count);

        search = new EditText(this);
        search.setHint("Suche: Leiter, Standort oder Leiterart");
        root.addView(search);

        modeButton = new Button(this);
        modeButton.setText("Ansicht: nur erfasste Leitern");
        modeButton.setOnClickListener(v -> {
            onlyRecorded = !onlyRecorded;
            modeButton.setText(onlyRecorded
                    ? "Ansicht: nur erfasste Leitern"
                    : "Ansicht: alle 300 Leiter-IDs");
            refresh();
        });
        root.addView(modeButton);

        TextView legend = new TextView(this);
        legend.setText("GRÜN = Prüfung gültig   •   ROT = fällig / überfällig / Mangel / noch nicht geprüft");
        legend.setPadding(0, dp(8), 0, dp(8));
        root.addView(legend);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        return root;
    }

    private void refresh() {
        list.removeAllViews();

        int recorded = db.recordedLadderCount();
        count.setText(recorded + " von 300 Leitern im Bestand");

        String q = search == null ? "" : search.getText().toString();
        try (Cursor c = db.inventory(onlyRecorded, q)) {
            while (c.moveToNext()) {
                String id = c.getString(c.getColumnIndexOrThrow("ladder_id"));
                int tsIdx = c.getColumnIndexOrThrow("ts");
                boolean recordedRow = !c.isNull(tsIdx);

                String location = c.isNull(c.getColumnIndexOrThrow("location"))
                        ? "" : c.getString(c.getColumnIndexOrThrow("location"));
                String type = c.isNull(c.getColumnIndexOrThrow("ladder_type"))
                        ? "" : c.getString(c.getColumnIndexOrThrow("ladder_type"));
                String status = c.isNull(c.getColumnIndexOrThrow("status"))
                        ? "" : c.getString(c.getColumnIndexOrThrow("status"));

                long nextTs = c.isNull(c.getColumnIndexOrThrow("next_ts"))
                        ? 0L : c.getLong(c.getColumnIndexOrThrow("next_ts"));

                boolean due = !recordedRow
                        || nextTs <= System.currentTimeMillis()
                        || !"i.O.".equals(status);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = dp(8);

                // bewusst einfache, kontrastreiche Ampelfarben für altes Android
                card.setBackgroundColor(due
                        ? Color.rgb(255, 205, 205)
                        : Color.rgb(205, 245, 205));

                TextView top = new TextView(this);
                top.setTextSize(19);
                top.setTextColor(Color.BLACK);
                top.setText(id + "   " + (due ? "ROT" : "GRÜN"));
                card.addView(top);

                TextView details = new TextView(this);
                details.setTextColor(Color.BLACK);
                details.setTextSize(15);

                if (!recordedRow) {
                    details.setText("Noch nicht geprüft / noch nicht im Bestand erfasst");
                } else {
                    SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
                    StringBuilder sb = new StringBuilder();

                    if (!location.trim().isEmpty()) {
                        sb.append("Standort: ").append(location.trim()).append("\n");
                    }
                    if (!type.trim().isEmpty()) {
                        sb.append("Leiterart: ").append(type.trim()).append("\n");
                    }

                    sb.append("Letzter Status: ").append(status).append("\n");
                    sb.append("Nächste Prüfung: ").append(df.format(new Date(nextTs)));

                    if (nextTs <= System.currentTimeMillis()) {
                        sb.append("\nPRÜFUNG FÄLLIG / ÜBERFÄLLIG");
                    } else if (!"i.O.".equals(status)) {
                        sb.append("\nLEITER GESPERRT / MANGEL");
                    } else {
                        sb.append("\nPrüfung gültig");
                    }
                    details.setText(sb.toString());
                }

                card.addView(details);
                list.addView(card, cardLp);
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
