package de.smartphone.aufnahme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Regeln für die Liste „Stimme wählen“ – ohne Android-Teile, damit sie sich testen lassen (StimmenListeTest).
 * Aus allen Stimmen aller Vorlese-Programme (Samsung, Google …) werden die deutschen herausgesucht,
 * sortiert und mit verständlichen Namen versehen („Google – Stimme 2 · Internet nötig“).
 */
final class StimmenListe {

    /** Eine Stimme, wie Android sie meldet (nur die Angaben, die wir brauchen). */
    static final class Eintrag {
        final String programm;      // Paketname des Vorlese-Programms, z. B. com.google.android.tts
        final String programmName;  // angezeigter Name, z. B. "Sprachausgabe von Google"
        final String name;          // interner Name der Stimme, z. B. de-de-x-deb-local
        final String sprache;       // z. B. "de-DE"
        final int qualitaet;        // Android: 100 sehr schlecht … 300 normal … 500 sehr gut
        final boolean internet;     // braucht eine Internet-Verbindung
        final boolean installiert;  // Sprachdaten sind auf dem Handy
        String anzeige = "";

        Eintrag(String programm, String programmName, String name, String sprache,
                int qualitaet, boolean internet, boolean installiert) {
            this.programm = programm;
            this.programmName = programmName == null ? "" : programmName;
            this.name = name;
            this.sprache = sprache == null ? "" : sprache;
            this.qualitaet = qualitaet;
            this.internet = internet;
            this.installiert = installiert;
        }
    }

    /** Nur deutsche, installierte Stimmen; sortiert; mit Anzeige-Namen. */
    static List<Eintrag> aufbereiten(List<Eintrag> alle) {
        List<Eintrag> de = new ArrayList<>();
        for (Eintrag e : alle) {
            if (e == null || e.name == null || !e.installiert) continue;
            if (!e.sprache.toLowerCase().startsWith("de")) continue;
            boolean doppelt = false;
            for (Eintrag d : de) if (d.programm.equals(e.programm) && d.name.equals(e.name)) doppelt = true;
            if (!doppelt) de.add(e);
        }
        Collections.sort(de, (a, b) -> {
            int c = kurzname(a).compareToIgnoreCase(kurzname(b));
            if (c != 0) return c;
            c = Integer.compare(landRang(a.sprache), landRang(b.sprache));   // Deutschland zuerst
            if (c != 0) return c;
            c = Boolean.compare(a.internet, b.internet);                      // ohne Internet zuerst
            if (c != 0) return c;
            c = Integer.compare(b.qualitaet, a.qualitaet);                    // bessere zuerst
            if (c != 0) return c;
            return a.name.compareTo(b.name);
        });
        String vorher = null;
        int nr = 0;
        for (Eintrag e : de) {
            String k = kurzname(e);
            nr = k.equals(vorher) ? nr + 1 : 1;
            vorher = k;
            StringBuilder t = new StringBuilder(k).append(" – Stimme ").append(nr);
            String land = land(e.sprache);
            if (!land.isEmpty()) t.append(" (").append(land).append(")");
            if (e.qualitaet >= 400) t.append(" · gute Qualität");
            if (e.internet) t.append(" · Internet nötig");
            e.anzeige = t.toString();
        }
        return de;
    }

    /** Kurzer Name des Vorlese-Programms. */
    static String kurzname(Eintrag e) {
        String p = e.programm.toLowerCase();
        if (p.contains("samsung")) return "Samsung";
        if (p.contains("google")) return "Google";
        String n = e.programmName.trim();
        return n.isEmpty() ? e.programm : n;
    }

    private static int landRang(String sprache) {
        String l = land(sprache);
        return l.isEmpty() ? 0 : l.equals("Österreich") ? 1 : l.equals("Schweiz") ? 2 : 3;
    }

    /** "de-AT" → "Österreich"; Deutschland bleibt leer (Normalfall). */
    static String land(String sprache) {
        String s = sprache == null ? "" : sprache.toUpperCase().replace('_', '-');
        if (s.endsWith("-AT") || s.contains("-AT-")) return "Österreich";
        if (s.endsWith("-CH") || s.contains("-CH-")) return "Schweiz";
        if (s.endsWith("-BE") || s.contains("-BE-")) return "Belgien";
        if (s.endsWith("-LU") || s.contains("-LU-")) return "Luxemburg";
        return "";
    }

    /** Index der gespeicherten Stimme in der Liste, oder -1. */
    static int finde(List<Eintrag> liste, String programm, String name) {
        if (programm == null || name == null) return -1;
        for (int i = 0; i < liste.size(); i++) {
            Eintrag e = liste.get(i);
            if (e.programm.equals(programm) && e.name.equals(name)) return i;
        }
        return -1;
    }
}
