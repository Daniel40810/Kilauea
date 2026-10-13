package com.dan.kilauea.lava;

import java.util.SplittableRandom;

/**
 * Lavafontäne als Teilchen, ohne Grafik (Phase 8). Jedes Teilchen steht für viele Fetzen und Tropfen.
 * <ul>
 * <li>Austritt mit v = √(2 g H) mal 0,55 bis 1 (Höhe H der Fontäne), um bis zu rund 8° gegen die
 * Senkrechte gestreut, aus der ganzen Länge des Schlots [L ballistisch; Streuung G].</li>
 * <li>Der Passat versetzt die Teilchen; kleine folgen ihm schneller (Zeit 3 bis 25 s) [A].</li>
 * <li>Abkühlung im Flug: T = Luft + (1 150 °C − Luft) · e^(−t/τ), τ 4 bis 30 s, große Fetzen glühen
 * länger [A, Größenordnung für Lapilli bis Fladen]. Glut nach Planck im Shader.</li>
 * <li>Peles Haar: goldene Glasfäden, vom Wind fortgetragen, fallen langsam [L, Erscheinung; Mengen G].</li>
 * </ul>
 * Fester Keim. Keine Allokation im Schritt. {@link #out} enthält je Teilchen
 * x, y, z, Größe, vx, vy, vz, Temperatur (°C; Peles Haar: −1).
 */
public final class Fountain implements Sparks {
    public static final int MAX = 6000;
    /** Flecken im Kern des Strahls. */
    static final int CORE = 36;
    static final int P = 13;
    private final float[] p = new float[P * MAX];    // x y z vx vy vz alter tau drag größe art (0 Glut, 1 Haar) T0 Boden
    private int n;
    private final SplittableRandom rnd = new SplittableRandom(1959);
    public final float[] out = new float[8 * (MAX + CORE)];
    public int outCount;
    /** Höhe der Fontäne beim letzten Schritt (m). */
    public double height;

    /**
     * Weiterrechnen. src: x0, z0, x1, z1 (Schlot), y (Boden am Schlot), H (m); oder null (keine Fontäne,
     * die vorhandenen Teilchen fallen zu Ende). wind: m/s nach x und z.
     */
    public void update(double dt, double[] src, double[] wind) {
        update(dt, src == null ? NONE : new double[][]{src}, wind);
    }

    private static final double[][] NONE = new double[0][];
    /** Höchstens so viele Schlote zugleich (Phase 12: Spaltenreihen 2018). */
    public static final int SOURCES = 8;
    private final double[] accS = new double[SOURCES], accH = new double[SOURCES];

    /**
     * Weiterrechnen mit mehreren Schloten zugleich (Phase 12): je Schlot x0, z0, x1, z1, y, H. Die Teilchen
     * teilen sich den Vorrat nach dem Auswurf der Schlote (Höhe mal Länge der Spalte) [G]; der Kern steht über
     * jedem Schlot ab 40 m Höhe, über einer langen Spalte als Reihe (Feuervorhang).
     */
    public void update(double dt, double[][] srcs, double[] wind) {
        dt = Math.min(dt, 0.1);
        int ns = Math.min(SOURCES, srcs.length);
        double weightSum = 0, hMax = 0;
        for (int s = 0; s < ns; s++) {
            double h = srcs[s][5];
            if (h > 1) weightSum += weight(srcs[s]);
            hMax = Math.max(hMax, h);
        }
        height = hMax;
        for (int s = 0; s < ns; s++) {
            double[] src = srcs[s];
            double h = src[5];
            if (h <= 1) { accS[s] = accH[s] = 0; continue; }
            double share = weight(src) / weightSum;
            double v0 = Math.sqrt(2 * 9.81 * h);
            double flight = 2 * v0 / 9.81 + 1;
            accS[s] += dt * 0.8 * MAX * share / flight;
            while (accS[s] >= 1 && n < MAX) {
                accS[s] -= 1;
                spawn(src, v0, 0);
            }
            if (accS[s] >= 1) accS[s] = 0;
            accH[s] += dt * (h > 20 ? 25 * Math.sqrt(share) : 0);
            while (accH[s] >= 1 && n < MAX) {
                accH[s] -= 1;
                spawn(src, v0, 1);
            }
        }
        double g = 9.81;
        for (int i = n - 1; i >= 0; i--) {
            int o = i * P;
            float kind = p[o + 10];
            double drag = p[o + 8];
            double vx = p[o + 3], vy = p[o + 4], vz = p[o + 5];
            vx += (wind[0] - vx) * dt / drag;
            vz += (wind[1] - vz) * dt / drag;
            if (kind == 0) vy -= g * dt;
            else vy += (-0.6 - vy) * dt / 2;                 // Haar: sinkt langsam
            p[o] += (float) (vx * dt);
            p[o + 1] += (float) (vy * dt);
            p[o + 2] += (float) (vz * dt);
            p[o + 3] = (float) vx;
            p[o + 4] = (float) vy;
            p[o + 5] = (float) vz;
            p[o + 6] += (float) dt;
            boolean dead = kind == 0 ? vy < 0 && p[o + 1] < p[o + 12] - 2 : p[o + 6] > 90;
            if (dead) {
                System.arraycopy(p, (n - 1) * P, p, o, P);
                n--;
            }
        }
        outCount = 0;
        // Kern des Strahls: dichte, gleißende Säule als weiche Flecken, mit dem Wind geneigt (Größe negativ) [G]
        // Niedrige Fontänen (unter rund 40 m) sind eher ein Brodeln: dort nur die einzelnen Fetzen, der Kern
        // wird mit der Höhe eingeblendet (weniger und schmalere Flecken) [G]. Mehrere Schlote teilen sich die Flecken.
        int coreLeft = CORE;
        for (int s = 0; s < ns && coreLeft > 0; s++) {
            double[] src = srcs[s];
            double h = src[5];
            int core = h < 40 ? 0 : (int) Math.min(CORE, Math.round(CORE * (h - 40) / 60.0) + 6);
            if (ns > 1) core = (int) Math.round(core * Math.min(1, 1.5 * weight(src) / weightSum));
            core = Math.min(core, coreLeft);
            coreLeft -= core;
            if (h <= 1 || core <= 0) continue;
            double v0 = Math.sqrt(2 * 9.81 * h);
            double len = Math.hypot(src[2] - src[0], src[3] - src[1]);
            boolean curtain = len > 80;
            for (int k = 0; k < core; k++) {
                double f = (k + 0.5) / core;
                double y = h * Math.pow(f, 0.85) * 0.9;
                double along = 0.5;
                if (curtain) {
                    // Feuervorhang: die Flecken stehen entlang der Spalte, verschieden hoch
                    along = (k * 0.6180339887) % 1.0;
                    y = h * Math.pow((k * 0.7548776662) % 1.0, 0.85) * 0.9;
                }
                double tUp = (v0 - Math.sqrt(Math.max(0, v0 * v0 - 2 * 9.81 * y))) / 9.81;   // Steigzeit bis y
                double drift = tUp * 0.35;
                int o = 8 * outCount++;
                double cx = src[0] + (src[2] - src[0]) * along, cz = src[1] + (src[3] - src[1]) * along;
                out[o] = (float) (cx + wind[0] * drift);
                out[o + 1] = (float) (src[4] + y);
                out[o + 2] = (float) (cz + wind[1] * drift);
                out[o + 3] = (float) -(2 + h * (0.10 + 0.06 * f) + (curtain ? 0.08 * Math.min(len, 400) : 0.5 * len));
                out[o + 4] = 0;
                out[o + 5] = (float) Math.max(1, Math.sqrt(Math.max(0, v0 * v0 - 2 * 9.81 * y)));
                out[o + 6] = 0;
                out[o + 7] = (float) (1140 - 90 * f);
            }
        }
        for (int i = 0; i < n; i++) {
            int o = i * P, k = 8 * outCount++;
            out[k] = p[o];
            out[k + 1] = p[o + 1];
            out[k + 2] = p[o + 2];
            out[k + 3] = p[o + 9];
            out[k + 4] = p[o + 3];
            out[k + 5] = p[o + 4];
            out[k + 6] = p[o + 5];
            out[k + 7] = p[o + 10] == 0 ? (float) (Rheology.T_AIR + (p[o + 11] - Rheology.T_AIR) * Math.exp(-p[o + 6] / p[o + 7])) : -1;
        }
    }

