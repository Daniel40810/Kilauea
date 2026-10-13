package com.dan.kilauea.render;

import com.dan.kilauea.terrain.SeaArch;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

/** Feste Felsnetze: der Felsbogen Hōlei (vor oder nach dem Einsturz im September 2026). */
final class RockRenderer {
    private int vao, vbo, ibo, count;
    private Boolean built;
    /** Felsbogen vor dem Einsturz zeigen (sonst Stand heute). */
    volatile boolean intact;

    void update(GL4 gl) {
        if (built != null && built == intact) return;
        dispose(gl);
        SeaArch a = SeaArch.build(intact);
        int[] t = new int[2];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ibo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, a.vertices.length * 4L, Buffers.newDirectFloatBuffer(a.vertices), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0L);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12L);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, a.indices.length * 4L, Buffers.newDirectIntBuffer(a.indices), GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        count = a.indices.length;
        built = intact;
    }

    void draw(GL4 gl) {
        if (count == 0) return;
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
        built = null;
    }
}
