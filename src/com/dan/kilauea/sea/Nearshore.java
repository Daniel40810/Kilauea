package com.dan.kilauea.sea;

/**
 * Das Wasser vor der Küste um die Kamera, ohne Grafik (Phase 9): ein Raster aus 512 × 512 Zellen zu 10 m
 * mit Tiefe, Abstand zur Uferlinie (vorzeichenbehaftet, seewärts positiv) und Richtung zum Meer. Der
 * Meer-Shader liest es als Textur und rechnet daraus Aufsteilen, Brechen und Schaum; {@link Coast} findet
 * darin die Klippen für die Gischt.
 * <p>
 * Abstand exakt nach Felzenszwalb &amp; Huttenlocher (2012), getrennt für Wasser und Land [L, Verfahren].
 * Gebaut im Hintergrund, wenn die Kamera mehr als 1,2 km gewandert ist.
 */
public final class Nearshore {
    public static final int N = 512;
    public static final double CELL = 10;

    /** Linke obere Ecke (Mitte der Zelle 0, 0) und Seite (m). */
    public final double x0, z0, size;
    /** Je Zelle: Tiefe (m, an Land negativ), Abstand zur Uferlinie (m, seewärts positiv), Richtung zum Meer x, z. */
    public final float[] data = new float[N * N * 4];
    public final Coast coast;
    public final double cx, cz;

    private Nearshore(double cx, double cz, float[] h) {
        this.cx = cx; this.cz = cz;
        size = N * CELL;
        x0 = cx - size / 2 + CELL / 2;
        z0 = cz - size / 2 + CELL / 2;
        // Abstand zum nächsten Land (für Wasser) und zum nächsten Wasser (für Land)
        double[] dw = edt(h, true), dl = edt(h, false);
        float[] s = new float[N * N];
        for (int i = 0; i < N * N; i++)
            s[i] = (float) (h[i] < 0 ? Math.sqrt(dw[i]) * CELL - CELL / 2 : -(Math.sqrt(dl[i]) * CELL - CELL / 2));
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int k = j * N + i;
                double gx = s[j * N + Math.min(N - 1, i + 1)] - s[j * N + Math.max(0, i - 1)];
                double gz = s[Math.min(N - 1, j + 1) * N + i] - s[Math.max(0, j - 1) * N + i];
                double gl = Math.hypot(gx, gz);
                data[4 * k] = -h[k];
                data[4 * k + 1] = s[k];
                data[4 * k + 2] = gl > 1e-6 ? (float) (gx / gl) : 0;
                data[4 * k + 3] = gl > 1e-6 ? (float) (gz / gl) : 0;
            }
        coast = Coast.scan(this::height, cx, cz, size / 2 - 40, CELL);
    }

    /** Raster um (cx, cz) aus dem Gelände. */
    public static Nearshore build(Coast.Terrain t, double cx, double cz) {
        float[] h = new float[N * N];
        double x0 = cx - N * CELL / 2 + CELL / 2, z0 = cz - N * CELL / 2 + CELL / 2;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) h[j * N + i] = (float) t.height(x0 + i * CELL, z0 + j * CELL);
        return new Nearshore(cx, cz, h);
    }

    /** Höhe bilinear aus dem Raster (m ü. M.); außerhalb tiefes Meer. */
    public double height(double x, double z) {
        double u = (x - x0) / CELL, v = (z - z0) / CELL;
        if (u < 0 || v < 0 || u > N - 1 || v > N - 1) return -1000;
        int i = Math.min(N - 2, (int) u), j = Math.min(N - 2, (int) v);
        double fu = u - i, fv = v - j;
        double a = -data[4 * (j * N + i)], b = -data[4 * (j * N + i + 1)], c = -data[4 * ((j + 1) * N + i)], d = -data[4 * ((j + 1) * N + i + 1)];
        return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv;
    }

    /** Abstand zur Uferlinie (m, seewärts positiv), bilinear. */
    public double shoreDistance(double x, double z) {
        double u = (x - x0) / CELL, v = (z - z0) / CELL;
        if (u < 0 || v < 0 || u > N - 1 || v > N - 1) return Double.NaN;
        int i = Math.min(N - 2, (int) u), j = Math.min(N - 2, (int) v);
        double fu = u - i, fv = v - j;
        double a = data[4 * (j * N + i) + 1], b = data[4 * (j * N + i + 1) + 1], c = data[4 * ((j + 1) * N + i) + 1], d = data[4 * ((j + 1) * N + i + 1) + 1];
        return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv;
    }

    // -------------------------------------------- Abstandstransformation (quadriert, in Zellen)
    private static double[] edt(float[] h, boolean water) {
        double inf = 1e20;
        double[] f = new double[N * N];
        // Ziel: die andere Seite (für Wasser das Land und umgekehrt)
        for (int k = 0; k < N * N; k++) f[k] = (h[k] < 0) == water ? inf : 0;
        double[] col = new double[N], res = new double[N];
        double[] zz = new double[N + 1];
        int[] vv = new int[N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) col[j] = f[j * N + i];
            dt1(col, res, vv, zz);
            for (int j = 0; j < N; j++) f[j * N + i] = res[j];
        }
        for (int j = 0; j < N; j++) {
            System.arraycopy(f, j * N, col, 0, N);
            dt1(col, res, vv, zz);
            System.arraycopy(res, 0, f, j * N, N);
        }
        return f;
    }

    private static void dt1(double[] f, double[] d, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < N; q++) {
            double s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < N; q++) {
            while (z[k + 1] < q) k++;
            d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
        }
    }
}
