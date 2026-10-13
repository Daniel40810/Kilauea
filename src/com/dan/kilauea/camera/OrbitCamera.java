package com.dan.kilauea.camera;

import com.dan.kilauea.render.Mat4;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.Island;

/**
 * Kamera, die um einen Drehpunkt kreist, für eine ganze Insel: vom Kraterrand (20 m) bis zum Blick aus
 * 180 km auf Hawaiʻi. Maus: links ziehen dreht, rechts ziehen verschiebt, das Rad zoomt in Schritten von
 * 12 %. W A S D gleiten über das Land, schneller je höher die Kamera steht. Alle Werte laufen weich
 * ihrem Ziel nach. Die Kamera bleibt über Gelände und Meer (übernommen aus Kneiphof, ohne Rundgang).
 */
public final class OrbitCamera {
    public double tx = 2500, ty = 1000, tz = 1500, yaw = Math.toRadians(205), pitch = Math.toRadians(22), dist = 14000;
    private double cx = tx, cy = ty, cz = tz, cyaw = yaw, cpitch = pitch, cdist = dist;
    public double fovy = 50;
    public boolean autoOrbit;
    public double orbitSpeed = 3.0;
    public static final double MIN_DIST = 20, MAX_DIST = 180_000;
    /** Mindesthöhe des Auges über Boden bzw. Wasser (m). */
    public static final double CLEARANCE = 2.0;
    /** Tauchen (Phase 10): die Kamera darf unter den Meeresspiegel, bis knapp über den Grund. */
    public volatile boolean diving;

    public double ex, ey, ez;
    public volatile boolean kFwd, kBack, kLeft, kRight, kRun, kUp, kDown;
    /** Von der Eingabe gesetzt, wenn jemand die Kamera selbst bewegt (beendet später eine Regiefahrt). */
    public volatile boolean userMoved;

    // ------------------------------------------------------------------ Gehen
    /** Gehen statt fliegen: Augen 1,65 m über dem Boden, W A S D gehen (Umschalt laufen), ziehen schaut. */
    public volatile boolean walking;
    /** Füße (x, Höhe, z), Blickrichtung (Bogenmaß; 0 = Norden, wachsend nach Westen wie yaw) und Neigung. */
    public double wx, wfoot, wz, wyaw, wpitch;
    public static final double EYE = 1.65, WALK = 1.4, RUN = 4.0;
    /** An Wänden entlang gleiten (aus nur für Prüfungen). */
    public boolean slide = true;
    /** Wo man stehen kann (Gelände oder Röhre); vom Renderer gesetzt. */
    public static volatile WalkSurface surface = (x, z, foot) -> Ground.height(x, z);
    /** Was dem Gehen im Weg ist (Wände der Röhre); null = nichts. */
    public static volatile WalkBlock block;

    @FunctionalInterface
    public interface WalkSurface { double height(double x, double z, double footY); }

    @FunctionalInterface
    public interface WalkBlock { boolean blocked(double x, double z, double footY); }

    /** Beginnt zu gehen an (x, z) mit Blick nach heading (Grad von Nord, im Uhrzeigersinn). */
    public void startWalk(double x, double z, double headingDeg) {
        wx = x;
        wz = z;
        wfoot = surface.height(x, z, Ground.height(x, z));
        wyaw = Math.toRadians(-headingDeg);
        wpitch = Math.toRadians(-6);
        walking = true;
        autoOrbit = false;
        placeWalk();
    }

    /** Hört auf zu gehen: die Kamera steigt hinter dem Standort auf. */
    public void stopWalk() {
        if (!walking) return;
        walking = false;
        tx = wx; tz = wz; ty = Math.max(0, wfoot);
        yaw = wyaw; pitch = Math.toRadians(25); dist = 80;
        cx = tx; cz = tz; cy = ty; cyaw = yaw; cpitch = pitch; cdist = 12;
    }

