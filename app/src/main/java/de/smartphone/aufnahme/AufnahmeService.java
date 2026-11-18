package de.smartphone.aufnahme;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;

/**
 * Nimmt im Hintergrund auf - entweder das Mikrofon (eigene Stimme)
 * oder den Ton, den das Handy gerade abspielt (Medienaudio).
 * Laeuft als "Vordergrund-Dienst" mit Benachrichtigung, damit Android die
 * Aufnahme nicht beendet, wenn der Bildschirm ausgeht.
 */
public class AufnahmeService extends Service {
    static final String ART = "art", ERGEBNIS_CODE = "code", ERGEBNIS_DATEN = "daten";
    static final String ART_MIKRO = "Sprache", ART_MEDIEN = "Medien";
    static final String AKTION_STOPP = "stopp", AKTION_PAUSE = "pause";

    // Zustand, den die Bildschirm-Seite regelmaessig abfragt
    static volatile boolean laeuft = false, pausiert = false;
    static volatile long dauerMs = 0;
    static volatile int pegel = 0;               // 0..100
    static volatile boolean nochKeinTon = true;   // true, solange nur absolute Stille ankommt
    static volatile String art = "";
    static volatile String meldung = null;        // Nachricht fuer den Bildschirm (wird dort geleert)

    private static final int ABTASTRATE = 44100;
    private static final String KANAL = "aufnahme";
    private Thread arbeiter;
    private volatile boolean stoppen = false;
    private MediaProjection projektion;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (AKTION_STOPP.equals(intent.getAction())) {
            stoppen = true;
            return START_NOT_STICKY;
        }
        if (AKTION_PAUSE.equals(intent.getAction())) {
            pausiert = !pausiert;
            zeigeBenachrichtigung();
            return START_NOT_STICKY;
        }
        if (laeuft) return START_NOT_STICKY;

        art = intent.getStringExtra(ART);
        boolean medien = ART_MEDIEN.equals(art);
        pausiert = false;
        dauerMs = 0;
        stoppen = false;
        nochKeinTon = true;
        try {
            // Muss VOR dem Zugriff auf Mikrofon / Bildschirmton passieren (Android-Regel)
            zeigeBenachrichtigung();
        } catch (Exception fehler) {
            meldung = "Aufnahme nicht möglich: " + fehler.getMessage();
            stopSelf();
            return START_NOT_STICKY;
        }

