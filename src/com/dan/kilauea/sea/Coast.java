package com.dan.kilauea.sea;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Uferlinie in einem Ausschnitt, ohne Grafik (Phase 9): Höhen im Raster abtasten, wo sie die
 * Meereshöhe kreuzen, liegt ein Uferpunkt. Je Punkt die Richtung zum Meer, die Höhe des Landes dahinter
 * und die Tiefe davor; daraus die Art des Ufers.
 * <ul>
 * <li>Klippe: das Land steigt binnen 25 m um mehr als 6 m, davor ist das Wasser tiefer als 3 m. Hier
 * brechen die Wellen nicht, sie schlagen an die Wand und werfen Gischt hoch.</li>
 * <li>Bank: flaches Lavaufer oder Strand; die Wellen laufen auf und brechen davor.</li>
 * </ul>
 * Die Grenzwerte sind gestaltet [G].
 */
public final class Coast {
    /** Höhe des Geländes (m ü. M.) an x, z. */
    public interface Terrain { double height(double x, double z); }

    public static final class Point {
        public final double x, z;
        /** Richtung zum Meer (Einheitsvektor). */
        public final double nx, nz;
        /** Höhe des Landes 25 m landeinwärts (m), Tiefe 40 m seewärts (m). */
        public final double land, depth;
        public final boolean cliff;

        Point(double x, double z, double nx, double nz, double land, double depth) {
            this.x = x; this.z = z; this.nx = nx; this.nz = nz; this.land = land; this.depth = depth;
            this.cliff = land > 6 && depth > 3;
        }
    }

    public final List<Point> points = new ArrayList<>();
    /** Mitte und halbe Seite des Ausschnitts. */
    public double cx, cz, half;

    /**
     * Ufer im Quadrat um (cx, cz) mit halber Seite half, Raster step (m). Ein Punkt je Rasterzelle, die die
     * Uferlinie schneidet.
     */
    public static Coast scan(Terrain t, double cx, double cz, double half, double step) {
        Coast c = new Coast();
        c.cx = cx; c.cz = cz; c.half = half;
        int n = (int) Math.ceil(2 * half / step) + 1;
        double x0 = cx - half, z0 = cz - half;
        double[] h = new double[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) h[j * n + i] = t.height(x0 + i * step, z0 + j * step);
        for (int j = 0; j + 1 < n; j++)
            for (int i = 0; i + 1 < n; i++) {
                double a = h[j * n + i], b = h[j * n + i + 1], d = h[(j + 1) * n + i], e = h[(j + 1) * n + i + 1];
                double mn = Math.min(Math.min(a, b), Math.min(d, e)), mx = Math.max(Math.max(a, b), Math.max(d, e));
                if (mn >= 0 || mx < 0) continue;                     // 0 zählt als Land
                // Uferpunkt: Mittel der Kreuzungen auf den vier Kanten
                double sx = 0, sz = 0;
                int k = 0;
                double[][] edges = {{a, b, 0, 0, 1, 0}, {d, e, 0, 1, 1, 1}, {a, d, 0, 0, 0, 1}, {b, e, 1, 0, 1, 1}};
                for (double[] ed : edges) {
                    if ((ed[0] < 0) == (ed[1] < 0)) continue;
                    double f = ed[0] / (ed[0] - ed[1]);
                    sx += ed[2] + (ed[4] - ed[2]) * f;
                    sz += ed[3] + (ed[5] - ed[3]) * f;
                    k++;
                }
                if (k == 0) continue;
                double px = x0 + (i + sx / k) * step, pz = z0 + (j + sz / k) * step;
                // Richtung zum Meer: gegen das Gefälle der Zelle
                double gx = (b - a + e - d) * 0.5, gz = (d - a + e - b) * 0.5;
                double gl = Math.hypot(gx, gz);
                if (gl < 1e-6) continue;
                double nx = -gx / gl, nz = -gz / gl;
                double land = Double.NEGATIVE_INFINITY;
                for (double r = 5; r <= 25; r += 5) land = Math.max(land, t.height(px - nx * r, pz - nz * r));
                double depth = -t.height(px + nx * 40, pz + nz * 40);
                c.points.add(new Point(px, pz, nx, nz, land, depth));
            }
        return c;
    }

    public int cliffs() {
        int n = 0;
        for (Point p : points) if (p.cliff) n++;
        return n;
    }
}
