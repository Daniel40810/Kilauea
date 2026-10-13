package com.dan.kilauea.render;

import com.dan.kilauea.atmos.Weather;
import com.dan.kilauea.terrain.HeightGrid;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Passatwolken auf der Grafikkarte (Phase 11): Wetterkarte als Textur (Einheit 2, für den Wolkenschatten in
 * allen Shadern), eine kachelnde 3D-Rauschtextur (64³, Einheit 16), das Wolkenbild in halber Auflösung und
 * der Zusammenbau mit dem Hitzeflimmern.
 */
final class CloudLayer {
    static final int WEATHER_UNIT = 2, NOISE_UNIT = 16, CLOUD_UNIT = 5;
    static final int NS = 64;
    private int weatherTex, noiseTex, cloudTex, cloudFbo, cw, ch;
    private int uploaded = -1;
    private FloatBuffer wbuf;
    /** Versatz der Wolken durch den Wind (m). */
    double windX, windZ;
    volatile boolean show = true;

    void init(GL4 gl) {
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        weatherTex = t[0];
        noiseTex = t[1];
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, noiseTex);
        ByteBuffer b = Buffers.newDirectByteBuffer(NS * NS * NS * 4);
        b.put(noise3d()).flip();
        gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_RGBA8, NS, NS, NS, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_REPEAT);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
        // leere Wetterkarte, bis die erste gerechnet ist
        gl.glBindTexture(GL.GL_TEXTURE_2D, weatherTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, 1, 1, 0, GL.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(4));
        params2d(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    private static void params2d(GL4 gl) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    /** Wetterkarte hochladen (wenn neu) und auf Einheit 2 binden. */
    void bindWeather(GL4 gl, Weather w) {
        if (w.ready() && w.version != uploaded) {
            if (wbuf == null) wbuf = Buffers.newDirectFloatBuffer(Weather.N * Weather.N * 4);
            wbuf.clear();
            synchronized (w) { wbuf.put(w.map).flip(); }
            gl.glBindTexture(GL.GL_TEXTURE_2D, weatherTex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, Weather.N, Weather.N, 0, GL.GL_RGBA, GL.GL_FLOAT, wbuf);
            params2d(gl);
            uploaded = w.version;
        }
        gl.glActiveTexture(GL.GL_TEXTURE0 + WEATHER_UNIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, weatherTex);
        gl.glActiveTexture(GL.GL_TEXTURE0 + NOISE_UNIT);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, noiseTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Uniforms des Wetters für einen Shader. */
    void uniforms(GL4 gl, Shader s, Weather w, double[] sunDir) {
        s.set(gl, "uWeatherMap", WEATHER_UNIT);
        s.set(gl, "uCloudNoise", NOISE_UNIT);
        boolean on = show && w.ready() && uploaded >= 0;
        if (on) s.set(gl, "uWeatherRect", (float) HeightGrid.X0, (float) HeightGrid.Z0, (float) HeightGrid.EXTENT, 1f);
        else s.set(gl, "uWeatherRect", 0f, 0f, 1f, 0f);
        s.set(gl, "uCloudLayer", (float) Weather.BASE, (float) Weather.TOP, 0f, 0f);
        s.set(gl, "uCloudWind", (float) (windX % 1_000_000), (float) (windZ % 1_000_000));
        s.set(gl, "uSunDirW", (float) sunDir[0], (float) sunDir[1], (float) sunDir[2]);
    }

    /** Bildspeicher für die Wolken in halber Auflösung. */
    int fbo(GL4 gl, int w, int h) {
        int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
        if (cloudFbo != 0 && nw == cw && nh == ch) return cloudFbo;
        if (cloudFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{cloudFbo}, 0);
            gl.glDeleteTextures(1, new int[]{cloudTex}, 0);
        }
        cw = nw; ch = nh;
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        cloudTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, cw, ch, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        params2d(gl);
        gl.glGenFramebuffers(1, t, 0);
        cloudFbo = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, cloudFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, cloudTex, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return cloudFbo;
    }

    int width() { return cw; }
    int height() { return ch; }
    int texture() { return cloudTex; }

    void dispose(GL4 gl) {
        if (weatherTex != 0) gl.glDeleteTextures(2, new int[]{weatherTex, noiseTex}, 0);
        if (cloudFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{cloudFbo}, 0);
            gl.glDeleteTextures(1, new int[]{cloudTex}, 0);
        }
        weatherTex = cloudFbo = 0;
    }

    // ------------------------------------------------------------ kachelndes Rauschen
    /** R: grobe Form (Wert- und Zellrauschen, 4 Zellen je Kante), G: feine Zellen (8 je Kante). */
    static byte[] noise3d() {
        byte[] out = new byte[NS * NS * NS * 4];
        float[][] f1 = cells(4, 11), f2 = cells(8, 23);
        for (int z = 0; z < NS; z++)
            for (int y = 0; y < NS; y++)
                for (int x = 0; x < NS; x++) {
                    double u = (x + 0.5) / NS, v = (y + 0.5) / NS, w = (z + 0.5) / NS;
                    double wor1 = 1 - worley(f1, 4, u, v, w);
                    double wor2 = 1 - worley(f2, 8, u, v, w);
                    double val = value(u * 4, v * 4, w * 4, 4) * 0.6 + value(u * 8, v * 8, w * 8, 8) * 0.4;
                    double r = Math.max(0, Math.min(1, 0.55 * wor1 + 0.45 * val));
                    double g = Math.max(0, Math.min(1, wor2));
                    int k = 4 * ((z * NS + y) * NS + x);
                    out[k] = (byte) Math.round(r * 255);
                    out[k + 1] = (byte) Math.round(g * 255);
                    out[k + 2] = (byte) Math.round(val * 255);
                    out[k + 3] = (byte) 255;
                }
        return out;
    }

    private static float[][] cells(int n, long seed) {
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
        float[][] p = new float[n * n * n][3];
        for (float[] q : p) { q[0] = (float) r.nextDouble(); q[1] = (float) r.nextDouble(); q[2] = (float) r.nextDouble(); }
        return p;
    }

    /** Abstand zum nächsten Zellpunkt, kachelnd, 0..~1. */
    private static double worley(float[][] pts, int n, double u, double v, double w) {
        double x = u * n, y = v * n, z = w * n;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double best = 9;
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = ix + dx, cy = iy + dy, cz = iz + dz;
                    float[] p = pts[(Math.floorMod(cz, n) * n + Math.floorMod(cy, n)) * n + Math.floorMod(cx, n)];
                    double ex = cx + p[0] - x, ey = cy + p[1] - y, ez = cz + p[2] - z;
                    best = Math.min(best, ex * ex + ey * ey + ez * ez);
                }
        return Math.min(1, Math.sqrt(best));
    }

    /** Wertrauschen, kachelnd mit Periode p. */
    private static double value(double x, double y, double z, int p) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double s = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = c >> 2;
            double hv = hash(Math.floorMod(ix + dx, p), Math.floorMod(iy + dy, p), Math.floorMod(iz + dz, p));
            s += hv * (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
        }
        return s;
    }

    private static double hash(int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffffff) / (double) 0xffffff;
    }
}
