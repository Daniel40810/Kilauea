package com.dan.kilauea.terrain;

/**
 * Das Höhenraster der Szene: 2 560 × 2 560 Zellen zu 75 m (192 × 192 km, die ganze Insel mit Meer),
 * dazu je Zelle die Bodenart (frische Lava, Bewuchs, Asche, Küste als 4 Byte). Daraus werden die
 * Texturen für den Viererbaum und die Höhenabfragen der Kamera. Abfragen mit kubischem B-Spline wie im
 * Shader, damit Kamera und Bild dieselbe Fläche sehen.
 */
public final class HeightGrid {
    public static final int N = 2560;
    public static final double CELL = 75.0;
    /** Lage der Mitte von Zelle (0, 0) in der Szene. */
    public static final double X0 = -108_000, Z0 = -115_000;
    public static final double EXTENT = N * CELL;

    public final float[] h;
    /** Bewuchs, Baumanteil, Asche/Tephra, Küste (je 0..255). */
    public final byte[] cover;
    /** Alterscode der Lava ({@link Geology#ageOf}), ʻaʻā-Anteil, Flechten, Regen (0..8 000 mm). */
    public final byte[] geo;
    /** Band um die Straßen (1 = kein Feinrelief), je Zelle 1 Byte; null ohne Straßen ({@link ParkRoads}). */
    public volatile byte[] road;
    /** Woher die Höhen stammen (für Statuszeile und Werkbuch). */
    public final String source;
    /** Kleinste und größte Höhe je Block aus 2^k × 2^k Zellen, k = 0 … LEVELS−1 (für das Ausblenden von Knoten). */
    private final float[][] minPyr, maxPyr;
    private final int[] side = new int[LEVELS];
    private static final int LEVELS = 12;

