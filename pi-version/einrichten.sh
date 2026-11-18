#!/bin/bash
# Richtet die Smartphone-Audio-Aufnahme auf dem Raspberry Pi ein:
#  - laedt die neueste Version herunter
#  - installiert ffmpeg (wandelt Aufnahmen in MP3 um), falls es fehlt
#  - startet es automatisch, wenn der Pi hochfaehrt
#  - legt einen Eintrag im Startmenue an (zeigt die Adresse fuers Handy)
ORDNER="$HOME/smartphone-audio-aufnahme"
mkdir -p "$ORDNER" "$HOME/Aufnahmen" "$HOME/.config/autostart" "$HOME/.local/share/applications"
wget -q -O "$ORDNER/aufnahme.py" https://raw.githubusercontent.com/marchens1977/Smartphone-Audio-Aufnahme/main/pi-version/aufnahme.py

# ffmpeg wandelt die Aufnahmen in MP3 um (spielt ueberall ab)
if ! command -v ffmpeg >/dev/null; then
  echo "Installiere ffmpeg (kann ein paar Minuten dauern) ..."
  sudo apt-get install -y ffmpeg >/dev/null 2>&1 || \
    (sudo apt-get update >/dev/null 2>&1 && sudo apt-get install -y ffmpeg >/dev/null 2>&1) || \
    echo "Hinweis: ffmpeg ließ sich nicht installieren. Aufnahmen bleiben dann im Format .webm."
fi

# Automatischer Start (unsichtbar im Hintergrund)
cat > "$HOME/.config/autostart/smartphone-audio-aufnahme.desktop" <<EOT
[Desktop Entry]
Type=Application
Name=Smartphone-Audio-Aufnahme (Autostart)
Exec=python3 $ORDNER/aufnahme.py
Terminal=false
EOT

# Eintrag im Startmenue (Himbeere -> Zubehoer)
cat > "$HOME/.local/share/applications/smartphone-audio-aufnahme.desktop" <<EOT
[Desktop Entry]
Type=Application
Name=Smartphone-Audio-Aufnahme
Comment=Tonaufnahmen mit dem Handy - zeigt die Adresse fuers Handy
Exec=python3 $ORDNER/aufnahme.py
Icon=audio-x-generic
Terminal=true
Categories=Utility;AudioVideo;
EOT

# Alte Version beenden und neue gleich im Hintergrund starten
pkill -f "smartphone-audio-aufnahme/aufnahme.py" 2>/dev/null
sleep 1
nohup python3 "$ORDNER/aufnahme.py" >/dev/null 2>&1 &
sleep 2

IP=$(hostname -I | awk '{print $1}')
echo
echo "=================================================="
echo "  Fertig eingerichtet!"
echo "  Die Pi-Aufnahme läuft jetzt und startet ab sofort"
echo "  automatisch, wenn der Pi hochfährt."
echo
echo "  Adresse fürs Handy:   https://$IP:8444"
echo
echo "  Deine Aufnahmen findest du im Ordner: Aufnahmen"
echo
echo "  Adresse vergessen? Startmenü (Himbeere) ->"
echo "  Zubehör -> Smartphone-Audio-Aufnahme"
echo "=================================================="