    private void walkStep(double dt) {
        double f = (kFwd ? 1 : 0) - (kBack ? 1 : 0), s = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
        if (f != 0 || s != 0) {
            userMoved = true;
            double len = (kRun ? RUN : WALK) * dt / Math.hypot(f, s);
            double fx = -Math.sin(wyaw), fz = -Math.cos(wyaw), rx = Math.cos(wyaw), rz = -Math.sin(wyaw);
            double mx = (fx * f + rx * s) * len, mz = (fz * f + rz * s) * len;
            int sub = (int) Math.ceil(Math.hypot(mx, mz) / 0.25);
            for (int i = 0; i < sub; i++) {
                double sx = mx / sub, sz = mz / sub;
                if (!tryStep(sx, sz) && !(slide && (tryStep(sx, 0) || tryStep(0, sz)))) break;   // an Wänden entlang gleiten
            }
        }
        placeWalk();
    }

    /** Ein kleiner Schritt; false, wenn er zu steil (rauf oder runter), ins Meer oder gegen eine Wand führt. */
    private boolean tryStep(double dx, double dz) {
        if (dx == 0 && dz == 0) return false;
        double nx = wx + dx, nz = wz + dz, d = Math.hypot(dx, dz);
        double h = surface.height(nx, nz, wfoot);
        if (h < 0.3 && Ground.height(nx, nz) < 0.3) return false;            // nicht ins Meer
        double rise = h - wfoot;
        if (rise > d * 0.85 + 0.05) return false;                            // Wand (steiler als rund 45°)
        if (rise < -(d * 0.9 + 0.02)) return false;                           // Abbruch: nicht über die Kante (steiler als rund 44°)
        WalkBlock b = block;
        if (b != null && b.blocked(nx, nz, h)) return false;
        wx = nx;
        wz = nz;
        wfoot = h;
        return true;
    }

    private void placeWalk() {
        double cp = Math.cos(wpitch);
        ex = wx;
        ey = wfoot + EYE;
        ez = wz;
        cx = ex - Math.sin(wyaw) * cp * 10;
        cy = ey + Math.sin(wpitch) * 10;
        cz = ez - Math.cos(wyaw) * cp * 10;
        cyaw = wyaw;
        cpitch = -wpitch;
        cdist = 10;
        tx = cx; ty = cy; tz = cz; yaw = cyaw; pitch = cpitch; dist = cdist;
    }

    // ------------------------------------------------------------------ Phase 13: Flug und freie Sicht

    /**
     * Ein geplanter Flug zum neuen Ziel: Lage, Blick, Neigung und Abstand gleiten mit einer Kurve fünften Grades
     * (Anfang und Ende ohne Ruck) hinüber; bei weiten Wegen steigt die Kamera dazwischen auf, damit man sieht,
     * wohin es geht. Die Dauer wächst mit dem Logarithmus des Weges (1,5 bis 9 s). Jede eigene Bewegung beendet ihn.
     */
    private final class Flight {
        final double x0, y0, z0, yaw0, pitch0, ld0, x1, y1, z1, yaw1, pitch1, ld1, hop, lift, dur;
        double t;

        Flight(double x1, double y1, double z1, double yaw1, double pitch1, double dist1) {
            x0 = cx; y0 = cy; z0 = cz; yaw0 = cyaw; pitch0 = cpitch; ld0 = Math.log(cdist);
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.yaw1 = yaw0 + wrap(yaw1 - yaw0);
            this.pitch1 = pitch1;
            ld1 = Math.log(dist1);
            double d = Math.hypot(x1 - x0, z1 - z0);
            hop = Math.log(1 + d / (1.2 * Math.max(Math.exp(ld0), Math.exp(ld1))));
            lift = Math.min(Math.toRadians(25), hop * 0.18);
            double tWay = 1.6 + 1.05 * Math.log(1 + d / 300) / Math.log(2) * 0.55;
            double tTurn = 1.2 + Math.abs(this.yaw1 - yaw0) / Math.PI * 1.6;
            double tZoom = 1.2 + Math.abs(ld1 - ld0) * 0.5;
            dur = Math.max(1.5, Math.min(9, Math.max(tWay, Math.max(tTurn, tZoom))));
        }

