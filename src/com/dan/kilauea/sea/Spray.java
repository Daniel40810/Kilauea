package com.dan.kilauea.sea;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Gischt an den Klippen als Teilchen, ohne Grafik (Phase 9). Läuft ein Kamm der Dünung gegen eine
 * Klippe, schlägt er an die Wand und wirft Wasser hoch; der Passat trägt den Sprühnebel fort.
 * <ul>
 * <li>Zeitpunkt: Phase der Dünung an der Klippe, wie im Shader (k x − ω t) [L, lineare Theorie].</li>
 * <li>Wellengruppen: etwa jede siebte Welle ist die höchste (Hüllkurve) [L, Erscheinung; Form G].</li>
 * <li>Höhe des Wurfs: bis rund das Zwölffache der Wellenhöhe, höchstens 35 m [G, nach Fotos von Hōlei].</li>
 * <li>Nur an Uferpunkten, die {@link Coast} als Klippe kennt, und nur, wo die Dünung auf das Ufer zuläuft.</li>
 * </ul>
 * Ausgabe wie {@code atmos.Steam}: je Teilchen x, y, z, Größe, Deckkraft, Zufall, Art (2), –, von hinten
 * nach vorn sortiert. Fester Keim, keine Allokation im Schritt (außer beim Wachsen der Puffer).
 */
public final class Spray implements com.dan.kilauea.atmos.Puffs {
    public static final int MAX = 3000;
    static final int P = 12;   // x y z vx vy vz alter leben größe0 größe1 deckkraft zufall
    private final float[] p = new float[P * MAX];
    private int n;
    private final SplittableRandom rnd = new SplittableRandom(1868);
    public final float[] out = new float[8 * MAX];
    public int outCount;
    private final long[] keys = new long[MAX];
    private volatile Coast coast;
    private double[] lastCycle = new double[0];
    private Coast lastCoast;
    /** Würfe seit dem Start (für die Prüfung). */
    public int bursts;
    /** Reichweite um die Kamera (m). */
    public double range = 1800;

    public void coast(Coast c) { coast = c; }
    public Coast coast() { return coast; }

    /**
     * Ein Schritt. time: Sekunden (dieselbe Uhr wie die Phase im Shader), period (s), dir: Laufrichtung der
     * Dünung (Einheitsvektor x, z), h0: Höhe der Dünung (m), wind (m/s nach x, z), cam (x, y, z).
     */
    public void update(double dt, double time, double period, double[] dir, double h0, double[] wind, double[] cam) {
        dt = Math.min(dt, 0.1);
        Coast c = coast;
        if (c != null && h0 > 0.05) {
            if (c != lastCoast) {
                lastCoast = c;
                lastCycle = new double[c.points.size()];
                for (int i = 0; i < lastCycle.length; i++) lastCycle[i] = Double.NaN;
            }
            double w = Waves.omega(period);
            for (int i = 0; i < c.points.size(); i++) {
                Coast.Point q = c.points.get(i);
                if (!q.cliff || (i % 3) != 0) continue;               // jeder dritte Punkt (rund 30 m)
                if (Math.hypot(q.x - cam[0], q.z - cam[2]) > range) continue;
                double onshore = -(dir[0] * q.nx + dir[1] * q.nz);
                if (onshore < 0.15) continue;
                // jede Stelle der Wand etwas anders (Buchten, Vorsprünge): bis ±1 s versetzt [G]
                double jit = 1.6 * Math.sin(q.x * 0.37 + q.z * 0.23);
                double ph = Waves.deepPhase(q.x, q.z, dir[0], dir[1], period, time) + jit;
                double cyc = Math.floor(ph / (2 * Math.PI));
                double prev = lastCycle[i];
                lastCycle[i] = cyc;
                if (Double.isNaN(prev) || cyc >= prev) continue;     // die Phase fällt mit der Zeit: neuer Kamm
                double g = 0.5 + 0.5 * Math.cos(ph / 7.0 + q.x * 0.006 + q.z * 0.004);
                double rock = 0.25 + 0.75 * (0.5 + 0.5 * Math.sin(q.x * 1.73 + q.z * 2.11));   // Form der Wand an dieser Stelle [G]
                double group = 0.25 + 0.75 * g * g;
                double s = h0 * onshore * group * rock * Math.min(1, q.depth / 8) * (0.6 + 0.4 * rnd.nextDouble());
                if (s > 0.3) burst(q, s, w);
            }
        }
        // bewegen
        int k = 0;
        for (int i = 0; i < n; i++) {
            int o = i * P;
            float age = p[o + 6] + (float) dt;
            if (age > p[o + 7]) continue;
            double vx = p[o + 3], vy = p[o + 4], vz = p[o + 5];
            double drag = 0.9;                                    // feine Tropfen folgen der Luft rasch
            vx += (wind[0] - vx) * drag * dt;
            vz += (wind[1] - vz) * drag * dt;
            vy += (-9.81 * 0.35 - vy * 0.35) * dt;                // fallen, gebremst
            p[o] += (float) (vx * dt);
            p[o + 1] = (float) Math.max(0.3, p[o + 1] + vy * dt);
            p[o + 2] += (float) (vz * dt);
            p[o + 3] = (float) vx; p[o + 4] = (float) vy; p[o + 5] = (float) vz;
            p[o + 6] = age;
            if (k != i) System.arraycopy(p, o, p, k * P, P);
            k++;
        }
        n = k;
        // ausgeben, hinten zuerst
        for (int i = 0; i < n; i++) {
            int o = i * P;
            double dx = p[o] - cam[0], dy = p[o + 1] - cam[1], dz = p[o + 2] - cam[2];
            keys[i] = ((long) Float.floatToIntBits((float) (dx * dx + dy * dy + dz * dz)) << 32) | i;
        }
        Arrays.sort(keys, 0, n);
        outCount = 0;
        for (int j = n - 1; j >= 0; j--) {
            int o = (int) (keys[j] & 0xffffffffL) * P;
            float u = p[o + 6] / p[o + 7];
            float size = (float) (p[o + 8] + (p[o + 9] - p[o + 8]) * Math.sqrt(u));
            float a = (float) (p[o + 10] * Math.min(1, p[o + 6] / 0.25) * (1 - u) * (1 - u));
            if (a < 0.003) continue;
            int b = 8 * outCount++;
            out[b] = p[o]; out[b + 1] = p[o + 1]; out[b + 2] = p[o + 2]; out[b + 3] = size;
            out[b + 4] = a; out[b + 5] = p[o + 11]; out[b + 6] = 2; out[b + 7] = 0;
        }
    }

