#!/usr/bin/env python3
"""
Smartphone-Audio-Aufnahme fuer den Raspberry Pi
===============================================
Starten auf dem Pi mit:   python3 aufnahme.py
Dann am Handy (gleiches WLAN) die angezeigte Adresse in Chrome oeffnen.

Das Handy nimmt mit seinem Mikrofon auf und schickt die Aufnahme hierher.
Der Pi speichert sie im Ordner ~/Aufnahmen (und wandelt sie in MP3 um,
wenn das Programm ffmpeg vorhanden ist).
Es wird nur Python verwendet, das auf dem Pi schon vorhanden ist.
"""

import datetime
import http.server
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import threading
import urllib.parse

PORT = 8444                                     # eigene Adresse (das Pi-Mikrofon nutzt 8443)
ORDNER = os.path.dirname(os.path.abspath(__file__))
ZERTIFIKAT = os.path.join(ORDNER, "zertifikat.pem")
SCHLUESSEL = os.path.join(ORDNER, "schluessel.pem")
AUFNAHMEN = os.path.expanduser("~/Aufnahmen")   # hier landen die Aufnahmen
MAX_AUFNAHME = 1024 * 1024 * 1024               # hoechstens 1 GB pro Aufnahme
ENDUNGEN = {"mp3": "audio/mpeg", "webm": "audio/webm", "m4a": "audio/mp4", "ogg": "audio/ogg"}


# ---------------------------------------------------------------- Aufnahmen verwalten
def aufnahme_pfad(name):
    """Pfad einer Aufnahme - nur fertige Dateien direkt im Aufnahmen-Ordner (Sicherheit)."""
    if (not name or name != os.path.basename(name) or name.startswith(".")
            or name.rsplit(".", 1)[-1] not in ENDUNGEN):
        return None
    datei = os.path.join(AUFNAHMEN, name)
    return datei if os.path.isfile(datei) else None


def liste_aufnahmen():
    if not os.path.isdir(AUFNAHMEN):
        return []
    namen = [n for n in os.listdir(AUFNAHMEN) if aufnahme_pfad(n)]
    return [{"name": n, "groesse": os.path.getsize(os.path.join(AUFNAHMEN, n))}
            for n in sorted(namen, reverse=True)]


def in_mp3_umwandeln(datei):
    """Wandelt die Aufnahme im Hintergrund in MP3 um (spielt ueberall, zeigt die Laenge an).
    Klappt es nicht, bleibt einfach die Originaldatei erhalten."""
    if not shutil.which("ffmpeg"):
        return
    ziel = datei.rsplit(".", 1)[0] + ".mp3"
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", datei, "-vn",
                        "-codec:a", "libmp3lame", "-q:a", "4", "-f", "mp3", ziel + ".teil"],
                       check=True, timeout=3600)
        os.replace(ziel + ".teil", ziel)
        os.remove(datei)
        print(f"  In MP3 umgewandelt: {os.path.basename(ziel)}")
    except Exception as fehler:
        print(f"  MP3-Umwandlung nicht moeglich ({fehler}) - Original bleibt erhalten.")
        if os.path.exists(ziel + ".teil"):
            os.remove(ziel + ".teil")


