package de.smartphone.aufnahme;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Der Bildschirm der App mit drei Bereichen:
 *   🎙 Stimme   - eigene Stimme mit dem Mikrofon aufnehmen
 *   🎵 Medien   - Ton aufnehmen, den das Handy abspielt (z. B. aus einer anderen App)
 *   ⌨ Diktieren - Sprache wird zu Text
 * Alles wird auf dem Handy gespeichert.
 */
public class MainActivity extends Activity {
    private static final int ANFRAGE_RECHTE = 1, ANFRAGE_MEDIEN = 2;
    // Farben der New York Mets: Blau und Orange
    private static final int METS_BLAU = Color.rgb(0, 45, 114), METS_ORANGE = Color.rgb(255, 89, 16);
    private static final int FARBE_HINTERGRUND = METS_BLAU, FARBE_KARTE = Color.rgb(16, 66, 145),
            FARBE_ROT = METS_ORANGE, FARBE_GELB = METS_ORANGE,
            FARBE_GRAU = Color.rgb(90, 110, 150), FARBE_BLAU = Color.WHITE,
            FARBE_KNOPF = Color.rgb(28, 85, 170), FARBE_HELL = Color.rgb(190, 205, 230);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences einstellungen;
    private String modus;
    private Runnable nachErlaubnis;

    private Button knopfStimme, knopfMedien, knopfDiktat, knopfVorlesen, aufnahmeKnopf, pauseKnopf, diktatKnopf,
            probeKnopf, speichernKnopf;
    private final Button[] tempoKnoepfe = new Button[3];
    private TextView info, uhr, status, diktatStatus, vorleseStatus;
    private ProgressBar pegel;
    private LinearLayout bereichAufnahme, bereichDiktat, bereichVorlesen, bereichListe, liste;
    private EditText diktatText, vorleseText;

    private MediaPlayer spieler;
    private Uri spielt;
    private boolean warLaufend = false;

    private SpeechRecognizer erkenner;
    private boolean diktatAktiv = false;

    private TextToSpeech stimme;
    private boolean stimmeBereit = false, liestVor = false, speichert = false;
    private static final float[] TEMPO = {0.8f, 1.0f, 1.25f};

