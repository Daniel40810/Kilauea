package com.dan.kilauea.render;

import com.dan.kilauea.terrain.ParkRoads;
import com.dan.road.Network;
import com.dan.road.Way;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

/**
 * Die Parkstraßen als Band auf dem Gelände: je Abtastpunkt der Achse (alle 2 m) sieben Punkte quer
 * (Bankett, Fahrbahnrand, Fahrbahn, Mitte). Die Höhe rechnet der Vertex-Shader mit derselben Funktion
 * wie das Gelände, darum liegt die Straße in jeder Entfernung auf dem Boden. Markierung und Belag macht
 * {@code road.frag} aus der Querlage.
 */
final class RoadRenderer {
    private int vao, vbo, ibo, count;
    float paved, half;
    float[] surface, worn, verge, white, yellow;

    /** 0 Straße (mit Markierung), 1 Pfad, 2 befestigter Weg. */
    final int kind;

    RoadRenderer(int kind) { this.kind = kind; }

    void init(GL4 gl) {
        Network net = kind == 0 ? ParkRoads.network() : ParkRoads.trails(kind == 1 ? 0 : 1);
        if (net == null || net.ways.isEmpty()) return;
        var t = net.ways.get(0).type;
        paved = t.paved();
        half = t.half();
        surface = t.surface; worn = t.worn; verge = t.vergeColor; white = t.lineWhite; yellow = t.lineYellow;
        float[] us = {-half, -paved, -0.55f * paved, 0, 0.55f * paved, paved, half};
        int cols = us.length;
        int nv = 0, ni = 0;
        for (Way w : net.ways) { nv += w.n * cols; ni += (w.n - 1) * (cols - 1) * 6; }
        float[] v = new float[nv * 4];
        int[] idx = new int[ni];
        int k = 0, q = 0, base = 0;
        for (Way w : net.ways) {
            for (int i = 0; i < w.n; i++) {
                float rx = -w.tz[i], rz = w.tx[i];
                for (float u : us) {
                    v[k++] = w.x[i] + rx * u;
                    v[k++] = w.z[i] + rz * u;
                    v[k++] = u;
                    v[k++] = i * w.step();
                }
            }
            for (int i = 0; i < w.n - 1; i++)
                for (int c = 0; c < cols - 1; c++) {
                    int a = base + i * cols + c, b = a + 1, d = a + cols, e = d + 1;
                    idx[q++] = a; idx[q++] = d; idx[q++] = b;
                    idx[q++] = b; idx[q++] = d; idx[q++] = e;
                }
            base += w.n * cols;
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
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, 16, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        count = idx.length;
    }

    boolean ready() { return count > 0; }

    void draw(GL4 gl, Shader s) {
        if (count == 0) return;
        s.set(gl, "uKind", kind);
        s.set(gl, "uPaved", paved);
        s.set(gl, "uHalf", half);
        s.set(gl, "uAsphalt", surface);
        s.set(gl, "uWorn", worn);
        s.set(gl, "uVerge", verge);
        s.set(gl, "uLineWhite", white);
        s.set(gl, "uLineYellow", yellow);
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
