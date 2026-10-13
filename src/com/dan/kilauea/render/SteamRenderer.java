package com.dan.kilauea.render;

import com.dan.kilauea.atmos.Puffs;
import com.dan.kilauea.atmos.Steam;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/** Weiche Teilchen als Billboards, durchsichtig, von hinten nach vorn (die Quelle sortiert): Dampf, Gischt. */
final class SteamRenderer {
    /** Dampf und Gasfahnen (null, wenn der Zeichner für eine andere Quelle da ist). */
    final Steam steam;
    private final Puffs src;

    SteamRenderer() { this(new Steam()); }

    SteamRenderer(Puffs src) {
        this.src = src;
        this.steam = src instanceof Steam s ? s : null;
    }
    private int vao, inst;
    private FloatBuffer buf = Buffers.newDirectFloatBuffer(8 * 2048);

    void init(GL4 gl) {
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(1, t, 0);
        inst = t[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 32 * 64, null, GL4.GL_STREAM_DRAW);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, 4, GL.GL_FLOAT, false, 32, a * 16L);
            gl.glVertexAttribDivisor(a, 1);
        }
        gl.glBindVertexArray(0);
    }

    void upload(GL4 gl) {
        int n = src.puffCount();
        if (n == 0) return;
        if (buf.capacity() < n * 8) buf = Buffers.newDirectFloatBuffer(n * 8 * 2);
        buf.clear();
        buf.put(src.puffs(), 0, n * 8).flip();
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, n * 32L, buf, GL4.GL_STREAM_DRAW);
    }

    void draw(GL4 gl) {
        if (src.puffCount() == 0) return;
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, src.puffCount());
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        if (vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(1, new int[]{inst}, 0);
        vao = 0;
    }
}
