package com.dan.kilauea.sea;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Kissenlava vor einem Meereseintritt, ohne Grafik (Phase 10). Unter Wasser fließt die Lava nicht als Strom,
 * sie knospt: aus einem Riss im Rand eines Kissens quillt ein neues, bläht sich in Sekunden auf, glüht kurz
 * und schreckt ab; die Glut bleibt noch eine Weile in den Rissen. Kissen wachsen hangabwärts [L, Beobachtungen
 * von Tauchern vor Kīlauea, Moore 1975, Tribble 1991; Größen 0,5–2 m L, Takt und Formen G].
 * <ul>
 * <li>Eine Kolonie beginnt dort, wo der Hang vor dem stärksten Eintritt 8 m Tiefe erreicht; hangaufwärts liegen
 * schon ältere, kalte Kissen der letzten Stunden [G].</li>
 * <li>Bis zu vier Spitzen wachsen gleichzeitig; alle 10 bis 30 s knospt eine davon (also mindestens zwei
 * Knospungen je Minute, solange Lava ins Meer läuft).</li>
 * <li>Aufblähen in 6–15 s, Haut glüht rund 2 s, Risse glühen rund 40 s [G].</li>
 * <li>Blasen steigen von jungen Kissen auf (Dampf und Gas), 0,2–0,45 m/s [G].</li>
 * </ul>
 * Fester Keim. Ausgabe der Blasen wie {@code atmos.Steam} (Art 3).
 */
public final class Pillows implements com.dan.kilauea.atmos.Puffs {
    public static final int MAX = 600, BUB = 1500;
    /** Je Kissen für die Grafik: x, y, z, Länge, Breite, Höhe, Richtung (rad), Alter (s), Aufblähen 0..1, Glut der Haut, Glut der Risse, Zufall. */
    public static final int F = 12;
    public final float[] inst = new float[F * MAX];
    public int instCount;

    private final double[] px = new double[MAX], py = new double[MAX], pz = new double[MAX], len = new double[MAX], wid = new double[MAX],
            hei = new double[MAX], yaw = new double[MAX], born = new double[MAX], grow = new double[MAX], seed = new double[MAX];
    private int n;
    private final int[] tips = new int[4];
    private int tipCount;
    private double time, nextBud = Double.NaN, settle;
    private double ax = Double.NaN, az;
    private final SplittableRandom rnd = new SplittableRandom(1975);
    /** Knospungen insgesamt und ihre Zeiten (die letzten 64). */
    public int buds;
    private final double[] budTimes = new double[64];

    // Blasen: x y z vy alter leben größe zufall
    private final float[] b = new float[8 * BUB];
    private int bn;
    public final float[] out = new float[8 * BUB];
    public int outCount;
    private final long[] keys = new long[BUB];

    public double time() { return time; }
    public int count() { return n; }

    /** Knospungen in den letzten s Sekunden. */
    public int budsWithin(double s) {
        int c = 0;
        for (int i = 0; i < Math.min(buds, budTimes.length); i++) if (time - budTimes[i] <= s) c++;
        return c;
    }

    /** Mitte der Kolonie (NaN, solange es keine gibt). */
    public double[] anchor() { return Double.isNaN(ax) ? null : new double[]{ax, az}; }

    /** Richtung hangabwärts an (x, z) als Einheitsvektor (x, z). */
    public static double[] downhill(Coast.Terrain t, double x, double z) {
        double gx = t.height(x + 3, z) - t.height(x - 3, z), gz = t.height(x, z + 3) - t.height(x, z - 3);
        double gl = Math.hypot(gx, gz);
        return gl < 1e-6 ? new double[]{0, 1} : new double[]{-gx / gl, -gz / gl};
    }

    /** Lage der jüngsten Spitze (für die Kamera), oder null. */
    public double[] activeTip() {
        if (n == 0) return null;
        int k = 0;
        for (int i = 1; i < n; i++) if (born[i] > born[k]) k = i;
        return new double[]{px[k], py[k], pz[k]};
    }