        /** Hub für freie Sicht über den ganzen Flug, vorab geplant und geglättet (Bogenmaß je Stützstelle). */
        double[] lifts;

        /** Lage zur Zeit u (0..1) in p: x, y, z, Gier, Neigung, Abstand. */
        void at(double u, double[] p) {
            double s = u * u * u * (10 - 15 * u + 6 * u * u), b = Math.sin(Math.PI * s);
            p[0] = x0 + (x1 - x0) * s;
            p[1] = ys != null ? sample(ys, u) : y0 + (y1 - y0) * s;
            p[2] = z0 + (z1 - z0) * s;
            p[3] = yaw0 + (yaw1 - yaw0) * s;
            p[4] = pitch0 + (pitch1 - pitch0) * s + lift * b;
            p[5] = Math.exp(ld0 + (ld1 - ld0) * s + hop * b);
        }

        /** Höhe des Drehpunkts über den Flug: folgt dem Boden unter ihm, nie darunter (sonst blickte man auf einen Punkt im Fels). */
        double[] ys;

        double sample(double[] a, double u) {
            double f = u * (a.length - 1);
            int i = (int) Math.min(a.length - 2, Math.floor(f));
            return a[i] + (a[i + 1] - a[i]) * (f - i);
        }

        /** Aufweiten (Maximum über ±w) und mitteln: das Ergebnis liegt überall mindestens beim Ausgangswert. */
        double[] envelope(double[] raw, int w) {
            int k = raw.length - 1;
            double[] dil = new double[k + 1], out = new double[k + 1];
            for (int i = 0; i <= k; i++) {
                double m = -Double.MAX_VALUE;
                for (int j = Math.max(0, i - w); j <= Math.min(k, i + w); j++) m = Math.max(m, raw[j]);
                dil[i] = m;
            }
            for (int i = 0; i <= k; i++) {
                double sum = 0;
                for (int j = i - w; j <= i + w; j++) sum += dil[Math.max(0, Math.min(k, j))];
                out[i] = sum / (2 * w + 1);
            }
            return out;
        }

        void plan(double startLift) {
            int k = 120;
            int w = Math.max(4, (int) Math.ceil(0.6 / dur * k));
            double[] raw = new double[k + 1], p = new double[6];
            ys = null;
            double[] gy = new double[k + 1];
            for (int i = 0; i <= k; i++) {
                at(i / (double) k, p);
                gy[i] = Math.max(p[1], diving ? Ground.height(p[0], p[2]) : Math.max(0, Ground.height(p[0], p[2])));
            }
            ys = envelope(gy, w);
            // vom Anfang und zum Ende stetig: Anfang und Ende gleiten weich (ohne Ruck) in die festen Werte, auch wenn
            // ein Blick seinen Drehpunkt bewusst unter die Oberfläche legt (etwa auf einen Kraterboden)
            int my = Math.min(k / 2, Math.max(w, (int) Math.ceil(1.0 / dur * k)));
            for (int i = 0; i <= my; i++) {
                double a = i / (double) my, sm = a * a * a * (10 - 15 * a + 6 * a * a);
                ys[i] = y0 + (ys[i] - y0) * sm;
                if (k - i > i) ys[k - i] = y1 + (ys[k - i] - y1) * sm;
            }
            // die Abtastungen sind unabhängig: parallel, damit der Flugbeginn nicht stockt
            // jede zweite Stützstelle wird abgetastet, die übrigen nehmen das Maximum ihrer Nachbarn
            java.util.stream.IntStream.rangeClosed(0, k / 2).parallel().forEach(h -> {
                double[] q = new double[6];
                at(2 * h / (double) k, q);
                raw[2 * h] = liftFor(q[0], q[1], q[2], q[3], q[4], q[5]);
            });
            for (int i = 1; i < k; i += 2) raw[i] = Math.max(raw[i - 1], raw[i + 1]);
            raw[0] = Math.max(raw[0], startLift);
            // Hüllkurve mit begrenzter Steigung (höchstens 20° je Sekunde Flug), dann geglättet [A]
            double d = Math.toRadians(20) * dur / k;
            double[] e = raw.clone();
            for (int i = k - 1; i >= 0; i--) e[i] = Math.max(e[i], e[i + 1] - d);
            for (int i = 1; i <= k; i++) e[i] = Math.max(e[i], e[i - 1] - d);
            lifts = envelope(e, w);
            // stetig aus dem Zustand davor: der Unterschied am Anfang klingt über die ersten Stützstellen ab
            double off = startLift - lifts[0];
            int m = Math.min(k, Math.max(w, (int) Math.ceil(1.2 / dur * k)));
            for (int i = 0; i <= m; i++) {
                double a = i / (double) m, sm = a * a * (3 - 2 * a);
                lifts[i] = Math.max(raw[i], lifts[i] + off * (1 - sm));
            }
        }

