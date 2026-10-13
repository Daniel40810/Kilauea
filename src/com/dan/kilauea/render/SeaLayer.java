package com.dan.kilauea.render;

import com.dan.kilauea.sea.Nearshore;
import com.dan.kilauea.sea.Spray;
import com.dan.kilauea.sea.Waves;
import com.dan.kilauea.terrain.Ground;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Meer vor der Küste auf der Grafikkarte (Phase 9): baut im Hintergrund das Raster {@link Nearshore} um die
 * Kamera, lädt es als Textur hoch und setzt die Uniforms für sea.glsl. Dazu die Gischt an den Klippen
 * ({@link Spray}), im selben Takt wie die Dünung im Shader: die Phase ω t rechnet die CPU in double.
 */
final class SeaLayer {
    static final int UNIT = 15;
    /** Neu bauen, wenn die Kamera so weit (m) von der Mitte des Rasters entfernt ist. */
    static final double REBUILD = 1200;

    final Spray spray = new Spray();
    final SteamRenderer sprayR = new SteamRenderer(spray);
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Meer vor der Küste");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private Future<Nearshore> job;
    private volatile Nearshore current;
    private Nearshore uploaded;
    private int tex;
    private FloatBuffer buf;
    /** Zeit (ms) für den letzten Bau des Rasters. */
    volatile double buildMs;
    volatile boolean show = true;
    private double phaseA, phaseB;

    void init(GL4 gl) { sprayR.init(gl); }

    Nearshore nearshore() { return current; }

    /** Für Standbilder: Raster sofort bauen (wartet). */
    void buildNow(double x, double z) {
        long t0 = System.nanoTime();
        current = Nearshore.build(Ground::height, x, z);
        buildMs = (System.nanoTime() - t0) / 1e6;
        spray.coast(current.coast);
    }

    /** Im Rechenschritt: Raster nachführen, Gischt bewegen. */
    void step(double dt, double simTime, SceneState st, double[] cam) {
        if (job != null && job.isDone()) {
            try {
                current = job.get();
                spray.coast(current.coast);
            } catch (Exception e) {
                System.err.println("Meer vor der Küste: " + e);
            }
            job = null;
        }
        Nearshore c = current;
        if (job == null && Ground.grid() != null && (c == null || Math.hypot(cam[0] - c.cx, cam[2] - c.cz) > REBUILD)) {
            double x = cam[0], z = cam[2];
            job = pool.submit(() -> {
                long t0 = System.nanoTime();
                Nearshore n = Nearshore.build(Ground::height, x, z);
                buildMs = (System.nanoTime() - t0) / 1e6;
                return n;
            });
        }
        double[] sw = swellA(st), wb = swellB(st);
        phaseA = (Waves.omega(sw[3]) * simTime) % (2 * Math.PI);
        phaseB = (Waves.omega(wb[3]) * simTime) % (2 * Math.PI);
        if (show && cam[1] < 6000) spray.update(dt, simTime, sw[3], new double[]{sw[0], sw[1]}, sw[2], st.windVector(), cam);
        else spray.clear();
    }

    /** Südswell: Laufrichtung, Höhe, Periode. */
    static double[] swellA(SceneState st) {
        double[] v = st.swellVector();
        double h = Math.hypot(v[0], v[1]);
        double a = Math.toRadians(st.swellDirDeg + 180);
        return new double[]{Math.sin(a), -Math.cos(a), h, st.swellPeriod};
    }

    /** See aus dem Passat: Richtung wie der Wind, Höhe und Periode nach der Windstärke [A]. */
    static double[] swellB(SceneState st) {
        double[] w = st.windVector();
        double u = Math.hypot(w[0], w[1]);
        double dx = u > 0.01 ? w[0] / u : -1, dz = u > 0.01 ? w[1] / u : 0;
        return new double[]{dx, dz, 0.16 * u, 3.3 + 0.62 * u};
    }

    /** Textur hochladen, wenn ein neues Raster da ist. */
    void upload(GL4 gl) {
        Nearshore c = current;
        if (c == null || c == uploaded) return;
        if (tex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tex = t[0];
        }
        if (buf == null) buf = Buffers.newDirectFloatBuffer(Nearshore.N * Nearshore.N * 4);
        buf.clear();
        buf.put(c.data).flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, Nearshore.N, Nearshore.N, 0, GL.GL_RGBA, GL.GL_FLOAT, buf);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        uploaded = c;
    }

    void bind(GL4 gl, Shader s, SceneState st) {
        Nearshore c = uploaded;
        boolean on = show && c != null && tex != 0;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, on ? tex : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uSeaTex", UNIT);
        if (on) s.set(gl, "uSeaRect", (float) (c.x0 - Nearshore.CELL / 2), (float) (c.z0 - Nearshore.CELL / 2), (float) c.size, 1f);
        else s.set(gl, "uSeaRect", 0f, 0f, 1f, 0f);
        double[] a = swellA(st), b = swellB(st);
        s.set(gl, "uSwellA", (float) a[0], (float) a[1], (float) (show ? a[2] : 0), (float) a[3]);
        s.set(gl, "uSwellB", (float) b[0], (float) b[1], (float) (show ? b[2] : 0), (float) b[3]);
        s.set(gl, "uSwellPhase", (float) phaseA, (float) phaseB);
    }

    void dispose(GL4 gl) {
        sprayR.dispose(gl);
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        tex = 0;
        pool.shutdownNow();
    }
}
