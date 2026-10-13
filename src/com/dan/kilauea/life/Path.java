package com.dan.kilauea.life;

import com.dan.kilauea.terrain.Ground;

/**
 * Ein Weg als Linienzug mit Bogenlänge: für Wanderwege, die Röhre Nāhuku und die Flugrouten. Höhen sind
 * entweder fest (Röhre, Flug) oder NaN (dann das Gelände an der Stelle).
 */
public final class Path {
    public final String name;
    public final double[] x, z, y, s;
    public final double length;
    public final boolean loop;

    public Path(String name, double[] x, double[] z, double[] y, boolean loop) {
        this.name = name;
        this.x = x; this.z = z; this.y = y; this.loop = loop;
        s = new double[x.length];
        for (int i = 1; i < x.length; i++) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
        length = s[x.length - 1];
    }

    /** Aus Breite und Länge (Grad), Höhen aus dem Gelände. */
    public static Path latLon(String name, double[][] ll, boolean loop) {
        double[] x = new double[ll.length], z = new double[ll.length], y = new double[ll.length];
        for (int i = 0; i < ll.length; i++) {
            x[i] = com.dan.kilauea.terrain.Geo.x(ll[i][0], ll[i][1]);
            z[i] = com.dan.kilauea.terrain.Geo.z(ll[i][0]);
            y[i] = Double.NaN;
        }
        return new Path(name, x, z, y, loop);
    }

    /** Aus einem Weg des Straßenpakets (alle 2 m ein Punkt). */
    public static Path of(String name, com.dan.road.Way w) {
        int n = w.n;
        double[] x = new double[n], z = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) { x[i] = w.x[i]; z[i] = w.z[i]; y[i] = Double.NaN; }
        boolean loop = Math.hypot(x[0] - x[n - 1], z[0] - z[n - 1]) < 8;
        return new Path(name, x, z, y, loop);
    }

    /** Bogenlänge auf den Weg bringen (im Kreis oder festgehalten). */
    public double wrap(double t) {
        if (loop) { t %= length; return t < 0 ? t + length : t; }
        return Math.max(0, Math.min(length, t));
    }

    /** Punkt bei Bogenlänge t, quer um u versetzt (rechts positiv): out = x, y, z, Richtung x, Richtung z. */
    public void at(double t, double u, double[] out) {
        t = wrap(t);
        int lo = 0, hi = x.length - 1;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (s[m] <= t) lo = m; else hi = m; }
        double seg = Math.max(1e-9, s[hi] - s[lo]), f = (t - s[lo]) / seg;
        double dx = (x[hi] - x[lo]) / seg, dz = (z[hi] - z[lo]) / seg;
        double px = x[lo] + (x[hi] - x[lo]) * f, pz = z[lo] + (z[hi] - z[lo]) * f;
        // rechts von der Laufrichtung (x Osten, z Süden): (−dz, dx)
        px += -dz * u;
        pz += dx * u;
        double py = Double.isNaN(y[lo]) || Double.isNaN(y[hi]) ? Ground.height(px, pz) : y[lo] + (y[hi] - y[lo]) * f;
        out[0] = px; out[1] = py; out[2] = pz; out[3] = dx; out[4] = dz;
    }

    /** Kleinster Abstand des Punkts zum Weg (m) und die Bogenlänge dort in out[0]. */
    public double distance(double px, double pz, double[] out) {
        double best = Double.MAX_VALUE, bs = 0;
        for (int i = 0; i + 1 < x.length; i++) {
            double ax = x[i], az = z[i], bx = x[i + 1], bz = z[i + 1];
            double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
            double f = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / l2));
            double qx = ax + vx * f - px, qz = az + vz * f - pz, d = qx * qx + qz * qz;
            if (d < best) { best = d; bs = s[i] + f * (s[i + 1] - s[i]); }
        }
        if (out != null) out[0] = bs;
        return Math.sqrt(best);
    }

    /** Gierwinkel des Shaders für die Blickrichtung (dx, dz): das Netz schaut nach +z. */
    public static double yaw(double dx, double dz) { return Math.atan2(-dx, dz); }

    @Override public String toString() { return name; }
}
