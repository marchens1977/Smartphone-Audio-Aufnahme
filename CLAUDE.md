# Projekt-Tagebuch: Smartphone-Audio-Aufnahme

## 1. Worum geht es?

Eine **Android-App** für das Samsung-Smartphone des Nutzers. Alles wird **auf dem Handy** gespeichert,
kein Pi nötig (Wunsch des Nutzers, 2026-11-18). Vier Bereiche:

1. **Stimme** – Mikrofon aufnehmen → `.m4a`
2. **Medien** – Ton aufnehmen, den das Handy abspielt (MediaProjection + `AudioPlaybackCaptureConfiguration`,
   ab Android 10) → `.m4a`
3. **Diktieren** – `SpeechRecognizer` (de-DE), hört fortlaufend zu (startet nach jedem Ergebnis neu).
   `DiktatSammler.java` übernimmt die Vorschau (Zwischenergebnis), wenn das Endergebnis leer ist oder fehlt
   (Samsung!) – Regeln aus dem Fisch-Fang-Tracker (`DictationMerge`), ohne Doppelungen.
   gesprochene Satzzeichen („Punkt“, „neue Zeile“ …) werden umgewandelt. Text: Kopieren/Teilen/als `.txt` speichern.
   Text bleibt in SharedPreferences erhalten.
4. **Vorlesen** – Text einfügen (Zwischenablage), Android-`TextToSpeech` (de-DE, Tempo 0,8/1,0/1,25):
   „Anhören“ (`speak`) oder „Als Aufnahme speichern“ (`synthesizeToFile` → WAV-Stücke ≤ 3000 Zeichen →
   `Vorleser.wavZuM4a` → `.m4a` in Music). Anlass: Claude-Sprachmodus lässt sich nicht mitschneiden.
   **„🗣 Stimme: …“-Knopf**: listet alle deutschen Stimmen aller Vorlese-Programme des Handys (Samsung, Google …,
   gefunden über `TTS_SERVICE`), antippen = Probesatz, „Übernehmen“ speichert Programm + Stimme
   (`tts_programm`, `tts_stimme`, `tts_anzeige` in SharedPreferences). Fehlt das Programm später → Handy-Standard.

### Dateien
- `app/src/main/java/de/smartphone/aufnahme/`
  - `MainActivity.java` – Oberfläche komplett im Code gebaut (keine Layout-XML), dunkles Design wie die Pi-Seiten.
    Fragt den Zustand des Dienstes alle 200 ms ab (statische Felder in `AufnahmeService`).
  - `AufnahmeService.java` – Vordergrund-Dienst (Typ `microphone` bzw. `mediaProjection`), `AudioRecord` → PCM,
    Pegel, Pause, Benachrichtigung mit Pause/Stopp. Mikro mono, Medien stereo, 44,1 kHz.
  - `AacSchreiber.java` – PCM → AAC per `MediaCodec` + `MediaMuxer` (.m4a), erst in den Cache.
  - `Vorleser.java` – Text teilen, TTS in WAV-Dateien, WAV-Kopf lesen, zu m4a zusammensetzen.
  - `StimmenListe.java` – reine Regeln für „Stimme wählen“: nur Deutsch + installiert, sortiert
    (Programm, Deutschland vor AT/CH, ohne Internet zuerst, Qualität), Namen wie „Google – Stimme 2 · Internet nötig“.
  - `DiktatSammler.java` – reine Diktat-Regeln (ohne Android): Vorschau merken, Neubeginn nach Pause erkennen,
    gesprochene Satzzeichen, Großschreibung (nach Satzende und neuer Zeile). Getestet in `DiktatSammlerTest`.
  - `UpdateSuche.java` – Update-Suche (übernommen aus dem Fisch-Fang-Tracker, dort `UpdateChecker.java`):
    fragt `api.github.com/repos/marchens1977/Smartphone-Audio-Aufnahme/releases/latest`, liest die Nummer aus
    dem Release-Namen `app-v1.0.N`, vergleicht mit dem eigenen `versionCode`. Neuer + APK vorhanden →
    Fenster „Update verfügbar“ → „Jetzt herunterladen“ öffnet die APK im Browser. Beim Start von selbst
    (höchstens 1x pro Stunde, still), von Hand über **⚙ oben rechts** → „Nach Updates suchen“
    (zeigt auch „neueste Version“ bzw. „Internet prüfen“). Kein Schlüssel nötig (öffentliches Projekt).
  - `Speicher.java` – MediaStore: Ton nach `Music/Smartphone-Aufnahme/`, Text nach `Documents/Smartphone-Aufnahme/`.
    Keine Speicher-Berechtigung nötig (App sieht/löscht eigene Dateien).