    // ------------------------------------------------------------ Aufbau
    @Override
    protected void onCreate(Bundle zustand) {
        super.onCreate(zustand);
        einstellungen = getSharedPreferences("einstellungen", MODE_PRIVATE);
        getWindow().setStatusBarColor(FARBE_HINTERGRUND);
        getWindow().setNavigationBarColor(FARBE_HINTERGRUND);

        LinearLayout seite = spalte();
        seite.setPadding(dp(16), dp(16), dp(16), dp(32));
        TextView titel = text("🎙 Smartphone-Aufnahme", 22, Color.WHITE);
        titel.setTypeface(null, Typeface.BOLD);
        seite.addView(titel);

        LinearLayout modi = new LinearLayout(this);
        modi.setPadding(0, dp(12), 0, dp(6));
        knopfStimme = modusKnopf("🎙\nStimme", AufnahmeService.ART_MIKRO);
        knopfMedien = modusKnopf("🎵\nMedien", AufnahmeService.ART_MEDIEN);
        knopfDiktat = modusKnopf("⌨\nDiktieren", "Diktat");
        knopfVorlesen = modusKnopf("🔊\nVorlesen", "Vorlesen");
        modi.addView(knopfStimme, gewicht());
        modi.addView(knopfMedien, gewicht());
        modi.addView(knopfDiktat, gewicht());
        modi.addView(knopfVorlesen, gewicht());
        seite.addView(modi);

        info = text("", 15, FARBE_HELL);
        seite.addView(info);

        // --- Bereich Aufnahme (Stimme und Medien)
        bereichAufnahme = spalte();
        aufnahmeKnopf = rundKnopf("●");
        aufnahmeKnopf.setOnClickListener(v -> aufnahmeKnopfGedrueckt());
        bereichAufnahme.addView(aufnahmeKnopf, mittig(dp(170), dp(170), dp(16)));
        uhr = text("0:00", 40, Color.WHITE);
        uhr.setTypeface(Typeface.MONOSPACE);
        bereichAufnahme.addView(uhr);
        pegel = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pegel.setMax(100);
        pegel.setProgressTintList(android.content.res.ColorStateList.valueOf(METS_ORANGE));
        pegel.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        bereichAufnahme.addView(pegel, mittig(dp(200), dp(14), dp(4)));
        pauseKnopf = flacherKnopf("⏸ Pause");
        pauseKnopf.setOnClickListener(v -> startService(
                new Intent(this, AufnahmeService.class).setAction(AufnahmeService.AKTION_PAUSE)));
        bereichAufnahme.addView(pauseKnopf, mittig(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(4)));
        status = text("Bereit.", 17, Color.WHITE);
        bereichAufnahme.addView(status);
        seite.addView(bereichAufnahme);

        // --- Bereich Vorlesen
        bereichVorlesen = spalte();
        vorleseText = new EditText(this);
        textfeld(vorleseText, "Hier Text einfügen, z. B. eine Antwort von Claude: im Claude-Chat lange auf die "
                + "Antwort tippen → Kopieren, dann hier auf „📋 Einfügen“.");
        vorleseText.setText(einstellungen.getString("vorlesen", ""));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.topMargin = dp(12);
        bereichVorlesen.addView(vorleseText, vlp);
        LinearLayout vreihe1 = new LinearLayout(this);
        vreihe1.setPadding(0, dp(8), 0, 0);
        vreihe1.addView(aktionsKnopf("📋 Einfügen", v -> einfuegen()), gewicht());
        vreihe1.addView(aktionsKnopf("🗑 Leeren", v -> leereVorleseText()), gewicht());
        bereichVorlesen.addView(vreihe1);
        TextView tempoTitel = text("Tempo:", 15, FARBE_HELL);
        tempoTitel.setGravity(Gravity.START);
        tempoTitel.setPadding(dp(4), dp(10), 0, 0);
        bereichVorlesen.addView(tempoTitel);
        LinearLayout tempoReihe = new LinearLayout(this);
        String[] tempoNamen = {"🐢 Langsam", "Normal", "🐇 Schnell"};
        for (int i = 0; i < 3; i++) {
            final int nr = i;
            tempoKnoepfe[i] = aktionsKnopf(tempoNamen[i], v -> setzeTempo(nr));
            tempoReihe.addView(tempoKnoepfe[i], gewicht());
        }
        bereichVorlesen.addView(tempoReihe);
        LinearLayout vreihe2 = new LinearLayout(this);
        vreihe2.setPadding(0, dp(8), 0, 0);
        probeKnopf = aktionsKnopf("▶ Anhören", v -> probehoeren());
        speichernKnopf = aktionsKnopf("💾 Als Aufnahme speichern", v -> vorleseSpeichern());
        speichernKnopf.setBackground(rund(METS_ORANGE, dp(8)));
        vreihe2.addView(probeKnopf, gewicht());
        LinearLayout.LayoutParams breit = gewicht();
        breit.weight = 1.6f;
        vreihe2.addView(speichernKnopf, breit);
        bereichVorlesen.addView(vreihe2);
        vorleseStatus = text("Die Handy-Stimme liest deinen Text vor und speichert ihn als Tonaufnahme.", 15, Color.WHITE);
        vorleseStatus.setPadding(0, dp(8), 0, 0);
        bereichVorlesen.addView(vorleseStatus);
        seite.addView(bereichVorlesen);
        setzeTempo(einstellungen.getInt("tempo", 1));

        // --- Liste der Aufnahmen (bei Stimme, Medien und Vorlesen)
        bereichListe = spalte();
        TextView ueberschrift = text("Meine Aufnahmen", 18, Color.WHITE);
        ueberschrift.setTypeface(null, Typeface.BOLD);
        ueberschrift.setGravity(Gravity.START);
        ueberschrift.setPadding(0, dp(20), 0, dp(4));
        bereichListe.addView(ueberschrift);
        TextView ort = text("Gespeichert im Handy unter: Musik → Smartphone-Aufnahme", 13, FARBE_HELL);
        ort.setGravity(Gravity.START);
        bereichListe.addView(ort);
        liste = spalte();
        bereichListe.addView(liste);
        seite.addView(bereichListe);

        // --- Bereich Diktieren
        bereichDiktat = spalte();
        diktatKnopf = rundKnopf("🎤");
        diktatKnopf.setOnClickListener(v -> diktatKnopfGedrueckt());
        bereichDiktat.addView(diktatKnopf, mittig(dp(150), dp(150), dp(16)));
        diktatStatus = text("Tippe auf das Mikrofon und sprich.", 16, Color.WHITE);
        bereichDiktat.addView(diktatStatus);
        diktatText = new EditText(this);
        textfeld(diktatText, "Hier erscheint dein Text. Du kannst ihn auch selbst ändern.");
        diktatText.setText(einstellungen.getString("diktat", ""));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        bereichDiktat.addView(diktatText, lp);
        LinearLayout reihe1 = new LinearLayout(this), reihe2 = new LinearLayout(this);
        reihe1.setPadding(0, dp(8), 0, 0);
        reihe1.addView(aktionsKnopf("📋 Kopieren", v -> kopieren()), gewicht());
        reihe1.addView(aktionsKnopf("📤 Senden", v -> teileText()), gewicht());
        reihe2.addView(aktionsKnopf("💾 Speichern", v -> speichereText()), gewicht());
        reihe2.addView(aktionsKnopf("🗑 Leeren", v -> leereText()), gewicht());
        bereichDiktat.addView(reihe1);
        bereichDiktat.addView(reihe2);
        TextView tipp = text("Tipp: Sag „Punkt“, „Komma“, „Fragezeichen“, „neue Zeile“ oder „neuer Absatz“.",
                13, Color.GRAY);
        tipp.setPadding(0, dp(8), 0, 0);
        bereichDiktat.addView(tipp);
        seite.addView(bereichDiktat);

        ScrollView rollen = new ScrollView(this);
        rollen.setBackgroundColor(FARBE_HINTERGRUND);
        rollen.setFillViewport(true);
        rollen.addView(seite);
        setContentView(rollen);

        String start = getIntent().getStringExtra("modus");   // nur fuer die automatischen Bildschirmfotos
        setzeModus(start != null ? start : einstellungen.getString("modus", AufnahmeService.ART_MIKRO));
        if (Build.VERSION.SDK_INT >= 33) frageRechte(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(aktualisieren);
        ladeListe();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(aktualisieren);
        einstellungen.edit().putString("diktat", diktatText.getText().toString())
                .putString("vorlesen", vorleseText.getText().toString()).apply();
        stoppeDiktat();
        stoppeWiedergabe();
        stoppeVorlesen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stimme != null && !speichert) stimme.shutdown();
    }

