# 🔴 Smartphone-Audio-Aufnahme für den Raspberry Pi

Mit diesem kleinen Programm nimmst du mit deinem Handy Ton auf, zum Beispiel Sprache, Musik oder Notizen.
Die Aufnahmen werden automatisch auf deinem Raspberry Pi gespeichert.

- Kein App-Store nötig: Die „App“ ist eine Webseite, die du am Handy in Chrome öffnest.
- Auf dem Pi muss nichts Zusätzliches programmiert werden. Es läuft mit dem Python, das schon da ist.
- Die Aufnahmen landen am Pi im Ordner **Aufnahmen** (in deinem persönlichen Ordner) als **MP3**.

![Vorschau](vorschau.png)

## Einrichten / Aktualisieren (einmalig am Raspberry Pi)

1. Öffne am Pi das **Terminal** (schwarzes Fenster-Symbol oben in der Leiste).
2. Füge diese Zeilen ein (Rechtsklick → Einfügen) und drücke **Enter**:

   ```
   echo start
   wget -qO- https://raw.githubusercontent.com/marchens1977/Smartphone-Audio-Aufnahme/main/einrichten.sh | bash
   echo ende
   ```

3. Falls nach dem Passwort gefragt wird: dein Pi-Passwort eingeben (man sieht beim Tippen nichts, das ist normal).
4. Am Ende steht die **Adresse fürs Handy** da, z. B. `https://192.168.178.50:8444`. Schreib sie dir auf.

Danach startet das Programm jedes Mal automatisch, wenn der Pi hochfährt.
Adresse vergessen? Startmenü (Himbeere) → **Zubehör** → **Smartphone-Audio-Aufnahme**.

Zum **Aktualisieren** auf eine neue Version einfach dieselben Zeilen nochmal einfügen.

## Am Handy (einmalig)

1. Das Handy muss im **selben WLAN** sein wie der Pi.
2. Öffne **Chrome** und tippe die Adresse vom Pi ein (mit `https://` am Anfang und `:8444` am Ende).
3. Chrome warnt: „Die Verbindung ist nicht privat“. Das ist hier normal und ungefährlich, weil der Pi sich sein
   Sicherheits-Zertifikat selbst ausgestellt hat. Tippe auf **Erweitert** → **Weiter zu … (unsicher)**.
4. Tipp: Im Chrome-Menü (⋮) → **Zum Startbildschirm hinzufügen**. Dann hast du ein Symbol wie bei einer App.

## Aufnehmen

1. Auf den großen **roten Knopf** tippen. Beim ersten Mal fragt Chrome nach dem Mikrofon → **Zulassen**.
2. Die Uhr läuft, der grüne Balken zeigt, dass das Mikrofon etwas hört.
3. **Bildschirm anlassen**, während aufgenommen wird. Die Seite versucht, ihn selbst wach zu halten.
4. Zum Beenden nochmal auf den Knopf (⏹) tippen.
5. Die Aufnahme wird zum Pi geschickt. Nach ein paar Sekunden erscheint sie in der Liste als MP3.

In der Liste kannst du jede Aufnahme:
- ▶ **anhören**
- ⬇ **aufs Handy laden** (landet im Ordner „Downloads“ am Handy)
- 🗑 **löschen**

Ist der Pi beim Stoppen nicht erreichbar, geht die Aufnahme nicht verloren:
Es erscheint ein Link **„Aufnahme aufs Handy speichern“**.

## Wo finde ich die Aufnahmen am Pi?

Dateimanager öffnen → Ordner **Aufnahmen**. Dateinamen enthalten Datum und Uhrzeit,
z. B. `Aufnahme_2026-11-18_14-30-05.mp3`.

## Wenn etwas nicht klappt

| Problem | Lösung |
|---|---|
| Seite lädt nicht | Gleiches WLAN? Pi an? Adresse mit `https://` und `:8444`? Am Pi die Einrichtungs-Zeilen nochmal einfügen. |
| „Mikrofon gesperrt“ | In Chrome links neben der Adresse auf das Symbol tippen → Berechtigungen → Mikrofon → **Zulassen**, Seite neu laden. |
| „Kein Mikrofon gefunden“ | Android-Einstellungen → Apps → Chrome → Berechtigungen → Mikrofon → **Erlauben**. Andere Apps schließen, die gerade das Mikrofon nutzen. |
| Aufnahme bleibt `.webm` statt `.mp3` | Am Pi fehlt `ffmpeg`. Im Terminal: `sudo apt install ffmpeg` |
| Aufnahme bricht ab | Bildschirm ist ausgegangen oder Chrome wurde verlassen. Während der Aufnahme auf der Seite bleiben. |

## Technik (kurz erklärt)

- `aufnahme.py` läuft am Pi. Es ist ein kleiner Webserver (ein Programm, das Webseiten ausliefert) auf
  Port **8444**. Der „Port“ ist wie eine Hausnummer für Programme auf dem Pi.
- **https** (verschlüsselte Verbindung) ist nötig, weil Chrome sonst das Mikrofon nicht erlaubt.
  Das Zertifikat (`zertifikat.pem`, `schluessel.pem`) erstellt der Pi beim ersten Start selbst.
- Das Handy nimmt im Browser auf (Format `.webm`) und schickt die Datei an den Pi.
- Der Pi wandelt sie mit dem Programm **ffmpeg** in **MP3** um, weil MP3 überall abspielt.
- `einrichten.sh` lädt die neueste Version, installiert ffmpeg, richtet den Autostart und den Startmenü-Eintrag ein.

Beenden von Hand (falls im Terminal gestartet): **Strg + C**.

## Verwandtes Projekt

[Smartphone-Mikrofon](https://github.com/marchens1977/Smartphone-Mikrofon): das Handy als
Sprach-Fernbedienung für den Pi (Port 8443). Beide Programme können gleichzeitig laufen.
