package com.dan.kilauea.render;

import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.dan.kilauea.flora.FloraField;
import com.dan.kilauea.flora.HawaiiSpecies;
import com.dan.kilauea.flora.MeshData;
import com.dan.kilauea.flora.TreeFern;
import com.dan.kilauea.terrain.HeightGrid;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/**
 * Bäume und Baumfarne auf der Grafikkarte: je Art, Variante und Detailstufe ein Netz (ʻōhiʻa aus
 * {@code com.dan.forest}: Stufe 1 mit Blattbüscheln bis rund 45 m, Stufe 2 mit Laubballen, Stufe 3 als
 * Umriss; hāpuʻu aus {@link TreeFern}), gezeichnet als Instanzen mit Lage, Drehung, Größe und Tönung.
 * Die Auswahl je Bild macht {@link FloraField} auf der CPU; Schatten werfen die Pflanzen in die zwei
 * nahen Kaskaden.
 */
final class FloraRenderer {
    final FloraField field = new FloraField();
    private final MeshData[] mesh = new MeshData[FloraField.BUCKETS];
    private final int[] vao = new int[FloraField.BUCKETS], vbo = new int[FloraField.BUCKETS], ibo = new int[FloraField.BUCKETS],
            inst = new int[FloraField.BUCKETS], tris = new int[FloraField.BUCKETS], drawn = new int[FloraField.BUCKETS];
    private FloatBuffer buf = Buffers.newDirectFloatBuffer(8 * 4096);
    private boolean ready;
    int triangles;