    private void setzeModus(String m) {
        if (modus != null && AufnahmeService.laeuft && !m.equals(modus)) {
            status.setText("Erst die laufende Aufnahme stoppen.");
            return;
        }
        if (!"Diktat".equals(m)) stoppeDiktat();
        modus = m;
        einstellungen.edit().putString("modus", m).apply();
        knopfStimme.setBackground(rund(m.equals(AufnahmeService.ART_MIKRO) ? FARBE_GELB : FARBE_KARTE, dp(8)));
        knopfMedien.setBackground(rund(m.equals(AufnahmeService.ART_MEDIEN) ? FARBE_GELB : FARBE_KARTE, dp(8)));
        knopfDiktat.setBackground(rund(m.equals("Diktat") ? FARBE_GELB : FARBE_KARTE, dp(8)));
        knopfVorlesen.setBackground(rund(m.equals("Vorlesen") ? FARBE_GELB : FARBE_KARTE, dp(8)));
        knopfVorlesen.setTextColor(m.equals("Vorlesen") ? Color.WHITE : FARBE_HELL);
        knopfStimme.setTextColor(m.equals(AufnahmeService.ART_MIKRO) ? Color.WHITE : FARBE_HELL);
        knopfMedien.setTextColor(m.equals(AufnahmeService.ART_MEDIEN) ? Color.WHITE : FARBE_HELL);
        knopfDiktat.setTextColor(m.equals("Diktat") ? Color.WHITE : FARBE_HELL);
        boolean aufnahme = m.equals(AufnahmeService.ART_MIKRO) || m.equals(AufnahmeService.ART_MEDIEN);
        bereichAufnahme.setVisibility(aufnahme ? View.VISIBLE : View.GONE);
        bereichDiktat.setVisibility(m.equals("Diktat") ? View.VISIBLE : View.GONE);
        bereichVorlesen.setVisibility(m.equals("Vorlesen") ? View.VISIBLE : View.GONE);
        bereichListe.setVisibility(m.equals("Diktat") ? View.GONE : View.VISIBLE);
        if (m.equals("Vorlesen")) starteStimme(); else stoppeVorlesen();
        if (m.equals(AufnahmeService.ART_MIKRO)) {
            info.setText("Nimmt deine Stimme mit dem Mikrofon auf. Oranger Knopf = Start, nochmal = Stopp. "
                    + "Läuft auch bei ausgeschaltetem Bildschirm weiter.");
        } else if (m.equals(AufnahmeService.ART_MEDIEN)) {
            info.setText("Nimmt den Ton auf, den das Handy gerade abspielt (z. B. Video, Radio-App). "
                    + "Android fragt beim Start, ob du die Aufnahme erlaubst → „Jetzt starten“. "
                    + "Manche Apps (z. B. Netflix, Spotify) sperren die Aufnahme – dann bleibt es still.");
        } else if (m.equals("Vorlesen")) {
            info.setText("Text einfügen – das Handy liest ihn vor. Mit „Als Aufnahme speichern“ wird daraus "
                    + "eine Tondatei, die du anhören und per WhatsApp oder E-Mail senden kannst.");
        } else {
            info.setText("Sprich – dein Text erscheint unten. Danach kopieren, senden oder als Datei speichern.");
        }
        if (!AufnahmeService.laeuft) status.setText("Bereit.");
    }

