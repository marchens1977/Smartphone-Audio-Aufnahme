package de.smartphone.aufnahme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Sucht bei GitHub nach einer neueren Version der App und bietet sie zum Herunterladen an.
 * Übernommen aus dem Fisch-Fang-Tracker (UpdateChecker.java).
 *
 * Ablauf: neuestes GitHub-„Release“ abfragen → Nummer aus dem Namen (app-v1.0.N) mit der eigenen
 * Bau-Nummer (versionCode) vergleichen → Fenster „Update verfügbar“ → APK im Browser herunterladen.
 *
 * Bewusst OHNE die Erlaubnis „andere Apps installieren“ (REQUEST_INSTALL_PACKAGES):
 * Damit stuft Google Play Protect eine selbst gebaute App als „schädlich“ ein und blockiert sie.
 * Keine Schlüssel nötig: die GitHub-Abfrage für öffentliche Projekte geht ohne Anmeldung.
 */
public class UpdateSuche {
    static final String REPO = "marchens1977/Smartphone-Audio-Aufnahme";
    static final String APK_NAME = "Smartphone-Aufnahme.apk";
    static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long PAUSE_AUTOMATISCH_MS = 60L * 60L * 1000L; // von selbst höchstens 1x pro Stunde

    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean beschaeftigt = false;

    UpdateSuche(Activity activity) {
        this.activity = activity;
    }

    // ------------------------------------------------------------ reine Regeln (getestet in UpdateSucheTest)

    /** "app-v1.0.7" → 7. Alles andere (z. B. "neueste-version") → 0 = unbekannt. */
    static long versionAusTag(String tag) {
        if (tag == null) return 0;
        tag = tag.trim();
        if (!tag.matches("(app-)?v?1\\.0\\.\\d+")) return 0;
        try {
            return Long.parseLong(tag.substring(tag.lastIndexOf('.') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Download-Link der APK aus der GitHub-Antwort, oder null, wenn sie fehlt. */
    static String apkLink(JSONObject release) {
        JSONArray dateien = release.optJSONArray("assets");
        if (dateien == null) return null;
        for (int i = 0; i < dateien.length(); i++) {
            JSONObject a = dateien.optJSONObject(i);
            if (a != null && APK_NAME.equals(a.optString("name"))) {
                String link = a.optString("browser_download_url", "");
                return link.startsWith("https://") ? link : null;
            }
        }
        return null;
    }

    /** Ein Update wird nur angeboten, wenn die Nummer größer ist UND es eine APK gibt. */
    static boolean istNeuer(long neueste, long eigene, String link) {
        return link != null && neueste > 0 && neueste > eigene;
    }

    /** Bau-Nummer N → Anzeige "1.0.N" (passt zum Release-Namen app-v1.0.N). */
    static String anzeige(long nummer) {
        return "1.0." + nummer;
    }

    // ------------------------------------------------------------ eigene Version

    long eigeneNummer() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    String eigeneVersion() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ------------------------------------------------------------ Suche

    /** Beim Start: von selbst nachsehen (nicht öfter als einmal pro Stunde, ohne Meldung). */
    void automatisch() {
        SharedPreferences p = activity.getSharedPreferences("update", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("zuletzt", 0) < PAUSE_AUTOMATISCH_MS) return;
        suche(false);
    }

    /** vonHand = true: auch „neueste Version“ bzw. Fehler anzeigen. */
    void suche(boolean vonHand) {
        if (beschaeftigt) return;
        beschaeftigt = true;
        if (vonHand) meldung("Suche nach Updates …");
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(leseText(API_URL));
                long neueste = versionAusTag(release.optString("tag_name", ""));
                String link = apkLink(release);
                activity.getSharedPreferences("update", Context.MODE_PRIVATE)
                        .edit().putLong("zuletzt", System.currentTimeMillis()).apply();
                ui.post(() -> {
                    beschaeftigt = false;
                    if (istNeuer(neueste, eigeneNummer(), link)) {
                        zeigeUpdate(anzeige(neueste), link);
                    } else if (vonHand) {
                        meldung("Du hast schon die neueste Version (" + eigeneVersion() + ").");
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    beschaeftigt = false;
                    if (vonHand) meldung("Update-Suche nicht möglich. Bitte Internet prüfen.");
                });
            }
        }).start();
    }

    private void zeigeUpdate(String neu, String link) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Update verfügbar")
                .setMessage("Eine neue Version der App ist da (" + neu + ", du hast " + eigeneVersion()
                        + ").\n\nDeine Aufnahmen und Texte bleiben beim Update erhalten.")
                .setPositiveButton("Jetzt herunterladen", (d, w) -> herunterladen(link))
                .setNegativeButton("Später", null)
                .show();
    }

    /** Lädt die neue Version im Browser herunter; danach tippt man auf die Datei → „Aktualisieren“. */
    private void herunterladen(String link) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            new AlertDialog.Builder(activity)
                    .setTitle("So geht es weiter")
                    .setMessage("1. Warten, bis der Download fertig ist.\n"
                            + "2. Auf die heruntergeladene Datei tippen (oben in der Benachrichtigung).\n"
                            + "3. „Aktualisieren“ wählen.\n\nDeine Aufnahmen bleiben erhalten.")
                    .setPositiveButton("Verstanden", null)
                    .show();
        } catch (Exception e) {
            meldung("Download konnte nicht gestartet werden.");
        }
    }

    private static String leseText(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "SmartphoneAufnahmeApp");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] puffer = new byte[8192];
                int n;
                while ((n = in.read(puffer)) > 0) out.write(puffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private void meldung(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
