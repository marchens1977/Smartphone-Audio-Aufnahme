package de.smartphone.aufnahme;

import java.util.Locale;

/**
 * Sammelt beim Dauer-Diktat, was die Spracherkennung liefert – ohne Android-Teile, damit es sich
 * leicht testen lässt (DiktatSammlerTest). Regeln übernommen aus dem Fisch-Fang-Tracker (DictationMerge).
 *
 * Problem auf Samsung-Handys: Das Gesagte kommt als „Zwischenergebnis“ (Vorschau). Das Endergebnis
 * ist danach oft LEER, fehlt ganz (statt dessen Fehler „Sprechpause“) oder enthält nur den letzten
 * Abschnitt, weil die Erkennung nach einer Pause intern neu anfängt. Darum merkt sich der Sammler
 * die letzte Vorschau und gibt sie heraus, wenn sonst etwas verloren ginge – ohne Doppelungen.
 *
 * Jede Methode gibt den Text zurück, der JETZT ans Textfeld angehängt werden soll (oft "").
 */
final class DiktatSammler {
    private String letzte = "";                                  // letzte Vorschau dieser Runde
    private final StringBuilder gesichert = new StringBuilder();  // in dieser Runde schon übernommen

    /** Neue Vorschau. Fängt sie ganz neu an, wird die alte Vorschau übernommen. */
    String zwischen(String neu) {
        neu = neu == null ? "" : neu.trim();
        if (neu.isEmpty()) return "";
        String raus = "";
        if (istNeubeginn(letzte, neu)) {
            raus = letzte;
            if (gesichert.length() > 0) gesichert.append(' ');
            gesichert.append(letzte);
        }
        letzte = neu;
        return raus;
    }

    /** Endergebnis einer Runde (kann leer sein). */
    String ende(String fin) {
        String f = ohneGesichertes(gesichert.toString(), fin);
        if (f.isEmpty()) f = letzte;   // nichts verlieren
        neueRunde();
        return f;
    }

    /** Runde ohne Endergebnis vorbei (Sprechpause, Fehler, Stopp): letzte Vorschau übernehmen. */
    String abbruch() {
        String r = letzte;
        neueRunde();
        return r;
    }

    /** Was gerade als Vorschau angezeigt wird. */
    String vorschau() {
        return letzte;
    }

    private void neueRunde() {
        letzte = "";
        gesichert.setLength(0);
    }

    // ------------------------------------------------------------ Regeln

    /** true, wenn die neue Vorschau nicht die Fortsetzung der alten ist, sondern neu beginnt. */
    static boolean istNeubeginn(String alt, String neu) {
        String[] a = woerter(alt), b = woerter(neu);
        if (a.length == 0 || b.length == 0) return false;
        if (!aehnlich(a[0], b[0])) return true;          // ganz anderer Anfang = neuer Abschnitt
        return a.length >= 3 && b.length * 2 < a.length;  // gleicher Anfang, aber plötzlich viel kürzer
    }

    /** Schneidet schon übernommenen Text vorne vom Endergebnis ab, falls er dort steht. */
    static String ohneGesichertes(String gesichert, String fin) {
        String g = norm(gesichert), f = norm(fin);
        if (g.isEmpty() || f.isEmpty()) return fin == null ? "" : fin.trim();
        if (f.equals(g)) return "";
        if (f.startsWith(g + " ")) {
            String[] alle = fin.trim().split("\\s+");
            StringBuilder r = new StringBuilder();
            for (int i = woerter(gesichert).length; i < alle.length; i++) {
                if (r.length() > 0) r.append(' ');
                r.append(alle[i]);
            }
            return r.toString();
        }
        return fin.trim();
    }

    /**
     * Was an den bisherigen Text angehängt wird: gesprochene Satzzeichen umwandeln,
     * Großschreibung am Satzanfang, passender Abstand.
     */
    static String anhang(String bisher, String gesagt) {
        if (gesagt == null) return "";
        String neu = " " + gesagt.trim() + " ";
        neu = neu.replaceAll("(?i)\\s+neuer absatz\\s+", "\n\n")
                .replaceAll("(?i)\\s+neue zeile\\s+", "\n")
                .replaceAll("(?i)\\s+punkt(?=\\s)", ".")
                .replaceAll("(?i)\\s+komma(?=\\s)", ",")
                .replaceAll("(?i)\\s+fragezeichen(?=\\s)", "?")
                .replaceAll("(?i)\\s+ausrufezeichen(?=\\s)", "!")
                .replaceAll("(?i)\\s+doppelpunkt(?=\\s)", ":")
                .replaceAll("[ \\t]+\n", "\n").replaceAll("\n[ \\t]+", "\n")
                .replaceAll("^[ \\t]+|[ \\t]+$", "");   // nur Leerzeichen weg, Zeilenumbrüche bleiben
        if (neu.trim().isEmpty() && !neu.contains("\n")) return "";
        String ohneLeer = bisher.replaceAll("\\s+$", "");
        boolean satzanfang = ohneLeer.isEmpty() || ohneLeer.endsWith(".") || ohneLeer.endsWith("?")
                || ohneLeer.endsWith("!") || bisher.endsWith("\n");
        int b = 0;
        while (b < neu.length() && neu.charAt(b) == '\n') b++;
        if ((satzanfang || b > 0) && b < neu.length()) {   // Satzanfang oder neue Zeile: groß
            neu = neu.substring(0, b) + neu.substring(b, b + 1).toUpperCase(Locale.GERMANY) + neu.substring(b + 1);
        }
        boolean ohneAbstand = bisher.isEmpty() || bisher.endsWith(" ") || bisher.endsWith("\n")
                || neu.startsWith("\n") || neu.matches("^[.,?!:].*");
        return (ohneAbstand ? "" : " ") + neu;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ");
    }

    private static String[] woerter(String s) {
        String n = norm(s);
        return n.isEmpty() ? new String[0] : n.split(" ");
    }

    /** Ähnliche Wörter (z. B. „sander“/„zander“) gelten als gleich – das ist nur eine Korrektur. */
    static boolean aehnlich(String x, String y) {
        if (x.equals(y)) return true;
        return abstand(x, y) * 2 < Math.max(x.length(), y.length());
    }

    private static int abstand(String x, String y) {
        int[] vor = new int[y.length() + 1], jetzt = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) vor[j] = j;
        for (int i = 1; i <= x.length(); i++) {
            jetzt[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int k = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                jetzt[j] = Math.min(Math.min(jetzt[j - 1] + 1, vor[j] + 1), vor[j - 1] + k);
            }
            int[] t = vor; vor = jetzt; jetzt = t;
        }
        return vor[y.length()];
    }
}