    private void burst(Coast.Point q, double s, double w) {
        bursts++;
        double hUp = Math.min(35, Math.max(3, 12 * s));
        int m = (int) Math.min(50, 8 + 20 * s);
        double tx = -q.nz, tz = q.nx;                            // entlang der Wand
        for (int j = 0; j < m && n < MAX; j++) {
            int o = n * P;
            double along = (rnd.nextDouble() - 0.5) * 30;
            double v = Math.sqrt(2 * 9.81 * hUp) * (0.45 + 0.55 * rnd.nextDouble());
            p[o] = (float) (q.x + tx * along + q.nx * (1 + 2 * rnd.nextDouble()));
            p[o + 1] = (float) (0.5 + rnd.nextDouble());
            p[o + 2] = (float) (q.z + tz * along + q.nz * (1 + 2 * rnd.nextDouble()));
            p[o + 3] = (float) (q.nx * v * 0.15 * rnd.nextDouble() + tx * (rnd.nextDouble() - 0.5) * 2);
            p[o + 4] = (float) v;
            p[o + 5] = (float) (q.nz * v * 0.15 * rnd.nextDouble() + tz * (rnd.nextDouble() - 0.5) * 2);
            p[o + 6] = 0;
            p[o + 7] = (float) (2.5 + 4.5 * rnd.nextDouble());
            p[o + 8] = (float) (1.5 + 2.5 * rnd.nextDouble());
            p[o + 9] = (float) (5 + 9 * s * rnd.nextDouble() + 4 * rnd.nextDouble());
            p[o + 10] = (float) (0.25 + 0.3 * rnd.nextDouble());
            p[o + 11] = (float) rnd.nextDouble();
            n++;
        }
    }

    public int count() { return n; }

    @Override public float[] puffs() { return out; }
    @Override public int puffCount() { return outCount; }

    /** Höchstes Teilchen über dem Meer (m), für die Prüfung. */
    public double top() {
        double t = 0;
        for (int i = 0; i < n; i++) t = Math.max(t, p[i * P + 1]);
        return t;
    }

    public void clear() { n = 0; outCount = 0; }
}
