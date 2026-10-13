package com.dan.kilauea.ui;

import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.render.SceneView;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Übersichtskarte im Bedienfeld: Schummerung der Insel aus dem Höhenraster (Licht aus Nordwest),
 * Meer nach Tiefe, Orte als Punkte, dazu Drehpunkt, Augpunkt und Blickfeld der Kamera. Ein Klick
 * fliegt dorthin; die Maus zeigt Breite, Länge und Höhe.
 */
final class OverviewMap extends JComponent {
    /** Ausschnitt der Karte in der Szene (m): die Insel mit etwas Meer. */
    static final double X0 = -100_000, X1 = 60_000, Z0 = -110_000, Z1 = 50_000;
    private final Renderer r;
    private BufferedImage img;
    private String hover = "";

    OverviewMap(SceneView view) {
        r = view.renderer();
        setOpaque(true);
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                double[] w = world(e.getX(), e.getY());
                view.goTo(w[0], w[1]);
            }
            @Override public void mouseExited(MouseEvent e) { hover = ""; repaint(); }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                double[] w = world(e.getX(), e.getY());
                double h = Ground.height(w[0], w[1]);
                hover = Geo.label(w[0], w[1]) + (h >= 0 ? String.format(java.util.Locale.GERMAN, " · %,.0f m", h) : String.format(java.util.Locale.GERMAN, " · Tiefe %,.0f m", -h));
                repaint();
            }
        });
        new Timer(200, e -> repaint()).start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(240, 240); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 240); }
    @Override public String getToolTipText(MouseEvent e) { return null; }

    private double scale() { return Math.min(getWidth(), getHeight()) / (X1 - X0); }

    private double[] world(int px, int py) {
        double s = scale();
        return new double[]{X0 + px / s, Z0 + py / s};
    }

    private int sx(double x) { return (int) Math.round((x - X0) * scale()); }
    private int sy(double z) { return (int) Math.round((z - Z0) * scale()); }

    /** Schummerung aus dem Raster (einmal, sobald es steht). */
    private static BufferedImage shade(HeightGrid g, int size) {
        BufferedImage im = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double step = (X1 - X0) / size;
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++) {
                double x = X0 + (i + 0.5) * step, z = Z0 + (j + 0.5) * step;
                double h = g.height(x, z);
                int rgb;
                if (h < 0) {
                    double d = Math.min(1, -h / 5000);
                    rgb = mix(0x1E4A6E, 0x08121E, Math.sqrt(d));
                } else {
                    double dx = (g.height(x + step, z) - g.height(x - step, z)) / (2 * step);
                    double dz = (g.height(x, z + step) - g.height(x, z - step)) / (2 * step);
                    // Licht aus Nordwest, 45° hoch
                    double lx = -0.5, ly = 0.7071, lz = -0.5;
                    double sh = Math.max(0, (-dx * lx + ly - dz * lz) / Math.sqrt(dx * dx + 1 + dz * dz));
                    double age = g.age(x, z), fresh = 1 - Math.min(1, Math.log1p(age) / Math.log1p(400)), veg = g.cover(x, z, 0), ash = g.cover(x, z, 2);
                    int base = mix(0x6E6258, 0x2C2A2A, fresh);
                    base = mix(base, 0x34502C, Math.min(1, veg * 1.3));
                    base = mix(base, h > 2200 ? 0x8A5A44 : 0x9A8C76, ash);
                    base = mix(base, 0xB4AAA0, Math.max(0, Math.min(1, (h - 3500) / 600)) * 0.5);
                    rgb = mul(base, 0.45 + 0.85 * sh);
                }
                im.setRGB(i, j, rgb);
            }
        return im;
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int mul(int a, double f) {
        int r = (int) Math.min(255, ((a >> 16) & 255) * f), g = (int) Math.min(255, ((a >> 8) & 255) * f), b = (int) Math.min(255, (a & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = getWidth(), h = getHeight(), side = Math.min(w, h);
        g.setColor(ControlPanel.BG);
        g.fillRect(0, 0, w, h);
        HeightGrid grid = Ground.grid();
        if (img == null && grid != null) img = shade(grid, 320);
        if (img != null) g.drawImage(img, 0, 0, side, side, null);
        else {
            g.setColor(new Color(0x10202E));
            g.fillRect(0, 0, side, side);
            g.setColor(ControlPanel.MUTED);
            g.drawString("Gelände lädt …", 12, 20);
        }
        // Orte
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        for (Place p : Place.values()) {
            g.setColor(new Color(255, 255, 255, 150));
            g.fillOval(sx(p.x()) - 2, sy(p.z()) - 2, 4, 4);
        }
        // Kamera: Blickfeld vom Auge, Drehpunkt
        var c = r.camera;
        double ex = c.ex, ez = c.ez, tx = c.targetX(), tz = c.targetZ();
        double yaw = Math.atan2(tx - ex, -(tz - ez));
        double len = Math.max(Math.hypot(tx - ex, tz - ez) * 1.6, 3000), half = Math.toRadians(c.fovy * 0.5 * 1.6);
        Polygon fov = new Polygon();
        fov.addPoint(sx(ex), sy(ez));
        fov.addPoint(sx(ex + Math.sin(yaw - half) * len), sy(ez - Math.cos(yaw - half) * len));
        fov.addPoint(sx(ex + Math.sin(yaw + half) * len), sy(ez - Math.cos(yaw + half) * len));
        g.setColor(new Color(240, 140, 64, 60));
        g.fillPolygon(fov);
        g.setColor(new Color(240, 140, 64, 200));
        g.setStroke(new BasicStroke(1.2f));
        g.drawPolygon(fov);
        g.fillOval(sx(ex) - 3, sy(ez) - 3, 7, 7);
        g.setColor(Color.WHITE);
        g.drawOval(sx(tx) - 4, sy(tz) - 4, 9, 9);
        // Nordpfeil und Maßstab
        g.setColor(ControlPanel.INK);
        g.drawString("N", side - 14, 14);
        g.drawLine(side - 11, 18, side - 11, 30);
        int km20 = (int) Math.round(20_000 * scale());
        g.drawLine(8, side - 10, 8 + km20, side - 10);
        g.drawLine(8, side - 13, 8, side - 7);
        g.drawLine(8 + km20, side - 13, 8 + km20, side - 7);
        g.drawString("20 km", 12 + km20, side - 7);
        if (!hover.isEmpty()) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, side, 16);
            g.setColor(ControlPanel.INK);
            g.drawString(hover, 5, 12);
        }
        g.dispose();
    }
}
