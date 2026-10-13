package com.dan.kilauea.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Der Felsbogen Hōlei in der Meeresklippe am Ende der Chain of Craters Road: rund 27 m hoch (90 ft)
 * [L, NPS], vor rund 550 Jahren durch Brandung aus einem alten Strom gewaschen [L]. Zwischen dem 23. und
 * dem 27. September 2026 ist er ins Meer gestürzt [L, NPS-Mitteilung vom 1. Oktober 2026].
 * <p>
 * Weil ein Bogen überhängt, gehört er nicht ins Höhenraster, sondern ist ein eigenes Netz:
 * <ul>
 * <li><b>vor dem Einsturz</b>: eine Felsrippe von 30 m Länge, 8 m Dicke, die von der Klippe ins Meer
 * reicht, mit einem Tor von 16 m Breite und 17 m Höhe (Form gestaltet [G]);</li>
 * <li><b>heute</b>: nur der landseitige Pfeiler als Nase an der Klippe, davor Blöcke in der Brandung [G];
 * was genau stehen blieb, sagt die Mitteilung nicht.</li>
 * </ul>
 * Lage: Wasserlinie bei 19,2948° N, 155,0975° W [L, nach Wikipedia 19,29451/−155,09794, an die Küste
 * der Daten gesetzt].
 */
public final class SeaArch {
    public static final double LAT = 19.2948, LON = -155.0975;
    /** Datum des Einsturzes (gesichert zwischen 23. und 27.09.2026). */
    public static final String COLLAPSE = "September 2026";

    /** Netz: je Ecke Lage (3) und Normale (3); Dreiecke. */
    public final float[] vertices;
    public final int[] indices;
    /** Ursprung (Wasserlinie), Richtung seewärts (x, z), Richtung entlang der Küste (x, z), Höhe oben. */
    public final double ox, oz, sx, sz, tx, tz, top;

    private final List<float[]> pos = new ArrayList<>();
    private final List<int[]> tris = new ArrayList<>();

    private SeaArch(boolean intact) {
        // Richtung seewärts: bergab im Mittel über 300 m (die Klippe selbst ist zu steil, um sie abzutasten)
        double x = Geo.x(LAT, LON), z = Geo.z(LAT);
        double e = 150;
        double gx = (Ground.baseHeight(x + e, z) - Ground.baseHeight(x - e, z)) / (2 * e), gz = (Ground.baseHeight(x, z + e) - Ground.baseHeight(x, z - e)) / (2 * e);
        double gl = Math.hypot(gx, gz) + 1e-9;
        double dx = -gx / gl, dz = -gz / gl;
        // Wasserlinie: auf dieser Linie den Wechsel von Land zu Wasser suchen (vor- oder rückwärts)
        double sign = Ground.baseHeight(x, z) > 0 ? 1 : -1;
        for (int k = 0; k < 600 && Ground.baseHeight(x, z) * sign > 0; k++) { x += dx * sign; z += dz * sign; }
        if (sign < 0) { x += dx; z += dz; }
        x -= dx * 0.5;
        z -= dz * 0.5;
        ox = x;
        oz = z;
        sx = dx;
        sz = dz;
        tx = -sz;
        tz = sx;
        double cliff = Ground.baseHeight(x - sx * 30, z - sz * 30);
        top = Math.max(27, cliff + 2);
        if (intact) arch(); else ruin();
        vertices = new float[pos.size() * 6];
        indices = new int[tris.size() * 3];
        float[] nacc = new float[pos.size() * 3];
        for (int i = 0; i < tris.size(); i++) {
            int[] t = tris.get(i);
            indices[3 * i] = t[0]; indices[3 * i + 1] = t[1]; indices[3 * i + 2] = t[2];
            float[] a = pos.get(t[0]), b = pos.get(t[1]), c = pos.get(t[2]);
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            for (int o : t) { nacc[o * 3] += nx; nacc[o * 3 + 1] += ny; nacc[o * 3 + 2] += nz; }
        }
        for (int i = 0; i < pos.size(); i++) {
            float[] p = pos.get(i);
            double l = Math.sqrt(nacc[3 * i] * nacc[3 * i] + nacc[3 * i + 1] * nacc[3 * i + 1] + nacc[3 * i + 2] * nacc[3 * i + 2]) + 1e-9;
            vertices[6 * i] = p[0]; vertices[6 * i + 1] = p[1]; vertices[6 * i + 2] = p[2];
            vertices[6 * i + 3] = (float) (nacc[3 * i] / l); vertices[6 * i + 4] = (float) (nacc[3 * i + 1] / l); vertices[6 * i + 5] = (float) (nacc[3 * i + 2] / l);
        }
    }

    public static SeaArch build(boolean intact) { return new SeaArch(intact); }

    // ------------------------------------------------------------------ Formen (örtlich: s seewärts, t entlang der Küste, y aufwärts)

    private static final double S0 = -7, S1 = 24, YB = -5, HOLE_S = 10.5, HOLE_A = 8, HOLE_B = 17;

    /** Ecke an der örtlichen Lage, mit grober und feiner Unruhe des Felsens. */
    private int vert(double s, double t, double y, double rough) {
        double n1 = Noise.fbm(s * 0.09 + 3, y * 0.09 + t * 0.05, 3) - 0.5, n2 = Noise.fbm(t * 0.11 - 5, y * 0.1 + s * 0.04, 3) - 0.5;
        double n3 = Noise.value(s * 0.6 + t * 0.4, y * 0.6) - 0.5;
        s += rough * (1.4 * n1 + 0.35 * n3);
        t += rough * (1.6 * n2 + 0.35 * n3);
        y += rough * 0.5 * n1 * Math.min(1, Math.max(0, y) / 6);
        double wx = ox + sx * s + tx * t, wz = oz + sz * s + tz * t;
        pos.add(new float[]{(float) wx, (float) y, (float) wz});
        return pos.size() - 1;
    }

