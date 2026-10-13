package com.dan.kilauea.render;

import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.Place;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.Animator;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

/**
 * Die 3D-Fläche: ein {@link GLCanvas} mit Kern-Profil OpenGL 4, der Animator im Takt des Bildschirms
 * und die Bedienung mit Maus und Tasten.
 * <ul>
 * <li>Links ziehen: um den Drehpunkt kreisen · rechts (oder Mitte) ziehen: verschieben · Rad: zoomen</li>
 * <li>W A S D: über das Land gleiten (Umschalt schneller) · Pfeiltasten: kreisen · Bild auf/ab: zoomen</li>
 * <li>Pos1: ganze Insel · 1 bis 9: Blickpunkte · R: um den Drehpunkt kreisen · G: gehen/fliegen · F12: Foto · F5: Shader neu laden</li>
 * </ul>
 * Mit {@code -Dkilauea.gljpanel=true} zeichnet statt des GLCanvas ein leichtes GLJPanel (Rückfall,
 * falls ein Treiber den Canvas im FFrame nicht zeigt).
 */
public final class SceneView extends JPanel {
    private final Renderer renderer = new Renderer();
    private final java.awt.Component canvas;
    private final GLAutoDrawable drawable;
    public final boolean lightweight = Boolean.getBoolean("kilauea.gljpanel");
    private Animator animator;
    private Consumer<Boolean> orbitListener = b -> { };
    private Consumer<Place> placeListener = p -> { };

    public SceneView() {
        super(new BorderLayout());
        setBackground(new Color(10, 12, 15));
        GLProfile profile;
        try {
            profile = GLProfile.get(GLProfile.GL4);
        } catch (GLException e) {
            throw new GLException("Kein OpenGL-4-Kernprofil verfügbar: " + e.getMessage(), e);
        }
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        caps.setHardwareAccelerated(true);
        if (lightweight) {
            GLJPanel p = new GLJPanel(caps);
            p.addGLEventListener(renderer);
            canvas = p;
            drawable = p;
        } else {
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(renderer);
            canvas = c;
            drawable = c;
        }
        canvas.setFocusable(true);
        add(canvas, BorderLayout.CENTER);
        installInput();
    }

    public Renderer renderer() { return renderer; }
    public void setOrbitListener(Consumer<Boolean> l) { orbitListener = l; }
    public void setPlaceListener(Consumer<Place> l) { placeListener = l; }
    private Consumer<Boolean> thermalListener = b -> { };
    private Consumer<Boolean> soundListener = b -> { };
    public void setSoundListener(Consumer<Boolean> l) { soundListener = l; }

    /** Phase 15: Klang an oder aus (Taste M). */
    public void toggleSound() {
        renderer.sound.muted(!renderer.sound.muted());
        soundListener.accept(!renderer.sound.muted());
    }
    public void setThermalListener(Consumer<Boolean> l) { thermalListener = l; }

    /** Phase 14: Wärmebild an oder aus (Taste T). */
    public void toggleThermal() {
        renderer.thermal = !renderer.thermal;
        thermalListener.accept(renderer.thermal);
    }

    /** Phase 14: Tafel „Was liegt hier?“ für den Boden unter dem Blick (Taste I); noch einmal: weg. */
    public void showGroundInfo() {
        renderer.showInfo(renderer.groundInfo(), renderer.camera.targetX(), renderer.camera.targetZ());
    }

    public void start() {
        animator = new Animator(drawable);
        animator.setUpdateFPSFrames(60, null);
        animator.start();
        canvas.requestFocusInWindow();
    }

    public void stop() { if (animator != null) animator.stop(); }

    @Override
    public boolean requestFocusInWindow() { return canvas.requestFocusInWindow(); }

    /** Die ganze Insel von Süden, schräg von oben. */
    public void goOverview() {
        renderer.camera.autoOrbit = false;
        Place p = Place.MAUNA_LOA;
        renderer.camera.lookAt(p.x() + 8000, 1500, p.z() - 6000, 5, 55, 125_000);
        orbitListener.accept(false);
    }