# ---------------------------------------------------------------- Handy-Seite
SEITE = """<!DOCTYPE html>
<html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pi-Aufnahme</title>
<style>
 *{box-sizing:border-box}
 body{font-family:sans-serif;background:#12202b;color:#eee;margin:0;padding:16px;text-align:center}
 h1{font-size:1.4em;margin:8px 0 10px}
 #info{font-size:.95em;color:#bbb}
 #knopf{width:180px;height:180px;border-radius:50%;border:none;font-size:70px;background:#c62828;color:#fff;margin:14px auto 4px;display:block}
 #knopf.an{background:#555}
 #uhr{font-size:2.4em;font-variant-numeric:tabular-nums;margin:4px 0}
 #pegel{height:10px;background:#1b2e3c;border-radius:5px;overflow:hidden;margin:4px 20% 10px}
 #pegel div{height:100%;width:0;background:#66bb6a;transition:width .1s}
 #status{min-height:1.5em;font-size:1.1em;margin:8px 0}
 #liste{text-align:left;margin-top:14px}
 .auf{background:#1b2e3c;border-radius:8px;padding:10px;margin:8px 0}
 .auf audio{width:100%;margin:6px 0}
 .auf a,.auf button{color:#9ecbff;background:none;border:none;font-size:1em;margin-right:14px;padding:4px 0}
 small{color:#999}
</style></head><body>
<h1>🔴 Pi-Aufnahme</h1>
<div id="info">Roter Knopf = Aufnahme starten, nochmal tippen = Stopp.<br>
Die Aufnahme wird am Pi im Ordner „Aufnahmen“ gespeichert.</div>
<button id="knopf">⏺</button>
<div id="uhr">0:00</div>
<div id="pegel"><div></div></div>
<div id="status">Bereit zur Aufnahme.</div>
<div id="liste"></div>
<script>
const st=document.getElementById('status'),knopf=document.getElementById('knopf'),
      uhr=document.getElementById('uhr'),liste=document.getElementById('liste'),
      pegel=document.querySelector('#pegel div');
const HINWEIS_KEIN_MIKRO='Kein Mikrofon gefunden. Bitte prüfen: 1) Android-Einstellungen → Apps → Chrome → Berechtigungen → Mikrofon → „Erlauben“. 2) Schnelleinstellungen (vom oberen Rand wischen) → „Mikrofonzugriff“ einschalten. 3) Andere Apps schließen, die das Mikrofon nutzen.';
const HINWEIS_GESPERRT='Mikrofon gesperrt. Tippe oben links neben der Adresse auf das Symbol → Berechtigungen → Mikrofon → „Zulassen“. Dann Seite neu laden.';
let rec=null,stuecke=[],start=0,tick=null,wach=null,ton=null;

function zeit(s){s=Math.floor(s);const h=Math.floor(s/3600),m=Math.floor(s/60)%60,x=s%60;
  return (h?h+':'+String(m).padStart(2,'0'):m)+':'+String(x).padStart(2,'0');}
function groesse(b){return b<1048576?Math.max(1,Math.round(b/1024))+' KB':(b/1048576).toFixed(1).replace('.',',')+' MB';}

async function ladeListe(){
  try{const d=await (await fetch('aufnahmen')).json();
    liste.innerHTML=d.length?'<b>Gespeichert am Pi:</b>':'<small>Noch keine Aufnahmen am Pi.</small>';
    for(const a of d){const n=encodeURIComponent(a.name),div=document.createElement('div');div.className='auf';
      div.innerHTML='<div>'+a.name.replace(/</g,'&lt;')+' <small>('+groesse(a.groesse)+')</small></div>'
       +'<audio controls preload="none" src="aufnahmen/'+n+'"></audio>'
       +'<a href="aufnahmen/'+n+'" download>⬇ Aufs Handy</a><button>🗑 Löschen</button>';
      div.querySelector('button').onclick=async()=>{
        if(!confirm('„'+a.name+'“ wirklich löschen?'))return;
        await fetch('loeschen',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:a.name})});
        ladeListe();};
      liste.appendChild(div);}
  }catch(e){liste.innerHTML='<small>Liste nicht ladbar – Pi nicht erreichbar.</small>';}}

function zeigePegel(strom){
  // Gruener Balken: zeigt, ob das Mikrofon etwas hoert
  try{ton=new AudioContext();const an=ton.createAnalyser();an.fftSize=512;
    ton.createMediaStreamSource(strom).connect(an);const w=new Uint8Array(an.fftSize);
    (function mal(){if(!rec||rec.state!='recording'){pegel.style.width='0';return;}
      an.getByteTimeDomainData(w);let max=0;for(const x of w)max=Math.max(max,Math.abs(x-128));
      pegel.style.width=Math.min(100,max*1.6)+'%';requestAnimationFrame(mal);})();
  }catch(e){}}

async function starteAufnahme(){
  if(!window.MediaRecorder||!navigator.mediaDevices){st.textContent='Aufnehmen ist in diesem Browser nicht möglich. Bitte Chrome nutzen.';return;}
  let strom;
  try{strom=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:true}});}
  catch(e){st.textContent=(e.name=='NotFoundError'||e.name=='NotReadableError')?HINWEIS_KEIN_MIKRO:HINWEIS_GESPERRT;return;}
  const typ=['audio/webm;codecs=opus','audio/webm','audio/mp4','audio/ogg'].find(t=>MediaRecorder.isTypeSupported(t))||'';
  rec=new MediaRecorder(strom,typ?{mimeType:typ,audioBitsPerSecond:128000}:{});
  stuecke=[];
  rec.ondataavailable=e=>{if(e.data.size)stuecke.push(e.data);};
  rec.onstop=()=>{strom.getTracks().forEach(t=>t.stop());clearInterval(tick);
    if(ton){ton.close();ton=null;}
    if(wach){wach.release().catch(()=>{});wach=null;}
    knopf.classList.remove('an');knopf.textContent='⏺';
    hochladen(new Blob(stuecke,{type:rec.mimeType||'audio/webm'}));};
  rec.start(1000);start=Date.now();uhr.textContent='0:00';
  tick=setInterval(()=>uhr.textContent=zeit((Date.now()-start)/1000),500);
  knopf.classList.add('an');knopf.textContent='⏹';
  st.textContent='🔴 Aufnahme läuft … Bildschirm bitte anlassen.';
  zeigePegel(strom);
  try{wach=await navigator.wakeLock.request('screen');}catch(e){}  // Bildschirm wach halten
}

async function hochladen(blob){
  const endung=blob.type.includes('mp4')?'m4a':blob.type.includes('ogg')?'ogg':'webm';
  st.textContent='Speichere am Pi … ('+groesse(blob.size)+')';
  try{const r=await fetch('aufnahme?endung='+endung,{method:'POST',headers:{'Content-Type':blob.type},body:blob});
    const d=await r.json();st.textContent=d.antwort;ladeListe();
    setTimeout(ladeListe,5000);setTimeout(ladeListe,20000);}  // MP3-Umwandlung braucht etwas Zeit
  catch(e){
    // Pi nicht erreichbar: Aufnahme wenigstens am Handy speichern
    const a=document.createElement('a');a.href=URL.createObjectURL(blob);
    a.download='Aufnahme_'+new Date().toISOString().slice(0,19).replace(/[T:]/g,'-')+'.'+endung;
    a.textContent='⬇ Aufnahme aufs Handy speichern';a.style.color='#9ecbff';
    st.textContent='Pi nicht erreichbar! ';st.appendChild(a);}
}

knopf.onclick=()=>{if(rec&&rec.state=='recording'){rec.stop();st.textContent='Aufnahme gestoppt.';}else starteAufnahme();};
window.onbeforeunload=e=>{if(rec&&rec.state=='recording'){e.preventDefault();return '';}};
ladeListe();
</script></body></html>"""