    public HeightGrid(float[] h, byte[] cover, byte[] geo, String source) {
        this.h = h;
        this.cover = cover;
        this.geo = geo;
        this.source = source;
        minPyr = new float[LEVELS][];
        maxPyr = new float[LEVELS][];
        minPyr[0] = h.clone();
        maxPyr[0] = h.clone();
        side[0] = N;
        int n = N;
        for (int k = 1; k < LEVELS; k++) {
            int m = (n + 1) / 2;   // aufgerundet: der Rand fällt nicht weg
            float[] mn = new float[m * m], mx = new float[m * m];
            float[] pmn = minPyr[k - 1], pmx = maxPyr[k - 1];
            for (int j = 0; j < m; j++)
                for (int i = 0; i < m; i++) {
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int b = 0; b < 2; b++)
                        for (int a = 0; a < 2; a++) {
                            int ii = Math.min(n - 1, 2 * i + a), jj = Math.min(n - 1, 2 * j + b);
                            lo = Math.min(lo, pmn[jj * n + ii]);
                            hi = Math.max(hi, pmx[jj * n + ii]);
                        }
                    mn[j * m + i] = lo;
                    mx[j * m + i] = hi;
                }
            minPyr[k] = mn;
            maxPyr[k] = mx;
            side[k] = m;
            n = m;
        }
    }

    public double at(int i, int j) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return h[j * N + i];
    }

    /** Höhe an (x, z) mit kubischem B-Spline (glatt, wie der Shader). */
    public double height(double x, double z) {
        double u = (x - X0) / CELL, v = (z - Z0) / CELL;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double[] wx = bspline(fx), wz = bspline(fz);
        double s = 0;
        for (int b = 0; b < 4; b++) {
            double row = 0;
            for (int a = 0; a < 4; a++) row += wx[a] * at(i - 1 + a, j - 1 + b);
            s += wz[b] * row;
        }
        return s;
    }

    /** Bodenart-Anteil (Kanal 0 frische Lava, 1 Bewuchs, 2 Asche, 3 Küste) an (x, z), bilinear, 0..1. */
    public double cover(double x, double z, int ch) { return bil(cover, x, z, ch); }

    /** Geologie-Kanal (0 Alterscode, 1 ʻaʻā, 2 Flechten, 3 Regen) an (x, z), bilinear, 0..1. */
    public double geo(double x, double z, int ch) { return bil(geo, x, z, ch); }

    /** Straßenband an (x, z), bilinear, 0..1. */
    public double road(double x, double z) {
        byte[] r = road;
        if (r == null) return 0;
        double u = (x - X0) / CELL, v = (z - Z0) / CELL;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double a = r1(r, i, j), b = r1(r, i + 1, j), c = r1(r, i, j + 1), d = r1(r, i + 1, j + 1);
        return ((a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz) / 255.0;
    }

    private static int r1(byte[] r, int i, int j) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return r[j * N + i] & 255;
    }

    /** Geologie-Kanal der nächsten Zelle, 0..255, ohne Mischen (Phase 14: Alterscodes zweier Ströme nicht vermengen). */
    public int geoNearest(double x, double z, int ch) {
        return c(geo, (int) Math.round((x - X0) / CELL), (int) Math.round((z - Z0) / CELL), ch);
    }

    /** Alter der Lava an (x, z) in Jahren. */
    public double age(double x, double z) { return Geology.ageOf(geo(x, z, 0) * 255); }

    private static double bil(byte[] b, double x, double z, int ch) {
        double u = (x - X0) / CELL, v = (z - Z0) / CELL;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double a = c(b, i, j, ch), bb = c(b, i + 1, j, ch), c = c(b, i, j + 1, ch), d = c(b, i + 1, j + 1, ch);
        return ((a + (bb - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz) / 255.0;
    }

    private static int c(byte[] b, int i, int j, int ch) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return b[(j * N + i) * 4 + ch] & 255;
    }

    static double[] bspline(double t) {
        double t2 = t * t, t3 = t2 * t, it = 1 - t;
        return new double[]{it * it * it / 6, (3 * t3 - 6 * t2 + 4) / 6, (-3 * t3 + 3 * t2 + 3 * t + 1) / 6, t3 / 6};
    }

    /**
     * Kleinste und größte Höhe im Rechteck [x0, x0+size] × [z0, z0+size] (aus der Pyramide, etwas
     * großzügig: die Blöcke ragen über den Rand; schließt die Zellen ein, die der B-Spline dort liest).
     */
    public void range(double x0, double z0, double size, float[] out) {
        // der B-Spline reicht zwei Zellen über die Fläche hinaus
        x0 -= 2 * CELL;
        z0 -= 2 * CELL;
        size += 4 * CELL;
        double cells = size / CELL;
        int k = Math.max(0, Math.min(LEVELS - 1, (int) Math.floor(Math.log(Math.max(1, cells)) / Math.log(2)) - 1));
        int bs = 1 << k, m = side[k];
        int i0 = Math.floorDiv((int) Math.floor((x0 - X0) / CELL), bs), j0 = Math.floorDiv((int) Math.floor((z0 - Z0) / CELL), bs);
        int i1 = Math.floorDiv((int) Math.floor((x0 + size - X0) / CELL), bs), j1 = Math.floorDiv((int) Math.floor((z0 + size - Z0) / CELL), bs);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        // außerhalb des Rasters gilt der Rand (wie bei der Höhenabfrage)
        int ja = clamp(j0 - 1, m), jb = clamp(j1 + 1, m), ia = clamp(i0 - 1, m), ib = clamp(i1 + 1, m);
        for (int j = ja; j <= jb; j++)
            for (int i = ia; i <= ib; i++) {
                lo = Math.min(lo, minPyr[k][j * m + i]);
                hi = Math.max(hi, maxPyr[k][j * m + i]);
            }
        if (lo > hi) { lo = 0; hi = 0; }
        out[0] = lo;
        out[1] = hi;
    }

    private static int clamp(int v, int m) { return Math.max(0, Math.min(m - 1, v)); }

    public float minHeight() { float m = Float.MAX_VALUE; for (float v : minPyr[LEVELS - 1]) m = Math.min(m, v); return m; }
    public float maxHeight() { float m = -Float.MAX_VALUE; for (float v : maxPyr[LEVELS - 1]) m = Math.max(m, v); return m; }
}