    // ------------------------------------------------------------ Aufnehmen
    private void aufnahmeKnopfGedrueckt() {
        if (AufnahmeService.laeuft) {
            startService(new Intent(this, AufnahmeService.class).setAction(AufnahmeService.AKTION_STOPP));
            status.setText("Speichere …");
            return;
        }
        stoppeWiedergabe();
        mitErlaubnis(() -> {
            if (AufnahmeService.ART_MEDIEN.equals(modus)) {
                // Android muss die Medien-Aufnahme jedes Mal bestaetigen
                MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
                startActivityForResult(mpm.createScreenCaptureIntent(), ANFRAGE_MEDIEN);
            } else {
                starteDienst(new Intent(this, AufnahmeService.class).putExtra(AufnahmeService.ART, modus));
            }
        });
    }

    @Override
    protected void onActivityResult(int anfrage, int ergebnis, Intent daten) {
        super.onActivityResult(anfrage, ergebnis, daten);
        if (anfrage != ANFRAGE_MEDIEN) return;
        if (ergebnis != RESULT_OK || daten == null) {
            status.setText("Medien-Aufnahme wurde nicht erlaubt.");
            return;
        }
        starteDienst(new Intent(this, AufnahmeService.class)
                .putExtra(AufnahmeService.ART, AufnahmeService.ART_MEDIEN)
                .putExtra(AufnahmeService.ERGEBNIS_CODE, ergebnis)
                .putExtra(AufnahmeService.ERGEBNIS_DATEN, daten));
    }

    private void starteDienst(Intent intent) {
        try {
            startForegroundService(intent);
            status.setText("Starte …");
        } catch (Exception fehler) {
            status.setText("Aufnahme nicht möglich: " + fehler.getMessage());
        }
    }