        double liftAt(double u) { return sample(lifts, u); }

        /** true, solange der Flug läuft. */
        boolean step(double dt) {
            t = Math.min(dur, t + dt);
            double[] p = new double[6];
            at(t / dur, p);
            cx = p[0]; cy = p[1]; cz = p[2]; cyaw = p[3]; cpitch = p[4]; cdist = p[5];
            double before = liftNow;
            liftNow = liftAt(t / dur);
            liftVel = (liftNow - before) / Math.max(1e-4, dt);
            liftAhead = 0;
            return t < dur;
        }
    }

    private Flight flight;
    /** Freie Sicht: die Kamera hebt sich, wenn Gelände zwischen Auge und Drehpunkt stünde (Phase 13). */
    public boolean keepView = true;

    /** Läuft gerade ein Flug? */
    public boolean flying() { return flight != null; }

    /** Wie weit der laufende Flug ist (0..1), 1 ohne Flug. */
    public double flightProgress() { Flight f = flight; return f == null ? 1 : f.t / f.dur; }

    public static double wrap(double a) {
        a = (a + Math.PI) % (2 * Math.PI);
        if (a < 0) a += 2 * Math.PI;
        return a - Math.PI;
    }

    /** Laufenden Flug beenden: die Kamera bleibt, wo sie gerade ist. */
    private void endFlight() {
        if (flight == null) return;
        flight = null;
        tx = cx; ty = cy; tz = cz; yaw = cyaw; pitch = cpitch; dist = cdist;
    }

    public void orbit(double dYaw, double dPitch) {
        endFlight();
        userMoved = true;
        if (walking) {
            wyaw += dYaw;
            wpitch = Math.max(Math.toRadians(-80), Math.min(Math.toRadians(80), wpitch - dPitch));
            return;
        }
        yaw += dYaw;
        pitch = Math.max(Math.toRadians(-8), Math.min(Math.toRadians(89), pitch + dPitch));
    }

    public void zoom(double steps) {
        endFlight();
        userMoved = true;
        if (walking) return;
        dist = Math.max(MIN_DIST, Math.min(MAX_DIST, dist * Math.pow(1.12, steps)));
    }

    /** Verschiebt den Drehpunkt in der Bildebene; dx, dy in Bruchteilen der Bildhöhe. */
    public void pan(double dx, double dy) {
        endFlight();
        userMoved = true;
        if (walking) return;
        double s = dist * Math.tan(Math.toRadians(fovy) / 2) * 2;
        double rx = Math.cos(yaw), rz = -Math.sin(yaw);
        double fx = -Math.sin(yaw), fz = -Math.cos(yaw);
        tx += -dx * s * rx + dy * s * fx;
        tz += -dx * s * rz + dy * s * fz;
        limit();
    }

