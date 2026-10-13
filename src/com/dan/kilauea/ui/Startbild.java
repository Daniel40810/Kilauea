package com.dan.kilauea.ui;

import com.dan.fbutton.FButton;
import com.dan.kilauea.atmos.SunClock;
import com.dan.kilauea.db.Dienst;
import com.dan.kilauea.db.Zustand;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.terrain.Place;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Das Startbild vor der Szene (Phase 16, Text Phase 17): Symbol, Titel, Fortschritt beim Bau der Insel, dazu der Stand der Datenbank und
 * die letzte Sitzung. Knöpfe: „Weiter wie zuletzt“ (vorgewählt, Frage 22 = A), „Neu am Gipfel“, „Einrichten …“.
 * Steht die Insel und ist alles klar, geht es nach 3 s von selbst mit der Vorwahl weiter; jede Eingabe hält an. Fehlt das
 * Passwort oder ist die Datenbank nicht eingerichtet, wartet es auf einen Klick.
 */
public final class Startbild extends JDialog {
    /** Die Wahl: laden = Zustand wiederherstellen (zustand ist dann nicht null). */
    public static final class Wahl {
        public final boolean laden;
        public final Zustand zustand;
        Wahl(boolean laden, Zustand zustand) { this.laden = laden; this.zustand = zustand; }
    }