    /** Laeuft alle 0,2 Sekunden: Uhr, Pegel, Knoepfe und Meldungen aktualisieren. */
    private final Runnable aktualisieren = new Runnable() {
        @Override
        public void run() {
            boolean laeuft = AufnahmeService.laeuft;
            aufnahmeKnopf.setText(laeuft ? "■" : "●");
            aufnahmeKnopf.setBackground(oval(laeuft ? FARBE_GRAU : FARBE_ROT));
            pauseKnopf.setVisibility(laeuft ? View.VISIBLE : View.INVISIBLE);
            pauseKnopf.setText(AufnahmeService.pausiert ? "▶ Weiter" : "⏸ Pause");
            pegel.setProgress(laeuft && !AufnahmeService.pausiert ? AufnahmeService.pegel : 0);
            if (laeuft) {
                uhr.setText(zeit(AufnahmeService.dauerMs));
                boolean still = AufnahmeService.ART_MEDIEN.equals(AufnahmeService.art)
                        && AufnahmeService.nochKeinTon && AufnahmeService.dauerMs > 4000;
                status.setText(AufnahmeService.pausiert ? "⏸ Pausiert"
                        : still ? AufnahmeService.STILLE_HINWEIS + " Läuft überhaupt etwas mit Ton?"
                        : "🔴 Aufnahme läuft …");
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            String meldung = AufnahmeService.meldung;
            if (meldung != null) {
                AufnahmeService.meldung = null;
                status.setText(meldung);
                ladeListe();
            } else if (warLaufend && !laeuft) {
                ladeListe();
            }
            warLaufend = laeuft;
            handler.postDelayed(this, 200);
        }
    };

    // ------------------------------------------------------------ Liste der Aufnahmen
    private void ladeListe() {
        liste.removeAllViews();
        List<Speicher.Eintrag> eintraege = Speicher.liste(this);
        if (eintraege.isEmpty()) {
            TextView leer = text("Noch keine Aufnahmen.", 15, Color.GRAY);
            leer.setPadding(0, dp(10), 0, 0);
            liste.addView(leer);
        }
        for (Speicher.Eintrag e : eintraege) {
            LinearLayout karte = spalte();
            karte.setBackground(rund(FARBE_KARTE, dp(8)));
            karte.setPadding(dp(12), dp(10), dp(12), dp(6));
            TextView name = text(e.name, 15, Color.WHITE);
            name.setGravity(Gravity.START);
            karte.addView(name);
            TextView details = text(zeit(e.dauerMs) + "  ·  " + groesse(e.groesse), 13, Color.GRAY);
            details.setGravity(Gravity.START);
            karte.addView(details);
            LinearLayout knoepfe = new LinearLayout(this);
            Button abspielen = flacherKnopf(e.uri.equals(spielt) ? "⏹ Stopp" : "▶ Anhören");
            abspielen.setOnClickListener(v -> abspielen(e.uri));
            knoepfe.addView(abspielen);
            Button senden = flacherKnopf("📤 Senden", v -> teileTon(e));
            senden.setBackground(rund(METS_ORANGE, dp(8)));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.setMargins(dp(4), dp(4), dp(4), dp(4));
            knoepfe.addView(senden, slp);
            knoepfe.addView(flacherKnopf("🗑 Löschen", v -> loeschen(e)));
            karte.addView(knoepfe);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            liste.addView(karte, lp);
        }
    }

    private void abspielen(Uri uri) {
        boolean warDieselbe = uri.equals(spielt);
        stoppeWiedergabe();
        if (!warDieselbe) {
            try {
                spieler = new MediaPlayer();
                spieler.setDataSource(this, uri);
                spieler.setOnCompletionListener(mp -> { stoppeWiedergabe(); ladeListe(); });
                spieler.prepare();
                spieler.start();
                spielt = uri;
            } catch (Exception fehler) {
                status.setText("Abspielen nicht möglich.");
                stoppeWiedergabe();
            }
        }
        ladeListe();
    }

    private void stoppeWiedergabe() {
        if (spieler != null) {
            try { spieler.stop(); } catch (Exception ignoriert) { }
            spieler.release();
            spieler = null;
        }
        spielt = null;
    }

    /** Oeffnet die Android-Auswahl "Senden mit …" (WhatsApp, E-Mail, Bluetooth, Drive …). */
    private void teileTon(Speicher.Eintrag e) {
        Intent senden = new Intent(Intent.ACTION_SEND).setType("audio/mp4")
                .putExtra(Intent.EXTRA_STREAM, e.uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Aufnahme: " + e.name)   // Betreff bei E-Mail
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        senden.setClipData(ClipData.newRawUri(e.name, e.uri));
        startActivity(Intent.createChooser(senden, "Aufnahme senden mit …"));
    }

    private void loeschen(Speicher.Eintrag e) {
        new AlertDialog.Builder(this).setTitle("Löschen?").setMessage("„" + e.name + "“ wirklich löschen?")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, w) -> {
                    if (e.uri.equals(spielt)) stoppeWiedergabe();
                    try {
                        getContentResolver().delete(e.uri, null, null);
                        status.setText("Gelöscht.");
                    } catch (Exception fehler) {
                        status.setText("Löschen ging nicht. Bitte in „Eigene Dateien“ löschen.");
                    }
                    ladeListe();
                }).show();
    }

    // ------------------------------------------------------------ Diktieren
    private void diktatKnopfGedrueckt() {
        if (diktatAktiv) {
            stoppeDiktat();
            diktatStatus.setText("Diktat beendet.");
            return;
        }
        if (AufnahmeService.laeuft) {
            diktatStatus.setText("Erst die laufende Aufnahme stoppen.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            diktatStatus.setText("Keine Spracherkennung gefunden. Bitte die App „Google“ installieren "
                    + "oder in den Einstellungen die Spracheingabe aktivieren.");
            return;
        }
        mitErlaubnis(() -> {
            diktatAktiv = true;
            diktatKnopf.setBackground(oval(FARBE_GRAU));
            diktatKnopf.setText("■");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            hoereZu();
        });
    }

    private void hoereZu() {
        if (!diktatAktiv) return;
        if (erkenner == null) {
            erkenner = SpeechRecognizer.createSpeechRecognizer(this);
            erkenner.setRecognitionListener(zuhoerer);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        try {
            erkenner.startListening(i);
        } catch (Exception fehler) {
            diktatStatus.setText("Spracherkennung ließ sich nicht starten.");
            stoppeDiktat();
        }
    }

    private void stoppeDiktat() {
        diktatAktiv = false;
        if (erkenner != null) {
            erkenner.destroy();
            erkenner = null;
        }
        if (diktatKnopf != null) {
            diktatKnopf.setBackground(oval(METS_ORANGE));
            diktatKnopf.setText("🎤");
        }
        if (!AufnahmeService.laeuft) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private final RecognitionListener zuhoerer = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle b) { diktatStatus.setText("🔴 Ich höre zu …"); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float db) { }
        @Override public void onBufferReceived(byte[] b) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int typ, Bundle b) { }