    /** Zum Ziel fliegen (Phase 13: als geplanter Flug; {@link #snap} springt sofort hin). */
    public void lookAt(double x, double y, double z, double yawDeg, double pitchDeg, double d) {
        if (walking) {
            walking = false;
            cx = x; cy = y; cz = z; cyaw = Math.toRadians(yawDeg); cpitch = Math.toRadians(pitchDeg); cdist = d;
        }
        tx = x; ty = y; tz = z;
        yaw = cyaw + wrap(Math.toRadians(yawDeg) - cyaw); pitch = Math.toRadians(pitchDeg); dist = d;
        flight = new Flight(tx, ty, tz, yaw, pitch, dist);
        if (keepView) flight.plan(liftNow);
    }

    /** Sofort an das Ziel springen (beim Start und für Standbilder). */
    public void snap() {
        flight = null;
        liftNow = keepView ? liftFor(tx, ty, tz, yaw, pitch, dist) : 0;
        liftVel = 0; liftAhead = 0;
        if (walking) { placeWalk(); return; }
        cx = tx; cy = ty; cz = tz; cyaw = yaw; cpitch = pitch; cdist = dist; place();
    }

    /** Kamera angehalten (für Standbilder mitten im Flug): update() ändert nichts. */
    public volatile boolean hold;

    public void update(double dt) {
        if (hold) return;
        lastDt = Math.max(1e-4, dt);
        if (walking) { walkStep(dt); return; }
        if (flight != null) {
            if (kFwd || kBack || kLeft || kRight || autoOrbit) endFlight();
            else {
                if (!flight.step(dt)) flight = null;
                place();
                return;
            }
        }
        if (autoOrbit) yaw += Math.toRadians(orbitSpeed) * dt;
        flyStep(dt);
        double k = 1 - Math.exp(-dt * 6.0);
        cx += (tx - cx) * k; cy += (ty - cy) * k; cz += (tz - cz) * k;
        cyaw += wrap(yaw - cyaw) * k; cpitch += (pitch - cpitch) * k;
        cdist = Math.exp(Math.log(cdist) + (Math.log(dist) - Math.log(cdist)) * k);
        place();
    }

    private void flyStep(double dt) {
        double f = (kFwd ? 1 : 0) - (kBack ? 1 : 0), s = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
        if (f == 0 && s == 0) return;
        userMoved = true;
        double above = ey - Math.max(0, Ground.height(ex, ez));
        double sp = flySpeed(above, dist) * (kRun ? 3.0 : 1.0) * dt / Math.max(1.0, Math.hypot(f, s));
        double fx = -Math.sin(yaw), fz = -Math.cos(yaw), rx = Math.cos(yaw), rz = -Math.sin(yaw);
        tx += (fx * f + rx * s) * sp;
        tz += (fz * f + rz * s) * sp;
        limit();
    }

    /**
     * Fluggeschwindigkeit (m/s) nach der Höhe des Auges über Grund (Phase 13): dicht am Boden langsam (8 m/s),
     * in 1 km Höhe rund 700 m/s, aus 60 km Höhe quert man die Insel in wenigen Sekunden. Der Abstand zum Drehpunkt
     * begrenzt nach oben, damit ein naher Blick bei steiler Neigung nicht davonschießt. [G]
     */
    public static double flySpeed(double aboveGround, double dist) {
        double h = Math.max(1.0, Math.min(aboveGround, dist));
        return Math.max(8.0, 0.7 * h);
    }

    private void limit() {
        tx = Math.max(Island.X0 * 0.95, Math.min(Island.X1 * 0.95, tx));
        tz = Math.max(Island.Z0 * 0.95, Math.min(Island.Z1 * 0.95, tz));
        ty = diving ? Ground.height(tx, tz) + 1.0 : Math.max(0, Ground.height(tx, tz));
    }

