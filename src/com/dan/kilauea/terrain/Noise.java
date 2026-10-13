package com.dan.kilauea.terrain;

/**
 * Wertrauschen auf einem Gitter, deterministisch (gleiche Eingabe, gleiche Ausgabe, ohne Zufallsgenerator).
 * Dieselbe Hash-Funktion wie {@code hash12} in common.glsl, damit Gelände und Shader zueinander passen.
 */
public final class Noise {
    private Noise() { }

    static double hash(double px, double py) {
        double x = fract(px * 0.1031), y = fract(py * 0.1031), z = fract(px * 0.1031);
        double d = x * (y + 33.33) + y * (z + 33.33) + z * (x + 33.33);
        x += d; y += d; z += d;
        return fract((x + y) * z);
    }

    private static double fract(double v) { return v - Math.floor(v); }

    /** Wertrauschen 0..1. */
    public static double value(double x, double y) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy;
    }

    /** Fraktales Rauschen 0..1 (Mittel 0,5) aus oct Oktaven. */
    public static double fbm(double x, double y, int oct) {
        double s = 0, a = 0.5, norm = 0;
        for (int i = 0; i < oct; i++) {
            s += a * value(x, y);
            norm += a;
            x = x * 2.03 + 17.1;
            y = y * 2.03 + 9.7;
            a *= 0.5;
        }
        return s / norm;
    }
}
