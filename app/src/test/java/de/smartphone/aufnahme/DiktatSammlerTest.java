package de.smartphone.aufnahme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Spielt nach, was die Spracherkennung beim Diktieren liefert (Vorschau, Endergebnis, Pausen),
 * und prüft, welcher Text im Textfeld landet – so wie MainActivity es macht.
 */
public class DiktatSammlerTest {
    private final DiktatSammler s = new DiktatSammler();
    private String feld = "";

    private void rein(String stueck) { feld += DiktatSammler.anhang(feld, stueck); }
    private void vorschau(String... v) { for (String t : v) rein(s.zwischen(t)); }
    private void ende(String fin) { rein(s.ende(fin)); }
    private void pause() { rein(s.abbruch()); }      // Fehler „Sprechpause“ / kein Treffer
    private void stopp() { rein(s.abbruch()); }      // Knopf ■ gedrückt

    @Test
    public void fallVomNutzer_leeresEndergebnis() {
        // Bildschirmfoto 19.11.: Vorschau war zu sehen, Textfeld blieb leer
        vorschau("123", "123 Test", "123 Test der Text erscheint nicht unten");
        ende("");
        assertEquals("123 Test der Text erscheint nicht unten", feld);
    }

    @Test
    public void fallVomNutzer_keinEndergebnisSondernPause() {
        vorschau("123 Test", "123 Test der Text erscheint nicht unten");
        pause();
        assertEquals("123 Test der Text erscheint nicht unten", feld);
    }

    @Test
    public void stoppWaehrendVorschau() {
        vorschau("Hallo", "Hallo Welt");
        stopp();
        assertEquals("Hallo Welt", feld);
    }

    @Test
    public void normalesHandy_endergebnisEnthaeltAlles() {
        vorschau("Heute", "Heute ist", "Heute ist schön");
        ende("heute ist schön");
        assertEquals("Heute ist schön", feld);
    }

    @Test
    public void samsungNeubeginnNachPause_endeNurLetzterTeil() {
        vorschau("Heute", "Heute ist schönes Wetter", "morgen", "morgen regnet es");
        ende("morgen regnet es");
        assertEquals("Heute ist schönes Wetter morgen regnet es", feld);
    }

    @Test
    public void neubeginn_endeEnthaeltDochAlles_nichtsDoppelt() {
        vorschau("Heute", "ist schön");
        ende("Heute ist schön");
        assertEquals("Heute ist schön", feld);
    }

    @Test
    public void mehrereRundenHintereinander() {
        vorschau("Erster Satz Punkt");
        ende("");
        vorschau("zweiter Satz");
        pause();
        vorschau("dritter");
        ende("dritter Satz Fragezeichen");
        assertEquals("Erster Satz. Zweiter Satz dritter Satz?", feld);
    }

    @Test
    public void nichtsGesagt_nichtsEingefuegt() {
        pause();
        ende("");
        stopp();
        assertEquals("", feld);
    }

    @Test
    public void nachEndeNichtsDoppelt() {
        vorschau("Hallo");
        ende("Hallo");
        stopp();   // danach gedrückt: Vorschau ist schon übernommen
        assertEquals("Hallo", feld);
    }

    @Test
    public void satzzeichenUndZeilen() {
        assertEquals("Hallo, wie geht es?", DiktatSammler.anhang("", "hallo komma wie geht es fragezeichen"));
        assertEquals(" Ende.", DiktatSammler.anhang("Text", "Ende Punkt"));
        assertEquals("Neu", DiktatSammler.anhang("Satz. ", "neu"));
        assertEquals("\nZweite", DiktatSammler.anhang("Erste", "neue Zeile zweite"));
        assertEquals("\n\nAbsatz", DiktatSammler.anhang("Text.", "neuer Absatz absatz"));
        assertEquals("\n", DiktatSammler.anhang("Text", "neue Zeile"));
        assertEquals("", DiktatSammler.anhang("a", "  "));
        assertEquals("", DiktatSammler.anhang("a", null));
    }

    @Test
    public void korrekturDesErstenWortesIstKeinNeubeginn() {
        assertFalse(DiktatSammler.istNeubeginn("Sander", "Zander 65"));
        assertTrue(DiktatSammler.istNeubeginn("Zander 65 Zentimeter", "am Rhein"));
    }
}