    private void place() {
        double p = cpitch;
        if (keepView && !diving && flight == null) {
            // ohne Flug: Ziel ist der größte nötige Hub der nächsten Sekunden (Kreisen und Nachlauf der Glättung);
            // der Hub steigt mit begrenzter Beschleunigung dorthin, nur echter Sichtverlust hebt sofort
            liftGoal = lookAheadNeed();
            double need = liftFor(cx, cy, cz, cyaw, cpitch, cdist);
            double goal = Math.max(need, liftGoal), d = goal - liftNow;
            double acc = Math.toRadians(10), vmax = Math.toRadians(10);
            double vWant = Math.signum(d) * Math.min(vmax, Math.sqrt(2 * acc * Math.abs(d) * 0.8));
            if (d < 0) vWant = Math.max(vWant, -Math.toRadians(4));
            liftVel += Math.max(-acc * lastDt, Math.min(acc * lastDt, vWant - liftVel));
            liftNow += liftVel * lastDt;
            if (liftNow < need) { liftNow = need; liftVel = Math.max(liftVel, 0); }
            if (liftNow < 0) { liftNow = 0; liftVel = 0; }
        } else if (!keepView || diving) liftNow = 0;
        p = Math.min(Math.toRadians(88), cpitch + liftNow);
        lastLift = p - cpitch;
        double cp = Math.cos(p);
        ex = cx + Math.sin(cyaw) * cp * cdist;
        ez = cz + Math.cos(cyaw) * cp * cdist;
        ey = cy + Math.sin(p) * cdist;
        double floor = diving ? Ground.height(ex, ez) + 0.8 : Math.max(0.0, Ground.height(ex, ez)) + CLEARANCE;
        if (ey < floor) ey = floor;
    }

    /** Hub für freie Sicht (Bogenmaß): so viel, wie die Neigung steigen muss, dass kein Gelände zwischen Drehpunkt und Auge steht. */
    private double liftNow, liftVel, liftGoal, liftAhead, lastDt = 1 / 60.0;

    /**
     * Größter nötiger Hub über die nächsten 4 s: Kreisen mit der Drehgeschwindigkeit und der Zielblick der Glättung.
     * Je Bild wird nur eine der 17 Vorschau-Lagen neu abgetastet (rollend), das Maximum gilt über alle.
     */
    private double lookAheadNeed() {
        if (liftAhead <= 0) {
            // nach einem Flug oder Sprung einmal ganz, danach rollend
            liftAhead = 1;
            for (int i = 0; i < ahead.length - 1; i++) lookAheadNeed();
        }
        int j = aheadIdx = (aheadIdx + 1) % ahead.length;
        if (j == 0) ahead[0] = liftFor(tx, ty, tz, yaw, pitch, dist);
        else {
            int kk = (j + 1) / 2;
            double s = 0.5 * kk, w = 1 - Math.exp(-s * 6.0), turn = autoOrbit ? Math.toRadians(orbitSpeed) * s : 0;
            ahead[j] = (j & 1) == 1
                    ? liftFor(cx + (tx - cx) * w, cy + (ty - cy) * w, cz + (tz - cz) * w, cyaw + wrap(yaw - cyaw) * w + turn,
                        cpitch + (pitch - cpitch) * w, Math.exp(Math.log(cdist) + (Math.log(dist) - Math.log(cdist)) * w))
                    : (autoOrbit ? liftFor(tx, ty, tz, yaw + turn, pitch, dist) : 0);
        }
        double m = 0;
        for (double v : ahead) m = Math.max(m, v);
        return m;
    }

    private final double[] ahead = new double[17];
    private int aheadIdx;

