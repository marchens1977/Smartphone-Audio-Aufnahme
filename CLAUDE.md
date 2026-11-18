# Projekt-Tagebuch: Smartphone-Audio-Aufnahme

## 1. Worum geht es?

Der Nutzer nimmt mit seinem Smartphone (Samsung, Chrome) Ton auf; die Aufnahmen werden auf seinem
Raspberry Pi 400 gespeichert.

- `aufnahme.py` – läuft auf dem Pi (nur Python-Standardbibliothek).
  - Webserver auf Port **8444** mit **https** (selbst erstelltes Zertifikat `zertifikat.pem`/`schluessel.pem`,
    wird beim ersten Start mit `openssl` erzeugt, nicht im Repository, steht in `.gitignore`).
  - Liefert die Handy-Seite aus (im Python-Code eingebettet, Variable `SEITE`).
  - Handy nimmt per `MediaRecorder` auf (Chrome: `.webm`/Opus), lädt per `POST /aufnahme?endung=…` hoch.
  - Pi speichert in `~/Aufnahmen/Aufnahme_JJJJ-MM-TT_HH-MM-SS.webm` (erst als `.teil`, dann umbenannt)
    und wandelt im Hintergrund mit `ffmpeg` in `.mp3` um (Original wird danach gelöscht).
  - `GET /aufnahmen` = Liste (JSON), `GET /aufnahmen/<name>` = Datei (mit Range, zum Spulen),
    `POST /loeschen` = löschen. `aufnahme_pfad()` lässt nur Dateien direkt im Ordner zu (Sicherheit).
  - Am Handy: Uhr, Pegel-Balken, Bildschirm-wach-halten (Wake Lock), Liste mit Anhören/Download/Löschen.
    Pi nicht erreichbar → Download-Link für die Aufnahme direkt am Handy.
- `einrichten.sh` – lädt neueste Version von `main`, installiert `ffmpeg`, Autostart + Startmenü-Eintrag.
- `vorschau.png` – Bildschirmfoto der Handy-Seite.

## 2. Entscheidungen

- Eigenes Repository und eigener Port (8444), damit es unabhängig vom Projekt
  **Smartphone-Mikrofon** (Port 8443) läuft.
- Webseite statt echter Android-App: keine Installation, kein App-Store, funktioniert mit Chrome.
- https ist nötig, weil Chrome das Mikrofon sonst nicht freigibt.
- MP3-Umwandlung am Pi, weil `.webm` aus Chrome keine Gesamtlänge anzeigt und nicht überall abspielt.
- Aufnahme komplett am Handy, Hochladen erst beim Stopp (einfacher als Streaming während der Aufnahme).

## 3. Verlauf

| Datum | Was |
|---|---|
| 2026-11-18 | Aufnahme-Modus zuerst im Repository Smartphone-Mikrofon gebaut (Branch `ccr-23711e9a-dpxr33`) |
| 2026-11-18 | Als eigenständige App hierher übernommen: MP3-Umwandlung, Pegel-Balken, Einrichtungs-Skript, Doku. Mit simuliertem Handy-Browser getestet (Aufnehmen, Speichern, MP3, Abspielen, Löschen) |

## 4. Offene Ideen

- Aufnahmen umbenennen können
- Lange Aufnahmen schon während der Aufnahme in Teilen hochladen (Schutz, falls das Handy abbricht)
- Aufnahme vom Pi aus starten / mehrere Handys gleichzeitig

## 5. Zusammenarbeit

- Einfügen ins Pi-Terminal fügt manchmal Steuerzeichen (`\E[200~`) ein. Deshalb Befehle immer so geben:
  erste Zeile `echo start`, dann die Befehle, letzte Zeile `echo ende`.
- Nutzer ist Programmier-Laie: einfaches Deutsch, kleine nummerierte Schritte,
  klar sagen, was Claude übernimmt und was der Nutzer tun muss.
- Claude übernimmt alles, was möglich ist. Vor dem Hochladen testen (Playwright mit
  `--use-fake-device-for-media-stream --use-fake-ui-for-media-stream`).
- Hardware: Samsung-Smartphone (Chrome) und Raspberry Pi 400.
