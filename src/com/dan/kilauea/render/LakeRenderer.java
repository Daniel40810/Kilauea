package com.dan.kilauea.render;

import com.dan.kilauea.lava.LakeImage;
import com.dan.kilauea.lava.LavaLake;
import com.dan.kilauea.lava.LavaSim;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Der Spiegel des Lavasees (Phase 8): eine Scheibe in der Grube, Ringe um die Mitte bis über das Ufer
 * hinaus (die Wand der Grube verdeckt, was darüber hinausragt). Das Bild der Platten und Nähte kommt aus
 * dem Lava-Thread ({@link LakeImage}) und wird hochgeladen, wenn es neu ist.
 */
final class LakeRenderer {
    private int vao, vbo, ibo, count, tex, version = -1;
    private final ByteBuffer pix = Buffers.newDirectByteBuffer(LakeImage.N * LakeImage.N * 4);
    private final float[] spatter = new float[16 * 4];
    private int spatterN;
    private double texX0, texZ0, texSize;

    void init(GL4 gl, LavaLake L) {
        int rings = 24, seg = 128;
        FloatBuffer v = Buffers.newDirectFloatBuffer((1 + rings * seg) * 2);
        v.put(0).put(0);
        for (int r = 1; r <= rings; r++)
            for (int k = 0; k < seg; k++) {
                double t = 2 * Math.PI * k / seg;
                double wob = 1 + 0.05 * Math.sin(3 * t + 1.1) + 0.03 * Math.sin(7 * t + 2.3);
                double f = 1.08 * r / rings * wob;
                v.put((float) (L.a * f * Math.cos(t))).put((float) (L.b * f * Math.sin(t)));
            }
        v.flip();
        java.nio.IntBuffer ix = Buffers.newDirectIntBuffer(seg * 3 + (rings - 1) * seg * 6);
        for (int k = 0; k < seg; k++) ix.put(0).put(1 + k).put(1 + (k + 1) % seg);
        for (int r = 1; r < rings; r++)
            for (int k = 0; k < seg; k++) {
                int a0 = 1 + (r - 1) * seg + k, a1 = 1 + (r - 1) * seg + (k + 1) % seg;
                int b0 = 1 + r * seg + k, b1 = 1 + r * seg + (k + 1) % seg;
                ix.put(a0).put(b0).put(b1).put(a0).put(b1).put(a1);
            }
        ix.flip();
        count = ix.remaining();
        int[] t = new int[3];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ibo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.remaining() * 4L, v, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 8, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, count * 4L, ix, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        gl.glGenTextures(1, t, 0);
        tex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, LakeImage.N, LakeImage.N, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    boolean ready() { return vao != 0; }

    /** Bild hochladen, wenn es neu ist. */
    void update(GL4 gl, LavaSim sim) {
        LakeImage im = sim.lakeImage;
        if (im.version == version) return;
        synchronized (im) {
            pix.clear();
            pix.put(im.rgba).flip();
            texX0 = im.x0;
            texZ0 = im.z0;
            texSize = im.size;
            System.arraycopy(im.spatter, 0, spatter, 0, spatter.length);
            spatterN = im.spatterCount;
            version = im.version;
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, LakeImage.N, LakeImage.N, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pix);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    void draw(GL4 gl, Shader s, LavaLake L) {
        gl.glActiveTexture(GL.GL_TEXTURE15);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uLakeTex", 15);
        gl.glUniform4f(s.loc(gl, "uLakeTexRect"), (float) texX0, (float) texZ0, (float) texSize, 0f);
        gl.glUniform4f(s.loc(gl, "uLakeGeoV"), (float) L.cx, (float) L.cz, (float) Math.cos(L.rot), (float) Math.sin(L.rot));
        s.set(gl, "uLakeY", (float) L.surfaceY());
        gl.glUniform4fv(s.loc(gl, "uSpatter"), 16, spatter, 0);
        gl.glUniform1i(s.loc(gl, "uSpatterN"), spatterN);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    /** Phase 14, Wärmebild: Bild des Sees und Spiegel binden (ohne zu zeichnen). */
    void bindForThermal(GL4 gl, Shader s, LavaLake L) {
        gl.glActiveTexture(GL.GL_TEXTURE15);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uLakeTex", 15);
        gl.glUniform4f(s.loc(gl, "uLakeTexRect"), (float) texX0, (float) texZ0, (float) texSize, 0f);
        s.set(gl, "uLakeY", (float) L.surfaceY());
    }

    void dispose(GL4 gl) {
        if (vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        vao = 0;
    }
}
