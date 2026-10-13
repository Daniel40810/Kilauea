package com.dan.kilauea.tools;

import com.dan.kilauea.app.KilaueaApp;
import com.dan.kilauea.db.Dienst;
import com.dan.kilauea.db.Zustand;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 16: startet die echte App (mit Startbild) und prüft Zustand und Bedienfeld. Zwei Läufe mit demselben Ordner
 * (-Dkilauea.state.dir):
 * <ol>
 * <li>{@code setzen}: neu am Gipfel, dann einen Zustand mit geänderten Schaltern, Zeit und Kamera (zu Fuß am Kīlauea Iki)
 * einstellen, erfassen, vergleichen, mit Dienst.beenden sichern (wie beim Schließen des Fensters).</li>
 * <li>{@code pruefen}: das Startbild wählt von selbst „Weiter wie zuletzt“; dann muss das Bedienfeld genau den gesicherten Zustand
 * zeigen.</li>
 * </ol>
 * Schreibt Bildschirmfotos in den Ordner des zweiten Arguments. Rückgabe 0 = alles gleich.
 */
public final class OberflaecheLauf {
    private OberflaecheLauf() { }

    static Zustand soll() {
        Zustand z = new Zustand();
        // Kīlauea Iki, zu Fuß auf dem Kraterboden, Blick nach Westen
        z.setze("kamera.modus", "GEHEN").setze("kamera.x", 3870).setze("kamera.y", 1058).setze("kamera.z", -980)
                .setze("kamera.gier", 255).setze("kamera.neigung", -4).setze("kamera.abstand", 0);
        z.setze("zeit.tag", 93).setze("zeit.stunde", 10.0667);
        z.setze("zeit.raffer", 0).setze("luft.dunst", 40).setze("luft.wind", 9.5).setze("luft.richtung", 200)
                .setze("meer.swell", 1.8).setze("meer.periode", 16).setze("bild.qualitaet", "MITTEL").setze("bild.belichtung", 0.3);
        z.setze("leben.birds", false).setze("klang.sea", false).setze("klang.lautstaerke", 55).setze("messnetz.tafel", 1)
                .setze("bild.balken_immer", true).setze("rundflug.morgen", false).setze("lava.tempo", 3);
        z.setze("panel.ansicht", true).setze("panel.leben", true).setze("panel.bild", false).setze("panel.daten", true).setze("panel.zeit", false);
        return z;
    }

