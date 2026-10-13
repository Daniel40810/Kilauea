package com.dan.kilauea.lava;

import java.util.SplittableRandom;

/**
 * Glut am Meereseintritt als Teilchen, ohne Grafik (Phase 10), im Format der Fontäne
 * ({@link Fountain#out}: x, y, z, Größe, vx, vy, vz, Temperatur):
 * <ul>
 * <li>Glutbrocken: Wo Wasser an heiße Lava kommt, verdampft es explosiv und wirft Fetzen und Brocken auf,
 * bis einige zehn Meter hoch (littorale Explosionen) [L, Mattox &amp; Mangan 1997; Häufigkeit und Mengen G].</li>
 * <li>Lavafälle und Feuerwehrschlauch: Lava, die über eine Stufe fließt, fällt als Strahl; an der Klippe
 * schießt sie waagerecht aus dem Ende der Röhre und fällt ins Meer, wie 2017 an Kamokuna [L, Erscheinung;
 * Geschwindigkeit aus dem Fluss A].</li>
 * </ul>
 * Fester Keim, keine Allokation im Schritt.
 */
public final class Littoral implements Sparks {
    public static final int MAX = 4000;
    static final int P = 9;   // x y z vx vy vz alter tau T0
    private final float[] p = new float[P * MAX];
    private int n;
    private final SplittableRandom rnd = new SplittableRandom(2017);
    public final float[] out = new float[8 * MAX];
    public int outCount;
    private double accFall;
    /** Explosionen seit dem Start (für die Prüfung). */
    public int bursts;

    /**
     * Ein Schritt. e: Eintritt und Fälle (darf null sein; dann fallen nur die vorhandenen Teilchen zu Ende).
     * Die Arrays werden gelesen, nicht verändert; wer schreibt, sperrt e.
     */
    public void update(double dt, SeaEntry e, double[] wind) {
        dt = Math.min(dt, 0.1);
        if (e != null) {
            // Explosionen: je Gruppe rund eine alle 4 s je GW (gedeckelt) [G]
            for (int i = 0; i < e.count; i++) {
                double heat = e.entries[5 * i + 2];
                double rate = Math.min(1.5, heat / 4000);
                if (rnd.nextDouble() < rate * dt) burst(e.entries[5 * i], e.entries[5 * i + 1], heat);
            }
            // Lavafälle: Teilchen nach dem Fluss
            for (int i = 0; i < e.fallCount; i++) {
                int o = 8 * i;
                double q = e.falls[o + 6];
                accFall += Math.min(60, 25 + 60 * q) * dt;
                while (accFall >= 1 && n < MAX) {
                    accFall -= 1;
                    double dx = e.falls[o + 3] - e.falls[o], dz = e.falls[o + 5] - e.falls[o + 2];
                    double dl = Math.max(1e-6, Math.hypot(dx, dz));
                    // Austritt: Fluss durch einen Querschnitt von rund 1,5 m × 5 m, mindestens 1 m/s [A]
                    double v = Math.max(1, Math.min(6, q / 7.5)) * (0.8 + 0.4 * rnd.nextDouble());
                    double side = (rnd.nextDouble() - 0.5) * 5;
                    spawn(e.falls[o] - dz / dl * side, e.falls[o + 1] - 0.3, e.falls[o + 2] + dx / dl * side,
                            dx / dl * v, 0.3 * rnd.nextDouble(), dz / dl * v, 8 + 8 * rnd.nextDouble(), e.falls[o + 7]);
                }
            }
            if (accFall > 1) accFall = 0;
        }
        int k = 0;
        for (int i = 0; i < n; i++) {
            int o = i * P;
            float age = p[o + 6] + (float) dt;
            double vy = p[o + 4] - 9.81 * dt;
            p[o + 3] += (float) ((wind[0] * 0.15 - p[o + 3] * 0.15) * dt);
            p[o + 5] += (float) ((wind[1] * 0.15 - p[o + 5] * 0.15) * dt);
            p[o] += (float) (p[o + 3] * dt);
            p[o + 1] += (float) (vy * dt);
            p[o + 2] += (float) (p[o + 5] * dt);
            p[o + 4] = (float) vy;
            p[o + 6] = age;
            if (p[o + 1] < -0.5 || age > 30) continue;            // im Wasser erloschen
            if (k != i) System.arraycopy(p, o, p, k * P, P);
            k++;
        }
        n = k;
        outCount = 0;
        for (int i = 0; i < n; i++) {
            int o = i * P, w = 8 * outCount++;
            out[w] = p[o]; out[w + 1] = p[o + 1]; out[w + 2] = p[o + 2];
            out[w + 3] = 0.5f + 0.3f * (i % 3);
            out[w + 4] = p[o + 3]; out[w + 5] = p[o + 4]; out[w + 6] = p[o + 5];
            out[w + 7] = (float) (Rheology.T_AIR + (p[o + 8] - Rheology.T_AIR) * Math.exp(-p[o + 6] / p[o + 7]));
        }
    }

    private void burst(double x, double z, double heat) {
        bursts++;
        int m = (int) (20 + 30 * rnd.nextDouble());
        double vmax = 8 + 10 * rnd.nextDouble();                  // bis rund 15 m hoch
        for (int j = 0; j < m && n < MAX; j++) {
            double a = rnd.nextDouble() * Math.PI * 2, th = Math.toRadians(10 + 40 * rnd.nextDouble());
            double v = vmax * (0.4 + 0.6 * rnd.nextDouble());
            spawn(x + (rnd.nextDouble() - 0.5) * 20, 0.5, z + (rnd.nextDouble() - 0.5) * 20,
                    Math.cos(a) * Math.sin(th) * v, Math.cos(th) * v, Math.sin(a) * Math.sin(th) * v, 1.5 + 4 * rnd.nextDouble(), 1100);
        }
    }

    private void spawn(double x, double y, double z, double vx, double vy, double vz, double tau, double t0) {
        int o = n * P;
        p[o] = (float) x; p[o + 1] = (float) y; p[o + 2] = (float) z;
        p[o + 3] = (float) vx; p[o + 4] = (float) vy; p[o + 5] = (float) vz;
        p[o + 6] = 0; p[o + 7] = (float) tau; p[o + 8] = (float) t0;
        n++;
    }

    public int count() { return n; }

    /** Höchster Brocken (m ü. M.). */
    public double top() {
        double t = -1e9;
        for (int i = 0; i < n; i++) t = Math.max(t, p[i * P + 1]);
        return t;
    }

    public void clear() { n = 0; outCount = 0; }

    @Override public float[] sparks() { return out; }
    @Override public int sparkCount() { return outCount; }
}
