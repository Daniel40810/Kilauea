package com.dan.kilauea.tools;

import com.dan.kilauea.render.Quality;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.terrain.SummitPaths;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.util.Locale;

/**
 * Tempo-Probe ohne Fenster.
 * <ul>
 * <li>{@code Tempo [Ausgangspunkt 0..5] [Bilder]}: geht einige Sekunden geradeaus und gibt die Zeit des
 * Prozessors je Schritt aus (kleines Bild, die Werte der Grafikkarte sind dort ohne Bedeutung).</li>
 * <li>{@code Tempo bild <ort> <breite> <höhe> <bilder> [png] [stufe]} (Phase 17): steht an einem festen Ort
 * mit fester Uhr und misst die Wanduhr je Bild bis glFinish; so lässt sich eine Änderung an einem Shader
 * vorher und nachher vergleichen, auch mit Software-Grafik (dort zählt die Rechenarbeit je Bildpunkt).
 * Orte: {@code iki} (zu Fuß auf dem Boden des Kīlauea Iki wie im Statusbild 15.2), {@code hoehe} (5,8 km über
 * dem Gipfel), {@code rand} (Uēkahuna am Kraterrand), {@code wald} (Nāhuku, Regenwald).</li>
 * </ul>
 */
public final class Tempo {
    private Tempo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("bild")) { bild(args); return; }
        int spot = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 120;
        Renderer.vorab();
        GLOffscreenAutoDrawable d = drawable(160, 90);
        Renderer r = start(d);
        r.showSteam = !Boolean.getBoolean("kilauea.ohneDampf");
        r.state.clock.set(280, 17.25);
        String name;
        if (spot < 0) {   // Boden des Halemaʻumaʻu, Blick nach Norden zur Wand
            name = "Halemaʻumaʻu, Boden";
            r.camera.startWalk(-150, 120, 20);
        } else {
            var sp = SummitPaths.WALK_SPOTS.get(spot);
            name = sp.name();
            r.camera.startWalk(sp.x(), sp.z(), sp.heading());
        }
        if (!Boolean.getBoolean("kilauea.gehen")) r.camera.kFwd = false;
        r.camera.snap();
        for (int i = 0; i < 400 && !r.floraComplete(); i++) d.display();
        r.camera.kFwd = Boolean.parseBoolean(System.getProperty("kilauea.gehen", "true"));
        r.warmSteam(150);
        long t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) { r.step(1 / 30.0); d.display(); }
        double ms = (System.nanoTime() - t0) / 1e6 / frames;

        System.out.printf(Locale.GERMAN, "%s: %d Bilder, %.1f ms je Bild (mit Software-Grafik)%nCPU %s%nGPU %s%n%s%n", name, frames, ms, r.cpuTimes(), r.passTimes(), r.floraStats());
        d.destroy();
        System.exit(0);
    }

    static GLOffscreenAutoDrawable drawable(int w, int h) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        return d;
    }

    static Renderer start(GLOffscreenAutoDrawable d) throws InterruptedException {
        Renderer r = new Renderer();
        r.frozen = true;
        d.addGLEventListener(r);
        d.display();
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        while (!r.terrainReady()) { Thread.sleep(100); d.display(); }
        return r;
    }

    /** Fester Ort, feste Uhr, Wanduhr je Bild bis glFinish; dazu ein Bild zum Vergleichen. */
    static void bild(String[] a) throws Exception {
        String where = a.length > 1 ? a[1] : "iki";
        int w = a.length > 2 ? Integer.parseInt(a[2]) : 640, h = a.length > 3 ? Integer.parseInt(a[3]) : 400;
        int frames = a.length > 4 ? Integer.parseInt(a[4]) : 6;
        File png = a.length > 5 && !a[5].equals("-") ? new File(a[5]) : null;
        Quality q = a.length > 6 ? Quality.valueOf(a[6]) : Quality.HOCH;
        Renderer.vorab();
        GLOffscreenAutoDrawable d = drawable(w, h);
        Renderer r = start(d);
        r.setQuality(q);
        for (var g : com.dan.kilauea.life.Life.Group.values()) r.life.set(g, false);   // gleiche Szene vorher und nachher
        r.lifeFrozen = true;
        r.showSteam = true;
        switch (where) {
            case "iki" -> {   // Statusbild 15.2: 3. April 10:04 HST, Kraterboden, Blick nach Osten zur Wand
                var sp = SummitPaths.WALK_SPOTS.get(3);
                r.state.clock.set(93, 10.07);
                r.camera.startWalk(sp.x(), sp.z(), 80);
            }
            case "rand" -> {
                var sp = SummitPaths.WALK_SPOTS.get(0);
                r.state.clock.set(93, 10.07);
                r.camera.startWalk(sp.x(), sp.z(), sp.heading());
            }
            case "wald" -> {
                var sp = SummitPaths.WALK_SPOTS.get(4);
                r.state.clock.set(93, 10.07);
                r.camera.startWalk(sp.x(), sp.z(), sp.heading());
            }
            case "hoehe" -> {   // Statusbild 15.1: aus 5,8 km auf den Gipfel
                r.state.clock.set(93, 10.07);
                r.camera.autoOrbit = false;
                r.camera.lookAt(0, 1100, 0, 200, 40, 7500);
            }
            default -> throw new IllegalArgumentException("Ort: iki, rand, wald, hoehe");
        }
        r.camera.kFwd = false;
        r.camera.snap();
        for (int i = 0; i < 400 && !r.floraComplete(); i++) d.display();
        r.warmSteam(150);
        r.warmWeather(60);
        for (int i = 0; i < 10; i++) { r.step(i < 9 ? 0.6 : 0.0); d.display(); }
        double best = Double.MAX_VALUE, sum = 0;
        double[] pass = new double[9];
        double[] passBest = new double[9];
        java.util.Arrays.fill(passBest, Double.MAX_VALUE);
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            d.display();
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms);
            sum += ms;
            if (i > 0) {   // die Zeitstempel kommen ein Bild später
                double[] lp = r.lastPassTimes();
                for (int k = 0; k < 9; k++) { pass[k] += lp[k] / (frames - 1); passBest[k] = Math.min(passBest[k], lp[k]); }
            }
        }
        String[] names = {"Schatten", "Himmel+Boden", "Straßen+Pflanzen+Figuren", "Lavasee", "Meer", "Wolken", "Dampf", "Fontäne", "Nachbearbeitung"};
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 9; k++) sb.append(String.format(Locale.GERMAN, "%s%s %.0f (%.0f)", k > 0 ? " · " : "", names[k], pass[k], passBest[k]));
        System.out.println("Durchgänge, Mittel (bestes) ms: " + sb);
        System.out.printf(Locale.GERMAN, "%s %d×%d %s: bestes Bild %.0f ms, Mittel %.0f ms (Software-Grafik) · Auge %.0f m%n", where, w, h, q.label, best, sum / frames, r.camera.ey);
        if (png != null) {
            d.display();
            javax.imageio.ImageIO.write(Standbild.Grab.read(d, w, h), "png", png);
        }
        d.destroy();
        System.exit(0);
    }

    @SuppressWarnings("unused") private static final int UNUSED = GL.GL_NONE;
}