- `app/src/test/…/UpdateSucheTest.java`, `DiktatSammlerTest.java`, `StimmenListeTest.java` – Tests ohne Handy (siehe „6. Tests“).
- `werkzeuge/update-test.sh` – startet ALLE Tests ohne Handy, ohne Android-Werkzeuge (Claude-Container oder Pi).
- `app/src/androidTest/…/AufnahmeTest.java` – Tests auf dem Emulator: AAC-Datei (Dauer, Format),
  Speichern/Liste/Löschen, Text speichern, komplette Mikrofon-Aufnahme über den Dienst.
- `.github/workflows/app-bauen.yml` – bei Push auf `main` (außer .md/pi-version): Tests ohne Handy, bauen,
  Emulator-Tests (API 34), Bildschirmfoto als Artefakt „ergebnisse“, APK als **neues Release `app-v1.0.N`**
  (N = `GITHUB_RUN_NUMBER`, als „latest“ markiert; alte Releases bleiben stehen).
  Download-Link: https://github.com/marchens1977/Smartphone-Audio-Aufnahme/releases/latest/download/Smartphone-Aufnahme.apk
  (Das alte Release `neueste-version` bleibt stehen, wird aber nicht mehr erneuert – Link dorthin ist veraltet.)
- `app/schluessel.keystore` – fester Signier-Schlüssel (Passwort `aufnahme`), damit Updates drüber installierbar sind.
  Bewusst im Repository (Hobby-App, nicht im Play Store).
- `pi-version/` – erste Version (Aufnahmen am Raspberry Pi speichern, Port 8444). Zurzeit nicht genutzt.

### Bauen
- Hier im Claude-Container gibt es kein Android-SDK (dl.google.com gesperrt). Zum schnellen Prüfen:
  `javac --release 17 -cp android-all.jar` mit `org.robolectric:android-all:14-robolectric-10818077`
  von Maven Central. Richtig gebaut und getestet wird in GitHub Actions.
- **Versionsnummer:** `versionCode` = N = `GITHUB_RUN_NUMBER`, `versionName` = `1.0.N` (in `app/build.gradle`).
  Release-Name `app-v1.0.N` → passt immer zur App-Version. Lokal gebaut: N = 1.
- AGP 8.5.2, Gradle 8.9 (über `gradle/actions/setup-gradle`, kein Wrapper im Repo), compileSdk/targetSdk 34, minSdk 29, Java 17.

## 2. Entscheidungen

- **Echte App statt Webseite**: Medienaudio kann eine Webseite in Chrome auf Android nicht aufnehmen.
- **Java ohne Bibliotheken**, Oberfläche im Code: wenig, was beim Bauen schiefgehen kann.
- **Verteilung über GitHub-Release** statt Play Store: Nutzer hat nur Handy + Pi, kein PC mit Android Studio.
- **AAC/m4a**: klein (~1 MB/Min.), spielt überall.
- **Design in Mets-Farben** (Wunsch des Nutzers): Blau `#002D72`, Orange `#FF5910`, Weiß.
- **Senden** über die Android-Auswahl (`ACTION_SEND`) – deckt WhatsApp, E-Mail und alles andere ab.
- targetSdk 34 (nicht 35), damit Android 15 keine Vollbild-Darstellung erzwingt.
- **Update-Suche wie im Fisch-Fang-Tracker** (Wunsch des Nutzers, 2026-11-19): Release-Namen `app-v1.0.N`
  statt dem immer gleichen `neueste-version` (damit war kein Vergleich möglich). Die App hatte keine
  Einstellungen → Nutzer wählte ein **⚙ oben rechts** (Fenster mit App-Version + Knopf).
- **Nie `REQUEST_INSTALL_PACKAGES`** (keine Selbst-Installation): Play Protect hat deshalb den
  Fisch-Fang-Tracker 1.0.6 als „schädlich“ blockiert. Download läuft über den Browser. Test prüft das.
