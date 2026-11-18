package de.smartphone.aufnahme;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wandelt rohen Ton (PCM, 16 Bit) in AAC um und schreibt ihn als .m4a-Datei.
 * AAC/m4a ist klein und spielt auf jedem Handy und Computer ab.
 */
class AacSchreiber {
    private final MediaCodec codec;
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private final int abtastrate, kanaele;
    private int spur = -1;
    private boolean muxerLaeuft = false;
    private long geschriebeneBytes = 0;   // fuer den Zeitstempel

    AacSchreiber(String datei, int abtastrate, int kanaele) throws IOException {
        this.abtastrate = abtastrate;
        this.kanaele = kanaele;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, abtastrate, kanaele);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, kanaele == 1 ? 96000 : 160000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        muxer = new MediaMuxer(datei, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    /** Gibt Ton-Daten an den Umwandler. */
    void schreibe(byte[] daten, int laenge) {
        int pos = 0;
        while (pos < laenge) {
            int index = codec.dequeueInputBuffer(10000);
            if (index >= 0) {
                ByteBuffer puffer = codec.getInputBuffer(index);
                puffer.clear();
                int stueck = Math.min(puffer.remaining(), laenge - pos);
                stueck -= stueck % (2 * kanaele);   // nur ganze Abtastwerte
                if (stueck <= 0) stueck = Math.min(puffer.remaining(), laenge - pos);
                puffer.put(daten, pos, stueck);
                codec.queueInputBuffer(index, 0, stueck, zeitstempel(), 0);
                geschriebeneBytes += stueck;
                pos += stueck;
            }
            leeren(false);
        }
    }

    private long zeitstempel() {
        return geschriebeneBytes / (2L * kanaele) * 1_000_000L / abtastrate;
    }

    /** Holt fertig umgewandelte Stuecke ab und schreibt sie in die Datei.
     *  bisZumEnde = true: so lange warten, bis der Umwandler "fertig" meldet. */
    private void leeren(boolean bisZumEnde) {
        int leereVersuche = 0;
        while (true) {
            int index = codec.dequeueOutputBuffer(info, bisZumEnde ? 10000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // Beim Abschluss bis zu 10 Sekunden ohne neue Daten warten, sonst sofort weiter
                if (!bisZumEnde || ++leereVersuche > 1000) return;
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                spur = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                muxerLaeuft = true;
            } else if (index >= 0) {
                leereVersuche = 0;
                ByteBuffer puffer = codec.getOutputBuffer(index);
                boolean konfig = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (!konfig && info.size > 0 && muxerLaeuft) {
                    puffer.position(info.offset);
                    puffer.limit(info.offset + info.size);
                    muxer.writeSampleData(spur, puffer, info);
                }
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }
    }

    /** Aufnahme abschliessen. Erst danach ist die Datei abspielbar.
     *  Wichtig: Erst wenn das "Ende"-Zeichen angenommen wurde, kommen auch die letzten Stuecke heraus. */
    void beenden() {
        try {
            boolean endeGesendet = false;
            for (int versuch = 0; versuch < 1000 && !endeGesendet; versuch++) {
                int index = codec.dequeueInputBuffer(10000);
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, zeitstempel(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    endeGesendet = true;
                } else {
                    leeren(false);   // Platz schaffen: fertige Stuecke abholen
                }
            }
            leeren(true);
        } finally {
            try { codec.stop(); } catch (Exception ignoriert) { }
            codec.release();
            try { if (muxerLaeuft) muxer.stop(); } catch (Exception ignoriert) { }
            muxer.release();
        }
    }

    /** Dauer der bisherigen Aufnahme in Millisekunden. */
    long dauerMs() {
        return zeitstempel() / 1000;
    }
}
