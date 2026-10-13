package com.dan.kilauea.ui;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaLake;
import com.dan.kilauea.lava.LavaSim;
import com.dan.kilauea.lava.VentSite;
import com.dan.kilauea.terrain.Ground;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Kleine Karte des Lava-Modells im Bedienfeld (Phase 7, bis die Lava in Phase 8 im Bild glüht): oben der
 * Strom von oben über der Schummerung, unten der Lavasee im Halemaʻumaʻu mit Platten und Nähten.
 */
final class LavaMap extends JComponent {
    private static final int SIDE = 240, LAKE_H = 150;
    private final LavaSim sim;
    private final BufferedImage flow = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
    private final BufferedImage lake = new BufferedImage(SIDE, LAKE_H, BufferedImage.TYPE_INT_RGB);
    private int[] shade;
    private final int[] rgb = new int[SIDE * SIDE];
    private double vx0, vz0, vSize;
    private Object shadeSite;

    LavaMap(LavaSim sim) {
        this.sim = sim;
        setOpaque(true);
        new Timer(250, e -> { if (isShowing()) repaint(); }).start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(SIDE, SIDE + LAKE_H + 6); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, SIDE + LAKE_H + 6); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ControlPanel.BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        LavaField f = sim.field();
        Object s = sim.focusKey;
        if (f == null || s == null || Ground.grid() == null) {
            g.setColor(new Color(0x1E232A));
            g.fillRect(0, 0, SIDE, SIDE);
            g.setColor(ControlPanel.MUTED);
            g.drawString("Noch kein Ausbruch", 12, 20);
        } else {
            synchronized (f) {
                // Ausschnitt: anfangs 1,2 km um den Schlot, wächst um die Hälfte, sobald die Lava an den Rand kommt
                if (s != shadeSite) {
                    vSize = 1200;
                    vx0 = sim.focusX - vSize / 2;
                    vz0 = sim.focusZ - vSize / 2;
                    shade = null;
                    shadeSite = s;
                }
                double[] fit = LavaPainter.fit(f, 0);
                double m = vSize * 0.08;
                if (fit[0] < vx0 + m || fit[1] < vz0 + m || fit[0] + fit[2] > vx0 + vSize - m || fit[1] + fit[2] > vz0 + vSize - m) {
                    vSize = Math.max(vSize * 1.5, fit[2] * 1.2);
                    vx0 = fit[0] + fit[2] / 2 - vSize / 2;
                    vz0 = fit[1] + fit[2] / 2 - vSize / 2;
                    shade = null;
                }
                if (shade == null) shade = LavaPainter.hillshade(Ground::solidHeight, vx0, vz0, vSize, SIDE);
                LavaPainter.overlay(f, shade, rgb, vx0, vz0, vSize, SIDE, 0);
            }
            flow.setRGB(0, 0, SIDE, SIDE, rgb, 0, SIDE);
            g.drawImage(flow, 0, 0, null);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, SIDE - 16, SIDE, 16);
            g.setColor(ControlPanel.INK);
            g.drawString(String.format(java.util.Locale.GERMAN, "Ausschnitt %.1f km · Norden oben", vSize / 1000), 5, SIDE - 4);
        }
        // Lavasee
        LavaLake L = sim.lake();
        Graphics2D gl = lake.createGraphics();
        gl.setColor(new Color(0x2A2624));
        gl.fillRect(0, 0, SIDE, LAKE_H);
        gl.dispose();
        synchronized (L) {
            double size = 2.2 * L.a;
            // Breite füllen, Höhe mittig
            BufferedImage sq = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
            Graphics2D gs = sq.createGraphics();
            gs.setColor(new Color(0x2A2624));
            gs.fillRect(0, 0, SIDE, SIDE);
            gs.dispose();
            LavaPainter.lake(L, sq, L.cx - size / 2, L.cz - size / 2, size, false);
            Graphics2D gl2 = lake.createGraphics();
            gl2.drawImage(sq, 0, -(SIDE - LAKE_H) / 2, null);
            gl2.dispose();
        }
        g.drawImage(lake, 0, SIDE + 6, null);
        g.setColor(ControlPanel.INK);
        g.drawString("Lavasee im Halemaʻumaʻu (Modell)", 5, SIDE + 6 + 12);
        g.dispose();
    }
}
