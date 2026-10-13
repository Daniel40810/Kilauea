package com.dan.kilauea.sound;

/**
 * Kleiner Hall nach Schroeder (vier Kammfilter, zwei Allpässe je Kanal) für die Lavaröhre Nāhuku: ein
 * enger Gang aus Fels mit kurzen, dichten Reflexionen [G]. Ohne Anteil (wet = 0) rechnet er nicht.
 */
final class Reverb {
    private static final int[] COMB = {1116, 1188, 1277, 1356}, AP = {556, 341};
    private final float[][] cb = new float[8][], ab = new float[4][];
    private final int[] ci = new int[8], ai = new int[4];
    private final float[] lpz = new float[8];
    private float wetNow;

    Reverb() {
        for (int c = 0; c < 2; c++) {
            for (int k = 0; k < 4; k++) cb[c * 4 + k] = new float[(int) (COMB[k] * 0.55) + c * 23];
            for (int k = 0; k < 2; k++) ab[c * 2 + k] = new float[(int) (AP[k] * 0.55) + c * 11];
        }
    }

    void process(float[] L, float[] R, int n, float wet) {
        if (wet == 0 && wetNow < 1e-4) { wetNow = 0; return; }
        float dw = (wet - wetNow) / n;
        for (int i = 0; i < n; i++) {
            wetNow += dw;
            float in = (L[i] + R[i]) * 0.5f;
            for (int c = 0; c < 2; c++) {
                float acc = 0;
                for (int k = 0; k < 4; k++) {
                    int j = c * 4 + k;
                    float[] b = cb[j];
                    float y = b[ci[j]];
                    lpz[j] = y * 0.7f + lpz[j] * 0.3f;   // Fels schluckt die Höhen
                    b[ci[j]] = in + lpz[j] * 0.80f;
                    ci[j] = (ci[j] + 1) % b.length;
                    acc += y;
                }
                float x = acc * 0.25f;
                for (int k = 0; k < 2; k++) {
                    int j = c * 2 + k;
                    float[] b = ab[j];
                    float bv = b[ai[j]];
                    float y = -x + bv;
                    b[ai[j]] = x + bv * 0.5f;
                    ai[j] = (ai[j] + 1) % b.length;
                    x = y;
                }
                if (c == 0) L[i] += x * wetNow; else R[i] += x * wetNow;
            }
        }
    }
}
