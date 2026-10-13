package com.dan.kilauea.render;

import com.dan.ground.Batch;
import com.dan.ground.Meadow;
import com.dan.kilauea.flora.HawaiiGround;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;

/**
 * Bodendecke um den Betrachter aus {@code com.dan.ground} ({@link Meadow}): nur zu Fuß und knapp über
 * dem Boden, im Umkreis von 24 m. Die Halme bewegt {@code Meadow} auf der CPU im Wind.
 * <p>
 * Tempo (Statusbild Phase 4: im Wald bremste die Bodendecke die CPU): Die Halme werden nur jedes
 * zweite Bild neu bewegt, solange man steht oder langsam geht; die Ecken liegen nicht verschränkt im
 * Puffer, sondern als Blöcke (Lage, Normale, Farbe, fest+Saum), damit sie am Stück kopiert werden.
 */
public final class GroundCoverRenderer {
    final Meadow meadow = new Meadow(HawaiiGround.biome(), HawaiiGround.site());
    private int vao, vbo, ibo, count;
    private FloatBuffer vb = Buffers.newDirectFloatBuffer(11 * 4096);
    private IntBuffer ib = Buffers.newDirectIntBuffer(3 * 4096);
    private float[] sf = new float[2 * 4096];
    int triangles;
    private int tick;
    private double lastX = Double.NaN, lastZ;

    GroundCoverRenderer() {
        meadow.radius = 24;
        meadow.near = 6.5f;
    }

    void init(GL4 gl) {
        int[] t = new int[2];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ibo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 44 * 64, null, GL4.GL_STREAM_DRAW);
        for (int a = 0; a < 5; a++) gl.glEnableVertexAttribArray(a);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBindVertexArray(0);
    }

    /** Bodendecke um (x, z) zur Zeit t bewegen und hochladen; aus, wenn off. */
    void update(GL4 gl, float t, double x, double z, int day, boolean off) {
        if (off || vao == 0) {
            count = 0;
            triangles = 0;
            lastX = Double.NaN;
            return;
        }
        tick++;
        boolean moved = Double.isNaN(lastX) || Math.hypot(x - lastX, z - lastZ) > 0.6;
        if (!moved && (tick & 1) == 1 && count > 0) return;   // Wind: jedes zweite Bild genügt
        lastX = x;
        lastZ = z;
        meadow.setSeason(day, 0);
        meadow.update(t, x, z);
        Batch b = meadow.batch;
        if (b.nt == 0) { count = 0; triangles = 0; return; }
        int nv = b.nv;
        if (vb.capacity() < nv * 11) vb = Buffers.newDirectFloatBuffer(nv * 11 * 3 / 2);
        if (ib.capacity() < b.nt * 3) ib = Buffers.newDirectIntBuffer(b.nt * 3 * 3 / 2);
        if (sf.length < 2 * nv) sf = new float[2 * nv * 3 / 2];
        final float[] f = sf;
        IntStream.range(0, (nv + 4095) / 4096).parallel().forEach(c -> {
            int a = c * 4096, e = Math.min(nv, a + 4096);
            for (int i = a; i < e; i++) { f[2 * i] = b.solid[i]; f[2 * i + 1] = b.fade[i]; }
        });
        vb.clear();
        vb.put(0, b.xyz, 0, 3 * nv).put(3 * nv, b.nrm, 0, 3 * nv).put(6 * nv, b.rgb, 0, 3 * nv).put(9 * nv, f, 0, 2 * nv);
        vb.limit(11 * nv);
        ib.clear();
        ib.put(0, b.tri, 0, b.nt * 3);
        ib.limit(b.nt * 3);
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) nv * 44, vb, GL4.GL_STREAM_DRAW);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0L);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 12, 12L * nv);
        gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, 12, 24L * nv);
        gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, 8, 36L * nv);
        gl.glVertexAttribPointer(4, 1, GL.GL_FLOAT, false, 8, 36L * nv + 4);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) b.nt * 12, ib, GL4.GL_STREAM_DRAW);
        gl.glBindVertexArray(0);
        count = b.nt * 3;
        triangles = b.nt;
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
    }
}