    void init(GL4 gl) {
        for (int v = 0; v < FloraField.OHIA_VARIANTS; v++) {
            TreeModel m = TreeGenerator.grow(HawaiiSpecies.ohia(), 1001 + v * 7919L, 1f);
            // Blüten nur in der feinsten Stufe (dort sind die Blattbüschel klein wie die Lehua-Blüten)
            for (int lod = 0; lod <= 3; lod++) mesh[FloraField.ohiaBucket(v, lod)] = MeshData.of(TreeMesh.build(m, lod), lod == 0 ? 0.035f : 0f, v * 13L + lod);
        }
        for (int v = 0; v < FloraField.FERN_VARIANTS; v++)
            for (int lod = 0; lod < 2; lod++) mesh[FloraField.fernBucket(v, lod)] = TreeFern.build(500 + v, lod);
        int[] t = new int[4];
        for (int b = 0; b < FloraField.BUCKETS; b++) {
            MeshData md = mesh[b];
            float[] inter = new float[md.vertices() * 9];
            for (int i = 0; i < md.vertices(); i++) {
                System.arraycopy(md.pos, 3 * i, inter, 9 * i, 3);
                System.arraycopy(md.nrm, 3 * i, inter, 9 * i + 3, 3);
                System.arraycopy(md.col, 3 * i, inter, 9 * i + 6, 3);
            }
            gl.glGenVertexArrays(1, t, 0);
            vao[b] = t[0];
            gl.glGenBuffers(3, t, 0);
            vbo[b] = t[0]; ibo[b] = t[1]; inst[b] = t[2];
            gl.glBindVertexArray(vao[b]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, inter.length * 4L, Buffers.newDirectFloatBuffer(inter), GL.GL_STATIC_DRAW);
            for (int a = 0; a < 3; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, 3, GL.GL_FLOAT, false, 36, a * 12L);
            }
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[b]);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, md.tri.length * 4L, Buffers.newDirectIntBuffer(md.tri), GL.GL_STATIC_DRAW);
            tris[b] = md.tri.length;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, 32 * 64, null, GL.GL_DYNAMIC_DRAW);
            for (int a = 0; a < 2; a++) {
                gl.glEnableVertexAttribArray(3 + a);
                gl.glVertexAttribPointer(3 + a, 4, GL.GL_FLOAT, false, 32, a * 16L);
                gl.glVertexAttribDivisor(3 + a, 1);
            }
        }
        // Schatten: nahe Bäume werfen den Schatten der Stufe 2, in der zweiten Kaskade den Umriss (Stufe 3)
        for (int v = 0; v < FloraField.OHIA_VARIANTS; v++)
            for (int src = 0; src <= 3; src++) {
                shadowVao[0][FloraField.ohiaBucket(v, src)] = pair(gl, 0, FloraField.ohiaBucket(v, Math.max(2, src)), FloraField.ohiaBucket(v, src));
                shadowVao[1][FloraField.ohiaBucket(v, src)] = pair(gl, 1, FloraField.ohiaBucket(v, 3), FloraField.ohiaBucket(v, src));
            }
        for (int v = 0; v < FloraField.FERN_VARIANTS; v++)
            for (int lod = 0; lod < 2; lod++) shadowVao[0][FloraField.fernBucket(v, lod)] = pair(gl, 0, FloraField.fernBucket(v, 1), FloraField.fernBucket(v, lod));
        gl.glBindVertexArray(0);
        ready = true;
    }

    /** Je Kaskade (0, 1) und Eimer ein VAO: Netz des einen Eimers, Instanzen eines anderen. */
    private final int[][] shadowVao = new int[2][FloraField.BUCKETS];
    private final int[][] shadowMesh = new int[2][FloraField.BUCKETS];

    private int pair(GL4 gl, int cascade, int meshBucket, int instBucket) {
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        gl.glBindVertexArray(t[0]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[meshBucket]);
        for (int a = 0; a < 3; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, 3, GL.GL_FLOAT, false, 36, a * 12L);
        }
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[meshBucket]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[instBucket]);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(3 + a);
            gl.glVertexAttribPointer(3 + a, 4, GL.GL_FLOAT, false, 32, a * 16L);
            gl.glVertexAttribDivisor(3 + a, 1);
        }
        shadowMesh[cascade][instBucket] = tris[meshBucket];
        return t[0];
    }

    /** Schatten der Pflanzen in Kaskade 0 oder 1 (vereinfachte Netze). */
    void drawShadow(GL4 gl, int cascade) {
        if (!ready) return;
        for (int b = 0; b < FloraField.BUCKETS; b++) {
            if (drawn[b] == 0 || shadowVao[cascade][b] == 0) continue;
            gl.glBindVertexArray(shadowVao[cascade][b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, shadowMesh[cascade][b], GL.GL_UNSIGNED_INT, 0, drawn[b]);
        }
        gl.glBindVertexArray(0);
    }

    /** Auswahl für das Bild und Hochladen der Instanzen. */
    void update(GL4 gl, HeightGrid g, double[] cam, float[] vp, int maxNewTiles) {
        if (!ready || g == null) return;
        field.update(g, cam, vp, maxNewTiles);
        triangles = 0;
        for (int b = 0; b < FloraField.BUCKETS; b++) {
            int n = field.count[b];
            drawn[b] = n;
            if (n == 0) continue;
            if (buf.capacity() < n * 8) buf = Buffers.newDirectFloatBuffer(Math.max(n * 8, buf.capacity() * 2));
            buf.clear();
            buf.put(field.bucket[b], 0, n * 8).flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, n * 32L, buf, GL4.GL_STREAM_DRAW);
            triangles += n * tris[b] / 3;
        }
    }

    /** Zeichnet alle Eimer; minLod: nur ʻōhiʻa-Stufen ab dieser (für ferne Schattenkaskaden), Farne nur bei minLod ≤ 1. */
    void draw(GL4 gl, int minLod) {
        if (!ready) return;
        for (int b = 0; b < FloraField.BUCKETS; b++) {
            if (drawn[b] == 0) continue;
            boolean fern = b >= FloraField.OHIA_VARIANTS * FloraField.OHIA_LODS;
            int lod = fern ? 1 : b / FloraField.OHIA_VARIANTS;
            if (lod < minLod || (fern && minLod > 1)) continue;
            gl.glBindVertexArray(vao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, tris[b], GL.GL_UNSIGNED_INT, 0, drawn[b]);
        }
        gl.glBindVertexArray(0);
    }

    void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteVertexArrays(FloraField.BUCKETS, vao, 0);
        gl.glDeleteBuffers(FloraField.BUCKETS, vbo, 0);
        gl.glDeleteBuffers(FloraField.BUCKETS, ibo, 0);
        gl.glDeleteBuffers(FloraField.BUCKETS, inst, 0);
        ready = false;
    }
}