- Erlaubnis `INTERNET` neu (nur für die Update-Suche; Android fragt dafür nicht nach).
- **Keine kostenpflichtigen Stimmen** (Nutzer, 2026-11-19): ElevenLabs verworfen (Kosten pro Text,
  Nutzer will keine Credits verbrauchen). Stattdessen kostenlose Handy-Stimmen zur Auswahl.
- Grenzen von Android (dem Nutzer erklärt): Apps wie Netflix/Spotify sperren die Medienaufnahme;
  Telefonate nicht aufnehmbar; Medienaufnahme muss jedes Mal bestätigt werden.

## 3. Verlauf

| Datum | Was |
|---|---|
| 2026-11-18 | Erste Version als Pi-Webserver (Handy nimmt auf, Pi speichert, MP3) – jetzt in `pi-version/` |
| 2026-11-18 | Nutzer-Ziel präzisiert: Stimme + Medienaudio + Diktieren, Speichern auf dem Handy → Android-App gebaut |
| 2026-11-18 | Mets-Farben, oranger „📤 Senden“-Knopf (WhatsApp/E-Mail mit Betreff) |
| 2026-11-18 | Fehler behoben: am Aufnahme-Ende konnten die letzten Stücke fehlen (Ende-Zeichen an den Umwandler wurde bei vollem Puffer nicht gesendet). Gefunden durch Emulator-Test |
| 2026-11-18 | Bereich „Vorlesen“ (Text → Handy-Stimme → Tondatei), Liste der Aufnahmen auch dort sichtbar |
| 2026-11-18 | Nutzer-Test: Claude-Sprachmodus per Medien aufgenommen → 75 s leer (Sprachmodus = Gesprächs-Audio, von Android gesperrt). Stille-Erkennung eingebaut: Warnung nach 4 s, leere Medien-Aufnahme wird nicht gespeichert |
| 2026-11-19 | Update-Suche (wie Fisch-Fang-Tracker): ⚙ oben rechts → „Nach Updates suchen“, Suche beim Start, Releases heißen jetzt `app-v1.0.N` |
| 2026-11-19 | Nutzer: Update klappt (1.0.11). Fehler: Diktat-Vorschau kam nie ins Textfeld (Samsung liefert leeres/kein Endergebnis) → `DiktatSammler` übernimmt die Vorschau. Dabei behoben: „neue Zeile“ am Anfang eines Abschnitts ging verloren |
| 2026-11-19 | Vorlesen: „🗣 Stimme wählen“ – alle kostenlosen deutschen Handy-Stimmen mit Probehören; Bildschirmfoto `bildschirm-stimmen.png` im Release |

## 4. Offene Ideen

- Medien + Mikrofon gleichzeitig (gemischt) aufnehmen
- Aufnahmen umbenennen
- Diktat aus einer vorhandenen Aufnahme (Datei → Text)
- Schnellstart-Kachel in den Schnelleinstellungen
- Altes Release `neueste-version` löschen, sobald der Nutzer die neue Version installiert hat
  (nur nach Rückfrage – der alte Link führt sonst zu einer alten App)
- Im ⚙-Fenster später weitere Einstellungen (z. B. Standard-Tempo beim Vorlesen)

## 5. Zusammenarbeit

- Nutzer ist Programmier-Laie: einfaches Deutsch, kleine nummerierte Schritte,
  klar sagen, was Claude übernimmt und was der Nutzer tun muss.
- Claude übernimmt alles, was möglich ist. Vor dem Weitergeben testen (Emulator-Tests in GitHub Actions
  müssen grün sein, Bildschirmfoto ansehen).
- Hardware: Samsung-Smartphone (Chrome) und Raspberry Pi 400.
- Befehle fürs Pi-Terminal immer mit `echo start` beginnen und `echo ende` beenden (Steuerzeichen beim Einfügen).

## 6. Tests

- **Update-Suche (ohne Handy):** `app/src/test/…/UpdateSucheTest.java`, 11 Tests: Nummer aus Release-Namen,
  fremde Namen (`neueste-version`) gelten nicht als Update, APK-Link aus einer GitHub-Antwort, nur `https`,
  „neuer“-Vergleich, App-Version ↔ Release-Name passen zusammen, `build.gradle`/Workflow/Manifest stimmen
  überein, keine `REQUEST_INSTALL_PACKAGES`.
  - In GitHub Actions: `gradle testDebugUnitTest` (eigener Schritt vor dem Bauen).
  - Hier im Container oder am Pi: `bash werkzeuge/update-test.sh` (lädt Hilfsdateien von Maven Central,
    übersetzt die ganze App und startet die Tests). Ergebnis 2026-11-19: **OK (11 tests)**.