    /** Zum Blickpunkt eines Ortes fliegen. */
    public void goPlace(Place p) {
        renderer.camera.autoOrbit = false;
        renderer.camera.lookAt(p.x(), Math.max(0, Ground.height(p.x(), p.z())), p.z(), p.viewYaw, p.viewPitch, p.viewDist);
        orbitListener.accept(false);
        placeListener.accept(p);
        if (renderer.placeInfo) renderer.showInfo(com.dan.kilauea.monitor.Lexicon.place(p), p.x(), p.z());
    }

    /** Fliegt vor den Schnitt (Phase 6), wenn er an ist. */
    public void goSection() {
        double[] v = renderer.sectionView();
        if (v == null) return;
        renderer.camera.autoOrbit = false;
        renderer.camera.lookAt(v[0], v[1], v[2], v[3], v[4], v[5]);
        orbitListener.accept(false);
    }

    /** Fliegt zum Punkt (x, z), Blickrichtung bleibt; aus großer Höhe geht es näher heran (Übersichtskarte). */
    public void goTo(double x, double z) {
        OrbitCameraHelper.go(renderer.camera, x, z);
        orbitListener.accept(false);
        requestFocusInWindow();
    }

    /** Zu Fuß an einem Ausgangspunkt beginnen. */
    public void goWalk(com.dan.kilauea.terrain.SummitPaths.Spot s) {
        renderer.camera.startWalk(s.x(), s.z(), s.heading());
        orbitListener.accept(false);
        walkListener.accept(true);
    }

    /** G: gehen, wo die Kamera hinschaut, oder wieder fliegen. */
    public void toggleWalk() {
        var c = renderer.camera;
        if (c.walking) c.stopWalk();
        else c.startWalk(c.targetX(), c.targetZ(), -c.yawDeg());
        walkListener.accept(c.walking);
    }

    private Consumer<Boolean> walkListener = b -> { };

    /** Phase 16: weitere Tasten (V Ansicht merken, F1 Tastenübersicht), vom Bedienfeld gesetzt. */
    private final java.util.Map<Integer, Runnable> keyActions = new java.util.concurrent.ConcurrentHashMap<>();
    public void setKeyAction(int keyCode, Runnable action) {
        if (action == null) keyActions.remove(keyCode); else keyActions.put(keyCode, action);
    }

    /** Phase 16: zu Fuß an (x, z) mit Blick nach heading (Grad) und Neigung (Grad), etwa für einen gespeicherten Zustand. */
    public void walkAt(double x, double z, double headingDeg, double pitchDeg) {
        renderer.camera.startWalk(x, z, headingDeg);
        renderer.camera.wpitch = Math.toRadians(Math.max(-80, Math.min(80, pitchDeg)));
        orbitListener.accept(false);
        walkListener.accept(true);
    }
    public void setWalkListener(Consumer<Boolean> l) { walkListener = l; }

    public void setAutoOrbit(boolean on) { renderer.camera.autoOrbit = on; orbitListener.accept(on); }

