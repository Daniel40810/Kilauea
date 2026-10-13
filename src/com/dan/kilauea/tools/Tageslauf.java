package com.dan.kilauea.tools;

import com.dan.kilauea.render.Quality;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.Place;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Prüfhilfe (Phase 11): ein Tag in 90 Sekunden. Rechnet mit demselben Renderer wie die App einen ganzen Tag im
 * Zeitraffer 960 (Mitternacht bis Mitternacht) und misst jedes Bild: mittlere Helligkeit, Anteil ausgebrannter
 * Bildpunkte (alle Farben ≥ 250 von 255) und das Flackern, also wie weit ein Bild vom Mittel seiner beiden
 * Nachbarn abweicht (ein gleichmäßiger Sonnenaufgang zählt nicht als Flackern, ein Sprung schon).
 * <p>Aufruf: {@code Tageslauf <ordner> [breite höhe] [bildabstand_s]} — schreibt tageslauf.csv, ein
 * Bild alle 2,5 s als PNG und am Ende eine Zusammenfassung.</p>
 */
public final class Tageslauf {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "tageslauf");
        out.mkdirs();
        int w = args.length > 2 ? Integer.parseInt(args[1]) : 480, h = args.length > 2 ? Integer.parseInt(args[2]) : 300;
        double dt = args.length > 3 ? Double.parseDouble(args[3]) : 0.25;
        Renderer.vorab();
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
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
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        while (!r.terrainReady()) { Thread.sleep(100); d.display(); }
        r.setQuality(Quality.HOCH);
        r.state.clock.set(280, 0.0);
        // Blick von Nordosten über die Caldera nach Südwesten: Lavasee, Fahne, Wolken, Mauna Loa
        var sim = r.lava;
        sim.speed = 0;
        sim.installGround();
        sim.reset();
        sim.refreshLakeImage();
        sim.refreshGlow();
        double gx = Place.KALUAPELE.x(), gz = Place.KALUAPELE.z();
        r.camera.lookAt(gx, Ground.height(gx, gz), gz, 215, 9, 7_500);
        r.camera.snap();
        for (int i = 0; i < 400 && !r.floraComplete(); i++) d.display();
        r.warmSteam(150);
        for (int i = 0; i < 10; i++) { r.step(0.6); d.display(); }
        r.timeLapse = 960;
        int n = (int) Math.round(90 / dt);
        double[] mean = new double[n + 1], burnt = new double[n + 1], hour = new double[n + 1], expo = new double[n + 1];
        long t0 = System.nanoTime();
        for (int i = 0; i <= n; i++) {
            if (i > 0) r.step(dt);
            d.display();
            BufferedImage img = Standbild.Grab.read(d, w, h);
            double sum = 0;
            int hot = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int p = img.getRGB(x, y), R = (p >> 16) & 255, G = (p >> 8) & 255, B = p & 255;
                    sum += 0.2126 * R + 0.7152 * G + 0.0722 * B;
                    if (R >= 250 && G >= 250 && B >= 250) hot++;
                }
            mean[i] = sum / (w * h);
            burnt[i] = hot / (double) (w * h);
            hour[i] = r.state.clock.hour();
            expo[i] = r.state.exposure;
            int every = (int) Math.max(1, Math.round(2.5 / dt));       // alle 2,5 s ein Bild aufheben
            if (i % every == 0) javax.imageio.ImageIO.write(img, "png", new File(out, String.format("t%04d.png", i)));
            if (i % (4 * every) == 0) System.out.printf(Locale.GERMAN, "  Bild %d/%d, %s, Helligkeit %.1f, ausgebrannt %.3f %%%n", i, n,
                    com.dan.kilauea.atmos.SunClock.timeLabel(hour[i]), mean[i], 100 * burnt[i]);
            if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        }
        double maxFlick = 0, maxBurnt = 0, maxStep = 0;
        int atFlick = 0, atBurnt = 0;
        try (PrintWriter pw = new PrintWriter(new File(out, "tageslauf.csv"), StandardCharsets.UTF_8)) {
            pw.println("bild;sekunde;uhrzeit;belichtung;helligkeit;flackern;ausgebrannt_prozent");
            for (int i = 0; i <= n; i++) {
                double fl = i > 0 && i < n ? Math.abs(mean[i] - 0.5 * (mean[i - 1] + mean[i + 1])) : 0;
                if (fl > maxFlick) { maxFlick = fl; atFlick = i; }
                if (burnt[i] > maxBurnt) { maxBurnt = burnt[i]; atBurnt = i; }
                if (i > 0) maxStep = Math.max(maxStep, Math.abs(mean[i] - mean[i - 1]));
                pw.printf(Locale.ROOT, "%d;%.2f;%.3f;%.5g;%.2f;%.3f;%.4f%n", i, i * dt, hour[i], expo[i], mean[i], fl, 100 * burnt[i]);
            }
        }
        System.out.printf(Locale.GERMAN, "Tageslauf: %d Bilder in %.0f s (Software-Grafik)%n", n + 1, (System.nanoTime() - t0) / 1e9);
        System.out.printf(Locale.GERMAN, "  größtes Flackern %.2f von 255 (Bild %d, %s); größter Schritt zwischen zwei Bildern %.2f%n", maxFlick, atFlick,
                com.dan.kilauea.atmos.SunClock.timeLabel(hour[atFlick]), maxStep);
        System.out.printf(Locale.GERMAN, "  größter ausgebrannter Anteil %.3f %% (Bild %d, %s)%n", 100 * maxBurnt, atBurnt,
                com.dan.kilauea.atmos.SunClock.timeLabel(hour[atBurnt]));
        d.destroy();
        System.exit(0);
    }

    private Tageslauf() { }
}
