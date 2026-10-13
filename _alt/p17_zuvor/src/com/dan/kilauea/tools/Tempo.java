package com.dan.kilauea.tools;

import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.terrain.SummitPaths;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.util.Locale;

/**
 * Tempo-Probe ohne Fenster: geht an einem Ausgangspunkt einige Sekunden geradeaus und gibt die Zeit des
 * Prozessors je Schritt aus (Pflanzen, Bodendecke, Gelände-Auswahl, Dampf). Kleines Bild, damit die
 * Software-Grafik wenig Zeit kostet; die Werte der Grafikkarte sind hier ohne Bedeutung.
 * Aufruf: Tempo [Ausgangspunkt 0..5] [Bilder].
 */
public final class Tempo {
    private Tempo() { }

    public static void main(String[] args) throws Exception {
        int spot = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 120;
        Renderer.vorab();
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, 160, 90);
        d.setAutoSwapBufferMode(false);
        Renderer r = new Renderer();
        r.frozen = true;
        d.addGLEventListener(r);
        d.addGLEventListener(new GLEventListener() {
            @Override public void init(GLAutoDrawable a) { }
            @Override public void dispose(GLAutoDrawable a) { }
            @Override public void reshape(GLAutoDrawable a, int x, int y, int ww, int hh) { }
            @Override public void display(GLAutoDrawable a) { }
        });
        d.display();
        while (!r.terrainReady()) { Thread.sleep(100); d.display(); }
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
}