    private void installInput() {
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) {
                lx = e.getX(); ly = e.getY();
                canvas.requestFocusInWindow();
            }
            @Override public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lx, dy = e.getY() - ly;
                lx = e.getX(); ly = e.getY();
                double hgt = Math.max(1, canvas.getHeight());
                if (SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown()) {
                    renderer.camera.orbit(-dx * 0.0062, dy * 0.0052);
                    if (renderer.camera.autoOrbit) setAutoOrbit(false);
                } else {
                    renderer.camera.pan(dx / hgt, dy / hgt);
                }
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { renderer.camera.zoom(e.getPreciseWheelRotation()); }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        final KeyAdapter keys = new KeyAdapter() {
            private boolean moveKey(KeyEvent e, boolean down) {
                com.dan.kilauea.camera.OrbitCamera c = renderer.camera;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: c.kFwd = down; return true;
                    case KeyEvent.VK_S: c.kBack = down; return true;
                    case KeyEvent.VK_A: c.kLeft = down; return true;
                    case KeyEvent.VK_D: c.kRight = down; return true;
                    case KeyEvent.VK_SHIFT: c.kRun = down; return true;
                    default: return false;
                }
            }
            @Override public void keyReleased(KeyEvent e) { moveKey(e, false); }
            @Override public void keyPressed(KeyEvent e) {
                if (moveKey(e, true)) return;
                int kc = e.getKeyCode();
                if (kc >= KeyEvent.VK_1 && kc <= KeyEvent.VK_9 && !renderer.camera.walking) {
                    int i = kc - KeyEvent.VK_1;
                    if (i < Place.values().length) goPlace(Place.values()[i]);
                    return;
                }
                switch (kc) {
                    case KeyEvent.VK_LEFT: renderer.camera.orbit(0.06, 0); break;
                    case KeyEvent.VK_RIGHT: renderer.camera.orbit(-0.06, 0); break;
                    case KeyEvent.VK_UP: renderer.camera.orbit(0, 0.04); break;
                    case KeyEvent.VK_DOWN: renderer.camera.orbit(0, -0.04); break;
                    case KeyEvent.VK_PAGE_UP: case KeyEvent.VK_PLUS: case KeyEvent.VK_ADD: renderer.camera.zoom(-1); break;
                    case KeyEvent.VK_PAGE_DOWN: case KeyEvent.VK_MINUS: case KeyEvent.VK_SUBTRACT: renderer.camera.zoom(1); break;
                    case KeyEvent.VK_HOME: goOverview(); break;
                    case KeyEvent.VK_R: setAutoOrbit(!renderer.camera.autoOrbit); break;
                    case KeyEvent.VK_G: toggleWalk(); break;
                    case KeyEvent.VK_T: toggleThermal(); break;
                    case KeyEvent.VK_I: showGroundInfo(); break;
                    case KeyEvent.VK_M: toggleSound(); break;
                    case KeyEvent.VK_F5: renderer.requestShaderReload(); break;
                    case KeyEvent.VK_F12: renderer.photoRequest = true; break;
                    default: {
                        Runnable a = keyActions.get(kc);
                        if (a != null) a.run();
                        break;
                    }
                }
            }
        };
        canvas.addKeyListener(keys);
        canvas.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { releaseKeys(); }
        });
        // Tasten gelten im ganzen Fenster, nicht nur bei Fokus auf der Szene (außer in Textfeldern)
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ev -> {
            if (ev.getSource() == canvas || !canvas.isShowing()) return false;
            if (ev.getID() != KeyEvent.KEY_PRESSED && ev.getID() != KeyEvent.KEY_RELEASED) return false;
            java.awt.Window w = SwingUtilities.getWindowAncestor(canvas);
            if (w == null || !w.isActive() || SwingUtilities.getWindowAncestor(ev.getComponent()) != w) return false;
            java.awt.Component fo = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (fo instanceof javax.swing.text.JTextComponent) return false;
            int kc = ev.getKeyCode();
            boolean ours = kc == KeyEvent.VK_W || kc == KeyEvent.VK_A || kc == KeyEvent.VK_S || kc == KeyEvent.VK_D || kc == KeyEvent.VK_SHIFT
                    || kc == KeyEvent.VK_R || kc == KeyEvent.VK_G || kc == KeyEvent.VK_T || kc == KeyEvent.VK_I || kc == KeyEvent.VK_M || kc == KeyEvent.VK_F12 || kc == KeyEvent.VK_HOME || (kc >= KeyEvent.VK_1 && kc <= KeyEvent.VK_9)
                    || keyActions.containsKey(kc);
            if (!ours) return false;
            if (ev.getID() == KeyEvent.KEY_PRESSED) keys.keyPressed(ev); else keys.keyReleased(ev);
            return kc != KeyEvent.VK_SHIFT;
        });
    }

    private void releaseKeys() {
        com.dan.kilauea.camera.OrbitCamera c = renderer.camera;
        c.kFwd = c.kBack = c.kLeft = c.kRight = c.kRun = c.kUp = c.kDown = false;
    }
}
