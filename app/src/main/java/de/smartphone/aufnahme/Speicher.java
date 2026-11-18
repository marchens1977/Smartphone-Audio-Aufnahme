package de.smartphone.aufnahme;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Speichert Aufnahmen und Texte im normalen Handy-Speicher, damit man sie auch
 * in "Eigene Dateien", in Musik-Apps oder per WhatsApp usw. findet:
 *   Aufnahmen: Music/Smartphone-Aufnahme
 *   Diktate:   Documents/Smartphone-Aufnahme
 */
class Speicher {
    static final String ORDNER_TON = Environment.DIRECTORY_MUSIC + "/Smartphone-Aufnahme/";
    static final String ORDNER_TEXT = Environment.DIRECTORY_DOCUMENTS + "/Smartphone-Aufnahme/";

    static class Eintrag {
        Uri uri;
        String name;
        long dauerMs, groesse;
    }

    static String dateiname(String art, String endung) {
        return art + "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(new Date()) + endung;
    }

    /** Kopiert die fertige Aufnahme aus dem App-Zwischenspeicher in den Ordner "Music". */
    static Uri tonSpeichern(Context ctx, File quelle, String name) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues werte = new ContentValues();
        werte.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
        werte.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        werte.put(MediaStore.Audio.Media.RELATIVE_PATH, ORDNER_TON);
        werte.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), werte);
        if (uri == null) throw new Exception("Speicher nicht erreichbar");
        try (InputStream ein = new FileInputStream(quelle); OutputStream aus = cr.openOutputStream(uri)) {
            byte[] puffer = new byte[65536];
            int n;
            while ((n = ein.read(puffer)) > 0) aus.write(puffer, 0, n);
        } catch (Exception fehler) {
            cr.delete(uri, null, null);
            throw fehler;
        }
        werte.clear();
        werte.put(MediaStore.Audio.Media.IS_PENDING, 0);
        cr.update(uri, werte, null, null);
        quelle.delete();
        return uri;
    }

    /** Speichert einen Text (Diktat) als .txt-Datei im Ordner "Documents". */
    static String textSpeichern(Context ctx, String text) throws Exception {
        String name = dateiname("Diktat", ".txt");
        ContentValues werte = new ContentValues();
        werte.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        werte.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        werte.put(MediaStore.MediaColumns.RELATIVE_PATH, ORDNER_TEXT);
        Uri uri = ctx.getContentResolver().insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), werte);
        if (uri == null) throw new Exception("Speicher nicht erreichbar");
        try (OutputStream aus = ctx.getContentResolver().openOutputStream(uri)) {
            aus.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return ORDNER_TEXT + name;
    }

    /** Alle Aufnahmen dieser App, neueste zuerst. */
    static List<Eintrag> liste(Context ctx) {
        List<Eintrag> ergebnis = new ArrayList<>();
        Uri sammlung = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] spalten = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE};
        try (Cursor c = ctx.getContentResolver().query(sammlung, spalten,
                MediaStore.Audio.Media.RELATIVE_PATH + "=?", new String[]{ORDNER_TON},
                MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext()) {
                Eintrag e = new Eintrag();
                e.uri = ContentUris.withAppendedId(sammlung, c.getLong(0));
                e.name = c.getString(1);
                e.dauerMs = c.getLong(2);
                e.groesse = c.getLong(3);
                ergebnis.add(e);
            }
        } catch (Exception ignoriert) { }
        return ergebnis;
    }
}