    private void quad(int a, int b, int c, int d, boolean flip) {
        if (flip) { tris.add(new int[]{a, c, b}); tris.add(new int[]{a, d, c}); }
        else { tris.add(new int[]{a, b, c}); tris.add(new int[]{a, c, d}); }
    }

    /** Oberkante: wellig, zur See hin etwas tiefer. */
    private double topAt(double s) {
        return top - 2.5 * Math.max(0, s - 12) / 12 + 1.6 * (Noise.fbm(s * 0.15 + 9, 1.3, 3) - 0.5);
    }

    /**
     * Rippe mit Tor: Punkte um die Mitte des Tors (s = HOLE_S, y = YB) auf Strahlen, innen die Ellipse
     * des Tors, außen der Umriss der Rippe; vorn und hinten je eine Fläche, dazu Torlaibung und Außenhaut.
     */
    private void arch() {
        int N = 72, M = 8, K = 6;
        double T = 8;
        int[][][] face = new int[2][N + 1][M + 1];
        for (int side = 0; side < 2; side++) {
            double t = side == 0 ? -T / 2 : T / 2;
            for (int a = 0; a <= N; a++) {
                double th = Math.PI * a / N;
                double cx = Math.cos(th), cy = Math.sin(th);
                double rin = 1 / Math.sqrt(cx * cx / (HOLE_A * HOLE_A) + cy * cy / (HOLE_B * HOLE_B));
                double rout = rayToBox(cx, cy);
                for (int m = 0; m <= M; m++) {
                    double r = rin + (rout - rin) * m / M;
                    face[side][a][m] = vert(HOLE_S + cx * r, t, YB + cy * r, 1);
                }
            }
            for (int a = 0; a < N; a++)
                for (int m = 0; m < M; m++)
                    quad(face[side][a][m], face[side][a + 1][m], face[side][a + 1][m + 1], face[side][a][m + 1], side == 1);
        }
        // Laibung (innen) und Außenhaut: Streifen von vorn nach hinten
        for (int ring = 0; ring < 2; ring++) {
            int m = ring == 0 ? 0 : M;
            int[][] strip = new int[N + 1][K + 1];
            for (int a = 0; a <= N; a++) {
                strip[a][0] = face[0][a][m];
                strip[a][K] = face[1][a][m];
                double th = Math.PI * a / N, cx = Math.cos(th), cy = Math.sin(th);
                double r = ring == 0 ? 1 / Math.sqrt(cx * cx / (HOLE_A * HOLE_A) + cy * cy / (HOLE_B * HOLE_B)) : rayToBox(cx, cy);
                for (int k = 1; k < K; k++) strip[a][k] = vert(HOLE_S + cx * r, -T / 2 + T * k / K, YB + cy * r, 1);
            }
            for (int a = 0; a < N; a++)
                for (int k = 0; k < K; k++)
                    quad(strip[a][k], strip[a + 1][k], strip[a + 1][k + 1], strip[a][k + 1], ring == 1);
        }
    }

    /** Abstand von der Tormitte bis zum Umriss der Rippe in Richtung (cx, cy). */
    private double rayToBox(double cx, double cy) {
        double best = 1e9;
        if (cx > 1e-6) best = Math.min(best, (S1 - HOLE_S) / cx);
        if (cx < -1e-6) best = Math.min(best, (S0 - HOLE_S) / cx);
        if (cy > 1e-6) {
            // Oberkante hängt von s ab: zwei Schritte genügen
            double r = (topAt(HOLE_S) - YB) / cy;
            r = (topAt(HOLE_S + cx * r) - YB) / cy;
            best = Math.min(best, r);
        }
        return best;
    }

    /** Heute: der landseitige Pfeiler als Nase an der Klippe, davor Blöcke in der Brandung. */
    private void ruin() {
        roughBox(S0, 3.5, -4.5, 4.5, YB, topAt(0) - 1.5, 1.0);
        Random r = new Random(26);
        for (int i = 0; i < 9; i++) {
            double s = 4 + r.nextDouble() * 18, t = (r.nextDouble() - 0.5) * 12, size = 2 + r.nextDouble() * 4;
            roughBox(s - size / 2, s + size / 2, t - size / 2, t + size / 2, -3, -1 + size * (0.4 + 0.5 * r.nextDouble()), 0.6);
        }
    }

    /** Quader mit unterteilten Seiten (je 6 × 6) und rauer Haut. */
    private void roughBox(double s0, double s1, double t0, double t1, double y0, double y1, double rough) {
        int n = 6;
        double[][] lo = {{s0, t0, y0}}, hi = {{s1, t1, y1}};
        // sechs Seiten: Achse, Vorzeichen
        for (int axis = 0; axis < 3; axis++)
            for (int sg = 0; sg < 2; sg++) {
                if (axis == 2 && sg == 0) continue;   // Unterseite liegt im Wasser
                int[][] g = new int[n + 1][n + 1];
                for (int i = 0; i <= n; i++)
                    for (int j = 0; j <= n; j++) {
                        double u = i / (double) n, v = j / (double) n;
                        double[] p = new double[3];
                        int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
                        p[axis] = sg == 0 ? lo[0][axis] : hi[0][axis];
                        p[a1] = lo[0][a1] + (hi[0][a1] - lo[0][a1]) * u;
                        p[a2] = lo[0][a2] + (hi[0][a2] - lo[0][a2]) * v;
                        g[i][j] = vert(p[0], p[1], p[2], rough);
                    }
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++) quad(g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1], (sg == 0) == (axis != 1));
            }
    }
}
