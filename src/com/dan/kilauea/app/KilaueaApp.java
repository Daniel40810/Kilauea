package com.dan.kilauea.app;

import com.dan.fframe.FFrame;
import com.dan.foptionpane.FOptionPane;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.render.SceneView;
import com.dan.kilauea.ui.AppIcon;
import com.dan.kilauea.ui.ControlPanel;
import com.dan.kilauea.ui.Startbild;
import com.jogamp.opengl.GLProfile;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;

/**
 * Kīlauea — der ganze Vulkan als 3D-Szene auf der Grafikkarte (JOGL, OpenGL 4.3).
 * <p>
 * Einstieg: Startbild, dann FFrame beim Start maximiert (Taskleiste bleibt frei), Größe frei
 * veränderbar; Szene in der Mitte, Bedienfeld rechts, Statuszeile unten. Ab Phase 16 merkt sie sich ihren
 * Zustand (Datenbank KILAUEA oder Datei) und schreibt ein Protokoll; sie läuft weiterhin auch ohne Datenbank.
 */
public final class KilaueaApp {
    static final Color BG = new Color(15, 17, 20), STATUS_BG = new Color(9, 10, 12), STATUS_INK = new Color(170, 168, 164);
    public static final String VERSION = "Phase 17";
    /** Für Werkzeuge (tools/OberflaecheLauf): Fenster und Bedienfeld, sobald sie stehen. */
    public static volatile FFrame frame;
    public static volatile ControlPanel panel;

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        // Der GL-Canvas ist ein schweres AWT-Element: Menüs und Tooltips müssen darüber liegen können
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        Renderer.vorab();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            com.dan.kilauea.db.Dienst.ereignis("FEHLER", t.getName(), String.valueOf(e), false);
        });
        // Phase 16: Datenbank im Hintergrund prüfen, während die Insel entsteht
        com.dan.kilauea.db.Dienst.start(null);
        GLProfile.initSingleton();
        SwingUtilities.invokeLater(KilaueaApp::open);
    }

    private static void open() {
        Startbild.Wahl wahl = "false".equals(System.getProperty("kilauea.splash")) ? null : Startbild.zeigen();
        FFrame f = new FFrame("Kīlauea · Der ganze Vulkan · " + VERSION);
        AppIcon.install(f);
        if (!Boolean.getBoolean("kilauea.gljpanel")) makeOpaque(f);
        SceneView scene;
        try {
            scene = new SceneView();
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(null, "OpenGL lässt sich nicht starten:\n" + e.getMessage()
                    + "\n\nBitte den Grafiktreiber aktualisieren. Als Rückfall: Start mit -Dkilauea.gljpanel=true.", "Kīlauea", FOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        ControlPanel controls = new ControlPanel(scene);
        JLabel status = new JLabel("Die Insel wird aufgebaut …");
        status.setForeground(STATUS_INK);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        Renderer rd = scene.renderer();
        rd.setStatusListener(s -> SwingUtilities.invokeLater(() -> status.setText(s)));
        // Phase 16: Stand der Datenbank rechts in der Statuszeile, immer sichtbar
        JLabel dbStatus = new JLabel(com.dan.kilauea.db.Dienst.statuszeile());
        dbStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dbStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        Runnable dbFarbe = () -> {
            dbStatus.setText(com.dan.kilauea.db.Dienst.statuszeile());
            dbStatus.setForeground(com.dan.kilauea.db.Dienst.bereit() ? new Color(150, 210, 160) : new Color(230, 190, 120));
            dbStatus.setToolTipText(com.dan.kilauea.db.Dienst.kurz());
        };
        dbFarbe.run();
        com.dan.kilauea.db.Dienst.beiAenderung(dbFarbe);
        rd.setErrorListener(msg -> {
            com.dan.kilauea.db.Dienst.ereignis("FEHLER", "Grafik", msg, false);
            SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(f, msg, "Kīlauea · Grafik", FOptionPane.ERROR_MESSAGE));
        });
        Renderer.photoHook = name -> com.dan.kilauea.db.Dienst.ereignis("FOTO", name, "", true);

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(scene, BorderLayout.CENTER);
        JScrollPane side = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createEmptyBorder());
        side.getViewport().setBackground(BG);
        com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
        bar.setUnitIncrement(16);
        bar.setBlockIncrement(120);
        side.setVerticalScrollBar(bar);
        side.setBackground(BG);
        side.setPreferredSize(new Dimension(330, 600));
        root.add(side, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(STATUS_BG);
        south.add(status, BorderLayout.CENTER);
        south.add(dbStatus, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        f.setPreferredFrameSize(new Dimension(1480, 900));
        f.setSize(1480, 900);
        f.setMinimumSize(new Dimension(800, 520));
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        startMaximized(f);
        boolean geladen = wahl != null && wahl.laden && wahl.zustand != null;
        if (geladen) {
            controls.anwenden(wahl.zustand, true);
        } else {
            scene.goPlace(com.dan.kilauea.terrain.Place.KALUAPELE);
            rd.camera.lookAt(rd.camera.tx, rd.camera.ty, rd.camera.tz, 205, 18, 16000);
            rd.camera.snap();
        }
        scene.start();
        // Phase 16: Sitzung, Kontext fürs Protokoll, Sicherung alle 60 s und beim Schließen
        com.dan.kilauea.db.Dienst.kontext(new com.dan.kilauea.db.Dienst.Kontext() {
            @Override public int tag() { return rd.state.clock.day(); }
            @Override public double stunde() { return rd.state.clock.hour(); }
            @Override public double[] ort() {
                double x = rd.camera.targetX(), z = rd.camera.targetZ();
                return new double[]{com.dan.kilauea.terrain.Geo.lon(x, z), com.dan.kilauea.terrain.Geo.lat(z)};
            }
        });
        com.dan.kilauea.db.Dienst.sitzungStart(f.getWidth(), f.getHeight(), rd.quality().name(), geladen);
        // Phase 16b: Läufe mit Beben und Messnetz nach KIL_LAUF, KIL_BEBEN, KIL_MESSUNG
        com.dan.kilauea.db.Laeufe.anmelden(rd.player, rd.magma, rd.monitor);
        final java.util.concurrent.atomic.AtomicReference<com.dan.kilauea.db.Zustand> zuletzt = new java.util.concurrent.atomic.AtomicReference<>();
        new javax.swing.Timer(5_000, e -> zuletzt.set(controls.erfassen())).start();
        new javax.swing.Timer(60_000, e -> com.dan.kilauea.db.Dienst.zwischensicherung(controls.erfassen())).start();
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { com.dan.kilauea.db.Dienst.beenden(controls.erfassen()); }
        });
        // Phase 15: Klang (ohne Tonausgabe bleibt er stumm; -Dkilauea.klang=false schaltet ihn ab)
        if (!"false".equals(System.getProperty("kilauea.klang"))) rd.sound.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            com.dan.kilauea.db.Dienst.beenden(zuletzt.get());
            rd.sound.stop();
            scene.stop();
        }, "Kīlauea-Ende"));
        scene.requestFocusInWindow();
        frame = f;
        panel = controls;
        // Phase 17: -Dkilauea.dauerlauf=true startet den Dauerlauf, sobald die Insel steht
        if (Boolean.getBoolean("kilauea.dauerlauf")) {
            javax.swing.Timer warten = new javax.swing.Timer(1000, null);
            warten.addActionListener(e -> {
                if (!rd.terrainReady()) return;
                warten.stop();
                controls.dauerlaufStarten();
            });
            warten.start();
        }
    }

    /**
     * Startet maximiert (MAXIMIZED_BOTH), ohne die Taskleiste zu verdecken. Die normale Größe von
     * 1480 × 900 bleibt als Rückfall; die Schaltfläche „Wiederherstellen“ des FFrame stellt sie wieder her,
     * danach lässt sich das Fenster frei ziehen (wie in Kneiphof und Geyser).
     */
    private static void startMaximized(FFrame f) {
        Rectangle normal = f.getBounds();
        f.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
        try {
            // Die Titelleiste des FFrame führt einen eigenen Maximiert-Zustand; ihn angleichen
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(f);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"),
                        btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true);
                rb.setAccessible(true);
                btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", com.dan.ficons.FIconType.class).invoke(icon, com.dan.ficons.FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                                f.setExtendedState(f.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
                                f.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht angeglichen: " + e);
        }
    }

    /**
     * Das FFrame ist ein durchscheinendes Fenster (runde Ecken). Darin zeigt Windows schwere Elemente wie
     * den GL-Canvas nicht; darum wird es deckend gemacht und die Rundung als Fensterform gesetzt.
     */
    private static void makeOpaque(FFrame f) {
        f.setBackground(BG);
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { reshape(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reshape(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> reshape(f));
    }

    private static void reshape(FFrame f) {
        try {
            boolean max = (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    private KilaueaApp() { }
}
