package de.smartphone.aufnahme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests der Update-Suche, laufen ohne Handy (gradle testDebugUnitTest
 * oder werkzeuge/update-test.sh). Arbeitsordner ist "app/".
 */
public class UpdateSucheTest {

    /** So sieht die Antwort von GitHub (releases/latest) aus – gekürzt auf das Nötige. */
    private static final String ANTWORT = "{"
            + "\"tag_name\":\"app-v1.0.12\",\"name\":\"Smartphone-Aufnahme 1.0.12\",\"prerelease\":false,"
            + "\"assets\":["
            + "{\"name\":\"bildschirm.png\",\"browser_download_url\":\"https://github.com/x/bildschirm.png\"},"
            + "{\"name\":\"Smartphone-Aufnahme.apk\",\"browser_download_url\":"
            + "\"https://github.com/marchens1977/Smartphone-Audio-Aufnahme/releases/download/app-v1.0.12/Smartphone-Aufnahme.apk\"}"
            + "]}";

    @Test
    public void nummerAusReleaseName() {
        assertEquals(7, UpdateSuche.versionAusTag("app-v1.0.7"));
        assertEquals(12, UpdateSuche.versionAusTag("app-v1.0.12"));
        assertEquals(12, UpdateSuche.versionAusTag(" app-v1.0.12 "));
        assertEquals(5, UpdateSuche.versionAusTag("v1.0.5"));
    }

    @Test
    public void fremdeReleaseNamenGeltenNichtAlsUpdate() {
        assertEquals(0, UpdateSuche.versionAusTag("neueste-version"));   // altes Release dieses Projekts
        assertEquals(0, UpdateSuche.versionAusTag(""));
        assertEquals(0, UpdateSuche.versionAusTag(null));
        assertEquals(0, UpdateSuche.versionAusTag("app-v1.0.x"));
        assertEquals(0, UpdateSuche.versionAusTag("app-v2.0.3"));
    }

    @Test
    public void apkLinkWirdGefunden() throws Exception {
        String link = UpdateSuche.apkLink(new JSONObject(ANTWORT));
        assertEquals("https://github.com/marchens1977/Smartphone-Audio-Aufnahme/releases/download/"
                + "app-v1.0.12/Smartphone-Aufnahme.apk", link);
    }

    @Test
    public void ohneApkKeinLink() throws Exception {
        assertNull(UpdateSuche.apkLink(new JSONObject("{\"tag_name\":\"app-v1.0.3\"}")));
        assertNull(UpdateSuche.apkLink(new JSONObject("{\"assets\":[]}")));
        assertNull(UpdateSuche.apkLink(new JSONObject(
                "{\"assets\":[{\"name\":\"andere.apk\",\"browser_download_url\":\"https://a/b.apk\"}]}")));
        // nur sichere Links (https)
        assertNull(UpdateSuche.apkLink(new JSONObject(
                "{\"assets\":[{\"name\":\"Smartphone-Aufnahme.apk\",\"browser_download_url\":\"http://a/b.apk\"}]}")));
    }

    @Test
    public void updateNurWennNeuer() {
        String link = "https://x/Smartphone-Aufnahme.apk";
        assertTrue(UpdateSuche.istNeuer(12, 11, link));
        assertTrue(UpdateSuche.istNeuer(12, 1, link));     // alte App-Version "1.0" hatte Nummer 1
        assertFalse(UpdateSuche.istNeuer(12, 12, link));   // gleiche Version
        assertFalse(UpdateSuche.istNeuer(11, 12, link));   // App ist sogar neuer
        assertFalse(UpdateSuche.istNeuer(12, 11, null));   // keine APK im Release
        assertFalse(UpdateSuche.istNeuer(0, 0, link));     // Name unbekannt
    }

    @Test
    public void ganzerAblaufMitGitHubAntwort() throws Exception {
        JSONObject r = new JSONObject(ANTWORT);
        long neueste = UpdateSuche.versionAusTag(r.optString("tag_name"));
        assertTrue(UpdateSuche.istNeuer(neueste, 10, UpdateSuche.apkLink(r)));
        assertEquals("1.0.12", UpdateSuche.anzeige(neueste));
    }

    @Test
    public void anzeigeUndReleaseNamePassenZusammen() {
        for (long n : new long[]{1, 8, 123}) {
            assertEquals(n, UpdateSuche.versionAusTag("app-v" + UpdateSuche.anzeige(n)));
        }
    }

    // ---------- Passen App, Bau-Anleitung und Release-Namen zusammen?

    private static String lies(String pfad) throws Exception {
        Path p = Paths.get(pfad);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    public void appVersionKommtAusDerBauNummer() throws Exception {
        String gradle = lies("build.gradle");
        assertTrue(gradle.contains("GITHUB_RUN_NUMBER"));
        assertTrue(gradle.contains("versionCode bauNummer"));
        assertTrue(gradle.contains("versionName \"1.0.${bauNummer}\""));
    }

    @Test
    public void releaseHeisstWieDieAppVersion() throws Exception {
        String bau = lies("../.github/workflows/app-bauen.yml");
        assertTrue("Release-Name muss app-v1.0.N sein", bau.contains("app-v1.0.${GITHUB_RUN_NUMBER}"));
        assertTrue("APK-Name muss stimmen", bau.contains(UpdateSuche.APK_NAME));
        assertTrue("neues Release muss als neuestes markiert sein", bau.contains("--latest"));
    }

    @Test
    public void erlaubnisseStimmen() throws Exception {
        String manifest = lies("src/main/AndroidManifest.xml");
        assertTrue("Internet nötig für die Update-Suche", manifest.contains("android.permission.INTERNET"));
        // Play Protect blockiert selbst gebaute Apps mit dieser Erlaubnis (Erfahrung Fisch-Fang-Tracker)
        assertFalse(manifest.contains("REQUEST_INSTALL_PACKAGES"));
    }

    @Test
    public void richtigesProjektGefragt() {
        assertEquals("https://api.github.com/repos/marchens1977/Smartphone-Audio-Aufnahme/releases/latest",
                UpdateSuche.API_URL);
    }
}