    /** Auswurf eines Schlots zum Teilen der Teilchen: Höhe mal (1 + Länge / 100 m) [G]. */
    private static double weight(double[] src) {
        return Math.max(1, src[5]) * (1 + Math.hypot(src[2] - src[0], src[3] - src[1]) / 100);
    }

    private void spawn(double[] src, double v0, int kind) {
        int o = n * P;
        double f = rnd.nextDouble();
        double x = src[0] + (src[2] - src[0]) * f + (rnd.nextDouble() - 0.5) * 6;
        double z = src[1] + (src[3] - src[1]) * f + (rnd.nextDouble() - 0.5) * 6;
        double th = Math.toRadians(8) * Math.sqrt(-2 * Math.log(1 - rnd.nextDouble() * 0.999)) * 0.6;
        double az = rnd.nextDouble() * Math.PI * 2;
        double v = v0 * (0.55 + 0.45 * Math.pow(rnd.nextDouble(), 0.6));
        double big = rnd.nextDouble();
        p[o] = (float) x;
        p[o + 1] = (float) (src[4] + 1);
        p[o + 2] = (float) z;
        p[o + 3] = (float) (v * Math.sin(th) * Math.cos(az));
        p[o + 4] = (float) (v * Math.cos(th));
        p[o + 5] = (float) (v * Math.sin(th) * Math.sin(az));
        p[o + 6] = 0;
        p[o + 7] = (float) (4 + 26 * big * big);                   // τ der Abkühlung
        p[o + 8] = (float) (kind == 1 ? 1.5 : 25 - 22 * (1 - big)); // Folgezeit im Wind
        p[o + 9] = (float) (kind == 1 ? 0.5 : 0.6 + 1.6 * big + src[5] / 160);
        p[o + 10] = kind;
        p[o + 11] = (float) (1150 - 30 * rnd.nextDouble());
        p[o + 12] = (float) src[4];                                // Boden am Schlot: darunter ist es gelandet
        if (kind == 1) {
            // Haar entsteht hoch in der Fontäne und wird verweht
            p[o + 1] = (float) (src[4] + src[5] * (0.5 + 0.5 * rnd.nextDouble()));
            p[o + 3] = 0;
            p[o + 4] = 0;
            p[o + 5] = 0;
        }
        n++;
    }

    public int count() { return n; }

    @Override public float[] sparks() { return out; }
    @Override public int sparkCount() { return outCount; }

    /** Alle Teilchen entfernen (neuer Ausbruch, Standbild). */
    public void clear() { n = 0; outCount = 0; java.util.Arrays.fill(accS, 0); java.util.Arrays.fill(accH, 0); }
}