        @Override public void onPartialResults(Bundle b) {
            ArrayList<String> t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (t != null && !t.isEmpty() && !t.get(0).isEmpty()) diktatStatus.setText("… " + t.get(0));
        }

        @Override public void onResults(Bundle b) {
            ArrayList<String> t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (t != null && !t.isEmpty()) fuegeTextEin(t.get(0));
            diktatStatus.setText("🔴 Ich höre zu …");
            hoereZu();   // gleich weiter zuhoeren
        }

        @Override public void onError(int fehler) {
            if (!diktatAktiv) return;
            if (fehler == SpeechRecognizer.ERROR_NO_MATCH || fehler == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                hoereZu();   // nur eine Sprechpause - weiter zuhoeren
            } else if (fehler == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || fehler == SpeechRecognizer.ERROR_CLIENT) {
                if (erkenner != null) { erkenner.destroy(); erkenner = null; }
                handler.postDelayed(MainActivity.this::hoereZu, 500);
            } else {
                String grund = fehler == SpeechRecognizer.ERROR_NETWORK || fehler == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                        ? "Keine Internet-Verbindung für die Spracherkennung."
                        : fehler == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ? "Mikrofon nicht erlaubt."
                        : fehler == SpeechRecognizer.ERROR_AUDIO
                        ? "Mikrofon wird gerade von einer anderen App benutzt."
                        : "Spracherkennung unterbrochen (Fehler " + fehler + ").";
                stoppeDiktat();
                diktatStatus.setText(grund + " Tippe nochmal auf das Mikrofon.");
            }
        }
    };

    /** Haengt erkannten Text an und wandelt gesprochene Satzzeichen um. */
    private void fuegeTextEin(String gesagt) {
        String neu = " " + gesagt.trim() + " ";
        neu = neu.replaceAll("(?i)\\s+neuer absatz\\s+", "\n\n")
                .replaceAll("(?i)\\s+neue zeile\\s+", "\n")
                .replaceAll("(?i)\\s+punkt(?=\\s)", ".")
                .replaceAll("(?i)\\s+komma(?=\\s)", ",")
                .replaceAll("(?i)\\s+fragezeichen(?=\\s)", "?")
                .replaceAll("(?i)\\s+ausrufezeichen(?=\\s)", "!")
                .replaceAll("(?i)\\s+doppelpunkt(?=\\s)", ":")
                .replaceAll("[ \\t]+\n", "\n").replaceAll("\n[ \\t]+", "\n").trim();
        if (neu.isEmpty()) return;
        String bisher = diktatText.getText().toString();
        String ohneLeer = bisher.replaceAll("\\s+$", "");
        boolean satzanfang = ohneLeer.isEmpty() || ohneLeer.endsWith(".") || ohneLeer.endsWith("?")
                || ohneLeer.endsWith("!") || bisher.endsWith("\n");
        if (satzanfang) neu = neu.substring(0, 1).toUpperCase(Locale.GERMANY) + neu.substring(1);
        boolean ohneAbstand = bisher.isEmpty() || bisher.endsWith(" ") || bisher.endsWith("\n")
                || neu.startsWith("\n") || neu.matches("^[.,?!:].*");
        diktatText.append((ohneAbstand ? "" : " ") + neu);
        einstellungen.edit().putString("diktat", diktatText.getText().toString()).apply();
    }

    private void kopieren() {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Diktat", diktatText.getText()));
        diktatStatus.setText("📋 Text kopiert – jetzt woanders lange tippen → Einfügen.");
    }

    private void teileText() {
        Intent senden = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, diktatText.getText().toString());
        startActivity(Intent.createChooser(senden, "Text senden mit …"));
    }

    private void speichereText() {
        String t = diktatText.getText().toString().trim();
        if (t.isEmpty()) {
            diktatStatus.setText("Noch kein Text zum Speichern.");
            return;
        }
        try {
            diktatStatus.setText("💾 Gespeichert unter: " + Speicher.textSpeichern(this, t));
        } catch (Exception fehler) {
            diktatStatus.setText("Speichern ging nicht: " + fehler.getMessage());
        }
    }

    private void leereText() {
        if (diktatText.length() == 0) return;
        new AlertDialog.Builder(this).setTitle("Text leeren?").setMessage("Der ganze Text wird gelöscht.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Leeren", (d, w) -> {
                    diktatText.setText("");
                    einstellungen.edit().putString("diktat", "").apply();
                }).show();
    }

    // ------------------------------------------------------------ Vorlesen
    private void starteStimme() {
        if (stimme != null) return;
        vorleseStatus.setText("Vorlese-Stimme wird vorbereitet …");
        stimme = new TextToSpeech(this, ergebnis -> {
            if (ergebnis != TextToSpeech.SUCCESS) {
                vorleseStatus.setText("Keine Vorlese-Stimme gefunden. Einstellungen → Allgemeine Verwaltung → "
                        + "Text-zu-Sprache → bevorzugtes Modul wählen (z. B. Google).");
                stimme = null;
                return;
            }
            int sprache = stimme.setLanguage(Locale.GERMANY);
            if (sprache == TextToSpeech.LANG_MISSING_DATA || sprache == TextToSpeech.LANG_NOT_SUPPORTED) {
                vorleseStatus.setText("Die deutsche Stimme fehlt. Einstellungen → Allgemeine Verwaltung → "
                        + "Text-zu-Sprache → ⚙ beim Modul → Sprachdaten installieren → Deutsch.");
                return;
            }
            stimme.setSpeechRate(TEMPO[einstellungen.getInt("tempo", 1)]);
            stimmeBereit = true;
            vorleseStatus.setText("Bereit. Text einfügen, dann „▶ Anhören“ oder „💾 Als Aufnahme speichern“.");
        });
    }

    private String vorleseTextOderHinweis() {
        String t = vorleseText.getText().toString().trim();
        if (t.isEmpty()) {
            vorleseStatus.setText("Erst einen Text einfügen.");
            return null;
        }
        if (!stimmeBereit) {
            starteStimme();
            if (!stimmeBereit) return null;
        }
        return t;
    }

    private void setzeTempo(int nr) {
        einstellungen.edit().putInt("tempo", nr).apply();
        for (int i = 0; i < 3; i++) {
            tempoKnoepfe[i].setBackground(rund(i == nr ? Color.WHITE : FARBE_KNOPF, dp(8)));
            tempoKnoepfe[i].setTextColor(i == nr ? METS_BLAU : Color.WHITE);
        }
        if (stimme != null) stimme.setSpeechRate(TEMPO[nr]);
    }

    private void probehoeren() {
        if (liestVor) {
            stoppeVorlesen();
            vorleseStatus.setText("Gestoppt.");
            return;
        }
        if (speichert) return;
        String t = vorleseTextOderHinweis();
        if (t == null) return;
        stoppeWiedergabe();
        stimme.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String u) { }
            @Override public void onDone(String u) { if (u.equals("ende")) runOnUiThread(() -> stoppeVorlesen()); }
            @Override public void onError(String u) { runOnUiThread(() -> stoppeVorlesen()); }
        });
        java.util.List<String> stuecke = Vorleser.teile(t, Math.min(3000, TextToSpeech.getMaxSpeechInputLength() - 100));
        for (int i = 0; i < stuecke.size(); i++) {
            stimme.speak(stuecke.get(i), i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null,
                    i == stuecke.size() - 1 ? "ende" : "teil" + i);
        }
        liestVor = true;
        probeKnopf.setText("■ Stopp");
        vorleseStatus.setText("🔊 Liest vor …");
    }

    private void stoppeVorlesen() {
        if (liestVor && stimme != null) stimme.stop();
        liestVor = false;
        if (probeKnopf != null) probeKnopf.setText("▶ Anhören");
    }

    private void vorleseSpeichern() {
        if (speichert) return;
        String t = vorleseTextOderHinweis();
        if (t == null) return;
        stoppeVorlesen();
        speichert = true;
        speichernKnopf.setText("⏳ Wird erstellt …");
        vorleseStatus.setText("Die Aufnahme wird erstellt. Bei langen Texten kann das etwas dauern …");
        TextToSpeech tts = stimme;
        new Thread(() -> {
            String meldung;
            try {
                Vorleser.alsAufnahme(this, tts, t);
                meldung = "✅ Als Aufnahme gespeichert – siehe unten in der Liste. Zum Verschicken: 📤 Senden.";
            } catch (Exception fehler) {
                meldung = "Speichern ging nicht: " + fehler.getMessage();
            }
            String m = meldung;
            runOnUiThread(() -> {
                speichert = false;
                speichernKnopf.setText("💾 Als Aufnahme speichern");
                vorleseStatus.setText(m);
                ladeListe();
                if (isDestroyed()) tts.shutdown();
            });
        }, "Vorlesen").start();
    }

    private void einfuegen() {
        ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
        CharSequence t = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(this) : null;
        if (t == null || t.length() == 0) {
            vorleseStatus.setText("Die Zwischenablage ist leer. Erst einen Text kopieren.");
            return;
        }
        String bisher = vorleseText.getText().toString();
        vorleseText.setText(bisher.trim().isEmpty() ? t.toString() : bisher + "\n\n" + t);
        vorleseStatus.setText("Text eingefügt.");
    }

    private void leereVorleseText() {
        if (vorleseText.length() == 0) return;
        new AlertDialog.Builder(this).setTitle("Text leeren?").setMessage("Der ganze Text wird gelöscht.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Leeren", (d, w) -> vorleseText.setText("")).show();
    }

    // ------------------------------------------------------------ Erlaubnis fuers Mikrofon
    private void mitErlaubnis(Runnable aktion) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            aktion.run();
        } else {
            nachErlaubnis = aktion;
            frageRechte(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void frageRechte(String recht) {
        if (checkSelfPermission(recht) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{recht}, ANFRAGE_RECHTE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int anfrage, String[] rechte, int[] ergebnisse) {
        for (int i = 0; i < rechte.length; i++) {
            if (!Manifest.permission.RECORD_AUDIO.equals(rechte[i])) continue;
            if (ergebnisse[i] == PackageManager.PERMISSION_GRANTED && nachErlaubnis != null) {
                nachErlaubnis.run();
            } else if (ergebnisse[i] != PackageManager.PERMISSION_GRANTED) {
                String t = "Ohne Mikrofon-Erlaubnis geht es nicht. Einstellungen → Apps → Smartphone-Aufnahme "
                        + "→ Berechtigungen → Mikrofon → Erlauben.";
                status.setText(t);
                diktatStatus.setText(t);
            }
            nachErlaubnis = null;
        }
    }

    // ------------------------------------------------------------ kleine Helfer fuer das Aussehen
    private int dp(int wert) {
        return Math.round(wert * getResources().getDisplayMetrics().density);
    }

    private static String zeit(long ms) {
        long s = ms / 1000, h = s / 3600, m = s / 60 % 60;
        return h > 0 ? String.format(Locale.GERMANY, "%d:%02d:%02d", h, m, s % 60)
                : String.format(Locale.GERMANY, "%d:%02d", m, s % 60);
    }

    private static String groesse(long bytes) {
        return bytes < 1048576 ? Math.max(1, bytes / 1024) + " KB"
                : String.format(Locale.GERMANY, "%.1f MB", bytes / 1048576.0);
    }

    private LinearLayout spalte() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String t, int sp, int farbe) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(farbe);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private GradientDrawable rund(int farbe, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(farbe);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable oval(int farbe) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(farbe);
        return g;
    }

    private void textfeld(EditText feld, String hinweis) {
        feld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        feld.setMinLines(6);
        feld.setGravity(Gravity.TOP | Gravity.START);
        feld.setTextColor(Color.BLACK);
        feld.setHintTextColor(Color.GRAY);
        feld.setBackgroundColor(Color.WHITE);
        feld.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        feld.setPadding(dp(10), dp(10), dp(10), dp(10));
        feld.setHint(hinweis);
    }

    private Button modusKnopf(String t, String m) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setPadding(0, dp(6), 0, dp(6));
        b.setOnClickListener(v -> setzeModus(m));
        return b;
    }

    private Button rundKnopf(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        b.setTextColor(Color.WHITE);
        b.setBackground(oval(FARBE_ROT));
        return b;
    }

    private Button flacherKnopf(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(FARBE_BLAU);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private Button flacherKnopf(String t, View.OnClickListener aktion) {
        Button b = flacherKnopf(t);
        b.setOnClickListener(aktion);
        return b;
    }

    private Button aktionsKnopf(String t, View.OnClickListener aktion) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(Color.WHITE);
        b.setBackground(rund(FARBE_KNOPF, dp(8)));
        b.setOnClickListener(aktion);
        return b;
    }

    private LinearLayout.LayoutParams gewicht() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private LinearLayout.LayoutParams mittig(int breite, int hoehe, int abstandOben) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(breite, hoehe);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = abstandOben;
        return lp;
    }
}
