package de.smartphone.aufnahme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.speech.tts.TextToSpeech;

import org.junit.Assume;

import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/** Tests, die auf einem (simulierten) Android-Handy laufen. */
@RunWith(AndroidJUnit4.class)
public class AufnahmeTest {
    @Rule
    public GrantPermissionRule rechte = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    private Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** Ein 2 Sekunden langer Ton wird zu einer abspielbaren m4a-Datei. */
    @Test
    public void tonWirdZuM4a() throws Exception {
        File datei = new File(ctx().getCacheDir(), "test.m4a");
        AacSchreiber s = new AacSchreiber(datei.getPath(), 44100, 2);
        byte[] puffer = new byte[4096 * 2];
        long abtastwert = 0;
        for (int block = 0; block < 2 * 44100 * 4 / puffer.length; block++) {
            for (int i = 0; i < puffer.length; i += 4, abtastwert++) {
                short wert = (short) (Math.sin(abtastwert * 2 * Math.PI * 440 / 44100) * 12000);
                puffer[i] = puffer[i + 2] = (byte) wert;
                puffer[i + 1] = puffer[i + 3] = (byte) (wert >> 8);
            }
            s.schreibe(puffer, puffer.length);
        }
        s.beenden();

        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(datei.getPath());
        assertEquals(1, ex.getTrackCount());
        MediaFormat f = ex.getTrackFormat(0);
        assertEquals("audio/mp4a-latm", f.getString(MediaFormat.KEY_MIME));
        assertEquals(2, f.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        ex.release();
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(datei.getPath());
        long dauer = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        mmr.release();
        assertTrue("Dauer war " + dauer, dauer > 1800 && dauer < 2300);

        // In den Handy-Speicher (Musik) kopieren, in der Liste finden, wieder loeschen
        String name = Speicher.dateiname("Test", ".m4a");
        Uri uri = Speicher.tonSpeichern(ctx(), datei, name);
        boolean gefunden = false;
        for (Speicher.Eintrag e : Speicher.liste(ctx())) if (e.name.equals(name)) gefunden = true;
        assertTrue("Aufnahme nicht in der Liste", gefunden);
        assertEquals(1, ctx().getContentResolver().delete(uri, null, null));
    }

    @Test
    public void textWirdGespeichert() throws Exception {
        String pfad = Speicher.textSpeichern(ctx(), "Hallo Welt. Das ist ein Test.");
        assertTrue(pfad, pfad.startsWith("Documents/Smartphone-Aufnahme/Diktat_"));
    }

    /** Ganze Mikrofon-Aufnahme ueber den Hintergrund-Dienst, wie beim Tippen auf den roten Knopf. */
    @Test
    public void mikrofonAufnahmeKomplett() throws Exception {
        try (ActivityScenario<MainActivity> bildschirm = ActivityScenario.launch(MainActivity.class)) {
            int vorher = Speicher.liste(ctx()).size();
            ctx().startForegroundService(new Intent(ctx(), AufnahmeService.class)
                    .putExtra(AufnahmeService.ART, AufnahmeService.ART_MIKRO));
            warte(() -> AufnahmeService.laeuft, 5000);
            assertTrue("Aufnahme startet nicht: " + AufnahmeService.meldung, AufnahmeService.laeuft);
            Thread.sleep(2500);
            assertTrue("Dauer zaehlt nicht", AufnahmeService.dauerMs > 1000);
            ctx().startService(new Intent(ctx(), AufnahmeService.class).setAction(AufnahmeService.AKTION_STOPP));
            warte(() -> !AufnahmeService.laeuft, 8000);
            String meldung = AufnahmeService.meldung;
            assertTrue("Meldung: " + meldung, meldung != null && meldung.startsWith("✅"));
            List<Speicher.Eintrag> liste = Speicher.liste(ctx());
            assertEquals(vorher + 1, liste.size());
            // Die Datei muss so lang sein wie der tatsaechlich aufgenommene Ton
            // (das simulierte Handy liefert Ton manchmal langsamer als in Echtzeit)
            long aufgenommen = AufnahmeService.dauerMs, datei = liste.get(0).dauerMs;
            assertTrue("Aufgenommen " + aufgenommen, aufgenommen > 800);
            assertTrue("Datei " + datei + " ms, aufgenommen " + aufgenommen + " ms",
                    Math.abs(datei - aufgenommen) < 300);
            ctx().getContentResolver().delete(liste.get(0).uri, null, null);
        }
    }

    @Test
    public void langerTextWirdSinnvollGeteilt() {
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < 400; i++) t.append("Das ist Satz Nummer ").append(i).append(". ");
        List<String> stuecke = Vorleser.teile(t.toString(), 3000);
        assertTrue(stuecke.size() > 1);
        StringBuilder zusammen = new StringBuilder();
        for (String st : stuecke) {
            assertTrue(st.length() <= 3000);
            assertTrue("Stueck endet mitten im Satz: " + st, st.endsWith("."));
            zusammen.append(st).append(' ');
        }
        assertEquals(t.toString().trim(), zusammen.toString().trim());
        assertEquals(Arrays.asList("Kurz."), Vorleser.teile("  Kurz. ", 3000));
    }

