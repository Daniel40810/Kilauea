package com.dan.kilauea.render;

import com.dan.kilauea.magma.Dike;
import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.magma.Quake;
import com.dan.kilauea.magma.Reservoir;
import com.dan.kilauea.magma.Rift;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.List;

/**
 * Die Schnittansicht (Phase 6): eine senkrechte Ebene durch den Südcaldera-Speicher klappt den Berg auf.
 * Was auf der Seite der Kamera liegt, zeichnen Gelände, Meer und Pflanzen nicht ({@code cutAway} in
 * common.glsl); die Ebene selbst zeigt Gestein, Speicher, Gänge, Gleitfläche und Beben.
 */
public final class SectionRenderer {
    /** Lage des Schnitts. */
    public enum Mode {
        OFF("aus", 0, 0, 0),
        EAST("Ostriftzone", 0, -12_000, 62_000),
        SOUTHWEST("Südwestriftzone", 0, -12_000, 32_000),
        NORTH_SOUTH("Nord–Süd durch den Gipfel", 0, -16_000, 26_000);

        final String label;
        final double a0, a1;

        Mode(String label, int dummy, double a0, double a1) {
            this.label = label;
            this.a0 = a0;
            this.a1 = a1;
        }

        @Override public String toString() { return label; }
    }

    static final double BOTTOM = -18_000, TOP = 4_500, WIDTH = 40_000;
    private int vao, vbo, ibo, count;
    /** Ebene: Ursprung (x, z), Richtung (x, z), Normale zur Kamera (x, z), Abstand, Abschnitt a0..a1. */
    final double[] origin = new double[2], axis = new double[2], normal = new double[2];
    double dist, a0, a1;
    Mode mode = Mode.OFF;

