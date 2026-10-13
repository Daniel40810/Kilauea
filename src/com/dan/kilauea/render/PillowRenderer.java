package com.dan.kilauea.render;

import com.dan.kilauea.sea.Pillows;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/** Kissenlava als Instanzen einer Kugel (Phase 10); dazu die Blasen über {@link SteamRenderer}. */
final class PillowRenderer {
    static final int LON = 20, LAT = 12;
    final Pillows pillows = new Pillows();
    final SteamRenderer bubbles = new SteamRenderer(pillows);
    private int vao, vbo, ibo, inst, indexCount;
    private final FloatBuffer buf = Buffers.newDirectFloatBuffer(Pillows.F * Pillows.MAX);

    void init(GL4 gl) {
        float[] v = new float[(LON + 1) * (LAT + 1) * 3];
        int k = 0;
        for (int j = 0; j <= LAT; j++) {
            double th = Math.PI * j / LAT;
            for (int i = 0; i <= LON; i++) {
                double ph = 2 * Math.PI * i / LON;
                v[k++] = (float) (Math.sin(th) * Math.cos(ph));
                v[k++] = (float) Math.cos(th);
                v[k++] = (float) (Math.sin(th) * Math.sin(ph));
            }
        }
        int[] idx = new int[LON * LAT * 6];
        k = 0;
        for (int j = 0; j < LAT; j++)
            for (int i = 0; i < LON; i++) {
                int a = j * (LON + 1) + i, b = a + LON + 1;
                idx[k++] = a; idx[k++] = b; idx[k++] = a + 1;
                idx[k++] = a + 1; idx[k++] = b; idx[k++] = b + 1;
            }
        indexCount = idx.length;
        int[] t = new int[3];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(3, t, 0);
        vbo = t[0]; ibo = t[1]; inst = t[2];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 4L * Pillows.F * Pillows.MAX, null, GL4.GL_STREAM_DRAW);
        for (int a = 0; a < 3; a++) {
            gl.glEnableVertexAttribArray(1 + a);
            gl.glVertexAttribPointer(1 + a, 4, GL.GL_FLOAT, false, Pillows.F * 4, a * 16L);
            gl.glVertexAttribDivisor(1 + a, 1);
        }
        gl.glBindVertexArray(0);
        bubbles.init(gl);
    }

    void draw(GL4 gl) {
        int n = pillows.instCount;
        if (n == 0 || vao == 0) return;
        buf.clear();
        buf.put(pillows.inst, 0, n * Pillows.F).flip();
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) n * Pillows.F * 4, buf);
        gl.glBindVertexArray(vao);
        gl.glDrawElementsInstanced(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0, n);
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        bubbles.dispose(gl);
        if (vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(3, new int[]{vbo, ibo, inst}, 0);
        vao = 0;
    }
}
