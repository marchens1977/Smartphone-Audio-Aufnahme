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
            assertTrue("Dauer " + liste.get(0).dauerMs, liste.get(0).dauerMs > 1500);
            ctx().getContentResolver().delete(liste.get(0).uri, null, null);
        }
    }

    interface Bedingung { boolean erfuellt(); }

    private static void warte(Bedingung b, long maxMs) throws InterruptedException {
        long ende = System.currentTimeMillis() + maxMs;
        while (!b.erfuellt() && System.currentTimeMillis() < ende) Thread.sleep(100);
    }
}
