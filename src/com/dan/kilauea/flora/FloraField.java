package com.dan.kilauea.flora;

import com.dan.kilauea.terrain.Geology;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Relief;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Wo Bäume und Baumfarne stehen. Die Fläche um die Kamera ist in Kacheln von 48 m geteilt; jede wird
 * beim ersten Bedarf aus einem festen Startwert bepflanzt (immer gleich) und behalten, solange sie in
 * Reichweite liegt. Gepflanzt wird nach denselben Masken, mit denen {@code terrain.frag} Kronendach
 * und Bewuchsflecken malt, damit Bäume dort stehen, wo der Boden von oben Wald zeigt:
 * <ul>
 * <li>ʻōhiʻa im Kronendach: dicht, 9 bis 20 m;</li>
 * <li>ʻōhiʻa als Pionier: einzeln auf jungen Strömen im Feuchten, 1 bis 5 m;</li>
 * <li>hāpuʻu unter dem Dach, wo es mehr als rund 2 200 mm regnet.</li>
 * </ul>
 * Je Bild sortiert {@link #update} die Pflanzen in Eimer nach Art, Variante und Detailstufe
 * (ohne Grafikkarte, prüfbar).
 */
public final class FloraField {
    public static final int OHIA = 0, FERN = 1;
    public static final int OHIA_VARIANTS = 6, FERN_VARIANTS = 3;
    /** Detailstufen: ʻōhiʻa 1, 2, 3 (aus TreeMesh), Farn 0, 1. */
    public static final int OHIA_LODS = 4, FERN_LODS = 2;
    public static final int BUCKETS = OHIA_VARIANTS * OHIA_LODS + FERN_VARIANTS * FERN_LODS;
    static final double TILE = 48, SPACING = 4.6;
    /** Je Pflanze in einer Kachel: x, y, z, Drehung, Maßstab, Tönung, Art, Variante. */
    static final int STRIDE = 8;

    public double radius = 900, fernRadius = 220, lod0 = 16, lod1 = 45, lod2 = 260;

    private final Map<Long, float[]> tiles = new HashMap<>();
    private final Map<Long, float[]> tileBounds = new HashMap<>();
    public final float[][] bucket = new float[BUCKETS][];
    public final int[] count = new int[BUCKETS];
    public int planted, shown, tilesKept;

    public FloraField() {
        for (int i = 0; i < BUCKETS; i++) bucket[i] = new float[8 * 256];
    }

    public static int ohiaBucket(int variant, int lod) { return lod * OHIA_VARIANTS + variant; }
    public static int fernBucket(int variant, int lod) { return OHIA_VARIANTS * OHIA_LODS + lod * FERN_VARIANTS + variant; }

    /** Reichweiten nach Qualität (m): Bäume, Farne, Stufe 1 bis, Stufe 2 bis. */
    public void setRanges(double trees, double ferns, double l1, double l2) {
        radius = trees; fernRadius = ferns; lod1 = l1; lod2 = l2;
    }

    // ------------------------------------------------------------------ Masken wie terrain.frag

    /** Ergebnis: Bewuchsfleck (0..1), Kronendach (0..1), Regen (mm), Alter (Jahre). */
    public static double[] masks(HeightGrid g, double x, double z, double[] out) {
        float fx = (float) x, fz = (float) z;
        double wx = (Relief.fbm(fx * 0.012f, fz * 0.012f, 3) - 0.5) * 70, wz = (Relief.fbm(fx * 0.012f + 7.3f, fz * 0.012f + 7.3f, 3) - 0.5) * 70;
        double veg = g.cover(x + wx, z + wz, 0), trees = g.cover(x + wx, z + wz, 1);
        double patchN = Relief.fbm(fx * 0.045f, fz * 0.045f, 3) * 0.7 + 0.3 * Relief.vnoise(fx * 0.4f, fz * 0.4f);
        double vegMask = smooth(-0.06, 0.06, veg - patchN * 0.95 - 0.03);
        double treeMask = smooth(-0.08, 0.08, trees - Relief.fbm(fx * 0.02f + 5f, fz * 0.02f + 5f, 3) * 0.9) * vegMask;
        out[0] = vegMask;
        out[1] = treeMask;
        out[2] = g.geo(x + wx * 0.4, z + wz * 0.4, 3) * Geology.RAIN_MAX;
        out[3] = Geology.ageOf(g.geo(x + wx * 0.4, z + wz * 0.4, 0) * 255);
        return out;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ Pflanzen

    /** Bepflanzt die Kachel (ti, tj). */
    static float[] plant(HeightGrid g, int ti, int tj) {
        Random r = new Random(ti * 73856093L ^ tj * 19349663L ^ 0x5EED);
        double x0 = ti * TILE, z0 = tj * TILE;
        int n = (int) Math.round(TILE / SPACING);
        float[] out = new float[n * n * STRIDE / 2 + STRIDE];
        int k = 0;
        double[] m = new double[4];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = x0 + (i + 0.15 + 0.7 * r.nextDouble()) * SPACING, z = z0 + (j + 0.15 + 0.7 * r.nextDouble()) * SPACING;
                double roll = r.nextDouble(), roll2 = r.nextDouble(), rot = r.nextDouble() * 6.2832, tint = r.nextDouble();
                int variant = r.nextInt(6);
                masks(g, x, z, m);
                if (m[0] < 0.02) continue;
                double base = g.height(x, z);
                if (base < 1.5) continue;
                double e = 6;
                double sl = Math.hypot(g.height(x + e, z) - g.height(x - e, z), g.height(x, z + e) - g.height(x, z - e)) / (2 * e);
                if (sl > 0.7) continue;                                   // Steilwand
                if (com.dan.kilauea.terrain.ParkRoads.distance(x, z) < 6.5) continue;   // Straße und Bankett
                double rain = m[2], age = m[3];
                int type = -1;
                float scale = 0;
                // Kronendach: rund jeder zweite Platz trägt einen Baum
                if (m[1] > 0.05 && roll < 0.5 * m[1]) {
                    type = OHIA;
                    scale = (float) ((0.55 + 0.5 * roll2) * (0.6 + 0.4 * smooth(1200, 3000, rain)) * (0.5 + 0.5 * smooth(60, 400, age)));
                } else if (roll < 0.5 * m[1] + 0.06 * m[0] * (1 - m[1]) * smooth(1200, 2600, rain) * smooth(15, 60, age)) {
                    type = OHIA;                                         // Pionier auf junger Lava
                    scale = (float) (0.08 + 0.18 * roll2);
                } else if (m[1] > 0.3 && rain > 2200 && roll2 < 0.28 * m[1] * smooth(2200, 3600, rain)) {
                    type = FERN;
                    scale = (float) (0.75 + 0.5 * r.nextDouble());
                    variant %= FERN_VARIANTS;
                }
                if (type < 0) continue;
                if (k + STRIDE > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[k++] = (float) x;
                out[k++] = (float) Ground.height(x, z) - 0.15f * scale;   // Fuß etwas eingesenkt
                out[k++] = (float) z;
                out[k++] = (float) rot;
                out[k++] = scale;
                out[k++] = (float) tint;
                out[k++] = type;
                out[k++] = variant;
            }
        return Arrays.copyOf(out, k);
    }

    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    // ------------------------------------------------------------------ Auswahl je Bild

    private final double[][] planes = new double[4][4];

    /**
     * Wählt die Pflanzen um die Kamera (cam) für das Bild mit Blick·Projektion vp (null: alle im
     * Umkreis, für Schatten) und füllt die Eimer. Höchstens maxNew Kacheln werden je Aufruf neu bepflanzt.
     */
    public void update(HeightGrid g, double[] cam, float[] vp, int maxNew) {
        Arrays.fill(count, 0);
        shown = 0;
        if (g == null || radius <= 0) return;
        if (cam[1] - g.height(cam[0], cam[2]) > radius) return;     // hoch oben: keine Pflanzen, auch nicht pflanzen
        boolean cull = vp != null;
        if (cull) frustum(vp);
        int r = (int) Math.ceil(radius / TILE) + 1;
        int ci = (int) Math.floor(cam[0] / TILE), cj = (int) Math.floor(cam[2] / TILE);
        List<long[]> missing = new ArrayList<>();
        double rr = (radius + TILE) * (radius + TILE);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                double tx = (i + 0.5) * TILE, tz = (j + 0.5) * TILE;
                double dx = tx - cam[0], dz = tz - cam[2];
                double d2 = dx * dx + dz * dz;
                if (d2 > rr) continue;
                long kk = key(i, j);
                float[] t = tiles.get(kk);
                if (t == null) { missing.add(new long[]{i, j, (long) d2}); continue; }
                float[] b = tileBounds.get(kk);
                // 60 m Zugabe: Bäume knapp außerhalb des Bildes werfen noch Schatten hinein
                if (cull && !visible(tx, (b[0] + b[1]) * 0.5, tz, Math.sqrt(TILE * TILE * 0.5 + Math.pow((b[1] - b[0]) * 0.5 + 22, 2)) + 60)) continue;
                emit(t, cam);
            }
        // neue Kacheln, die nächsten zuerst
        missing.sort((a, b) -> Long.compare(a[2], b[2]));
        for (int q = 0; q < Math.min(maxNew, missing.size()); q++) {
            int i = (int) missing.get(q)[0], j = (int) missing.get(q)[1];
            float[] t = plant(g, i, j);
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int k = 0; k < t.length; k += STRIDE) { lo = Math.min(lo, t[k + 1]); hi = Math.max(hi, t[k + 1]); }
            if (t.length == 0) { lo = hi = 0; }
            tiles.put(key(i, j), t);
            tileBounds.put(key(i, j), new float[]{lo, hi});
            planted += t.length / STRIDE;
        }
        // ferne Kacheln vergessen
        if (tiles.size() > 4 * (2 * r + 1) * (2 * r + 1)) {
            double keep = (radius * 1.6) * (radius * 1.6);
            tiles.keySet().removeIf(kk -> {
                int i = (int) (kk >> 32), j = (int) (long) kk;
                double dx = (i + 0.5) * TILE - cam[0], dz = (j + 0.5) * TILE - cam[2];
                boolean drop = dx * dx + dz * dz > keep;
                if (drop) tileBounds.remove(kk);
                return drop;
            });
        }
        tilesKept = tiles.size();
    }

    /** Ob alle Kacheln im Umkreis bepflanzt sind (für Standbilder). */
    public boolean complete(double[] cam) {
        int r = (int) Math.ceil(radius / TILE) + 1;
        int ci = (int) Math.floor(cam[0] / TILE), cj = (int) Math.floor(cam[2] / TILE);
        double rr = (radius + TILE) * (radius + TILE);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                double dx = (i + 0.5) * TILE - cam[0], dz = (j + 0.5) * TILE - cam[2];
                if (dx * dx + dz * dz <= rr && !tiles.containsKey(key(i, j))) return false;
            }
        return true;
    }

    private void emit(float[] t, double[] cam) {
        for (int k = 0; k < t.length; k += STRIDE) {
            double dx = t[k] - cam[0], dy = t[k + 1] - cam[1], dz = t[k + 2] - cam[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int type = (int) t[k + 6], variant = (int) t[k + 7];
            float scale = t[k + 4];
            int b;
            if (type == OHIA) {
                // kleine Pioniere reichen nicht so weit; in der Ferne dünner (das Kronendach im Boden trägt das Bild)
                double reach = radius * (scale < 0.3f ? 0.45 : 1.0);
                if (d > reach) continue;
                if (d > reach * 0.4 && hash(t[k], t[k + 2]) > smooth(reach, reach * 0.4, d)) continue;
                double sc = Math.max(0.6, scale);
                int lod = d < lod0 * sc ? 0 : d < lod1 * sc ? 1 : d < lod2 ? 2 : 3;
                b = ohiaBucket(variant, lod);
            } else {
                if (d > fernRadius) continue;
                b = fernBucket(variant, d < 50 ? 0 : 1);
            }
            float[] o = bucket[b];
            int c = count[b];
            if (8 * c + 8 > o.length) o = bucket[b] = Arrays.copyOf(o, o.length * 2);
            o[8 * c] = t[k]; o[8 * c + 1] = t[k + 1]; o[8 * c + 2] = t[k + 2]; o[8 * c + 3] = t[k + 3];
            o[8 * c + 4] = scale; o[8 * c + 5] = t[k + 5]; o[8 * c + 6] = (float) (hash(t[k + 2], t[k]) * 6.2832); o[8 * c + 7] = 0;
            count[b] = c + 1;
            shown++;
        }
    }

    private static double hash(double x, double z) {
        long h = Double.doubleToLongBits(x * 12.9898 + z * 78.233);
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private void frustum(float[] m) {
        for (int p = 0; p < 4; p++) {
            int row = p >> 1;
            double s = (p & 1) == 0 ? 1 : -1;
            double[] q = planes[p];
            for (int c = 0; c < 4; c++) q[c] = m[c * 4 + 3] + s * m[c * 4 + row];
            double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
            for (int c = 0; c < 4; c++) q[c] /= l;
        }
    }

    private boolean visible(double x, double y, double z, double rad) {
        for (double[] q : planes) if (q[0] * x + q[1] * y + q[2] * z + q[3] < -rad) return false;
        return true;
    }
}
