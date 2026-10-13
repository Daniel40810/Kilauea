package com.dan.kilauea.terrain;

/**
 * Feinrelief unter der Auflösung der Höhendaten (75 m): Wellen, Buckel und Rinnen von 6 bis 400 m,
 * gerechnet statt gemessen [G]. Frische Lava ist unruhiger, Wald und Asche glätten. Dieselbe Rechnung
 * steht in {@code terrain.vert} (Funktion {@code relief}); hier für die Kamera und die Simulation,
 * immer mit allen Stufen (die Grafik blendet die feinen Stufen in der Ferne aus).
 */
public final class Relief {
    private Relief() { }

    /** Wellenlängen (m) und Höhen (m) der Stufen, gleich wie im Shader. */
    static final float[] WAVE = {420f, 95f, 24f, 6.5f};
    static final float[] AMP = {7.0f, 2.4f, 0.75f, 0.22f};

    /**
     * Höhenzugabe an (x, z): young 0..1 (1 = Lava jünger als rund 30 Jahre), aa 0..1 (ʻaʻā-Anteil),
     * veg 0..1 (Bewuchs), h Grundhöhe. ʻaʻā ist in den feinen Stufen rauer (Brocken), Pāhoehoe in den
     * mittleren welliger (Loben, Tumuli), Bewuchs und Boden glätten.
     */
    public static double at(double x, double z, double young, double aa, double veg, double h) {
        double amp = scale(young, veg, h);
        if (amp <= 0) return 0;
        double s = 0;
        for (int k = 0; k < WAVE.length; k++) {
            float px = (float) (x / WAVE[k]) + 17.3f * k, pz = (float) (z / WAVE[k]) - 9.1f * k;
            double m = k >= 2 ? 1 + 1.2 * aa : 1;
            s += AMP[k] * m * (fbm2(px, pz) - 0.5f) * 2f;
        }
        return s * amp;
    }

    /** Stärke: junge Lava 1,4, Wald 0,45; unter Wasser nur am Ufer. */
    static double scale(double young, double veg, double h) {
        double a = (1 + 0.4 * young) * (1 - 0.55 * veg);
        return a * Island.smooth(-60, -8, h);
    }

    /** Wie stark das Straßenband das Feinrelief glättet (0..1), wie {@code roadFlat} im Shader. */
    public static double roadFlat(double mask) { return Island.smooth(0.3, 0.7, mask); }

    /** 1 für Lava jünger als rund 30 Jahre, 0 ab rund 300 Jahren (aus dem Alter in Jahren). */
    public static double young(double age) { return 1 - Island.smooth(30, 300, age); }

    // ------------------------------------------------------------------ wie common.glsl (float)

    /** Fraktales Wertrauschen wie {@code fbm(p, oct)} in common.glsl (Summe der Gewichte 1 − 2^−oct). */
    public static float fbm(float x, float y, int oct) {
        float s = 0, a = 0.5f;
        for (int i = 0; i < oct; i++) {
            s += a * vnoise(x, y);
            x = x * 2.03f + 17.1f;
            y = y * 2.03f + 9.7f;
            a *= 0.5f;
        }
        return s;
    }

    static float fract(float v) { return v - (float) Math.floor(v); }

    static float hash12(float x, float y) {
        float a = fract(x * 0.1031f), b = fract(y * 0.1031f), c = fract(x * 0.1031f);
        float d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f);
        a += d; b += d; c += d;
        return fract((a + b) * c);
    }

    /** Wertrauschen 0..1 wie {@code vnoise} in common.glsl. */
    public static float vnoise(float x, float y) {
        float ix = (float) Math.floor(x), iy = (float) Math.floor(y), fx = x - ix, fy = y - iy;
        float ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        float a = hash12(ix, iy), b = hash12(ix + 1, iy), c = hash12(ix, iy + 1), d = hash12(ix + 1, iy + 1);
        float ab = a + (b - a) * ux, cd = c + (d - c) * ux;
        return ab + (cd - ab) * uy;
    }

    /** Zwei Oktaven (wie {@code fbm(p, 2)} im Shader, auf 0..1 gestreckt). */
    static float fbm2(float x, float y) {
        float s = 0.5f * vnoise(x, y);
        x = x * 2.03f + 17.1f;
        y = y * 2.03f + 9.7f;
        s += 0.25f * vnoise(x, y);
        return s / 0.75f;
    }
}