    private static final int SEKUNDEN = 3;
    private static final Color OK = new Color(150, 210, 160), WARN = new Color(230, 190, 120);
    private final JLabel progressText = new JLabel(" "), countLine = new JLabel(" "), dbLine = new JLabel(" "), lastLine = new JLabel(" "),
            noteLine = new JLabel(" ");
    private final JPanel bar = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            g.setColor(new Color(34, 36, 40));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(ControlPanel.ACCENT);
            g.fillRect(0, 0, (int) Math.round(getWidth() * Renderer.vorabFortschritt()), getHeight());
        }
    };
    private final JPanel pwRow = new JPanel(new BorderLayout(8, 0));
    private final JPasswordField pw = new JPasswordField();
    private final FButton weiter = new FButton("Weiter wie zuletzt"), neu = new FButton("Neu am Gipfel"), einrichten = new FButton("Einrichten …"),
            ohne = new FButton("Ohne Datenbank");
    private volatile Zustand letzter;
    private volatile boolean letzterGeladen;
    private Wahl wahl;
    private Timer timer;
    private int rest = SEKUNDEN, viertel;
    private boolean angehalten;
    private AWTEventListener aufmerken;

    private Startbild() {
        super((java.awt.Frame) null, "Kīlauea", true);
        setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout(18, 14)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(26, 22, 30), 0, getHeight(), new Color(10, 11, 14)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ControlPanel.ACCENT);
                g2.fillRect(0, 0, getWidth(), 3);
            }
        };
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(70, 58, 50)),
                BorderFactory.createEmptyBorder(22, 26, 20, 28)));
        BufferedImage icon = AppIcon.load(128);
        if (icon != null) {
            JLabel il = new JLabel(new javax.swing.ImageIcon(icon));
            il.setVerticalAlignment(JLabel.TOP);
            root.add(il, BorderLayout.WEST);
        }
        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("KĪLAUEA");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(ControlPanel.INK);
        JLabel sub = new JLabel("Der ganze Vulkan · vom Gipfel bis unter das Meer");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 14));
        sub.setForeground(ControlPanel.ACCENT);
        JLabel info = new JLabel("<html><body style='width:420px'>Phase 17: Schliff – schnelleres Gelände, Qualitätsstufen, Dauerlauf mit Prüfliste. "
                + "Die App merkt sich Kamera, Zeit, Wetter und "
                + "alle Schalter, eigene Ansichten (Taste V), ein Protokoll und das Schema KILAUEA mit Einrichter. Dazu Leben und Klang, "
                + "Messnetz mit Wärmebild, Drehbücher echter Ausbrüche, Rundflüge, Lava im Meer und der Gipfel zum Gehen. "
                + "Wissenschaftlich angelehnt, keine Gefahrenprognose.</body></html>");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));
        info.setForeground(ControlPanel.MUTED);
        info.setBorder(BorderFactory.createEmptyBorder(10, 0, 6, 0));
        for (JLabel l : new JLabel[]{dbLine, lastLine}) {
            l.setFont(new Font("SansSerif", Font.PLAIN, 13));
            l.setForeground(ControlPanel.INK);
            l.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        }
        noteLine.setFont(new Font("SansSerif", Font.PLAIN, 11));
        noteLine.setForeground(ControlPanel.MUTED);
        progressText.setForeground(ControlPanel.MUTED);
        progressText.setFont(new Font("SansSerif", Font.PLAIN, 11));
        progressText.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        countLine.setForeground(ControlPanel.ACCENT);
        countLine.setFont(new Font("SansSerif", Font.PLAIN, 12));
        countLine.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        bar.setPreferredSize(new Dimension(420, 6));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        pwRow.setOpaque(false);
        pwRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        pw.setBackground(new Color(8, 10, 13));
        pw.setForeground(ControlPanel.INK);
        pw.setCaretColor(ControlPanel.INK);
        pw.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(80, 66, 58)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        FButton conn = new FButton("Verbinden");
        conn.addActionListener(e -> verbinden());
        pw.addActionListener(e -> verbinden());
        pwRow.add(pw, BorderLayout.CENTER);
        pwRow.add(conn, BorderLayout.EAST);
        pwRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        for (Component c : new Component[]{title, sub, info, dbLine, lastLine, noteLine, pwRow, progressText, bar, countLine}) {
            ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            mid.add(c);
        }
        root.add(mid, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.GridLayout(1, 0, 10, 0));
        south.setOpaque(false);
        weiter.addActionListener(e -> fertig(true));
        neu.addActionListener(e -> fertig(false));
        einrichten.addActionListener(e -> {
            stoppen();
            new EinrichterDialog(this, true).setVisible(true);
            neuLaden();
        });
        ohne.addActionListener(e -> {
            Dienst.abschalten();
            neuLaden();
        });
        south.add(weiter);
        south.add(neu);
        south.add(einrichten);
        south.add(ohne);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setPreferredSize(new Dimension(760, 400));
        pack();
        setLocationRelativeTo(null);
        java.util.List<java.awt.Image> icons = AppIcon.images();
        if (!icons.isEmpty()) setIconImages(icons);
        getRootPane().setDefaultButton(null);

        Dienst.beiAenderung(this::aktualisieren);
        aufmerken = ev -> {
            if (!isShowing()) return;
            if (ev instanceof java.awt.event.MouseEvent && ev.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED) return;
            if (ev instanceof KeyEvent ke) {
                if (ke.getID() != KeyEvent.KEY_PRESSED) return;
                if (ke.getKeyCode() == KeyEvent.VK_ENTER && !pwRow.isVisible()) { fertig(weiter.isEnabled()); return; }
            }
            stoppen();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(aufmerken, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        aktualisieren();
        neuLaden();
    }

    /** Zeigt das Startbild und wartet auf die Wahl. */
    public static Wahl zeigen() {
        Startbild s = new Startbild();
        s.timer = new Timer(250, e -> s.tick());
        s.timer.start();
        s.setVisible(true);
        return s.wahl == null ? new Wahl(false, null) : s.wahl;
    }

    private void verbinden() {
        char[] p = pw.getPassword();
        pw.setText("");
        if (p.length == 0) return;
        Dienst.verbinden(p);
        rest = SEKUNDEN;
        angehalten = false;
        neuLaden();
    }

    /** Sucht den letzten Zustand im Hintergrund, sobald die Prüfung der Datenbank fertig ist. */
    private void neuLaden() {
        letzterGeladen = false;
        aktualisieren();
        Thread t = new Thread(() -> {
            Dienst.warte(8000);
            Zustand z = Dienst.lage() == Dienst.Lage.BEREIT ? Dienst.ladeLetzten() : Zustand.laden(Dienst.zustandsDatei());
            letzter = z != null && z.hatKamera() ? z : null;
            letzterGeladen = true;
            SwingUtilities.invokeLater(this::aktualisieren);
        }, "Kilauea-Startbild");
        t.setDaemon(true);
        t.start();
    }

    /** „13.10.2026 18:42 · Kīlauea Iki, zu Fuß · 3. April 10:04 HST“ */
    public static String beschreibe(Zustand z) {
        StringBuilder b = new StringBuilder();
        if (z.gespeichertMs > 0)
            b.append(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm", Locale.GERMANY).format(Instant.ofEpochMilli(z.gespeichertMs).atZone(ZoneId.systemDefault()))).append(" · ");
        double x = z.zahl("kamera.x", 0), zz = z.zahl("kamera.z", 0);
        double[] d = new double[1];
        Place p = Place.nearest(x, zz, d);
        b.append(d[0] < 6000 ? p.label : String.format(Locale.GERMAN, "%.3f° N, %.3f° W", com.dan.kilauea.terrain.Geo.lat(zz), -com.dan.kilauea.terrain.Geo.lon(x, zz)));
        String m = z.text("kamera.modus");
        if ("GEHEN".equals(m)) b.append(", zu Fuß");
        else if ("KREISEN".equals(m)) b.append(", kreisend");
        if (z.hatZeit()) b.append(" · ").append(SunClock.dateLabel((int) Math.round(z.zahl("zeit.tag", 280)))).append(' ')
                .append(SunClock.timeLabel(z.zahl("zeit.stunde", 12))).append(" HST");
        return b.toString();
    }

    private void aktualisieren() {
        Dienst.Lage l = Dienst.lage();
        String db = Dienst.kurz();
        if (l == Dienst.Lage.BEREIT) db += " · " + Dienst.tabellen() + " Tabellen" + (Dienst.fassung().isEmpty() ? "" : " · " + Dienst.fassung());
        dbLine.setText("Datenbank   " + db);
        dbLine.setForeground(l == Dienst.Lage.BEREIT ? OK : l == Dienst.Lage.PRUEFT ? ControlPanel.MUTED : WARN);
        Zustand z = letzter;
        lastLine.setText(!letzterGeladen ? "Zuletzt       wird gesucht …" : z == null ? "Zuletzt       noch keine Sitzung gespeichert" : "Zuletzt       " + beschreibe(z));
        String m = Dienst.meldung();
        noteLine.setText("<html><body style='width:440px'>" + (m.isEmpty() ? " " : ControlPanel.esc(m)) + "</body></html>");
        pwRow.setVisible(l == Dienst.Lage.KEIN_PASSWORT);
        weiter.setEnabled(z != null);
        einrichten.setVisible(l == Dienst.Lage.NICHT_EINGERICHTET || l == Dienst.Lage.BEREIT);
        einrichten.setText(l == Dienst.Lage.BEREIT ? "Einrichter prüfen …" : "Einrichten …");
        ohne.setVisible(l == Dienst.Lage.PRUEFT || l == Dienst.Lage.KEIN_PASSWORT);
        revalidate();
        repaint();
        if (pwRow.isVisible()) pw.requestFocusInWindow();
    }

    /** Läuft der Zähler? Nur wenn nichts mehr zu entscheiden ist. */
    private boolean laeuft() {
        Dienst.Lage l = Dienst.lage();
        return !angehalten && letzterGeladen && Renderer.vorabFortschritt() >= 1
                && (l == Dienst.Lage.BEREIT || l == Dienst.Lage.OFFLINE || l == Dienst.Lage.AUS || l == Dienst.Lage.KEINE_KONFIG);
    }

    private void tick() {
        progressText.setText(Renderer.vorabText());
        bar.repaint();
        if (++viertel % 4 != 0) return;
        if (!laeuft()) {
            rest = SEKUNDEN;
            countLine.setText(angehalten || !letzterGeladen || Renderer.vorabFortschritt() < 1 ? " " : "Bitte wählen.");
            return;
        }
        rest--;
        countLine.setText(rest > 0 ? (letzter != null ? "Weiter wie zuletzt" : "Neu am Gipfel") + " in " + rest + " s (jede Eingabe hält an)" : " ");
        if (rest <= 0) fertig(letzter != null);
    }

    private void stoppen() {
        angehalten = true;
        countLine.setText(" ");
    }

    private void fertig(boolean laden) {
        if (wahl != null) return;
        Zustand z = letzter;
        wahl = new Wahl(laden && z != null, laden ? z : null);
        if (timer != null) timer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(aufmerken);
        dispose();
    }

    @Override public void paint(Graphics g) {
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        super.paint(g);
    }
}
