package de.smartphone.aufnahme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Prüft die Liste „Stimme wählen“: nur Deutsch, sortiert, verständliche Namen. */
public class StimmenListeTest {
    private static final String G = "com.google.android.tts", S = "com.samsung.SMT";

    private static StimmenListe.Eintrag e(String prog, String name, String sprache, int q, boolean netz, boolean inst) {
        return new StimmenListe.Eintrag(prog, prog.equals(G) ? "Sprachausgabe von Google" : "Samsung TTS",
                name, sprache, q, netz, inst);
    }

    private static List<String> anzeigen(List<StimmenListe.Eintrag> l) {
        List<String> r = new ArrayList<>();
        for (StimmenListe.Eintrag x : l) r.add(x.anzeige);
        return r;
    }

    @Test
    public void nurDeutscheUndInstallierteStimmen() {
        List<StimmenListe.Eintrag> l = StimmenListe.aufbereiten(Arrays.asList(
                e(G, "en-us-x-sfg-local", "en-US", 400, false, true),
                e(G, "de-de-x-deb-local", "de-DE", 400, false, true),
                e(G, "de-de-x-dea-local", "de-DE", 400, false, false),   // nicht heruntergeladen
                e(G, "fr-fr-x-frc-local", "fr-FR", 400, false, true)));
        assertEquals(1, l.size());
        assertEquals("de-de-x-deb-local", l.get(0).name);
    }

    @Test
    public void sortiertUndNummeriertJeProgramm() {
        List<StimmenListe.Eintrag> l = StimmenListe.aufbereiten(Arrays.asList(
                e(S, "de-DE-SMTf00", "de-DE", 300, false, true),
                e(G, "de-de-x-nfh-network", "de-DE", 400, true, true),
                e(G, "de-de-x-deb-local", "de-DE", 400, false, true),
                e(G, "de-de-x-deg-local", "de-DE", 300, false, true),
                e(S, "de-DE-SMTl01", "de-DE", 300, false, true)));
        assertEquals(Arrays.asList(
                "Google – Stimme 1 · gute Qualität",
                "Google – Stimme 2",
                "Google – Stimme 3 · gute Qualität · Internet nötig",
                "Samsung – Stimme 1",
                "Samsung – Stimme 2"), anzeigen(l));
    }

    @Test
    public void laenderWerdenGenannt() {
        List<StimmenListe.Eintrag> l = StimmenListe.aufbereiten(Arrays.asList(
                e(G, "de-ch-x", "de-CH", 300, false, true),
                e(G, "de-at-x", "de-AT", 300, false, true),
                e(G, "de-de-x", "de-DE", 300, false, true)));
        assertEquals(Arrays.asList("Google – Stimme 1", "Google – Stimme 2 (Österreich)",
                "Google – Stimme 3 (Schweiz)"), anzeigen(l));
    }

    @Test
    public void doppelteStimmenNurEinmal() {
        List<StimmenListe.Eintrag> l = StimmenListe.aufbereiten(Arrays.asList(
                e(G, "de-de-x-deb-local", "de-DE", 400, false, true),
                e(G, "de-de-x-deb-local", "de-DE", 400, false, true)));
        assertEquals(1, l.size());
    }

    @Test
    public void unbekanntesProgrammMitEigenemNamen() {
        StimmenListe.Eintrag x = new StimmenListe.Eintrag("org.beispiel.tts", "Beispiel-Stimme", "de1", "de", 300, false, true);
        assertEquals("Beispiel-Stimme – Stimme 1", StimmenListe.aufbereiten(Arrays.asList(x)).get(0).anzeige);
    }

    @Test
    public void gespeicherteStimmeWiederfinden() {
        List<StimmenListe.Eintrag> l = StimmenListe.aufbereiten(Arrays.asList(
                e(G, "de-de-x-deb-local", "de-DE", 400, false, true),
                e(S, "de-DE-SMTf00", "de-DE", 300, false, true)));
        assertEquals(1, StimmenListe.finde(l, S, "de-DE-SMTf00"));
        assertEquals(-1, StimmenListe.finde(l, S, "gibt-es-nicht"));
        assertEquals(-1, StimmenListe.finde(l, null, null));
    }

    @Test
    public void leereListe() {
        assertEquals(0, StimmenListe.aufbereiten(new ArrayList<>()).size());
    }
}