        AudioRecord rec;
        int kanaele = medien ? 2 : 1;
        try {
            rec = medien ? medienQuelle(intent) : mikroQuelle();
        } catch (SecurityException fehler) {
            meldung = "Keine Erlaubnis für das Mikrofon. Bitte in den Einstellungen erlauben.";
            beendeDienst();
            return START_NOT_STICKY;
        } catch (Exception fehler) {
            meldung = "Aufnahme nicht möglich: " + fehler.getMessage();
            beendeDienst();
            return START_NOT_STICKY;
        }
        laeuft = true;
        arbeiter = new Thread(() -> nimmAuf(rec, kanaele), "Aufnahme");
        arbeiter.start();
        return START_NOT_STICKY;
    }

    private AudioRecord mikroQuelle() {
        AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(ABTASTRATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        return new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format).setBufferSizeInBytes(puffergroesse(1)).build();
    }

    private AudioRecord medienQuelle(Intent intent) {
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        projektion = mpm.getMediaProjection(intent.getIntExtra(ERGEBNIS_CODE, 0),
                intent.getParcelableExtra(ERGEBNIS_DATEN));
        if (projektion == null) throw new IllegalStateException("Freigabe fehlt");
        projektion.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stoppen = true; }   // z. B. ueber die Android-Leiste beendet
        }, new Handler(Looper.getMainLooper()));
        AudioPlaybackCaptureConfiguration konfig = new AudioPlaybackCaptureConfiguration.Builder(projektion)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(ABTASTRATE).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
        return new AudioRecord.Builder().setAudioFormat(format)
                .setBufferSizeInBytes(puffergroesse(2))
                .setAudioPlaybackCaptureConfig(konfig).build();
    }

    private static int puffergroesse(int kanaele) {
        int min = AudioRecord.getMinBufferSize(ABTASTRATE,
                kanaele == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        return Math.max(min * 4, 32768);
    }

    /** Laeuft im Hintergrund: liest Ton und schreibt ihn in die Datei. */
    private void nimmAuf(AudioRecord rec, int kanaele) {
        String name = Speicher.dateiname(art, ".m4a");
        File zwischen = new File(getCacheDir(), name);
        AacSchreiber schreiber = null;
        try {
            schreiber = new AacSchreiber(zwischen.getPath(), ABTASTRATE, kanaele);
            rec.startRecording();
            byte[] puffer = new byte[4096 * kanaele];
            while (!stoppen) {
                int n = rec.read(puffer, 0, puffer.length);
                if (n <= 0) continue;
                pegel = berechnePegel(puffer, n);
                if (nochKeinTon && enthaeltTon(puffer, n)) nochKeinTon = false;
                if (pausiert) continue;
                schreiber.schreibe(puffer, n);
                dauerMs = schreiber.dauerMs();
            }
            rec.stop();
            schreiber.beenden();
            schreiber = null;
            if (dauerMs < 300) {
                zwischen.delete();
                meldung = "Aufnahme war zu kurz und wurde nicht gespeichert.";
            } else if (nochKeinTon && ART_MEDIEN.equals(art)) {
                // Android liefert nur Nullen, wenn die abspielende App die Aufnahme sperrt
                zwischen.delete();
                meldung = STILLE_HINWEIS + " Die leere Aufnahme wurde nicht gespeichert.";
            } else {
                Speicher.tonSpeichern(this, zwischen, name);
                meldung = "✅ Gespeichert: " + name;
            }
        } catch (Exception fehler) {
            meldung = "Fehler bei der Aufnahme: " + fehler.getMessage();
            if (schreiber != null) try { schreiber.beenden(); } catch (Exception ignoriert) { }
            zwischen.delete();
        } finally {
            rec.release();
            new Handler(Looper.getMainLooper()).post(this::beendeDienst);
        }
    }

    static final String STILLE_HINWEIS = "⚠️ Es kommt kein Ton an. Die App, die gerade spielt, "
            + "erlaubt keine Aufnahme (z. B. Claude-Sprachmodus, WhatsApp-Anrufe, Telefonate, Netflix, Spotify).";

    private static boolean enthaeltTon(byte[] b, int n) {
        for (int i = 0; i < n; i++) if (b[i] != 0) return true;
        return false;
    }

    private static int berechnePegel(byte[] b, int n) {
        int max = 0;
        for (int i = 0; i + 1 < n; i += 2) {
            int wert = Math.abs((short) ((b[i] & 0xff) | (b[i + 1] << 8)));
            if (wert > max) max = wert;
        }
        return Math.min(100, max * 100 / 32767);
    }

    private void beendeDienst() {
        laeuft = false;
        pausiert = false;
        pegel = 0;
        if (projektion != null) {
            projektion.stop();
            projektion = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void zeigeBenachrichtigung() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(KANAL, "Aufnahme", NotificationManager.IMPORTANCE_LOW));
        PendingIntent oeffnen = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopp = PendingIntent.getService(this, 1,
                new Intent(this, AufnahmeService.class).setAction(AKTION_STOPP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 2,
                new Intent(this, AufnahmeService.class).setAction(AKTION_PAUSE), PendingIntent.FLAG_IMMUTABLE);
        boolean medien = ART_MEDIEN.equals(art);
        Notification n = new Notification.Builder(this, KANAL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(pausiert ? "⏸ Aufnahme pausiert" : "🔴 Aufnahme läuft")
                .setContentText(medien ? "Medienaudio wird aufgenommen" : "Mikrofon wird aufgenommen")
                .setContentIntent(oeffnen)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, pausiert ? "Weiter" : "Pause", pause).build())
                .addAction(new Notification.Action.Builder(null, "Stopp", stopp).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, medien ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