    /**
     * Ein Schritt. entry: Eintritt (x, z, Wärmestrom MW) oder null (dann knospt nichts, die Kissen kühlen aus).
     * terrain: Höhe des Meeresgrunds.
     */
    public void update(double dt, double[] entry, Coast.Terrain terrain, double[] cam) {
        dt = Math.min(dt, 0.5);
        time += dt;
        boolean active = entry != null && entry[2] > 50;
        if (active && (Double.isNaN(ax) || Math.hypot(entry[0] - ax, entry[1] - az) > 200)) start(entry[0], entry[1], terrain);
        if (active && n > 0) {
            if (Double.isNaN(nextBud)) nextBud = time + 3;
            while (time >= nextBud) {
                bud(terrain);
                nextBud += 10 + 20 * rnd.nextDouble();
            }
        } else nextBud = Double.NaN;
        // Bricht das Delta ab, fällt der Hang darunter weg: Kissen sinken auf den neuen Grund (Statusbild
        // Phase 10: Kissen schwebten im Wasser)
        settle += dt;
        if (settle > 2 && n > 0) {
            settle = 0;
            for (int i = 0; i < n; i++) {
                double g = terrain.height(px[i], pz[i]) + hei[i] * 0.45;
                if (py[i] > g + 0.3) py[i] = g;
            }
        }
        bubbles(dt, cam);
        // für die Grafik
        instCount = 0;
        for (int i = 0; i < n; i++) {
            double age = time - born[i];
            double g = Math.min(1, age / grow[i]);
            g = 1 - (1 - g) * (1 - g);
            int o = F * instCount++;
            inst[o] = (float) px[i]; inst[o + 1] = (float) py[i]; inst[o + 2] = (float) pz[i];
            inst[o + 3] = (float) len[i]; inst[o + 4] = (float) wid[i]; inst[o + 5] = (float) hei[i];
            inst[o + 6] = (float) yaw[i]; inst[o + 7] = (float) age; inst[o + 8] = (float) (0.15 + 0.85 * g);
            inst[o + 9] = (float) Math.exp(-age / 2.5);
            inst[o + 10] = (float) Math.exp(-age / 40);
            inst[o + 11] = (float) seed[i];
        }
    }