    /**
     * Nötiger Hub bei Ziel (x, y, z), Gier, Neigung und Abstand, mit 0,5° Reserve; 0, wenn die Sicht frei ist.
     * Das Gelände wird nur einmal entlang der Blickrichtung abgetastet (alle 4 m, höchstens 320 Proben, 1,5 m Reserve):
     * Die Sicht ist bei Neigung p frei, wenn tan p über jeder Steigung (Gelände − Drehpunkt) / waagrechter Abstand liegt,
     * im Bereich 5 % … 100 % der waagrechten Strecke dist·cos p. Mit steigender Neigung wird die Strecke kürzer und
     * die Bedingung leichter, also lässt sich die kleinste freie Neigung ohne neue Höhenabfragen halbieren. [A]
     */
    public static double liftFor(double x, double y, double z, double yaw, double pitch, double dist) {
        if (dist > 60_000) return 0;
        double d0 = Math.cos(pitch) * dist;
        if (d0 < 1) return 0;
        int n = (int) Math.max(64, Math.min(320, d0 / 4));
        double sx = Math.sin(yaw), sz = Math.cos(yaw);
        double[] slope = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            double r = d0 * i / n;
            slope[i] = (Ground.height(x + sx * r, z + sz * r) - y + 1.5) / r;
        }
        if (free(slope, d0, n, pitch, dist)) return 0;
        double lo = pitch, hi = Math.toRadians(88);
        if (!free(slope, d0, n, hi, dist)) return hi - pitch;
        for (int i = 0; i < 14; i++) {
            double m = 0.5 * (lo + hi);
            if (free(slope, d0, n, m, dist)) hi = m; else lo = m;
        }
        return Math.min(Math.toRadians(88), hi + Math.toRadians(0.5)) - pitch;
    }

    /** Freie Sicht bei Neigung p mit den Steigungen einer Abtastung über die waagrechte Strecke d0. */
    private static boolean free(double[] slope, double d0, int n, double p, double dist) {
        double d = Math.cos(p) * dist, tp = Math.tan(p);
        int i0 = (int) Math.ceil(0.05 * d / d0 * n), i1 = (int) Math.min(n, Math.floor(d / d0 * n));
        for (int i = Math.max(1, i0); i <= i1; i++) if (slope[i] > tp) return false;
        return true;
    }

    /** Stünde bei Neigung p Gelände zwischen Drehpunkt und Auge? (wie {@link #liftFor}, für Prüfungen) */
    static boolean blockedAt(double x, double y, double z, double yaw, double p, double dist) {
        return liftFor(x, y, z, yaw, p, dist) > 0;
    }

    /** Wie weit die freie Sicht die Neigung zuletzt angehoben hat (Bogenmaß). */
    public double lastLift;

    /**
     * Schneidet die Strecke vom Drehpunkt (x0, y0, z0) zum Auge Gelände? Die ersten 6 % am Drehpunkt zählen
     * nicht (er liegt selbst auf dem Boden); sonst darf das Gelände nirgends mehr als 0,5 m über die Strecke ragen.
     */
    public static boolean lineBlocked(double x0, double y0, double z0, double x1, double y1, double z1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = (int) Math.max(24, Math.min(128, len / 20));
        for (int i = 1; i <= n; i++) {
            double t = 0.06 + 0.94 * i / n;
            double x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t, y = y0 + (y1 - y0) * t;
            if (Ground.height(x, z) > y + 0.5) return true;
        }
        return false;
    }

    /** Drehpunkt (x, y, z) der Kamera, wie er gerade gezeichnet wird. */
    public double[] lookPoint() { return new double[]{cx, cy, cz}; }

    public float[] view() { return Mat4.lookAt(ex, ey, ez, cx, cy, cz); }

    /** Nahe Ebene der Projektion (m). */
    public double near() { return walking ? 0.08 : Math.max(0.3, Math.min(30.0, cdist * 0.003)); }

    public float[] projection(double aspect, boolean reversedZ) {
        double near = near();
        return Mat4.perspective(fovy, aspect, near, 600_000, reversedZ);
    }

    public double distance() { return cdist; }
    public double targetX() { return cx; }
    public double targetZ() { return cz; }

    public double[] forward() {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez;
        double l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        return new double[]{fx / l, fy / l, fz / l};
    }

    public double yawDeg() { return (Math.toDegrees(cyaw) % 360 + 360) % 360; }
    public double pitchDeg() { return Math.toDegrees(cpitch); }
}
