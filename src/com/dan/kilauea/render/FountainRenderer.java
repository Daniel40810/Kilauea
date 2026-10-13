package com.dan.kilauea.render;

import com.dan.kilauea.lava.Fountain;
import com.dan.kilauea.lava.Sparks;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/** Glühende Teilchen (Fontäne, Glutbrocken, Lavafälle) als Instanzen, aufaddiert (Licht), ohne Tiefe zu schreiben. */
final class FountainRenderer {
    /** Die Fontäne (null, wenn der Zeichner für eine andere Quelle da ist). */
    final Fountain fountain;
    private final Sparks src;
    private final int max;
    private int vao, inst;
    private final FloatBuffer buf;

    FountainRenderer() { this(new Fountain(), Fountain.MAX + 64); }

    FountainRenderer(Sparks src, int max) {
        this.src = src;
        this.max = max;
        this.fountain = src instanceof Fountain f ? f : null;
        buf = Buffers.newDirectFloatBuffer(8 * max);
    }

    void init(GL4 gl) {
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(1, t, 0);
        inst = t[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 32L * max, null, GL4.GL_STREAM_DRAW);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, 4, GL.GL_FLOAT, false, 32, a * 16L);
            gl.glVertexAttribDivisor(a, 1);
        }
        gl.glBindVertexArray(0);
    }

    void upload(GL4 gl) {
        int n = Math.min(max, src.sparkCount());
        if (n == 0) return;
        buf.clear();
        buf.put(src.sparks(), 0, n * 8).flip();
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, n * 32L, buf);
    }

    void draw(GL4 gl) {
        int n = Math.min(max, src.sparkCount());
        if (n == 0) return;
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, n);
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        if (vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(1, new int[]{inst}, 0);
        vao = 0;
    }
}