    /** Neue Kolonie: vom Eintritt hangabwärts bis 8 m Tiefe. */
    private void start(double ex, double ez, Coast.Terrain t) {
        double x = ex, z = ez;
        // in Schritten von 5 m zur tiefsten Stelle im Umkreis, bis 8 m Tiefe (höchstens 600 m weit)
        // nur Schritte, die vom Eintritt wegführen (sonst pendelt der Weg in Mulden des Deltas)
        for (int s = 0; s < 120 && t.height(x, z) > -8; s++) {
            double best = Double.MAX_VALUE, bx = x, bz = z, d0 = Math.hypot(x - ex, z - ez);
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8, qx = x + Math.cos(a) * 5, qz = z + Math.sin(a) * 5;
                if (Math.hypot(qx - ex, qz - ez) < d0 + 2) continue;
                double hq = t.height(qx, qz);
                if (hq < best) { best = hq; bx = qx; bz = qz; }
            }
            if (best == Double.MAX_VALUE) break;
            x = bx;
            z = bz;
        }
        ax = x; az = z;
        n = 0;
        tipCount = 0;
        double down = Math.atan2(t.height(x, z + 3) - t.height(x, z - 3), t.height(x + 3, z) - t.height(x - 3, z)) + Math.PI;
        // ältere, kalte Kissen der letzten Stunden: ein Fächer hangaufwärts der ersten Spitze [G]
        for (int k = 0; k < 30; k++) {
            double r = 1.5 + 9 * Math.sqrt(rnd.nextDouble()), a = down + Math.PI + (rnd.nextDouble() - 0.5) * 2.2;
            double qx = x + Math.cos(a) * r, qz = z + Math.sin(a) * r;
            if (t.height(qx, qz) > -1.5) continue;
            add(qx, qz, down + (rnd.nextDouble() - 0.5) * 1.5, t, 600 + 3000 * rnd.nextDouble());
        }
        int first = add(x, z, down, t, 0);
        tips[tipCount++] = first;
    }

    private int add(double x, double z, double dir, Coast.Terrain t, double age0) {
        int k;
        if (n < MAX) k = n++;
        else {
            // das älteste ersetzen (weit weg von den Spitzen)
            k = 0;
            for (int i = 1; i < n; i++) if (born[i] < born[k]) k = i;
            for (int q = 0; q < tipCount; q++) if (tips[q] == k) { k = (k + 1) % n; break; }
        }
        len[k] = 0.8 + 1.2 * rnd.nextDouble();
        wid[k] = 0.5 + 0.5 * rnd.nextDouble();
        hei[k] = 0.35 + 0.35 * rnd.nextDouble();
        yaw[k] = dir;
        px[k] = x; pz[k] = z;
        py[k] = t.height(x, z) + hei[k] * 0.45;
        born[k] = time - age0;
        grow[k] = 6 + 9 * rnd.nextDouble();
        seed[k] = rnd.nextDouble();
        return k;
    }

    private void bud(Coast.Terrain t) {
        if (tipCount == 0) return;
        int q = rnd.nextInt(tipCount);
        int p = tips[q];
        // hangabwärts, mit Streuung
        double x0 = px[p], z0 = pz[p];
        double gx = t.height(x0 + 2, z0) - t.height(x0 - 2, z0), gz = t.height(x0, z0 + 2) - t.height(x0, z0 - 2);
        double down = Math.hypot(gx, gz) > 1e-6 ? Math.atan2(-gz, -gx) : yaw[p];
        double dir = down + (rnd.nextDouble() - 0.5) * 1.8;
        double reach = len[p] * 0.45 + 0.5;
        double x = x0 + Math.cos(dir) * reach, z = z0 + Math.sin(dir) * reach;
        if (t.height(x, z) > -1.5) { x = x0 + Math.cos(down) * reach; z = z0 + Math.sin(down) * reach; dir = down; }
        int k = add(x, z, dir, t, 0);
        // auf dem Rand des Elternkissens aufsetzen
        py[k] = Math.max(py[k], py[p] - hei[p] * 0.2);
        double r = rnd.nextDouble();
        if (r < 0.6) tips[q] = k;
        else if (tipCount < tips.length) tips[tipCount++] = k;
        else tips[rnd.nextInt(tipCount)] = k;
        budTimes[buds % budTimes.length] = time;
        buds++;
    }

    private void bubbles(double dt, double[] cam) {
        // junge Kissen geben Blasen ab
        for (int i = 0; i < n; i++) {
            double age = time - born[i];
            if (age > 40) continue;
            double rate = 10 * Math.exp(-age / 12);
            double m = rate * dt;
            int c = (int) m + (rnd.nextDouble() < m - (int) m ? 1 : 0);
            for (int j = 0; j < c && bn < BUB; j++) {
                int o = 8 * bn++;
                double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 0.5;
                b[o] = (float) (px[i] + Math.cos(a) * rr * len[i]);
                b[o + 1] = (float) (py[i] + hei[i] * 0.4);
                b[o + 2] = (float) (pz[i] + Math.sin(a) * rr * wid[i]);
                b[o + 3] = (float) (0.2 + 0.25 * rnd.nextDouble());
                b[o + 4] = 0;
                b[o + 5] = (float) (20 + 40 * rnd.nextDouble());
                b[o + 6] = (float) (0.02 + 0.08 * rnd.nextDouble());
                b[o + 7] = (float) rnd.nextDouble();
            }
        }
        int k = 0;
        for (int i = 0; i < bn; i++) {
            int o = 8 * i;
            float age = b[o + 4] + (float) dt;
            if (age > b[o + 5] || b[o + 1] > -0.1) continue;
            b[o + 1] += (float) (b[o + 3] * dt);
            b[o] += (float) (0.05 * Math.sin(age * 3 + b[o + 7] * 20) * dt);
            b[o + 4] = age;
            if (k != i) System.arraycopy(b, o, b, 8 * k, 8);
            k++;
        }
        bn = k;
        for (int i = 0; i < bn; i++) {
            double dx = b[8 * i] - cam[0], dy = b[8 * i + 1] - cam[1], dz = b[8 * i + 2] - cam[2];
            keys[i] = ((long) Float.floatToIntBits((float) (dx * dx + dy * dy + dz * dz)) << 32) | i;
        }
        Arrays.sort(keys, 0, bn);
        outCount = 0;
        for (int j = bn - 1; j >= 0; j--) {
            int o = 8 * (int) (keys[j] & 0xffffffffL), w = 8 * outCount++;
            out[w] = b[o]; out[w + 1] = b[o + 1]; out[w + 2] = b[o + 2]; out[w + 3] = b[o + 6];
            out[w + 4] = (float) (0.7 * Math.min(1, b[o + 4] / 0.3)); out[w + 5] = b[o + 7]; out[w + 6] = 3; out[w + 7] = 0;
        }
    }

    /** Höchste Blase (m ü. M.), für die Prüfung. */
    public double bubbleTop() {
        double t = -1e9;
        for (int i = 0; i < bn; i++) t = Math.max(t, b[8 * i + 1]);
        return t;
    }

    public int bubbleCount() { return bn; }

    public double[] size(int i) { return new double[]{len[i], wid[i], hei[i], py[i], px[i], pz[i]}; }

    public void clear() { n = 0; tipCount = 0; bn = 0; ax = Double.NaN; nextBud = Double.NaN; outCount = 0; instCount = 0; }

    @Override public float[] puffs() { return out; }
    @Override public int puffCount() { return outCount; }
}
