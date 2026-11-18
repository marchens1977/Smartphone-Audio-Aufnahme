package de.smartphone.aufnahme;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Macht aus einem Text eine Tonaufnahme (.m4a) mit der Vorlese-Stimme des Handys.
 * Lange Texte werden in Stuecke geteilt, einzeln vorgelesen und wieder zusammengesetzt.
 */
class Vorleser {

    /** Liest den Text in Dateien ein und speichert das Ergebnis in "Musik". Laeuft im Hintergrund! */
    static Uri alsAufnahme(Context ctx, TextToSpeech tts, String text) throws Exception {
        List<String> stuecke = teile(text, Math.min(3000, TextToSpeech.getMaxSpeechInputLength() - 100));
        List<File> wavs = new ArrayList<>();
        try {
            for (int i = 0; i < stuecke.size(); i++) {
                File wav = new File(ctx.getCacheDir(), "vorlesen_" + i + ".wav");
                wavs.add(wav);
                einStueck(tts, stuecke.get(i), wav, "stueck" + i);
            }
            String name = Speicher.dateiname("Vorlesen", ".m4a");
            File m4a = new File(ctx.getCacheDir(), name);
            wavZuM4a(wavs, m4a);
            return Speicher.tonSpeichern(ctx, m4a, name);
        } finally {
            for (File f : wavs) f.delete();
        }
    }

    private static void einStueck(TextToSpeech tts, String text, File wav, String id) throws Exception {
        CountDownLatch fertig = new CountDownLatch(1);
        String[] fehler = {null};
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String u) { }
            @Override public void onDone(String u) { fertig.countDown(); }
            @Override public void onError(String u) { fehler[0] = "Vorlese-Fehler"; fertig.countDown(); }
            @Override public void onError(String u, int code) { fehler[0] = "Vorlese-Fehler " + code; fertig.countDown(); }
        });
        if (tts.synthesizeToFile(text, new Bundle(), wav, id) != TextToSpeech.SUCCESS) {
            throw new Exception("Die Vorlese-Stimme hat den Text nicht angenommen.");
        }
        if (!fertig.await(10, TimeUnit.MINUTES)) throw new Exception("Vorlesen dauert zu lange.");
        if (fehler[0] != null) throw new Exception(fehler[0]);
    }

    /** Teilt langen Text an Satzenden (oder notfalls Leerzeichen) in Stuecke. */
    static List<String> teile(String text, int max) {
        List<String> ergebnis = new ArrayList<>();
        String rest = text.trim();
        while (rest.length() > max) {
            int schnitt = -1;
            for (String ende : new String[]{". ", "! ", "? ", "\n"}) {
                schnitt = Math.max(schnitt, rest.lastIndexOf(ende, max));
            }
            if (schnitt < max / 3) schnitt = rest.lastIndexOf(' ', max);
            if (schnitt <= 0) schnitt = max - 1;
            ergebnis.add(rest.substring(0, schnitt + 1).trim());
            rest = rest.substring(schnitt + 1).trim();
        }
        if (!rest.isEmpty()) ergebnis.add(rest);
        return ergebnis;
    }

    /** Haengt WAV-Dateien (rohen Ton) aneinander und speichert sie als platzsparende .m4a. */
    static void wavZuM4a(List<File> wavs, File ziel) throws IOException {
        AacSchreiber schreiber = null;
        int rate = 0, kanaele = 0;
        try {
            for (File wav : wavs) {
                try (InputStream ein = new FileInputStream(wav)) {
                    int[] format = leseKopf(ein);     // {Abtastrate, Kanaele}
                    if (schreiber == null) {
                        rate = format[0];
                        kanaele = format[1];
                        schreiber = new AacSchreiber(ziel.getPath(), rate, kanaele);
                    } else if (format[0] != rate || format[1] != kanaele) {
                        throw new IOException("Unterschiedliches Tonformat");
                    }
                    byte[] puffer = new byte[8192];
                    int n;
                    while ((n = ein.read(puffer, 0, puffer.length - 1)) > 0) {
                        if (n % 2 == 1) {          // nur ganze 16-Bit-Werte weitergeben
                            int b = ein.read();
                            if (b >= 0) puffer[n++] = (byte) b; else n--;
                        }
                        if (n > 0) schreiber.schreibe(puffer, n);
                    }
                }
            }
            if (schreiber == null) throw new IOException("Kein Ton erzeugt");
        } finally {
            if (schreiber != null) schreiber.beenden();
        }
    }

    /** Liest den Kopf einer WAV-Datei bis zum Beginn der Tondaten. */
    private static int[] leseKopf(InputStream ein) throws IOException {
        byte[] riff = genau(ein, 12);
        if (riff[0] != 'R' || riff[1] != 'I' || riff[2] != 'F' || riff[3] != 'F') throw new IOException("Keine WAV-Datei");
        int rate = 0, kanaele = 0;
        while (true) {
            byte[] kopf = genau(ein, 8);
            String id = new String(kopf, 0, 4, "US-ASCII");
            int laenge = zahl(kopf, 4, 4);
            if (id.equals("data")) {
                if (rate == 0) throw new IOException("WAV ohne Formatangabe");
                return new int[]{rate, kanaele};
            }
            byte[] inhalt = genau(ein, laenge + (laenge % 2));
            if (id.equals("fmt ")) {
                if (zahl(inhalt, 0, 2) != 1 || zahl(inhalt, 14, 2) != 16) throw new IOException("Nicht unterstütztes WAV");
                kanaele = zahl(inhalt, 2, 2);
                rate = zahl(inhalt, 4, 4);
            }
        }
    }

    private static byte[] genau(InputStream ein, int n) throws IOException {
        byte[] b = new byte[n];
        int pos = 0;
        while (pos < n) {
            int r = ein.read(b, pos, n - pos);
            if (r < 0) throw new IOException("WAV-Datei zu kurz");
            pos += r;
        }
        return b;
    }

    private static int zahl(byte[] b, int start, int bytes) {
        int wert = 0;
        for (int i = bytes - 1; i >= 0; i--) wert = (wert << 8) | (b[start + i] & 0xff);
        return wert;
    }
}