# ---------------------------------------------------------------- Webserver
class Handler(http.server.BaseHTTPRequestHandler):
    def _antworte(self, code, typ, daten):
        self.send_response(code)
        self.send_header("Content-Type", typ)
        self.send_header("Content-Length", str(len(daten)))
        self.end_headers()
        self.wfile.write(daten)

    def _json(self, daten):
        self._antworte(200, "application/json", json.dumps(daten).encode())

    def do_GET(self):
        pfad = urllib.parse.urlparse(self.path).path
        if pfad == "/aufnahmen":
            return self._json(liste_aufnahmen())
        if pfad.startswith("/aufnahmen/"):
            return self._sende_aufnahme(urllib.parse.unquote(pfad[len("/aufnahmen/"):]))
        self._antworte(200, "text/html; charset=utf-8", SEITE.encode())

    def _sende_aufnahme(self, name):
        datei = aufnahme_pfad(name)
        if not datei:
            return self._antworte(404, "text/plain", b"nicht gefunden")
        groesse = os.path.getsize(datei)
        # "Range" erlaubt dem Handy, in der Aufnahme vor- und zurueckzuspulen
        anfang, ende = 0, groesse - 1
        bereich = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
        if bereich and groesse:
            if bereich.group(1):
                anfang = int(bereich.group(1))
                if bereich.group(2):
                    ende = min(int(bereich.group(2)), groesse - 1)
            elif bereich.group(2):
                anfang = max(0, groesse - int(bereich.group(2)))
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {anfang}-{ende}/{groesse}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ENDUNGEN[name.rsplit(".", 1)[-1]])
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(max(0, ende - anfang + 1)))
        self.end_headers()
        with open(datei, "rb") as f:
            f.seek(anfang)
            rest = ende - anfang + 1
            while rest > 0:
                stueck = f.read(min(65536, rest))
                if not stueck:
                    break
                self.wfile.write(stueck)
                rest -= len(stueck)

    def _speichere_aufnahme(self, laenge):
        if not 0 < laenge <= MAX_AUFNAHME:
            return "Aufnahme ist leer oder zu groß."
        endung = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get("endung", ["webm"])[0]
        if endung not in ("webm", "m4a", "ogg"):
            endung = "webm"
        os.makedirs(AUFNAHMEN, exist_ok=True)
        name = datetime.datetime.now().strftime("Aufnahme_%Y-%m-%d_%H-%M-%S.") + endung
        datei = os.path.join(AUFNAHMEN, name)
        with open(datei + ".teil", "wb") as f:
            while laenge > 0:
                stueck = self.rfile.read(min(65536, laenge))
                if not stueck:
                    break
                f.write(stueck)
                laenge -= len(stueck)
        if laenge > 0:  # Verbindung abgebrochen
            os.remove(datei + ".teil")
            return "Übertragung abgebrochen – bitte nochmal versuchen."
        os.replace(datei + ".teil", datei)
        threading.Thread(target=in_mp3_umwandeln, args=(datei,), daemon=True).start()
        return "✅ Gespeichert am Pi im Ordner „Aufnahmen“."

    def do_POST(self):
        laenge = int(self.headers.get("Content-Length", 0))
        pfad = urllib.parse.urlparse(self.path).path
        if pfad == "/aufnahme":
            antwort = self._speichere_aufnahme(laenge)
            print(f"  Aufnahme: {antwort}")
            return self._json({"antwort": antwort})
        if pfad == "/loeschen":
            try:
                datei = aufnahme_pfad(json.loads(self.rfile.read(laenge)).get("name", ""))
            except Exception:
                datei = None
            if datei:
                os.remove(datei)
            return self._json({"antwort": "Gelöscht." if datei else "Nicht gefunden."})
        self._antworte(404, "text/plain", b"unbekannt")

    def log_message(self, *args):
        pass  # keine unnoetigen Meldungen im Terminal


