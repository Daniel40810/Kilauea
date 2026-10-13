package com.dan.kilauea.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol mit Goldkante (wie heidelberg.ico): als PNG in mehreren Größen im Jar
 * ({@code com/dan/kilauea/ui/icon/kilauea_NN.png}), dieselben Bilder wie in {@code icon/kilauea.ico}.
 * Fehlt eine Datei, bleibt das Fenster ohne Symbol; der Start bricht nicht ab.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) {
            BufferedImage img = load(s);
            if (img != null) l.add(img);
        }
        return l;
    }

    public static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("icon/kilauea_" + size + ".png")) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        if (l.isEmpty()) {
            System.err.println("Programmsymbol fehlt im Jar (com/dan/kilauea/ui/icon).");
            return;
        }
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }
}
