package com.dan.kilauea.render;

import com.dan.kilauea.terrain.LavaTube;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

/**
 * Das Innere der Lavaröhre Nāhuku: Ringe alle 0,5 m mit 28 Punkten, Querschnitt leicht oval, der Boden
 * flach (der Weg), die Wände unruhig (Bänke, Nasen, Tropfglasur). Beleuchtet von Lampen am Weg alle
 * 9 m und vom Tageslicht, das durch die beiden Öffnungen fällt. Das Gelände bekommt an den Öffnungen
 * ein Loch ({@link #holes}), damit man hinein- und hinaussehen kann.
 */
final class TubeRenderer {
    private int vao, vbo, ibo, count;
    final float[] lamps = new float[24 * 4];
    int lampCount;
    /** Löcher im Gelände: je Öffnung (x, z der Achse innen, x, z außen) und (Boden, Decke, Halbmesser, –). */
    final float[] holeA = new float[8], holeB = new float[8];
    float[] mouth = new float[6];

    void init(GL4 gl) {
        LavaTube t = LavaTube.get();
        if (t == null) return;
        int K = 28;
        double s0 = t.roofFrom, s1 = t.roofTo;
        int rings = (int) Math.ceil((s1 - s0) / 0.5) + 1;
        float[] v = new float[rings * K * 8];
        int q = 0;
        for (int r = 0; r < rings; r++) {
            double s = Math.min(s1, s0 + r * 0.5);
            int i = Math.min(t.n - 2, (int) s);
            double f = s - i;
            double ax = t.x[i] + (t.x[i + 1] - t.x[i]) * f, az = t.z[i] + (t.z[i + 1] - t.z[i]) * f;
            double tx = t.tx[i], tz = t.tz[i];
            double rx = -tz, rz = tx;
            double fl = t.floorAt(s), rad = t.radiusAt(s);
            double cy = fl + rad * 0.82;
            for (int k = 0; k < K; k++) {
                double th = -Math.PI / 2 + 2 * Math.PI * k / K;
                // Rauschen über (s, Kreis): rundum stetig, keine Streifen
                double ca = Math.cos(th), sa = Math.sin(th);
                // Beulen etwa so lang wie breit: in der Länge (s) dichter abgetastet als früher, sonst liefen
                // sie als Rippen die Röhre entlang und standen im Bild als Strahlen zum Ausgang
                double bump = 0.35 * (com.dan.kilauea.terrain.Noise.fbm(s * 0.55 + ca * 1.6 + 3, sa * 1.6 + s * 0.15, 3) - 0.5) * 2
                        + 0.18 * (com.dan.kilauea.terrain.Noise.value(s * 1.4 + ca * 4, sa * 4 + 9) - 0.5);
                double rr = rad * (1 + bump * 0.18);
                double lx = Math.cos(th) * rr, ly = Math.sin(th) * rr * 0.95;
                double y = cy + ly;
                boolean floorPt = y < fl + 0.02;
                if (floorPt) y = fl;
                v[q++] = (float) (ax + rx * lx);
                v[q++] = (float) y;
                v[q++] = (float) (az + rz * lx);
                v[q++] = 0; v[q++] = 0; v[q++] = 0;      // Normale, unten gerechnet
                v[q++] = (float) s;
                v[q++] = floorPt ? -1 : (float) (k / (double) K);
            }
        }
        int[] idx = new int[(rings - 1) * K * 6];
        int c = 0;
        for (int r = 0; r < rings - 1; r++)
            for (int k = 0; k < K; k++) {
                int a = r * K + k, b = r * K + (k + 1) % K, d = a + K, e = b + K;
                idx[c++] = a; idx[c++] = b; idx[c++] = d;
                idx[c++] = b; idx[c++] = e; idx[c++] = d;
            }
        // Normalen aus den Dreiecken, nach innen gedreht
        float[] nacc = new float[rings * K * 3];
        for (int p = 0; p < idx.length; p += 3) {
            int a = idx[p], b = idx[p + 1], d = idx[p + 2];
            float ux = v[b * 8] - v[a * 8], uy = v[b * 8 + 1] - v[a * 8 + 1], uz = v[b * 8 + 2] - v[a * 8 + 2];
            float wx = v[d * 8] - v[a * 8], wy = v[d * 8 + 1] - v[a * 8 + 1], wz = v[d * 8 + 2] - v[a * 8 + 2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            for (int o : new int[]{a, b, d}) { nacc[o * 3] += nx; nacc[o * 3 + 1] += ny; nacc[o * 3 + 2] += nz; }
        }
        for (int r = 0; r < rings; r++) {
            double s = Math.min(s1, s0 + r * 0.5);
            int i = Math.min(t.n - 2, (int) s);
            double f = s - i;
            double ax = t.x[i] + (t.x[i + 1] - t.x[i]) * f, az = t.z[i] + (t.z[i + 1] - t.z[i]) * f;
            double cy = t.floorAt(s) + t.radiusAt(s) * 0.82;
            for (int k = 0; k < K; k++) {
                int o = r * K + k;
                float nx = nacc[o * 3], ny = nacc[o * 3 + 1], nz = nacc[o * 3 + 2];
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
                nx /= l; ny /= l; nz /= l;
                double toC = (ax - v[o * 8]) * nx + (cy - v[o * 8 + 1]) * ny + (az - v[o * 8 + 2]) * nz;
                if (toC < 0) { nx = -nx; ny = -ny; nz = -nz; }
                v[o * 8 + 3] = nx; v[o * 8 + 4] = ny; v[o * 8 + 5] = nz;
            }
        }
        int[] tmp = new int[2];
        gl.glGenVertexArrays(1, tmp, 0);
        vao = tmp[0];
        gl.glGenBuffers(2, tmp, 0);
        vbo = tmp[0];
        ibo = tmp[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 32, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 32, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, 32, 24);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        count = idx.length;
        // Lampen am Weg, abwechselnd links und rechts, knapp über dem Boden
        lampCount = 0;
        int side = 1;
        for (double s = s0 + 5; s < s1 - 3 && lampCount < 24; s += 9, side = -side) {
            int i = Math.min(t.n - 2, (int) s);
            double rad = t.radiusAt(s) * 0.62 * side;
            lamps[lampCount * 4] = (float) (t.x[i] + -t.tz[i] * rad);
            lamps[lampCount * 4 + 1] = (float) (t.floorAt(s) + 0.45);
            lamps[lampCount * 4 + 2] = (float) (t.z[i] + t.tx[i] * rad);
            lamps[lampCount * 4 + 3] = 1;
            lampCount++;
        }
        hole(t, s0, -1, holeA);
        hole(t, s1, 1, holeB);
        int i0 = (int) s0, i1 = Math.min(t.n - 1, (int) s1);
        mouth = new float[]{(float) t.x[i0], (float) (t.floorAt(s0) + 1.5), (float) t.z[i0], (float) t.x[i1], (float) (t.floorAt(s1) + 1.5), (float) t.z[i1]};
    }

    /** Loch im Gelände an einer Öffnung: Achse von 6 m innen bis 1,5 m außen. */
    private static void hole(LavaTube t, double s, int dir, float[] out) {
        double sIn = Math.max(0, Math.min(t.n - 1, s - dir * 6)), sOut = Math.max(0, Math.min(t.n - 1, s + dir * 1.5));
        int a = (int) sIn, b = (int) sOut;
        out[0] = (float) t.x[a]; out[1] = (float) t.z[a]; out[2] = (float) t.x[b]; out[3] = (float) t.z[b];
        out[4] = (float) t.floorAt(s);
        out[5] = (float) (t.floorAt(s) + t.radiusAt(s) * 1.75);
        out[6] = (float) (t.radiusAt(s) * 1.05);
        out[7] = 0;
    }

    boolean ready() { return count > 0; }

    void draw(GL4 gl, Shader s) {
        if (count == 0) return;
        gl.glUniform4fv(s.loc(gl, "uLamp"), 24, lamps, 0);
        s.set(gl, "uLampCount", lampCount);
        s.set(gl, "uMouthA", mouth[0], mouth[1], mouth[2]);
        s.set(gl, "uMouthB", mouth[3], mouth[4], mouth[5]);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        if (vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        vao = 0;
        count = 0;
    }
}
