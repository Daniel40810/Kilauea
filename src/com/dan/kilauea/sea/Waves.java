package com.dan.kilauea.sea;

/**
 * Lineare Wellentheorie für die Dünung, ohne Grafik (Phase 9). Dieselben Formeln stehen in {@code sea.glsl};
 * die Prüfung vergleicht beide Wege.
 * <ul>
 * <li>Dispersion ω² = g k tanh(k d) [L, Airy]; Näherung für den Shader nach Eckart (1952) [L], hier zusätzlich
 * genau mit Newton gelöst.</li>
 * <li>Aufsteilen (Shoaling): H = H₀ · K_s, K_s = √(c_g,tief / c_g) [L].</li>
 * <li>Brechen, wenn H &gt; γ d mit γ = 0,78 (McCowan) [L].</li>
 * <li>Art des Brechers nach der Iribarren-Zahl ξ = m / √(H₀/L₀): unter 0,5 Schwallbrecher, bis 3,3
 * Sturzbrecher, darüber Reflexion an Klippe oder steilem Ufer (Battjes 1974) [L].</li>
 * <li>Im flachen Wasser laufen die Kämme senkrecht auf das Ufer zu; bei gleichmäßig ansteigendem Grund
 * (d = m · s, s Abstand zur Uferlinie) ist die Phase φ(s) = −2ω √(s / (g m)) − ω t, die Kämme laufen mit
 * √(g d) auf das Ufer zu [A, aus c = √(g d)].</li>
 * </ul>
 */
public final class Waves {
    public static final double G = 9.81;
    /** Brecherindex nach McCowan. */
    public static final double GAMMA = 0.78;

    private Waves() { }

    public static double omega(double period) { return 2 * Math.PI / period; }

    /** Wellenlänge im tiefen Wasser (m). */
    public static double deepLength(double period) { return G * period * period / (2 * Math.PI); }

    /** Wellenzahl, genau (Newton). */
    public static double k(double period, double depth) {
        double w = omega(period), k0 = w * w / G;
        if (depth <= 0) return Double.POSITIVE_INFINITY;
        double k = eckart(period, depth);
        for (int i = 0; i < 30; i++) {
            double th = Math.tanh(k * depth);
            double f = G * k * th - w * w;
            double df = G * th + G * k * depth * (1 - th * th);
            double dk = f / df;
            k -= dk;
            if (Math.abs(dk) < 1e-12 * k) break;
        }
        return Math.max(k, k0);
    }

    /** Wellenzahl nach Eckart (1952), wie im Shader. */
    public static double eckart(double period, double depth) {
        double w = omega(period), k0 = w * w / G;
        return k0 / Math.sqrt(Math.tanh(k0 * Math.max(depth, 1e-3)));
    }

    /** Phasengeschwindigkeit (m/s). */
    public static double celerity(double period, double depth) { return omega(period) / k(period, depth); }

    /** Gruppengeschwindigkeit (m/s). */
    public static double groupVelocity(double period, double depth) {
        double kk = k(period, depth), kd = kk * depth;
        double n = kd > 20 ? 0.5 : 0.5 * (1 + 2 * kd / Math.sinh(2 * kd));
        return n * omega(period) / kk;
    }

    /** Aufsteilen K_s (1 im tiefen Wasser). */
    public static double shoaling(double period, double depth) {
        return Math.sqrt(G * period / (4 * Math.PI) / groupVelocity(period, depth));
    }

    /** Tiefe (m), in der eine Welle der Höhe h0 (tief) bricht. */
    public static double breakingDepth(double h0, double period) {
        double lo = 0.01, hi = 200;
        for (int i = 0; i < 80; i++) {
            double m = 0.5 * (lo + hi);
            if (h0 * shoaling(period, m) > GAMMA * m) lo = m; else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    /** Iribarren-Zahl für Neigung m (Höhe je Weg), Höhe h0 und Periode. */
    public static double iribarren(double slope, double h0, double period) {
        return slope / Math.sqrt(Math.max(h0, 1e-3) / deepLength(period));
    }

    public enum Breaker {
        SCHWALL("Schwallbrecher"), STURZ("Sturzbrecher"), REFLEXION("Reflexion an Klippe");
        public final String label;
        Breaker(String l) { label = l; }
    }

    public static Breaker breaker(double slope, double h0, double period) {
        double xi = iribarren(slope, h0, period);
        return xi < 0.5 ? Breaker.SCHWALL : xi < 3.3 ? Breaker.STURZ : Breaker.REFLEXION;
    }

    /** Phase der Kämme im flachen Wasser (s Abstand zur Uferlinie, m Neigung des Grundes). */
    public static double shorePhase(double s, double slope, double period, double t) {
        double w = omega(period);
        return -2 * w * Math.sqrt(Math.max(s, 0) / (G * Math.max(slope, 1e-3))) - w * t;
    }

    /** Phase der Dünung im tiefen Wasser an (x, z) für Laufrichtung (dx, dz): k (dx·x + dz·z) − ω t. */
    public static double deepPhase(double x, double z, double dx, double dz, double period, double t) {
        double w = omega(period);
        return w * w / G * (dx * x + dz * z) - w * t;
    }
}
