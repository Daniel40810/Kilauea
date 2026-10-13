package com.dan.kilauea.lava;

/**
 * Farbe und Helligkeit glühender Lava nach Planck, in denselben Einheiten, in denen die Engine das
 * Sonnenlicht führt: die Sonne außerhalb der Luft hat je Kanal die Bestrahlungsstärke
 * {@link com.dan.kilauea.atmos.Atmosphere#SUN_POWER}. Eine Fläche mit Albedo a im vollen Sonnenlicht
 * leuchtet also mit a · 22 / π; glühende Lava mit {@link #radiance}.
 * <p>
 * Rechnung [L]: Strahldichte des schwarzen Körpers B(λ, T) (Planck) mal Emissionsgrad, gewichtet mit den
 * Normspektralwerten der CIE 1931 (analytische Näherung nach Wyman, Sloan &amp; Shirley 2013), daraus XYZ
 * und lineares sRGB. Bezug ist die Leuchtdichte der Sonne als schwarzer Körper von 5 778 K im
 * Raumwinkel der Sonnenscheibe (6,8·10⁻⁵ sr). Farben außerhalb des sRGB-Raums (tiefes Rot) werden auf 0
 * gekappt. Ergebnis: 1 150 °C frische Lava strahlt im Rot etwa so hell wie dunkles Gestein im
 * Mittagslicht und ist bei Tag deutlich, bei Nacht blendend; unter rund 550 °C (Draper-Punkt) ist nichts
 * mehr zu sehen.
 */
public final class Glow {
    private Glow() { }

    /** Tabelle für die Shader: Temperaturen von T0 bis T0 + (N−1)·STEP (°C). */
    public static final int N = 48;
    public static final double T0 = 400, STEP = 18.5;
    private static final float[] LUT = build();

    /** Strahldichte (RGB, Engine-Einheiten) bei der Temperatur t (°C), linear zwischen den Tabellenwerten. */
    public static void radiance(double t, double[] out) {
        double u = (t - T0) / STEP;
        if (u <= 0) { out[0] = out[1] = out[2] = 0; return; }
        int i = (int) Math.min(N - 2, Math.floor(u));
        double f = Math.min(1, u - i);
        for (int k = 0; k < 3; k++) out[k] = LUT[3 * i + k] * (1 - f) + LUT[3 * (i + 1) + k] * f;
    }

    /** Die Tabelle (N × RGB) für ein Uniform-Feld. */
    public static float[] table() { return LUT.clone(); }

    private static float[] build() {
        double ySun = 0;
        double[] sun = xyz(5778, 1);
        ySun = sun[1] * 6.8e-5;
        float[] t = new float[N * 3];
        for (int i = 0; i < N; i++) {
            double tc = T0 + i * STEP;
            double[] x = xyz(tc + 273.15, Rheology.EPS);
            double r = 3.2406 * x[0] - 1.5372 * x[1] - 0.4986 * x[2];
            double g = -0.9689 * x[0] + 1.8758 * x[1] + 0.0415 * x[2];
            double b = 0.0557 * x[0] - 0.2040 * x[1] + 1.0570 * x[2];
            double s = com.dan.kilauea.atmos.Atmosphere.SUN_POWER / ySun;
            t[3 * i] = (float) Math.max(0, r * s);
            t[3 * i + 1] = (float) Math.max(0, g * s);
            t[3 * i + 2] = (float) Math.max(0, b * s);
        }
        return t;
    }

    /** XYZ der Strahldichte eines grauen Strahlers (Kelvin, Emissionsgrad), 380–780 nm in Schritten von 2 nm. */
    static double[] xyz(double kelvin, double eps) {
        double X = 0, Y = 0, Z = 0;
        for (double l = 380; l <= 780; l += 2) {
            double b = eps * planck(l * 1e-9, kelvin) * 1e-9 * 2;   // W/(m²·sr) im Intervall
            X += b * xBar(l);
            Y += b * yBar(l);
            Z += b * zBar(l);
        }
        return new double[]{X, Y, Z};
    }

    /** Planck: spektrale Strahldichte (W/(m²·sr·m)). */
    static double planck(double lambda, double kelvin) {
        double c1 = 1.191042972e-16, c2 = 1.438776877e-2;
        double x = c2 / (lambda * kelvin);
        if (x > 700) return 0;
        return c1 / (Math.pow(lambda, 5) * (Math.exp(x) - 1));
    }

    private static double g(double l, double mu, double s1, double s2) {
        double t = (l - mu) / (l < mu ? s1 : s2);
        return Math.exp(-0.5 * t * t);
    }

    static double xBar(double l) { return 1.056 * g(l, 599.8, 37.9, 31.0) + 0.362 * g(l, 442.0, 16.0, 26.7) - 0.065 * g(l, 501.1, 20.4, 26.2); }
    static double yBar(double l) { return 0.821 * g(l, 568.8, 46.9, 40.5) + 0.286 * g(l, 530.9, 16.3, 31.1); }
    static double zBar(double l) { return 1.217 * g(l, 437.0, 11.8, 36.0) + 0.681 * g(l, 459.0, 26.0, 13.8); }
}
