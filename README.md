# Pegelstand

Wasser-Tracker für Android. Das Homescreen-Widget ist die Hauptsache: Ein Tap zählt ein Glas,
die Flasche füllt sich. Die App zeigt Verlauf und Einstellungen. Alles bleibt lokal auf dem Gerät.

Stand: in Arbeit. Details zum geplanten Umfang stehen in [SPEC.md](SPEC.md).

## Bauen

- Voraussetzungen: JDK 21, Android SDK mit Platform 36.
- SDK-Pfad bekannt machen: `ANDROID_HOME` setzen oder `local.properties` mit `sdk.dir=…` anlegen.
- Debug-APK bauen: `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`).
- Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`.
- Tests: `./gradlew :app:testDebugUnitTest`.

## Installieren

- Gerät: Android 8.0 oder neuer.
- Per USB: USB-Debugging einschalten, dann `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Ohne Kabel: APK aufs Handy schicken, antippen und „Unbekannte Apps installieren“ für die
  jeweilige App (Dateimanager, Messenger) erlauben.
- Updates lassen sich nur über eine frühere Version installieren, wenn sie mit demselben
  Schlüssel signiert sind, also vom selben Rechner gebaut wurden. Sonst vorher deinstallieren;
  dabei gehen die Daten verloren.

## Widget hinzufügen

- Startbildschirm lange drücken, „Widgets“ wählen, „Pegelstand“ suchen und platzieren.