    void init(GL4 gl) {
        int NA = 160, NY = 48;
        float[] v = new float[(NA + 1) * (NY + 1) * 2];
        int q = 0;
        for (int j = 0; j <= NY; j++)
            for (int i = 0; i <= NA; i++) {
                v[q++] = i / (float) NA;
                v[q++] = j / (float) NY;
            }
        int[] idx = new int[NA * NY * 6];
        int c = 0;
        for (int j = 0; j < NY; j++)
            for (int i = 0; i < NA; i++) {
                int a = j * (NA + 1) + i, b = a + 1, d = a + NA + 1, e = d + 1;
                idx[c++] = a; idx[c++] = b; idx[c++] = d;
                idx[c++] = b; idx[c++] = e; idx[c++] = d;
            }
        int[] t = new int[2];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ibo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 8, 0L);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        count = idx.length;
    }

    /** Ebene für die Lage m und die Kamera bei (cx, cz) bestimmen; Gitter bei Wechsel neu füllen. */
    void place(GL4 gl, Mode m, MagmaSystem ms, double cx, double cz) {
        mode = m;
        if (m == Mode.OFF) return;
        Reservoir sc = ms.sc;
        origin[0] = sc.x;
        origin[1] = sc.z;
        double dx, dz;
        switch (m) {
            case EAST -> { double[] p = Rift.EAST.at(26_000); dx = p[0] - sc.x; dz = p[1] - sc.z; }
            case SOUTHWEST -> { double[] p = Rift.SOUTHWEST.at(14_000); dx = p[0] - sc.x; dz = p[1] - sc.z; }
            default -> { dx = 0; dz = 1; }   // nach Süden
        }
        double l = Math.hypot(dx, dz);
        axis[0] = dx / l;
        axis[1] = dz / l;
        normal[0] = -axis[1];
        normal[1] = axis[0];
        if (normal[0] * (cx - origin[0]) + normal[1] * (cz - origin[1]) < 0) { normal[0] = -normal[0]; normal[1] = -normal[1]; }
        dist = -(normal[0] * origin[0] + normal[1] * origin[1]);
        a0 = m.a0;
        a1 = m.a1;
    }

    /** Lage entlang der Ebene. */
    double along(double x, double z) { return (x - origin[0]) * axis[0] + (z - origin[1]) * axis[1]; }

    /** Abstand von der Ebene. */
    double off(double x, double z) { return Math.abs((x - origin[0]) * normal[0] + (z - origin[1]) * normal[1]); }

    /** Die Ebene für alle Shader (Gelände, Meer … zeichnen davor nichts). */
    void cutUniforms(GL4 gl, Shader s) {
        boolean on = mode != Mode.OFF;
        gl.glUniform4f(s.loc(gl, "uCutPlane"), (float) normal[0], (float) normal[1], (float) dist, on ? 1f : 0f);
        double o = origin[0] * axis[0] + origin[1] * axis[1];
        gl.glUniform4f(s.loc(gl, "uCutAxis"), (float) axis[0], (float) axis[1], (float) (o + a0), (float) (o + a1));
        s.set(gl, "uCutWidth", (float) WIDTH);
    }

    private final float[] res = new float[8], resP = new float[2], dikes = new float[24], dikeI = new float[6], quakes = new float[96 * 4];

    /** Inhalt der Ebene setzen und zeichnen (Gelände-Texturen sind gebunden). */
    void draw(GL4 gl, Shader s, MagmaSystem ms, double window) {
        if (mode == Mode.OFF || vao == 0) return;
        List<Reservoir> rs = ms.reservoirs;
        for (int i = 0; i < 2; i++) {
            Reservoir r = rs.get(i);
            res[4 * i] = (float) along(r.x, r.z);
            res[4 * i + 1] = (float) r.y;
            res[4 * i + 2] = (float) r.rh;
            res[4 * i + 3] = (float) r.rv;
            resP[i] = (float) (r.pressure() / MagmaSystem.DIKE_START);
        }
        gl.glUniform4fv(s.loc(gl, "uRes"), 2, res, 0);
        gl.glUniform1fv(s.loc(gl, "uResP"), 2, resP, 0);
        gl.glUniform4f(s.loc(gl, "uLink"), res[4], res[5] + (float) ms.sc.rv * 0.8f, res[0], res[1] - (float) ms.hmm.rv * 0.8f);
        int n = 0;
        List<Dike> ds = ms.dikes();
        for (int k = ds.size() - 1; k >= 0 && n < 6; k--) {
            Dike d = ds.get(k);
            if (!matches(d.rift)) continue;
            // Phase 12: der Gang wurzelt im Südcaldera-Speicher (vorher klaffte eine Lücke von 1,5 km)
            double[] p0 = d.rift.at(0), p1 = d.tip();
            dikes[4 * n] = (float) along(p0[0], p0[1]);
            dikes[4 * n + 1] = (float) along(p1[0], p1[1]);
            dikes[4 * n + 2] = (float) d.top;
            dikes[4 * n + 3] = (float) d.bottom;
            dikeI[n] = d.active() ? 1f : 0.3f;
            n++;
        }
        gl.glUniform4fv(s.loc(gl, "uDike"), 6, dikes, 0);
        gl.glUniform1fv(s.loc(gl, "uDikeI"), 6, dikeI, 0);
        s.set(gl, "uDikeCount", n);
        int q = 0;
        List<Quake> qs = ms.quakesSince(ms.time() - window);
        for (int k = qs.size() - 1; k >= 0 && q < 96; k--) {
            Quake e = qs.get(k);
            if (off(e.x(), e.z()) > 6000) continue;
            double a = along(e.x(), e.z());
            if (a < a0 || a > a1) continue;
            quakes[4 * q] = (float) a;
            quakes[4 * q + 1] = (float) e.y();
            quakes[4 * q + 2] = (float) e.mag();
            quakes[4 * q + 3] = (float) (1 - (ms.time() - e.t()) / window);
            q++;
        }
        gl.glUniform4fv(s.loc(gl, "uQuake"), 96, quakes, 0);
        s.set(gl, "uQuakeCount", q);
        s.set(gl, "uDecol", -11_000f);
        gl.glBindVertexArray(vao);
        // Schnittebene
        face(gl, s, origin[0], origin[1], axis[0], axis[1], a0, a1, BOTTOM, TOP, normal[0], 0, normal[1], true);
        // Stirnseiten am Anfang und Ende des Abschnitts, zur Kamera hin
        double sx = origin[0] + axis[0] * a0, sz = origin[1] + axis[1] * a0;
        face(gl, s, sx, sz, normal[0], normal[1], 0, WIDTH, BOTTOM, TOP, axis[0], 0, axis[1], false);
        double ex = origin[0] + axis[0] * a1, ez = origin[1] + axis[1] * a1;
        face(gl, s, ex, ez, normal[0], normal[1], 0, WIDTH, BOTTOM, TOP, -axis[0], 0, -axis[1], false);
        // Boden des Blocks
        s.set(gl, "uFloor", 1);
        s.set(gl, "uFloorY", (float) BOTTOM);
        s.set(gl, "uFaceSide", (float) normal[0], (float) normal[1]);
        face(gl, s, origin[0], origin[1], axis[0], axis[1], a0, a1, 0, WIDTH, 0, 1, 0, false);
        s.set(gl, "uFloor", 0);
        gl.glBindVertexArray(0);
    }

    private void face(GL4 gl, Shader s, double ox, double oz, double dx, double dz, double h0, double h1, double y0, double y1,
                      double nx, double ny, double nz, boolean main) {
        s.set(gl, "uFaceOrigin", (float) ox, (float) oz);
        s.set(gl, "uFaceDir", (float) dx, (float) dz);
        gl.glUniform4f(s.loc(gl, "uFaceRange"), (float) h0, (float) h1, (float) y0, (float) y1);
        s.set(gl, "uFaceNormal", (float) nx, (float) ny, (float) nz);
        s.set(gl, "uMain", main ? 1 : 0);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
    }

    private boolean matches(Rift r) {
        return (mode == Mode.EAST && r == Rift.EAST) || (mode == Mode.SOUTHWEST && r == Rift.SOUTHWEST);
    }

    /** Ein guter Blick auf den Schnitt: Ziel (x, y, z), Blick (Grad) wie OrbitCamera, Neigung, Abstand. */
    double[] view(double cx, double cz) {
        double am = (a0 + a1) / 2;
        double tx = origin[0] + axis[0] * am, tz = origin[1] + axis[1] * am;
        // die Seite, von der aus die Riftzone nach rechts läuft (Gipfel links)
        double nx = -axis[1], nz = axis[0];
        double yaw = Math.toDegrees(Math.atan2(nx, nz));
        return new double[]{tx, -7000, tz, yaw, 3, Math.min(WIDTH * 0.92, (a1 - a0) * 0.5)};
    }

    void dispose(GL4 gl) {
        if (vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        vao = 0;
    }
}
