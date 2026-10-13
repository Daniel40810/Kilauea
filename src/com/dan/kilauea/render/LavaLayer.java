package com.dan.kilauea.render;

import com.dan.kilauea.lava.Glow;
import com.dan.kilauea.lava.GlowMap;
import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaLake;
import com.dan.kilauea.lava.LavaSim;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Bringt das Lava-Modell auf die Grafikkarte (Phase 8): eine Tabelle (je Block aus 32 × 32 Zellen seine
 * Lage im Atlas oder −1) und drei Atlanten mit den Werten der Zellen ({@link LavaField#exportBlock}), dazu
 * die Glutkarte ({@link GlowMap}). Hochgeladen wird höchstens zehnmal je Sekunde und je Runde höchstens
 * {@link #PER_ROUND} geänderte Blöcke, reihum; erstarrtes Gestein wird alle fünf Sekunden aufgefrischt (es
 * kühlt weiter aus). Gelesen wird in lava.glsl; {@link #bind} setzt Einheiten 10 bis 14 und die Uniforms.
 */
final class LavaLayer {
    static final int ROW = 32, PER_ROUND = 96;
    private int tableTex, aTex, cTex, vTex, glowTex;
    private int side, rows;
    private LavaField field;
    private int tableVersion = -1, glowVersion = -1, rr;
    private long lastRound, lastAll;
    private final float[] a = new float[4 * LavaField.BB], c = new float[4 * LavaField.BB], v = new float[2 * LavaField.BB];
    private final FloatBuffer fa = Buffers.newDirectFloatBuffer(4 * LavaField.BB), fc = Buffers.newDirectFloatBuffer(4 * LavaField.BB),
            fv = Buffers.newDirectFloatBuffer(2 * LavaField.BB);
    private FloatBuffer glowBuf = Buffers.newDirectFloatBuffer(GlowMap.G * GlowMap.G * 3);
    private final float[] box = {1, 1, -1, -1}, glowRect = new float[4];
    private static final float[] LUT = Glow.table();
    /** Lava im Bild zeigen (aus: Gelände wie ohne Lava). */
    volatile boolean show = true;
    private double exposure = 1, time;
    private LavaSim sim;
    int uploads;

    // ------------------------------------------------------------ Phase 12: Verformung des Geländes (Einheit 17)
    static final int DEFORM_UNIT = 17;
    private int deformTex;
    private float[] deformData;

    /** Raster der Verformung hochladen, sobald es gebaut ist (es ändert sich danach nicht mehr, nur F). */
    void updateDeform(GL4 gl) {
        float[] d = com.dan.kilauea.terrain.Deformation.delta(), r = com.dan.kilauea.terrain.Deformation.ring();
        if (d == null || d == deformData) return;
        int n = com.dan.kilauea.terrain.Deformation.N;
        FloatBuffer b = Buffers.newDirectFloatBuffer(2 * n * n);
        for (int k = 0; k < n * n; k++) { b.put(d[k]); b.put(r[k]); }
        b.flip();
        if (deformTex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            deformTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, deformTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RG32F, n, n);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        } else gl.glBindTexture(GL.GL_TEXTURE_2D, deformTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RG, GL.GL_FLOAT, b);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        deformData = d;
    }

    /** Uniforms der Verformung (auch für Shader ohne Lava: Straßen, Schnitt). */
    void bindDeform(GL4 gl, Shader s) {
        boolean on = deformTex != 0 && com.dan.kilauea.terrain.Deformation.active();
        int lr = s.loc(gl, "uDeformRect");
        if (lr < 0) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + DEFORM_UNIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, deformTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uDeform", DEFORM_UNIT);
        if (on) {
            gl.glUniform4f(lr, (float) com.dan.kilauea.terrain.Deformation.originX(), (float) com.dan.kilauea.terrain.Deformation.originZ(),
                    (float) com.dan.kilauea.terrain.Deformation.SIDE, 1f);
            s.set(gl, "uDeformF", (float) com.dan.kilauea.terrain.Deformation.progress());
        } else gl.glUniform4f(lr, 0, 0, 0, 0);
    }

    /** Einmal je Bild vor dem Zeichnen: neue Werte hochladen. */
    void update(GL4 gl, LavaSim sim, double exposure, double time) {
        updateDeform(gl);
        this.sim = sim;
        this.exposure = exposure;
        this.time = time;
        uploads = 0;
        LavaField f = sim.field();
        if (f == null) {
            // nur der Lavasee: eine eigene Glutkarte, damit er die Umgebung anleuchtet (Phase 11)
            field = null;
            if (soloGlow == 0) soloGlow = glowTexture(gl);
            glow(gl);
            return;
        }
        if (f != field || aTex == 0) create(gl, f);
        long now = System.nanoTime();
        if (now - lastRound < 100_000_000L) { glow(gl); return; }
        lastRound = now;
        // Phase 13: unter der Sperre nur auslesen, hochladen danach (vorher wartete das Hochladen auf den Lava-Faden
        // und der Lava-Faden auf das Hochladen); alles neu alle 5 s, aber verteilt auf die folgenden Runden
        int cnt = 0;
        synchronized (f) {
            if (f.tableVersion() != tableVersion) table(gl, f);
            int n = f.blocksUsed();
            if (forced.length < f.pool()) forced = new boolean[f.pool()];
            if (now - lastAll > 5_000_000_000L) {
                lastAll = now;
                for (int b = 0; b < n; b++) forced[b] = true;
            }
            for (int k = 0; k < n && cnt < PER_ROUND; k++) {
                int b = (rr + k) % n;
                boolean d = f.takeDirty(b);
                if (!d && !forced[b]) continue;
                forced[b] = false;
                f.exportBlock(b, stA[cnt], stC[cnt], stV[cnt]);
                stB[cnt++] = b;
            }
            rr = (rr + PER_ROUND) % Math.max(1, n);
        }
        for (int k = 0; k < cnt; k++) uploadStaged(gl, stB[k], stA[k], stC[k], stV[k]);
        glow(gl);
    }

    private void create(GL4 gl, LavaField f) {
        dispose(gl);
        field = f;
        side = f.side();
        rows = (f.pool() + ROW - 1) / ROW;
        int[] t = new int[5];
        gl.glGenTextures(5, t, 0);
        tableTex = t[0];
        aTex = t[1];
        cTex = t[2];
        vTex = t[3];
        glowTex = t[4];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tableTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32I, side, side, 0, GL4.GL_RED_INTEGER, GL4.GL_INT, null);
        nearest(gl);
        for (int tex : new int[]{aTex, cTex}) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RGBA32F, ROW * LavaField.B, rows * LavaField.B, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
            nearest(gl);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, vTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG16F, ROW * LavaField.B, rows * LavaField.B, 0, GL4.GL_RG, GL.GL_FLOAT, null);
        nearest(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, glowTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RGB16F, GlowMap.G, GlowMap.G, 0, GL.GL_RGB, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        tableVersion = -1;
        glowVersion = -1;
        lastAll = 0;
    }

    private static void nearest(GL4 gl) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    private void table(GL4 gl, LavaField f) {
        IntBuffer ib = Buffers.newDirectIntBuffer(side * side);
        for (int k = 0; k < side * side; k++) ib.put(k, -1);
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        double bs = LavaField.B * f.cell;
        for (int b = 0; b < f.blocksUsed(); b++) {
            ib.put(f.blockZ(b) * side + f.blockX(b), b);
            double bx = f.originX() + f.blockX(b) * bs, bz = f.originZ() + f.blockZ(b) * bs;
            x0 = Math.min(x0, bx); z0 = Math.min(z0, bz);
            x1 = Math.max(x1, bx + bs); z1 = Math.max(z1, bz + bs);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, tableTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, side, side, GL4.GL_RED_INTEGER, GL4.GL_INT, ib);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        box[0] = (float) x0; box[1] = (float) z0; box[2] = (float) x1; box[3] = (float) z1;
        tableVersion = f.tableVersion();
    }

    private boolean[] forced = new boolean[0];
    private final float[][] stA = new float[PER_ROUND][4 * LavaField.BB], stC = new float[PER_ROUND][4 * LavaField.BB], stV = new float[PER_ROUND][2 * LavaField.BB];
    private final int[] stB = new int[PER_ROUND];

    private void uploadStaged(GL4 gl, int b, float[] a, float[] c, float[] v) {
        fa.clear(); fa.put(a).flip();
        fc.clear(); fc.put(c).flip();
        fv.clear(); fv.put(v).flip();
        int x = (b % ROW) * LavaField.B, y = (b / ROW) * LavaField.B;
        gl.glBindTexture(GL.GL_TEXTURE_2D, aTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, x, y, LavaField.B, LavaField.B, GL.GL_RGBA, GL.GL_FLOAT, fa);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, x, y, LavaField.B, LavaField.B, GL.GL_RGBA, GL.GL_FLOAT, fc);
        gl.glBindTexture(GL.GL_TEXTURE_2D, vTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, x, y, LavaField.B, LavaField.B, GL4.GL_RG, GL.GL_FLOAT, fv);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        uploads++;
    }

    private int soloGlow, glowTarget;

    private int activeGlow() { return field != null ? glowTex : soloGlow; }

    private static int glowTexture(GL4 gl) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RGB16F, GlowMap.G, GlowMap.G, 0, GL.GL_RGB, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private void glow(GL4 gl) {
        GlowMap g = sim.glow;
        int tex = activeGlow();
        if (tex == 0 || (g.version == glowVersion && tex == glowTarget)) return;
        glowTarget = tex;
        synchronized (g) {
            glowBuf.clear();
            glowBuf.put(g.rgb).flip();
            glowRect[0] = (float) g.x0;
            glowRect[1] = (float) g.z0;
            glowRect[2] = (float) g.size;
            glowRect[3] = g.empty ? 0 : 1;
            glowVersion = g.version;
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, GlowMap.G, GlowMap.G, GL.GL_RGB, GL.GL_FLOAT, glowBuf);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Einheiten 10 bis 14 und Uniforms für lava.glsl setzen (auch ohne Lava: dann ist sie aus). */
    void bind(GL4 gl, Shader s) {
        bindDeform(gl, s);
        boolean on = show && field != null && aTex != 0;
        gl.glUniform1i(s.loc(gl, "uLavaOn"), on ? 1 : 0);
        if (on) {
            gl.glActiveTexture(GL.GL_TEXTURE10);
            gl.glBindTexture(GL.GL_TEXTURE_2D, tableTex);
            gl.glActiveTexture(GL.GL_TEXTURE11);
            gl.glBindTexture(GL.GL_TEXTURE_2D, aTex);
            gl.glActiveTexture(GL.GL_TEXTURE12);
            gl.glBindTexture(GL.GL_TEXTURE_2D, cTex);
            gl.glActiveTexture(GL.GL_TEXTURE13);
            gl.glBindTexture(GL.GL_TEXTURE_2D, vTex);
            gl.glActiveTexture(GL.GL_TEXTURE14);
            gl.glBindTexture(GL.GL_TEXTURE_2D, glowTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            s.set(gl, "uLavaTable", 10);
            s.set(gl, "uLavaA", 11);
            s.set(gl, "uLavaC", 12);
            s.set(gl, "uLavaV", 13);
            s.set(gl, "uGlowTex", 14);
            gl.glUniform4f(s.loc(gl, "uLavaGrid"), (float) field.originX(), (float) field.originZ(), (float) field.cell, side);
            gl.glUniform4fv(s.loc(gl, "uLavaBox"), 1, box, 0);
            gl.glUniform4fv(s.loc(gl, "uGlowRect"), 1, glowRect, 0);
        } else {
            // Phase 11: der Lavasee allein (ohne Strömungsfeld) leuchtet die Umgebung, die Fahne und die Wolken an
            boolean glowOn = show && soloGlow != 0 && glowTarget == soloGlow && glowRect[3] > 0;
            gl.glActiveTexture(GL.GL_TEXTURE14);
            gl.glBindTexture(GL.GL_TEXTURE_2D, soloGlow);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            if (glowOn) gl.glUniform4fv(s.loc(gl, "uGlowRect"), 1, glowRect, 0);
            else gl.glUniform4f(s.loc(gl, "uGlowRect"), 0, 0, 0, 0);
            // auch ausgeschaltet brauchen die Sampler gültige Einheiten verschiedener Art
            s.set(gl, "uLavaTable", 10);
            s.set(gl, "uLavaA", 11);
            s.set(gl, "uLavaC", 12);
            s.set(gl, "uLavaV", 13);
            s.set(gl, "uGlowTex", 14);
        }
        gl.glUniform3fv(s.loc(gl, "uGlowLut"), Glow.N, LUT, 0);
        s.set(gl, "uLavaExposure", (float) exposure);
        s.set(gl, "uLavaTime", (float) (time % 3600));
        LavaSim ls = sim;
        boolean lake = ls != null && ls.showLake && !Double.isNaN(ls.lake().rimY);
        gl.glUniform1i(s.loc(gl, "uLakeOn"), lake ? 1 : 0);
        if (lake) {
            LavaLake L = ls.lake();
            gl.glUniform4f(s.loc(gl, "uLakeGeo"), (float) L.cx, (float) L.cz, (float) L.a, (float) L.b);
            gl.glUniform4f(s.loc(gl, "uLakeGeo2"), (float) Math.cos(L.rot), (float) Math.sin(L.rot), (float) L.rimY, (float) LavaLake.PIT_DEPTH);
        }
    }

    void dispose(GL4 gl) {
        if (soloGlow != 0 && field == null) { gl.glDeleteTextures(1, new int[]{soloGlow}, 0); soloGlow = 0; }
        if (deformTex != 0) { gl.glDeleteTextures(1, new int[]{deformTex}, 0); deformTex = 0; deformData = null; }
        if (aTex == 0) return;
        gl.glDeleteTextures(5, new int[]{tableTex, aTex, cTex, vTex, glowTex}, 0);
        tableTex = aTex = cTex = vTex = glowTex = 0;
    }
}