def eigene_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "IP-ADRESSE-DES-PI"
    finally:
        s.close()


def main():
    # Das Handy erlaubt das Mikrofon nur ueber eine "sichere" Verbindung (https).
    # Dafuer wird einmalig ein eigenes Zertifikat erstellt.
    https = True
    if not os.path.exists(ZERTIFIKAT):
        try:
            subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                            "-keyout", SCHLUESSEL, "-out", ZERTIFIKAT, "-days", "3650",
                            "-subj", "/CN=raspberrypi"], check=True, capture_output=True)
        except Exception:
            https = False
    try:
        server = http.server.ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    except OSError:
        # Das Programm laeuft schon (z. B. automatisch beim Start des Pi)
        print("=" * 50)
        print("  Die Pi-Aufnahme läuft bereits!")
        print("  Adresse fürs Handy:")
        print()
        print(f"     https://{eigene_ip()}:{PORT}")
        print("=" * 50)
        try:
            input("\n  Enter drücken zum Schließen ...")
        except EOFError:
            pass
        return
    if https:
        kontext = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        kontext.load_cert_chain(ZERTIFIKAT, SCHLUESSEL)
        server.socket = kontext.wrap_socket(server.socket, server_side=True)
    adresse = f"{'https' if https else 'http'}://{eigene_ip()}:{PORT}"
    print("=" * 50)
    print("  Pi-Aufnahme läuft!")
    print("  Öffne am Handy in Chrome diese Adresse:")
    print()
    print("     " + adresse)
    print()
    print(f"  Aufnahmen werden gespeichert in: {AUFNAHMEN}")
    if not shutil.which("ffmpeg"):
        print("  (ffmpeg fehlt - Aufnahmen bleiben im Format .webm)")
    print("  Beenden: Strg + C drücken")
    print("=" * 50)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nBeendet.")


if __name__ == "__main__":
    main()
