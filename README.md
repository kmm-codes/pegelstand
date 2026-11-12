# Pegelstand

Wasser-Tracker für Android. Das Homescreen-Widget ist die Hauptsache: Ein Tap zählt ein Glas,
die Flasche füllt sich. Die App zeigt Verlauf und Einstellungen. Alles bleibt lokal auf dem Gerät.

- Widget: Tap auf die Flasche zählt die eingestellte Menge (Standard 250 ml), der Füllstand
  steigt animiert, dazwischen bewegt sich das Wasser leicht (abschaltbar); Tap daneben öffnet die
  App. Rückgängig-Pfeil, Serie in Tagen, Gold bei erreichtem Tagesziel.
- App: heutiger Stand, Serie, Wochenbalken, Monatskalender, Einstellungen für Tagesziel,
  Menge pro Tap, Wasserfarbe und ob sich die Flasche füllt oder leert.
- Erinnerungen kommen nur, wenn man hinter dem Tagesplan liegt.

Der genaue Umfang steht in [SPEC.md](SPEC.md), Testbefehle für Debug-Builds in
[docs/debug-commands.md](docs/debug-commands.md).

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