    /** Zwei WAV-Dateien (je 1 Sekunde) werden zu einer 2-Sekunden-m4a. */
    @Test
    public void wavWirdZuM4a() throws Exception {
        File a = schreibeWav("a.wav", 22050, 22050), b = schreibeWav("b.wav", 22050, 22050);
        for (int runde = 1; runde <= 5; runde++) {   // mehrmals, damit seltene Fehler auffallen
            File ziel = new File(ctx().getCacheDir(), "wav-test.m4a");
            ziel.delete();
            Vorleser.wavZuM4a(Arrays.asList(a, b), ziel);
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(ziel.getPath());
            long dauer = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            mmr.release();
            assertTrue("Runde " + runde + ": Dauer war " + dauer, dauer > 1900 && dauer < 2200);
        }
    }

    private File schreibeWav(String name, int rate, int abtastwerte) throws Exception {
        File f = new File(ctx().getCacheDir(), name);
        int daten = abtastwerte * 2;
        byte[] kopf = new byte[44];
        System.arraycopy("RIFF".getBytes(), 0, kopf, 0, 4);
        zahl(kopf, 4, 36 + daten);
        System.arraycopy("WAVEfmt ".getBytes(), 0, kopf, 8, 8);
        zahl(kopf, 16, 16);
        kopf[20] = 1; kopf[22] = 1;                      // PCM, mono
        zahl(kopf, 24, rate);
        zahl(kopf, 28, rate * 2);
        kopf[32] = 2; kopf[34] = 16;                     // 16 Bit
        System.arraycopy("data".getBytes(), 0, kopf, 36, 4);
        zahl(kopf, 40, daten);
        try (FileOutputStream aus = new FileOutputStream(f)) {
            aus.write(kopf);
            byte[] ton = new byte[daten];
            for (int i = 0; i < abtastwerte; i++) {
                short w = (short) (Math.sin(i * 2 * Math.PI * 300 / rate) * 9000);
                ton[2 * i] = (byte) w;
                ton[2 * i + 1] = (byte) (w >> 8);
            }
            aus.write(ton);
        }
        return f;
    }

    private static void zahl(byte[] b, int pos, int wert) {
        for (int i = 0; i < 4; i++) b[pos + i] = (byte) (wert >> (8 * i));
    }

    /** Echtes Vorlesen - nur, wenn das (simulierte) Handy eine Vorlese-Stimme hat. */
    @Test
    public void textWirdVorgelesenUndGespeichert() throws Exception {
        CountDownLatch bereit = new CountDownLatch(1);
        int[] ergebnis = {-1};
        TextToSpeech[] tts = new TextToSpeech[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                tts[0] = new TextToSpeech(ctx(), e -> { ergebnis[0] = e; bereit.countDown(); }));
        bereit.await(20, TimeUnit.SECONDS);
        Assume.assumeTrue("Keine Vorlese-Stimme auf diesem Geraet", ergebnis[0] == TextToSpeech.SUCCESS);
        tts[0].setLanguage(java.util.Locale.GERMANY);
        Uri uri = Vorleser.alsAufnahme(ctx(), tts[0], "Hallo. Das ist ein Test der Vorlese-Funktion.");
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(ctx(), uri);
        long dauer = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        mmr.release();
        assertTrue("Dauer war " + dauer, dauer > 1000);
        ctx().getContentResolver().delete(uri, null, null);
        tts[0].shutdown();
    }

    interface Bedingung { boolean erfuellt(); }

    private static void warte(Bedingung b, long maxMs) throws InterruptedException {
        long ende = System.currentTimeMillis() + maxMs;
        while (!b.erfuellt() && System.currentTimeMillis() < ende) Thread.sleep(100);
    }
}
