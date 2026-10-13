package com.dan.kilauea.render;

import com.dan.kilauea.life.Life;
import com.dan.kilauea.life.LifeMesh;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/**
 * Die Figuren von {@link Life} auf der Grafikkarte (Phase 15): je Art ein kleines Netz mit Teilen und
 * Drehpunkten, gezeichnet als Instanzen (16 Zahlen je Figur). Schatten in die zwei nahen Kaskaden, im
 * Wärmebild die Temperatur der Körper, Motoren und Abgase.
 */
final class LifeRenderer {
    private final int[] vao = new int[Life.KINDS], vbo = new int[Life.KINDS], ibo = new int[Life.KINDS], inst = new int[Life.KINDS],
            tris = new int[Life.KINDS], drawn = new int[Life.KINDS];
    private FloatBuffer buf = Buffers.newDirectFloatBuffer(Life.F * 256);
    private boolean ready;
    int figures, triangles;
    /** Phase 15: die acht nächsten Autos mit Licht (Lampenmitte, Stärke; Richtung) für cars.glsl. */
    final float[] carLight = new float[8 * 4], carDir = new float[8 * 4];
    int carLights;
    private final double[] carDist = new double[8];
    /** Zwei Lampen zusammen rund 40 000 cd, in Sonnen-Einheiten (1 ≈ 5 000 lx) [A]. */
    static final float HEADLIGHT = 40_000f / 5_000f;

    /** Wärme je Art über der Luft (K) [G]: Gefieder leicht, Menschen mehr, Motoren am meisten. */
    static final float[] WARM = {9, 9, 11, 11, 11, 11, 8, 8, 28, 12, 12, 12, 12, 22, 22, 22};

    void init(GL4 gl) {
        int[] t = new int[4];
        for (int k = 0; k < Life.KINDS; k++) {
            LifeMesh m = Life.mesh(k);
            int nv = m.vertices();
            float[] inter = new float[nv * 13];
            for (int i = 0; i < nv; i++) {
                System.arraycopy(m.pos, 3 * i, inter, 13 * i, 3);
                System.arraycopy(m.nrm, 3 * i, inter, 13 * i + 3, 3);
                System.arraycopy(m.col, 3 * i, inter, 13 * i + 6, 3);
                inter[13 * i + 9] = m.part[i];
                System.arraycopy(m.pivot, 3 * i, inter, 13 * i + 10, 3);
            }
            gl.glGenVertexArrays(1, t, 0);
            vao[k] = t[0];
            gl.glGenBuffers(3, t, 0);
            vbo[k] = t[0]; ibo[k] = t[1]; inst[k] = t[2];
            gl.glBindVertexArray(vao[k]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[k]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, inter.length * 4L, Buffers.newDirectFloatBuffer(inter), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 52, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 52, 12);
            gl.glEnableVertexAttribArray(2);
            gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, 52, 24);
            gl.glEnableVertexAttribArray(3);
            gl.glVertexAttribPointer(3, 3, GL.GL_FLOAT, false, 52, 40);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[k]);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, m.tri.length * 4L, Buffers.newDirectIntBuffer(m.tri), GL.GL_STATIC_DRAW);
            tris[k] = m.tri.length;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[k]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, Life.F * 4L * 32, null, GL.GL_DYNAMIC_DRAW);
            for (int a = 0; a < 4; a++) {
                gl.glEnableVertexAttribArray(4 + a);
                gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, Life.F * 4, a * 16L);
                gl.glVertexAttribDivisor(4 + a, 1);
            }
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
    }

    /** Die Figuren dieses Bildes hochladen (unter der Sperre von Life). */
    void update(GL4 gl, Life life, double cx, double cy, double cz) {
        figures = triangles = 0;
        carLights = 0;
        if (!ready) return;
        synchronized (life) {
            for (int k = Life.K_SEDAN; k <= Life.K_VAN; k++) {
                float[] a = life.inst[k];
                for (int i = 0; i < life.count[k]; i++) {
                    int q = i * Life.F;
                    if (a[q + 15] < 0.5f) continue;
                    double fx = -Math.sin(a[q + 3]), fz = Math.cos(a[q + 3]);
                    double lx = a[q] + fx * 2.4, ly = a[q + 1] + 0.75, lz = a[q + 2] + fz * 2.4;
                    double d = Math.hypot(lx - cx, lz - cz);
                    if (d > 700) continue;
                    int slot = carLights < 8 ? carLights++ : -1;
                    if (slot < 0) {
                        int far = 0;
                        for (int j = 1; j < 8; j++) if (carDist[j] > carDist[far]) far = j;
                        if (carDist[far] <= d) continue;
                        slot = far;
                    }
                    carDist[slot] = d;
                    carLight[4 * slot] = (float) lx; carLight[4 * slot + 1] = (float) ly; carLight[4 * slot + 2] = (float) lz; carLight[4 * slot + 3] = HEADLIGHT;
                    carDir[4 * slot] = (float) fx; carDir[4 * slot + 1] = (float) fz;
                }
            }
            for (int k = 0; k < Life.KINDS; k++) {
                int n = life.count[k];
                drawn[k] = n;
                if (n == 0) continue;
                if (buf.capacity() < n * Life.F) buf = Buffers.newDirectFloatBuffer(Math.max(n * Life.F, buf.capacity() * 2));
                buf.clear();
                buf.put(life.inst[k], 0, n * Life.F).flip();
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[k]);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) n * Life.F * 4, buf, GL4.GL_STREAM_DRAW);
                figures += n;
                triangles += n * tris[k] / 3;
            }
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    boolean any() { return figures > 0; }

    /** Alle Figuren mit dem gebundenen Shader zeichnen; im Wärmebild je Art die Wärme setzen. */
    void draw(GL4 gl, Shader s, boolean thermal) {
        if (!ready) return;
        for (int k = 0; k < Life.KINDS; k++) {
            if (drawn[k] == 0) continue;
            if (thermal) s.set(gl, "uWarm", WARM[k]);
            gl.glBindVertexArray(vao[k]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, tris[k], GL.GL_UNSIGNED_INT, 0, drawn[k]);
        }
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteVertexArrays(Life.KINDS, vao, 0);
        gl.glDeleteBuffers(Life.KINDS, vbo, 0);
        gl.glDeleteBuffers(Life.KINDS, ibo, 0);
        gl.glDeleteBuffers(Life.KINDS, inst, 0);
        ready = false;
    }
}