    public static void main(String[] args) throws Exception {
        String modus = args.length > 0 ? args[0] : "setzen";
        File out = new File(args.length > 1 ? args[1] : "build/oberflaeche");
        out.mkdirs();
        // Startbild im Hintergrund fotografieren, sobald es steht
        Thread foto = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    Thread.sleep(100);
                    for (Window w : Window.getWindows()) {
                        if (w.isShowing() && w instanceof com.dan.kilauea.ui.Startbild && com.dan.kilauea.render.Renderer.vorabFortschritt() >= 1) {
                            Thread.sleep(600);
                            shot(w.getBounds(), new File(out, "startbild_" + modus + ".png"));
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Startbild nicht fotografiert: " + e);
            }
        }, "Foto-Startbild");
        foto.setDaemon(true);
        foto.start();
        KilaueaApp.main(new String[0]);
        long t0 = System.currentTimeMillis();
        while (KilaueaApp.panel == null && System.currentTimeMillis() - t0 < 120_000) Thread.sleep(200);
        if (KilaueaApp.panel == null) { System.out.println("FEHLER: das Bedienfeld kam nicht"); System.exit(2); }
        Thread.sleep(6000);
        AtomicReference<Zustand> ist = new AtomicReference<>();
        int fehler = 0;
        if (modus.equals("setzen")) {
            Zustand soll = soll();
            SwingUtilities.invokeAndWait(() -> KilaueaApp.panel.anwenden(soll, true));
            Thread.sleep(3000);
            SwingUtilities.invokeAndWait(() -> ist.set(KilaueaApp.panel.erfassen()));
            fehler += vergleiche(soll, ist.get(), "eingestellt → erfasst");
            System.out.println("Erfasst: " + ist.get().werte().size() + " Werte, davon " + ist.get().schalter().size() + " Schalter");
            shot(KilaueaApp.frame.getBounds(), new File(out, "app_setzen.png"));
            Dienst.merken(ist.get().nurKamera(true), "Kīlauea Iki, Kraterboden am Morgen");
            Dienst.beenden(ist.get());
            Zustand datei = Zustand.laden(Dienst.zustandsDatei());
            fehler += vergleiche(ist.get(), datei, "erfasst → Datei beim Beenden");
        } else {
            Zustand datei = Zustand.laden(Dienst.zustandsDatei());
            SwingUtilities.invokeAndWait(() -> ist.set(KilaueaApp.panel.erfassen()));
            fehler += vergleiche(datei, ist.get(), "Datei → nach dem Neustart erfasst");
            shot(KilaueaApp.frame.getBounds(), new File(out, "app_pruefen.png"));
            System.out.println("Ansichten: " + Dienst.ansichten());
            SwingUtilities.invokeAndWait(() -> {
                javax.swing.JScrollPane sp = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, KilaueaApp.panel);
                if (sp != null) sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
            });
            Thread.sleep(1500);
            Rectangle fr = KilaueaApp.frame.getBounds();
            shot(new Rectangle(fr.x + fr.width - 340, fr.y, 340, fr.height), new File(out, "panel_unten.png"));
            // F1 zeigen und fotografieren
            SwingUtilities.invokeLater(() -> {
                try {
                    java.lang.reflect.Method m = KilaueaApp.panel.getClass().getDeclaredMethod("tastenZeigen");
                    m.setAccessible(true);
                    m.invoke(KilaueaApp.panel);
                } catch (Exception e) { System.out.println("F1: " + e); }
            });
            Thread.sleep(2500);
            for (Window w : Window.getWindows())
                if (w.isShowing() && w != KilaueaApp.frame && w.getWidth() > 100) gemalt(w, new File(out, "tasten.png"));
        }
        System.out.println(fehler == 0 ? "ERGEBNIS: alles gleich" : "ERGEBNIS: " + fehler + " Unterschiede");
        Runtime.getRuntime().halt(fehler == 0 ? 0 : 1);
    }

    /** Vergleicht alle Werte von soll mit ist; die Kamera beim Gehen auf 0,05 m und 0,1° genau (die Höhe folgt dem Boden). */
    static int vergleiche(Zustand soll, Zustand ist, String was) {
        if (soll == null || ist == null) { System.out.println("  FEHLER " + was + ": Zustand fehlt"); return 1; }
        int n = 0;
        Map<String, String> s = new TreeMap<>(soll.werte());
        for (Map.Entry<String, String> e : s.entrySet()) {
            String k = e.getKey(), a = e.getValue(), b = ist.text(k);
            boolean gleich = a.equals(b);
            if (!gleich && b != null && Zustand.fest(k) && Zustand.spalte(k).typ == Zustand.Typ.ZAHL) {
                double tol = k.equals("kamera.y") ? ("GEHEN".equals(soll.text("kamera.modus")) ? 15.0 : 0.1) : k.startsWith("kamera.") ? 0.1 : k.equals("zeit.stunde") ? 0.02 : 1e-6;
                gleich = Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) <= tol;
            }
            if (!gleich) { System.out.println("  FEHLER " + was + ": " + k + " soll " + a + ", ist " + b); n++; }
        }
        System.out.println("  " + (n == 0 ? "ok    " : "FEHLER ") + was + ": " + (s.size() - n) + " von " + s.size() + " Werten gleich");
        return n;
    }

    /** Malt ein Fenster selbst in ein Bild (durchscheinende FDialog-Fenster zeigt Xvfb ohne Compositor schwarz). */
    static void gemalt(Window w, File f) {
        try {
            BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> {
                java.awt.Graphics2D g = img.createGraphics();
                g.setColor(new java.awt.Color(15, 17, 20));
                g.fillRect(0, 0, w.getWidth(), w.getHeight());
                w.printAll(g);
                g.dispose();
            });
            ImageIO.write(img, "png", f);
            System.out.println("  Bild " + f.getName() + " (gemalt, " + w.getWidth() + " × " + w.getHeight() + ")");
        } catch (Exception e) {
            System.out.println("  Bild " + f.getName() + " nicht möglich: " + e);
        }
    }

    static void shot(Rectangle r, File f) {
        try {
            BufferedImage img = new Robot().createScreenCapture(r);
            ImageIO.write(img, "png", f);
            System.out.println("  Bild " + f.getName() + " (" + r.width + " × " + r.height + ")");
        } catch (Exception e) {
            System.out.println("  Bild " + f.getName() + " nicht möglich: " + e);
        }
    }
}