- **Diktat (ohne Handy):** `DiktatSammlerTest.java`, 11 Tests: Fall des Nutzers (Vorschau, dann leeres
  Endergebnis bzw. Sprechpause), Stopp während der Vorschau, Samsung-Neubeginn nach Pause, nichts doppelt,
  mehrere Runden, Satzzeichen/neue Zeile/Absatz. Läuft mit in `werkzeuge/update-test.sh` und in GitHub Actions.
  Ergebnis 2026-11-19: **OK (22 tests)** zusammen mit der Update-Suche.
- **Stimmen-Liste (ohne Handy):** `StimmenListeTest.java`, 7 Tests: nur Deutsch und installiert, Sortierung und
  Nummern je Programm, Länder (Österreich/Schweiz), Doppelte, fremde Programme, gespeicherte Stimme wiederfinden.
  Alle Tests zusammen 2026-11-19: **OK (29 tests)**.
- **Emulator-Tests:** `AufnahmeTest.java` (siehe oben), laufen nur in GitHub Actions.
- **Nicht automatisch getestet:** echte Abfrage bei GitHub aus der App, das Fenster „Update verfügbar“,
  der Download im Browser und das Drüberinstallieren, die echte Spracherkennung am Samsung, der Klang der Stimmen → Test-Liste unten.

## 7. Test-Liste für den Nutzer (am Handy abhaken)

**Update-Suche (nach dem ersten Bau mit Update-Suche):**
- [x] Einmalig von Hand installieren: https://github.com/marchens1977/Smartphone-Audio-Aufnahme/releases/latest/download/Smartphone-Aufnahme.apk
      (die bisher installierte Version kann noch nicht selbst suchen). Aufnahmen sind danach noch da.
- [ ] Oben rechts ist ein ⚙. Antippen → Fenster „⚙ Einstellungen“ mit „App-Version 1.0.N“ (N = Zahl).
- [ ] „Nach Updates suchen“ → Meldung „Du hast schon die neueste Version (1.0.N)“.
- [ ] Flugmodus an → „Nach Updates suchen“ → Meldung „Update-Suche nicht möglich. Bitte Internet prüfen.“
- [ ] Beim nächsten Update (nach der nächsten Änderung durch Claude): App öffnen → „Update verfügbar“
      → „Jetzt herunterladen“ → auf die Datei tippen → „Aktualisieren“ → Aufnahmen und Diktat-Text sind noch da.
- [ ] Play Protect blockiert die App nicht (höchstens die übliche Warnung „App scannen“).

**Diktieren (nach dem Update mit der Diktat-Reparatur):**
- [ ] Diktieren → 🎤 → einen Satz sprechen → kurz warten: Der Satz steht **im weißen Textfeld** (nicht nur darüber).
- [ ] Mit Pausen weitersprechen → jeder Teil wird angehängt, nichts doppelt.
- [ ] Mitten im Sprechen auf ■ tippen → das zuletzt Gesagte steht trotzdem im Textfeld.
- [ ] „Punkt“, „Komma“, „neue Zeile“ sagen → werden zu . , und Zeilenumbruch; danach Großbuchstabe.

**Vorlesen – Stimme wählen:**
- [ ] Vorlesen → Knopf „🗣 Stimme: Handy-Standard ▾“ → Liste mit Stimmen (Samsung/Google) erscheint.
- [ ] Eine Stimme antippen → Probesatz „Hallo! So klinge ich …“ in dieser Stimme.
- [ ] „Übernehmen“ → Knopf zeigt den Namen; „▶ Anhören“ und „💾 Als Aufnahme speichern“ nutzen diese Stimme.
- [ ] App schließen und wieder öffnen → gewählte Stimme ist noch eingestellt.
- [ ] Mehr Stimmen holen (kostenlos): Handy-Einstellungen → Allgemeine Verwaltung → Text-zu-Sprache →
      ⚙ beim Programm → Sprachdaten installieren → Deutsch. Danach erscheinen sie in der Liste.
