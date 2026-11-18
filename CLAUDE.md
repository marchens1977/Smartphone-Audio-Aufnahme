# Projekt-Tagebuch: Smartphone-Audio-Aufnahme

## 1. Worum geht es?

Eine **Android-App** für das Samsung-Smartphone des Nutzers. Alles wird **auf dem Handy** gespeichert,
kein Pi nötig (Wunsch des Nutzers, 2026-11-18). Drei Bereiche:

1. **Stimme** – Mikrofon aufnehmen → `.m4a`
2. **Medien** – Ton aufnehmen, den das Handy abspielt (MediaProjection + `AudioPlaybackCaptureConfiguration`,
   ab Android 10) → `.m4a`
3. **Diktieren** – `SpeechRecognizer` (de-DE), hört fortlaufend zu (startet nach jedem Ergebnis neu),
   gesprochene Satzzeichen („Punkt“, „neue Zeile“ …) werden umgewandelt. Text: Kopieren/Teilen/als `.txt` speichern.
   Text bleibt in SharedPreferences erhalten.

### Dateien
- `app/src/main/java/de/smartphone/aufnahme/`
  - `MainActivity.java` – Oberfläche komplett im Code gebaut (keine Layout-XML), dunkles Design wie die Pi-Seiten.
    Fragt den Zustand des Dienstes alle 200 ms ab (statische Felder in `AufnahmeService`).
  - `AufnahmeService.java` – Vordergrund-Dienst (Typ `microphone` bzw. `mediaProjection`), `AudioRecord` → PCM,
    Pegel, Pause, Benachrichtigung mit Pause/Stopp. Mikro mono, Medien stereo, 44,1 kHz.
  - `AacSchreiber.java` – PCM → AAC per `MediaCodec` + `MediaMuxer` (.m4a), erst in den Cache.
  - `Speicher.java` – MediaStore: Ton nach `Music/Smartphone-Aufnahme/`, Text nach `Documents/Smartphone-Aufnahme/`.
    Keine Speicher-Berechtigung nötig (App sieht/löscht eigene Dateien).
- `app/src/androidTest/…/AufnahmeTest.java` – Tests auf dem Emulator: AAC-Datei (Dauer, Format),
  Speichern/Liste/Löschen, Text speichern, komplette Mikrofon-Aufnahme über den Dienst.
- `.github/workflows/app-bauen.yml` – bei Push auf `main` (außer .md/pi-version): bauen, Emulator-Tests (API 34),
  Bildschirmfoto als Artefakt „ergebnisse“, APK als Release `neueste-version`.
  Download-Link: https://github.com/marchens1977/Smartphone-Audio-Aufnahme/releases/download/neueste-version/Smartphone-Aufnahme.apk
- `app/schluessel.keystore` – fester Signier-Schlüssel (Passwort `aufnahme`), damit Updates drüber installierbar sind.
  Bewusst im Repository (Hobby-App, nicht im Play Store).
- `pi-version/` – erste Version (Aufnahmen am Raspberry Pi speichern, Port 8444). Zurzeit nicht genutzt.

### Bauen
- Hier im Claude-Container gibt es kein Android-SDK (dl.google.com gesperrt). Zum schnellen Prüfen:
  `javac --release 17 -cp android-all.jar` mit `org.robolectric:android-all:14-robolectric-10818077`
  von Maven Central. Richtig gebaut und getestet wird in GitHub Actions.
- AGP 8.5.2, Gradle 8.9 (über `gradle/actions/setup-gradle`, kein Wrapper im Repo), compileSdk/targetSdk 34, minSdk 29, Java 17.

## 2. Entscheidungen

- **Echte App statt Webseite**: Medienaudio kann eine Webseite in Chrome auf Android nicht aufnehmen.
- **Java ohne Bibliotheken**, Oberfläche im Code: wenig, was beim Bauen schiefgehen kann.
- **Verteilung über GitHub-Release** statt Play Store: Nutzer hat nur Handy + Pi, kein PC mit Android Studio.
- **AAC/m4a**: klein (~1 MB/Min.), spielt überall.
- targetSdk 34 (nicht 35), damit Android 15 keine Vollbild-Darstellung erzwingt.
- Grenzen von Android (dem Nutzer erklärt): Apps wie Netflix/Spotify sperren die Medienaufnahme;
  Telefonate nicht aufnehmbar; Medienaufnahme muss jedes Mal bestätigt werden.

## 3. Verlauf

| Datum | Was |
|---|---|
| 2026-11-18 | Erste Version als Pi-Webserver (Handy nimmt auf, Pi speichert, MP3) – jetzt in `pi-version/` |
| 2026-11-18 | Nutzer-Ziel präzisiert: Stimme + Medienaudio + Diktieren, Speichern auf dem Handy → Android-App gebaut |

## 4. Offene Ideen

- Medien + Mikrofon gleichzeitig (gemischt) aufnehmen
- Aufnahmen umbenennen
- Diktat aus einer vorhandenen Aufnahme (Datei → Text)
- Schnellstart-Kachel in den Schnelleinstellungen

## 5. Zusammenarbeit

- Nutzer ist Programmier-Laie: einfaches Deutsch, kleine nummerierte Schritte,
  klar sagen, was Claude übernimmt und was der Nutzer tun muss.
- Claude übernimmt alles, was möglich ist. Vor dem Weitergeben testen (Emulator-Tests in GitHub Actions
  müssen grün sein, Bildschirmfoto ansehen).
- Hardware: Samsung-Smartphone (Chrome) und Raspberry Pi 400.
- Befehle fürs Pi-Terminal immer mit `echo start` beginnen und `echo ende` beenden (Steuerzeichen beim Einfügen).
