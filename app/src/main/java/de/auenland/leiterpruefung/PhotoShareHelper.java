
package de.auenland.leiterpruefung;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PhotoShareHelper {
    private PhotoShareHelper() {}

    public static void sharePhoto(Activity activity, File file, boolean emailOnly) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".files", file);

            Intent base = new Intent(Intent.ACTION_SEND);
            base.setType("image/jpeg");
            base.putExtra(Intent.EXTRA_STREAM, uri);
            base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            List<ResolveInfo> matches =
                    activity.getPackageManager().queryIntentActivities(base, 0);

            ArrayList<Intent> targets = new ArrayList<>();
            for (ResolveInfo ri : matches) {
                String pkg = ri.activityInfo.packageName == null
                        ? "" : ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
                String name = ri.activityInfo.name == null
                        ? "" : ri.activityInfo.name.toLowerCase(Locale.ROOT);

                boolean isBluetooth =
                        pkg.contains("bluetooth") || name.contains("bluetooth");

                boolean isEmail =
                        pkg.contains("mail") ||
                        pkg.contains("gmail") ||
                        pkg.contains("outlook") ||
                        pkg.contains("k9") ||
                        pkg.contains("proton") ||
                        pkg.contains("fairmail") ||
                        pkg.contains("spark") ||
                        name.contains("mail") ||
                        name.contains("email");

                if ((emailOnly && isEmail) || (!emailOnly && isBluetooth)) {
                    Intent targeted = new Intent(base);
                    targeted.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    targeted.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    targets.add(targeted);
                }
            }

            if (targets.isEmpty()) {
                Toast.makeText(
                        activity,
                        emailOnly
                                ? "Keine E-Mail-App zum Senden gefunden."
                                : "Kein Bluetooth-Sendeziel gefunden.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Intent first = targets.remove(0);
            Intent chooser = Intent.createChooser(
                    first, emailOnly ? "Per E-Mail senden" : "Per Bluetooth senden");
            if (!targets.isEmpty()) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                        targets.toArray(new Intent[0]));
            }
            activity.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(activity,
                    "Foto konnte nicht geteilt werden: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
