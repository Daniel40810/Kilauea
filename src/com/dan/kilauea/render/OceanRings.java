package com.dan.kilauea.render;

/**
 * Ringnetz für das Meer: eine Mitte und Ringe, deren Halbmesser von 2 m bis 400 km um je 7 % wächst,
 * in 192 Abschnitten. Das Netz wandert mit der Kamera (der Shader verschiebt es); fein, wo man
 * hinschaut, und weit genug, dass die Erdkrümmung den Horizont formt.
 */
final class OceanRings {
    static final int SEGMENTS = 192;
    static final double R0 = 2.0, GROWTH = 1.07, R_MAX = 400_000;

    private OceanRings() { }

    static int rings() { return 1 + (int) Math.ceil(Math.log(R_MAX / R0) / Math.log(GROWTH)); }

    /** Punkte: (Halbmesser, Winkel), zuerst die Mitte. */
    static float[][] build() {
        int rn = rings();
        float[] v = new float[(1 + rn * SEGMENTS) * 2];
        int k = 2;
        for (int r = 0; r < rn; r++) {
            double rad = Math.min(R_MAX, R0 * Math.pow(GROWTH, r));
            for (int s = 0; s < SEGMENTS; s++) {
                v[k++] = (float) rad;
                v[k++] = (float) (2 * Math.PI * s / SEGMENTS);
            }
        }
        return new float[][]{v};
    }

    static int[] indices() {
        int rn = rings();
        int[] idx = new int[SEGMENTS * 3 + (rn - 1) * SEGMENTS * 6];
        int p = 0;
        for (int s = 0; s < SEGMENTS; s++) {
            idx[p++] = 0;
            idx[p++] = 1 + (s + 1) % SEGMENTS;
            idx[p++] = 1 + s;
        }
        for (int r = 0; r + 1 < rn; r++) {
            int a0 = 1 + r * SEGMENTS, b0 = a0 + SEGMENTS;
            for (int s = 0; s < SEGMENTS; s++) {
                int s1 = (s + 1) % SEGMENTS;
                idx[p++] = a0 + s; idx[p++] = a0 + s1; idx[p++] = b0 + s;
                idx[p++] = a0 + s1; idx[p++] = b0 + s1; idx[p++] = b0 + s;
            }
        }
        return idx;
    }
}
