# Leiterprüfung Android v0.7.3

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

## Neu in v0.4.0
- Leiterbestand direkt in der App
- Zähler: `x von 300 Leitern im Bestand`
- Suche nach Leiter-ID, Standort oder Leiterart
- Umschaltung zwischen `nur erfasste Leitern` und `alle 300 Leiter-IDs`
- Ampelanzeige:
  - GRÜN = Prüfung noch gültig und letzter Status i.O.
  - ROT = fällig/überfällig, Mangel/Gesperrt oder noch nie geprüft
- Prüfintervall fest auf 12 Monate gesetzt

## Neu in v0.5.0
- Bei Eingabe oder Scan einer bereits bekannten Leiter-ID werden Stammdaten aus der letzten Prüfung automatisch übernommen:
  - Standort
  - Leiterart
- Das Prüfintervall bleibt fest auf 12 Monate.
- Der zuletzt verwendete Prüfername wird appweit gemerkt.
- Alte Prüfergebnisse, Mängel und Fotos werden bewusst nicht übernommen.

## Neu in v0.5.1
- Neues veränderbares Feld `Sprossen/Stufen Anzahl`
- Wertebereich 1 bis 100
- Bei bekannter Leiter-ID wird die zuletzt gespeicherte Anzahl automatisch übernommen
- Bei Nachprüfung frei änderbar
- Sprossen-/Stufenanzahl wird in Historie, Backup und Excel-Export gespeichert
- Datenbankmigration von Version 1 auf 2 erhält vorhandene Prüfungen

## Neu in v0.6.0
- Erfasste Leitern können **deaktiviert** statt gelöscht werden.
- Der Button `Leiter deaktivieren` wird erst aktiv, wenn eine gültige bereits erfasste Leiter-ID eingegeben oder gescannt wurde.
- Beim Deaktivieren kann ein Grund angegeben werden, z. B. `verschrottet`, `verloren`, `ausgemustert`.
- Historie und Prüfungen bleiben vollständig erhalten.
- Deaktivierte Leitern erscheinen im Leiterbestand **grau** als `AUSSER BETRIEB`.
- Für deaktivierte Leitern können keine neuen Prüfungen gespeichert werden.
- Derselbe Button wird bei einer deaktivierten Leiter zu `Leiter reaktivieren`.
- Deaktivierungsstatus und Grund werden im Backup mitgesichert.
- Datenbankmigration erhält vorhandene Prüfungen.

## Neu in v0.6.1
- Kritischen Absturz beim Speichern behoben.
- Sprossen/Stufen Anzahl ist jetzt eine Auswahlliste von 1 bis 100.
- Auswahl ist vor dem Speichern Pflicht.
- Bei bekannten Leiter-IDs wird die zuletzt gespeicherte Anzahl automatisch vorausgewählt.
- Deaktivieren/Reaktivieren und Bestand bleiben erhalten.

## Neu in v0.7.0
- Prüffotos direkt ansehen.
- Prüffotos mit Sicherheitsabfrage löschen.
- Historie als eigene Ansicht mit einzelnen Prüfungen.
- Alte Prüffotos aus der Historie ansehen oder löschen.
- Prüffotos gezielt per Bluetooth senden.
- Prüffotos gezielt per E-Mail senden.
- Andere Freigabeziele werden bewusst nicht angeboten.
- Nach dem Löschen eines historischen Fotos wird das Backup aktualisiert.

## Neu in v0.7.1
- `PRÜFUNG SPEICHERN` ist jetzt der erste Aktionsbutton nach der Fotoaufnahme.
- Speicherbutton deutlich größer, fett und grün hervorgehoben.
- Foto ansehen/löschen sowie Bluetooth/E-Mail folgen erst danach.

## Neu in v0.7.2
- Kritischen Foto-Zuordnungsfehler behoben.
- Ein neu aufgenommenes Foto wird eindeutig an die aktuell ausgewählte Leiter-ID gebunden.
- Beim Wechsel auf eine andere gültige Leiter-ID wird ein noch nicht gespeichertes Foto verworfen.
- Nach erfolgreichem Speichern wird die aktuelle Fotoauswahl zurückgesetzt.
- Ein Foto von `L-0001` kann dadurch nicht mehr versehentlich bei `L-0002` angezeigt oder gespeichert werden.
- Gespeicherte Prüffotos bleiben in der Historie der richtigen Leiter erhalten.
- Fotodateinamen enthalten zusätzlich die Leiter-ID.

## Neu in v0.7.3
- Neue Backups sind ZIP-Dateien statt reinem JSON.
- Das ZIP enthält `backup.json` und alle vorhandenen Prüffotos unter `photos/`.
- Beim Gerätewechsel werden die Fotos beim Restore auf das neue Gerät kopiert.
- Die gespeicherten `photo_path`-Einträge werden automatisch auf die neuen lokalen Pfade gesetzt.
- Automatische Backups: `leiterpruefung_LATEST.zip` plus tägliche ZIP-Dateien.
- 30-Tage-Aufbewahrung bleibt bestehen.
- Geteilte Backups sind jetzt ZIP-Dateien mit Daten und Fotos.
- Alte JSON-Backups bleiben lesbar, enthalten aber naturgemäß keine Bilddateien.
