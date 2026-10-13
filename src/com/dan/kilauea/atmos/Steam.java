package com.dan.kilauea.atmos;

import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.SummitPaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Dampf und Gasfahnen am Gipfel als Teilchen: die Fahne aus dem Halemaʻumaʻu (Schwefeldioxid und
 * Wasserdampf, steigt einige hundert Meter und zieht mit dem Passat nach Südwesten), Dampf aus den
 * Spalten am Steaming Bluff, über Haʻakulamanu (Sulphur Banks), schwach über Puʻu ʻŌʻō und Kīlauea Iki
 * [L Orte; G Mengen, Größen, Tempo]. Morgens und nachts (kühl, feucht) sieht man mehr Dampf als mittags.
 * <p>
 * Ohne Grafikkarte: {@link #update} bewegt die Teilchen und legt sie, von hinten nach vorn sortiert,
 * in {@link #out} ab (x, y, z, Größe, Deckkraft, Zufall, Art, –).
 */
public final class Steam implements Puffs {
    /** Eine Quelle: Ort (Linie oder Fläche), Rate (1/s), Lebensdauer (s), Größe am Anfang und Ende (m), Steigen (m/s), Deckkraft, Art. */
    record Source(String name, double[][] line, double radius, double y, double rate, double life, double size0, double size1,
                  double rise, double alpha, int kind) { }

    private final List<Source> sources = new ArrayList<>();
    private final Random rnd = new Random(7);
    private final double[] acc;
    // Teilchen: x, y, z, Alter, Lebensdauer, Größe0, Größe1, Steigen, Deckkraft, Zufall, Art, vx, vz
    static final int P = 13;
    private float[] part = new float[P * 4096];
    private int count;
    public float[] out = new float[8 * 4096];
    public int outCount;
    private long[] keys = new long[4096];
    // Meereseintritt (Phase 10): Dampf und Laze über den Stellen, wo Lava ins Wasser läuft
    private final double[] entry = new double[8 * 3];
    private int entryCount;
    private final double[] entryAcc = new double[8];
    private final Source lazeSrc = new Source("Laze", new double[][]{{0, 0}}, 25, 0.5, 0, 150, 12, 160, 4.5, 0.20, 1);
    /** Sichtbarkeit 0..1 nach Tageszeit (vom Renderer gesetzt) und an/aus. */
    public double strength = 1;

    public Steam() {
        double[][] hm = {{Geo.x(19.4072, -155.2834), Geo.z(19.4072)}};
        sources.add(new Source("Halemaʻumaʻu", hm, 160, Double.NaN, 5.0, 220, 50, 520, 7.5, 0.075, 0));
        for (double[][] l : SummitPaths.STEAM_CRACKS) {
            double[][] xz = new double[l.length][];
            for (int i = 0; i < l.length; i++) xz[i] = new double[]{Geo.x(l[i][0], l[i][1]), Geo.z(l[i][0])};
            sources.add(new Source("Steaming Bluff", xz, 2, Double.NaN, 6.0, 18, 2.0, 20, 1.6, 0.30, 1));
        }
        sources.add(new Source("Haʻakulamanu", new double[][]{{Geo.x(19.4335, -155.2617), Geo.z(19.4335)}}, 110, Double.NaN, 3.0, 11, 1.2, 9, 1.0, 0.18, 1));
        sources.add(new Source("Kīlauea Iki", new double[][]{{Geo.x(19.4142, -155.2490), Geo.z(19.4142)}}, 40, Double.NaN, 0.5, 10, 1.0, 7, 0.8, 0.15, 1));
        sources.add(new Source("Puʻu ʻŌʻō", new double[][]{{Geo.x(19.3880, -155.1050), Geo.z(19.3880)}}, 150, Double.NaN, 1.0, 70, 15, 110, 2.5, 0.08, 0));
        acc = new double[sources.size()];
    }

    /**
     * Eintrittsstellen setzen: je Stelle x, z, Wärmestrom (MW). Die Laze (Dampf mit Salzsäure und Glasfasern)
     * steigt weiß und dicht auf; die Menge wächst mit der Wurzel des Wärmestroms [G; Erscheinung L].
     */
    public void entries(double[] e, int stride, int count) {
        entryCount = Math.min(8, count);
        for (int i = 0; i < entryCount; i++) {
            entry[3 * i] = e[stride * i];
            entry[3 * i + 1] = e[stride * i + 1];
            entry[3 * i + 2] = e[stride * i + 2];
        }
    }

    // Dampf über heißer Lava (Phase 11): je Gruppe x, z, Fläche; bei Regen viel mehr [Erscheinung L, Mengen G]
    private final double[] hot = new double[8 * 3];
    private int hotCount;
    private double hotRain;
    private final double[] hotAcc = new double[8];
    private final Source hotSrc = new Source("Dampf über Lava", new double[][]{{0, 0}}, 30, Double.NaN, 0, 25, 3, 28, 1.6, 0.10, 1);

    public void hot(double[] h, int count, double rain) {
        hotCount = Math.min(8, count);
        System.arraycopy(h, 0, hot, 0, hotCount * 3);
        hotRain = rain;
    }

    /** Ein Abbruch des Deltas: ein Stoß Dampf (Volumen m³). */
    public void surge(double x, double z, double volume) {
        int m = (int) Math.min(80, 15 + volume / 500);
        for (int k = 0; k < m; k++) emitAt(lazeSrc, x + rnd.nextGaussian() * 30, z + rnd.nextGaussian() * 30, 1.6);
    }

    /** Asche und Staub (Phase 12): braungraue Wolke, steigt mehrere hundert Meter auf, lebt Minuten [G]. */
    private final Source ashSrc = new Source("Asche", new double[][]{{0, 0}}, 200, 0, 0, 420, 60, 520, 9.0, 0.45, 4);

    /**
     * Eine Wolke aus Asche und Gesteinsstaub (Phase 12): beim Absacken des Calderabodens 2018 stieg nach jedem
     * Einsturzbeben eine Staubwolke auf, am 17. Mai eine Aschesäule bis rund 9 km [L, USGS; Mengen G].
     * strength: Zahl der Schwaden (rund 20 bis 120).
     */
    public void ash(double x, double z, double radius, int strength) {
        for (int k = 0; k < strength; k++) {
            double a = rnd.nextDouble() * 6.2832, r = radius * Math.sqrt(rnd.nextDouble());
            double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
            emitAt(ashSrc, px, pz, 0.6 + 0.8 * rnd.nextDouble());
            part[(count - 1) * P + 1] = (float) (Ground.height(px, pz) + 2);
        }
    }

    /** Ein Zeitschritt: Wind (m/s nach x, z), Kamera (für Sortierung und Reichweite). */
    public void update(double dt, double[] wind, double[] cam, double time) {
        dt = Math.min(dt, 0.25);
        // ausgeben
        for (int s = 0; s < sources.size(); s++) {
            Source q = sources.get(s);
            double[] p0 = q.line[0];
            boolean near = Math.hypot(p0[0] - cam[0], p0[1] - cam[2]) < (q.kind == 0 ? 40_000 : 6_000);
            if (!near) continue;
            acc[s] += q.rate * dt * strength;
            while (acc[s] >= 1) {
                acc[s] -= 1;
                emit(q);
            }
        }
        for (int i = 0; i < entryCount; i++) {
            if (Math.hypot(entry[3 * i] - cam[0], entry[3 * i + 1] - cam[2]) > 30_000) continue;
            entryAcc[i] += Math.min(12, 0.08 * Math.sqrt(Math.max(0, entry[3 * i + 2]))) * dt;
            while (entryAcc[i] >= 1) {
                entryAcc[i] -= 1;
                emitAt(lazeSrc, entry[3 * i] + rnd.nextGaussian() * lazeSrc.radius(), entry[3 * i + 1] + rnd.nextGaussian() * lazeSrc.radius(), 1);
            }
        }
        for (int i = 0; i < hotCount; i++) {
            if (Math.hypot(hot[3 * i] - cam[0], hot[3 * i + 1] - cam[2]) > 15_000) continue;
            hotAcc[i] += Math.min(6, Math.sqrt(hot[3 * i + 2]) / 60 * (0.3 + 3 * hotRain)) * dt;
            while (hotAcc[i] >= 1) {
                hotAcc[i] -= 1;
                emit(hotSrc, hot[3 * i] + rnd.nextGaussian() * 25, hot[3 * i + 1] + rnd.nextGaussian() * 25);
            }
        }
        // bewegen und altern
        int w = 0;
        double t = time;
        for (int i = 0; i < count; i++) {
            int o = i * P;
            float age = part[o + 3] + (float) dt;
            if (age > part[o + 4]) continue;
            float life = part[o + 4], u = age / life;
            // Steigen lässt nach; der Wind nimmt die Fahne mit, oben mehr als unten
            double up = part[o + 7] * Math.exp(-age / (life * (part[o + 10] < 0.5f ? 0.45 : 0.35)));
            double wf = 0.35 + 0.65 * Math.min(1, u * 3);
            double turb = 0.6 * Math.sin(t * 0.7 + part[o + 9] * 40) + 0.4 * Math.sin(t * 1.9 + part[o + 9] * 13);
            part[o] += (float) ((wind[0] * wf + part[o + 11] + turb * 0.3) * dt);
            part[o + 2] += (float) ((wind[1] * wf + part[o + 12] + turb * 0.3) * dt);
            part[o + 1] += (float) (up * dt);
            part[o + 3] = age;
            if (w != i) System.arraycopy(part, o, part, w * P, P);
            w++;
        }
        count = w;
        // ausgeben, nach Abstand sortiert (hinten zuerst)
        // Schlüssel: Abstand (als Bits einer positiven Zahl, absteigend sortiert) und Nummer — ohne Boxen
        if (keys.length < count) keys = new long[count * 2];
        for (int i = 0; i < count; i++) {
            int o = i * P;
            double dx = part[o] - cam[0], dy = part[o + 1] - cam[1], dz = part[o + 2] - cam[2];
            keys[i] = ((long) Float.floatToIntBits((float) (dx * dx + dy * dy + dz * dz)) << 32) | i;
        }
        Arrays.sort(keys, 0, count);
        if (out.length < count * 8) out = new float[count * 8 * 2];
        outCount = 0;
        for (int k = count - 1; k >= 0; k--) {
            int o = (int) (keys[k] & 0xffffffffL) * P;
            float age = part[o + 3], life = part[o + 4], u = age / life;
            float size = (float) (part[o + 5] + (part[o + 6] - part[o + 5]) * Math.pow(u, 0.6));
            float a = (float) (part[o + 8] * Math.min(1, age / 2.5) * (1 - u) * (1 - u) * strength);
            if (a < 0.003) continue;
            int b = outCount * 8;
            out[b] = part[o]; out[b + 1] = part[o + 1]; out[b + 2] = part[o + 2]; out[b + 3] = size;
            out[b + 4] = a; out[b + 5] = part[o + 9]; out[b + 6] = part[o + 10]; out[b + 7] = 0;
            outCount++;
        }
    }

    private void emit(Source q, double x, double z) {
        emitAt(q, x, z, 1);
        part[(count - 1) * P + 1] = (float) (Ground.height(x, z) + 0.5);
    }

    private void emitAt(Source q, double x, double z, double boost) {
        if (count * P + P > part.length) part = Arrays.copyOf(part, part.length * 2);
        int o = count * P;
        part[o] = (float) x; part[o + 1] = (float) q.y; part[o + 2] = (float) z;
        part[o + 3] = 0;
        part[o + 4] = (float) (q.life * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 5] = (float) (q.size0 * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 6] = (float) (q.size1 * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 7] = (float) (q.rise * boost * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 8] = (float) (q.alpha * (0.6 + 0.8 * rnd.nextDouble()));
        part[o + 9] = rnd.nextFloat();
        part[o + 10] = q.kind;
        part[o + 11] = (float) (rnd.nextGaussian() * 0.3);
        part[o + 12] = (float) (rnd.nextGaussian() * 0.3);
        count++;
    }

    private void emit(Source q) {
        if (count * P + P > part.length) part = Arrays.copyOf(part, part.length * 2);
        double x, z;
        if (q.line.length >= 2) {
            int seg = rnd.nextInt(q.line.length - 1);
            double f = rnd.nextDouble();
            x = q.line[seg][0] + (q.line[seg + 1][0] - q.line[seg][0]) * f;
            z = q.line[seg][1] + (q.line[seg + 1][1] - q.line[seg][1]) * f;
        } else {
            double a = rnd.nextDouble() * 6.2832, r = q.radius * Math.sqrt(rnd.nextDouble());
            x = q.line[0][0] + Math.cos(a) * r;
            z = q.line[0][1] + Math.sin(a) * r;
        }
        x += rnd.nextGaussian() * q.radius * 0.1;
        z += rnd.nextGaussian() * q.radius * 0.1;
        double y = Double.isNaN(q.y) ? Ground.height(x, z) + 0.3 : q.y;
        int o = count * P;
        part[o] = (float) x; part[o + 1] = (float) y; part[o + 2] = (float) z;
        part[o + 3] = 0;
        part[o + 4] = (float) (q.life * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 5] = (float) (q.size0 * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 6] = (float) (q.size1 * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 7] = (float) (q.rise * (0.7 + 0.6 * rnd.nextDouble()));
        part[o + 8] = (float) (q.alpha * (0.6 + 0.8 * rnd.nextDouble()));
        part[o + 9] = rnd.nextFloat();
        part[o + 10] = q.kind;
        part[o + 11] = (float) (rnd.nextGaussian() * 0.3);
        part[o + 12] = (float) (rnd.nextGaussian() * 0.3);
        count++;
    }

    public int particles() { return count; }

    @Override public float[] puffs() { return out; }
    @Override public int puffCount() { return outCount; }
}
