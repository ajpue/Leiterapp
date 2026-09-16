# Leiterprüfung Android v0.3.0

Schlanke Offline-App für die Leiterprüfung auf älteren Android-Geräten.

## Zielgerät
- Android 7.0 oder neuer
- minSdk 24
- ausgelegt für ältere Samsung-/Moto-Geräte
- im laufenden Betrieb ohne Internet nutzbar

## Funktionen
- 300 feste Leiter-IDs: `L-0001` bis `L-0300`
- Barcode-/QR-Scan und manuelle Eingabe
- Prüfer, Standort, Leiterart und Prüfintervall
- automatischer Zeitstempel und nächster Prüftermin
- 6 kompakte Prüfschritte
- je Schritt: `i.O.`, `Mangel`, `nicht zutreffend`
- Gesamtstatus `i.O.` oder `MANGEL / GESPERRT`
- Mangelbeschreibung ist bei mindestens einem Mangel Pflicht
- optionales Foto
- lokale SQLite-Historie
- XLSX-Export für alle Prüfungen oder die ausgewählte Leiter
- SD-/Backup-Ordner über Android-Dateiauswahl
- Backup nach jeder Prüfung
- `leiterpruefung_LATEST.json`
- tägliches Backup `leiterpruefung_YYYY-MM-DD.json`
- Aufbewahrung ca. 30 Tage
- Restore nur nach ausdrücklicher Bestätigung
- Backup teilen / per Mail senden

## 6 Prüfschritte
1. Holme und Sprossen/Stufen
2. Schrauben, Nieten und Verbindungen
3. Füße, Gelenke und Spreizsicherung
4. Verriegelungen / bewegliche Teile
5. Kennzeichnung / allgemeiner Zustand
6. Gesamtprüfung / sicher verwendbar

## Build in GitHub
Das Projekt enthält `.github/workflows/android-build.yml`.

Nach dem Hochladen in ein GitHub-Repository:
1. **Actions** öffnen.
2. Workflow **Android APK bauen** auswählen.
3. Nach erfolgreichem Lauf das Artifact **Leiterpruefung-Android-debug** herunterladen.
4. Darin liegt `app-debug.apk`.

Der Workflow nutzt Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3 und API 35.

## Betrieb / Datensicherung
Beim ersten Einsatz unter **SD-/Backup-Ordner wählen** einen Ordner auf der SD-Karte auswählen.
Danach wird nach jeder gespeicherten Prüfung automatisch gesichert.

Die App speichert ihre Arbeitsdaten lokal in SQLite. Das JSON-Backup ist für Gerätewechsel/Wiederherstellung gedacht.
