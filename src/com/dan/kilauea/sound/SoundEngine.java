package com.dan.kilauea.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Klang der Szene (Phase 15), ganz synthetisch, ohne Tondateien: sieben Gruppen, jede einzeln an- und
 * ausschaltbar und mit eigener Lautstärke. Ein eigener Faden rechnet Blöcke zu 512 Abtastwerten
 * (44,1 kHz, Stereo, rund 12 ms) und schreibt sie in die Tonausgabe. Gibt es keine Tonausgabe (Prüfumgebung),
 * bleibt der Faden stumm; {@link #render} rechnet dann trotzdem (Hörproben, Prüfung).
 * <p>Der Renderer legt rund 20-mal je Sekunde einen neuen {@link Scene}-Schnappschuss ab und meldet
 * Ereignisse (Beben, Rufe, Dampfexplosionen) über {@link #post}.</p>
 */
public final class SoundEngine {
    public enum Group {
        FOUNTAIN("Fontäne"), LAVA("Lava und Dampf"), SEA("Meer"), WIND("Wind und Regen"), FOREST("Regenwald"), QUAKE("Beben"), TRAFFIC("Tiere und Verkehr");
        public final String label;
        Group(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public static final int RATE = Dsp.RATE, BLOCK = 512;
    /** Ereignisse: Beben (x, y, z, Magnitude), Ruf (Art, x, y, z), Dampfexplosion (x, z, Stärke). */
    public static final int EV_QUAKE = 1, EV_CALL = 2, EV_SURGE = 3;

    private volatile Scene scene = new Scene();
    private final boolean[] on = new boolean[Group.values().length];
    private final double[] vol = new double[Group.values().length];
    private final float[] gainNow = new float[Group.values().length];
    private volatile boolean muted;
    private volatile double master = 0.8;
    private final Synth[] synth;
    private final ConcurrentLinkedQueue<double[]> events = new ConcurrentLinkedQueue<>();
    private final float[][] gl = new float[Group.values().length][BLOCK], gr = new float[Group.values().length][BLOCK];
    private final Reverb reverb = new Reverb();
    private double time;
    private float masterNow = -1;
    private Thread thread;
    private volatile boolean running;
    private volatile String status = "Klang aus";
    /** Lautstärke je Gruppe im letzten Block (RMS, für die Prüfung und die Anzeige). */
    public final double[] rms = new double[Group.values().length];
    private volatile double peak;

    public SoundEngine() {
        java.util.Arrays.fill(on, true);
        java.util.Arrays.fill(vol, 1.0);
        synth = new Synth[]{new FountainSound(), new LavaSound(), new SeaSound(), new WindSound(), new ForestSound(), new QuakeSound(), new TrafficSound()};
    }

    // ------------------------------------------------------------------ Steuerung

    public void scene(Scene s) { scene = s; }
    public Scene scene() { return scene; }
    public void set(Group g, boolean v) { on[g.ordinal()] = v; }
    public boolean on(Group g) { return on[g.ordinal()]; }
    public void volume(Group g, double v) { vol[g.ordinal()] = Math.max(0, Math.min(2, v)); }
    public double volume(Group g) { return vol[g.ordinal()]; }
    public void muted(boolean m) { muted = m; }
    public boolean muted() { return muted; }
    public void master(double v) { master = Math.max(0, Math.min(1.5, v)); }
    public double master() { return master; }
    public String status() { return status; }
    public double peak() { return peak; }

    /** Ein Ereignis melden (aus dem Bildlauf). */
    public void post(int kind, double a, double b, double c, double d) {
        if (events.size() < 512) events.add(new double[]{kind, a, b, c, d});
    }

    // ------------------------------------------------------------------ Rechnen

    /** Einen Block rechnen und in L, R schreiben (n ≤ BLOCK). */
    public synchronized void render(float[] L, float[] R, int n) {
        Scene s = scene;
        if (s.stamp < 0) s.stamp = time;
        double[] e;
        while ((e = events.poll()) != null) for (Synth sy : synth) sy.event(s, e);
        double dt = n / (double) RATE;
        java.util.Arrays.fill(L, 0, n, 0f);
        java.util.Arrays.fill(R, 0, n, 0f);
        for (int g = 0; g < synth.length; g++) {
            float target = on[g] ? (float) vol[g] : 0f;
            float[] a = gl[g], b = gr[g];
            java.util.Arrays.fill(a, 0, n, 0f);
            java.util.Arrays.fill(b, 0, n, 0f);
            // abgeschaltet: nach dem Ausblenden nicht mehr rechnen (aber Ereignisse verwerfen)
            if (target == 0 && gainNow[g] < 1e-4) { gainNow[g] = 0; rms[g] = 0; synth[g].idle(s, n); continue; }
            synth[g].block(s, a, b, n, time);
            float g0 = gainNow[g], d = (target - g0) / n;
            double sum = 0;
            for (int i = 0; i < n; i++) {
                float k = g0 + d * (i + 1);
                float l = a[i] * k, r = b[i] * k;
                L[i] += l; R[i] += r;
                sum += l * l + r * r;
            }
            gainNow[g] = target;
            rms[g] = Math.sqrt(sum / (2 * n));
        }
        // Hall in der Lavaröhre [G]
        reverb.process(L, R, n, s.inTube ? 0.32f : 0f);
        float m = muted ? 0f : (float) master;
        if (masterNow < 0) masterNow = m;
        float dm = (m - masterNow) / n;
        double pk = 0;
        for (int i = 0; i < n; i++) {
            masterNow += dm;
            float l = soft(L[i] * masterNow), r = soft(R[i] * masterNow);
            L[i] = l; R[i] = r;
            pk = Math.max(pk, Math.max(Math.abs(l), Math.abs(r)));
        }
        peak = pk;
        time += dt;
    }

    /** Weiche Begrenzung: bis 0,5 linear, darüber sanft gegen 1 [G]. */
    static float soft(float x) {
        float a = Math.abs(x);
        if (a < 0.5f) return x;
        float y = 0.5f + 0.5f * (float) Math.tanh((a - 0.5f) / 0.5f);
        return Math.copySign(y, x);
    }

    // ------------------------------------------------------------------ Tonausgabe

    /** Faden starten; ohne Tonausgabe bleibt er stumm und meldet das. */
    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "Klang");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    public void stop() { running = false; }

    private void loop() {
        AudioFormat f = new AudioFormat(RATE, 16, 2, true, false);
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(f);
            line.open(f, BLOCK * 4 * 6);   // rund 70 ms Vorrat
            line.start();
            status = "Klang an · " + RATE / 1000.0 + " kHz Stereo";
        } catch (Exception ex) {
            status = "Keine Tonausgabe gefunden (" + ex.getClass().getSimpleName() + ")";
            running = false;
            return;
        }
        float[] L = new float[BLOCK], R = new float[BLOCK];
        byte[] out = new byte[BLOCK * 4];
        while (running) {
            try {
                render(L, R, BLOCK);
            } catch (RuntimeException ex) {
                status = "Klang: " + ex;
                java.util.Arrays.fill(L, 0f);
                java.util.Arrays.fill(R, 0f);
            }
            for (int i = 0; i < BLOCK; i++) {
                int l = Math.round(Math.max(-1f, Math.min(1f, L[i])) * 32767), r = Math.round(Math.max(-1f, Math.min(1f, R[i])) * 32767);
                out[4 * i] = (byte) l; out[4 * i + 1] = (byte) (l >> 8);
                out[4 * i + 2] = (byte) r; out[4 * i + 3] = (byte) (r >> 8);
            }
            line.write(out, 0, out.length);
        }
        line.drain();
        line.close();
        status = "Klang aus";
    }

    // ------------------------------------------------------------------ Hörproben

    /** Sekunden Klang ohne Ausgabe rechnen; scene liefert je Block den Schnappschuss (oder null für den aktuellen). */
    public float[][] offline(double seconds, java.util.function.DoubleFunction<Scene> sceneAt) {
        int total = (int) Math.round(seconds * RATE);
        float[] L = new float[total], R = new float[total];
        float[] bl = new float[BLOCK], br = new float[BLOCK];
        for (int i = 0; i < total; i += BLOCK) {
            int n = Math.min(BLOCK, total - i);
            if (sceneAt != null) { Scene s = sceneAt.apply(i / (double) RATE); if (s != null) scene = s; }
            render(bl, br, n);
            System.arraycopy(bl, 0, L, i, n);
            System.arraycopy(br, 0, R, i, n);
        }
        return new float[][]{L, R};
    }

    /** Als WAV (16 Bit, Stereo) speichern. */
    public static void wav(File file, float[] L, float[] R) throws IOException {
        int n = L.length;
        byte[] b = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            int l = Math.round(Math.max(-1f, Math.min(1f, L[i])) * 32767), r = Math.round(Math.max(-1f, Math.min(1f, R[i])) * 32767);
            b[4 * i] = (byte) l; b[4 * i + 1] = (byte) (l >> 8); b[4 * i + 2] = (byte) r; b[4 * i + 3] = (byte) (r >> 8);
        }
        AudioFormat f = new AudioFormat(RATE, 16, 2, true, false);
        try (javax.sound.sampled.AudioInputStream in = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(b), f, n)) {
            AudioSystem.write(in, javax.sound.sampled.AudioFileFormat.Type.WAVE, file);
        }
    }

    /** Zeile für das Bedienfeld. */
    public String summary() {
        StringBuilder b = new StringBuilder(status);
        if (muted) b.append(" · stumm (M)");
        b.append(String.format(java.util.Locale.GERMAN, " · Spitze %.0f %%", 100 * peak));
        return b.toString();
    }

    /** Die Synthese einer Gruppe. */
    interface Synth {
        /** Einen Block hinzumischen. */
        void block(Scene s, float[] L, float[] R, int n, double t);
        /** Ein Ereignis aufnehmen (Beben, Ruf, Dampfexplosion). */
        default void event(Scene s, double[] e) { }
        /** Gruppe aus: Zustand ruhen lassen, ohne zu rechnen. */
        default void idle(Scene s, int n) { }
    }
}
