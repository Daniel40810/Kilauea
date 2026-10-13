package com.dan.kilauea.magma;

import com.dan.kilauea.terrain.Geo;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Das Magmasystem unter dem Gipfel, ohne Grafik: ein qualitatives Modell, das zu den Beobachtungen passt,
 * keine Vorhersage.
 * <ul>
 * <li><b>Speicher</b>: Halemaʻumaʻu-Speicher rund 1–2 km unter dem Krater, Südcaldera-Speicher rund
 * 3–5 km tief, etwas südlich [L, USGS Volcano Watch Januar 2024]. Volumen und Zusammendrückbarkeit [G]:
 * so gewählt, dass 1 Mio. m³ Mehrvolumen im kleinen Speicher rund 1 MPa ausmachen.</li>
 * <li><b>Zufluss</b> aus dem Hotspot in den Südcaldera-Speicher, im Mittel rund 0,1 km³ im Jahr [L,
 * Größenordnung nach USGS]; vom Südcaldera- in den Halemaʻumaʻu-Speicher nach dem Druckunterschied.</li>
 * <li><b>Gang</b>: Übersteigt der Druck im Südcaldera-Speicher 10 MPa [A, Zugfestigkeit des Gesteins
 * plus Auflast, Größenordnung], bricht ein Gang in eine Riftzone aus (Ostrift 70 %, Südwestrift 30 %
 * [G]) und läuft, bis der Überdruck über 2 MPa auf 15 % gefallen ist; dann erstarrt er.</li>
 * <li><b>Gipfelschlot</b> (wahlweise, für die Drehbücher): öffnet bei hohem Druck im
 * Halemaʻumaʻu-Speicher und schließt, wenn er gefallen ist — so entstehen Episoden wie 2024–25.</li>
 * <li><b>Beben</b>: Grundrauschen unter Caldera und Südflanke; mehr, wenn der Druck steigt, nahe der
 * Bruchgrenze und beim Absacken; ein wandernder Schwarm an der Spitze des Gangs. Magnituden nach
 * Gutenberg–Richter mit b = 1 [L].</li>
 * <li><b>Hebung und Neigung</b> an der Oberfläche nach Mogi (Punktquelle im elastischen Halbraum,
 * Poissonzahl 0,25) [L], Summe über die Speicher.</li>
 * </ul>
 * Zeit in Sekunden; {@link #step} rechnet in Schritten von höchstens 10 Minuten. Gleicher Startwert,
 * gleicher Ablauf.
 */
public final class MagmaSystem {
    /** Halemaʻumaʻu-Speicher: rund 1,5 km unter dem Kraterboden (830 m), nördlich über dem Südcaldera-Speicher. */
    public final Reservoir hmm = new Reservoir("Halemaʻumaʻu-Speicher", 19.4090, -155.2790, 830 - 1500, 1.0e9, 1.0e-9, 700, 350);
    /** Südcaldera-Speicher: rund 4 km unter der Oberfläche (1 100 m). */
    public final Reservoir sc = new Reservoir("Südcaldera-Speicher", 19.3960, -155.2770, 1100 - 4000, 10.0e9, 1.0e-9, 1600, 700);
    public final List<Reservoir> reservoirs = List.of(hmm, sc);

    /** Zufluss aus der Tiefe (m³/s): 0,1 km³ im Jahr. */
    public static final double SUPPLY = 0.1e9 / (365.25 * 86400);
    /** Druck, bei dem ein Gang ausbricht, und bei dem er stehen bleibt (Pa). */
    public static final double DIKE_START = 10e6, DIKE_ARREST = 2e6;
    /** Leitwerte (m³/s je Pa): Südcaldera → Halemaʻumaʻu (Zeitkonstante einige Tage), Speicher → Gang (rund 300 m³/s am Anfang). */
    static final double K_LINK = 1.2e-6, K_DIKE = 3.75e-5;
    /** Gipfelschlot: öffnet bei 3 MPa, schließt bei 1 MPa, Abfluss (m³/s je Pa). */
    static final double VENT_OPEN = 3e6, VENT_CLOSE = 1e6, K_VENT = 2e-5;

    /** Zufluss als Vielfaches des Mittels (Drehbücher, Knopf „Zufluss verstärken“). */
    public double supplyFactor = 1;
    /** Gipfelschlot ein (Episoden) oder zu. */
    public boolean summitVent;

    private final SplittableRandom rnd;
    private double time;
    private double supplied, erupted, intruded;
    private boolean ventOpen;
    private Dike dike;
    private final List<Dike> dikes = new ArrayList<>();
    private final List<Quake> quakes = new ArrayList<>();
    private double prevPsc, rateSc;
    /** Erwartete Beben, die noch nicht gefallen sind (Bruchteile). */
    private double owedSummit, owedDike, owedFlank;
    /** Welche Riftzone der nächste Gang nimmt (null = zufällig). */
    public Rift nextRift;

    public MagmaSystem(long seed) {
        rnd = new SplittableRandom(seed);
        // Ruhezustand wie heute: Speicher halb gefüllt bis zur Bruchgrenze [G]
        sc.dV = 0.45 * DIKE_START * sc.volume * sc.beta;
        hmm.dV = sc.pressure() * hmm.volume * hmm.beta;
        prevPsc = sc.pressure();
        startSc = sc.dV;
        startHmm = hmm.dV;
    }

    private final double startSc, startHmm;

    // ------------------------------------------------------------------ Ablauf

    /** Rechnet dt Sekunden weiter. */
    public void step(double dt) {
        while (dt > 1e-9) {
            double h = Math.min(dt, 600);
            sub(h);
            dt -= h;
        }
    }

    private void sub(double h) {
        // Zufluss in den Südcaldera-Speicher
        double qIn = SUPPLY * supplyFactor;
        sc.dV += qIn * h;
        supplied += qIn * h;
        // Ausgleich zum Halemaʻumaʻu-Speicher (halbimplizit, damit große Schritte stabil bleiben)
        double ksc = 1 / (sc.volume * sc.beta), khm = 1 / (hmm.volume * hmm.beta);
        double dp = sc.pressure() - hmm.pressure();
        double q = K_LINK * dp / (1 + K_LINK * h * (ksc + khm));
        sc.dV -= q * h;
        hmm.dV += q * h;
        // Gipfelschlot
        if (summitVent) {
            if (!ventOpen && hmm.pressure() > VENT_OPEN) ventOpen = true;
            if (ventOpen && hmm.pressure() < VENT_CLOSE) ventOpen = false;
        } else ventOpen = false;
        if (ventOpen) {
            double qv = K_VENT * Math.max(0, hmm.pressure()) / (1 + K_VENT * h * khm);
            hmm.dV -= qv * h;
            erupted += qv * h;
        }
        // Phase 12: ausgelöster Gang mit vorgegebenem Tempo (Drehbuch, freier Ausbruch)
        if (dike != null && dike.forcedSpeed > 0) {
            double grow = Math.min(dike.forcedSpeed * h, Math.max(0, dike.target - dike.length()));
            double dv = grow * (dike.top - dike.bottom) * dike.width;
            // aus beiden Speichern nach ihrem Inhalt; der Gipfel sinkt dabei, wie 2018 [A]
            sc.dV -= dv * 0.85;
            hmm.dV -= dv * 0.15;
            dike.volume += dv;
            intruded += dv;
            dike.speed = grow / h;
            if (dike.arrived()) {
                dike.speed = 0;
                if (!holdDike) { dike.active = false; dike = null; }
            }
        } else
        // Gang
        if (dike == null && sc.pressure() > DIKE_START) {
            Rift r = nextRift != null ? nextRift : (rnd.nextDouble() < 0.7 ? Rift.EAST : Rift.SOUTHWEST);
            nextRift = null;
            dike = new Dike(r, 1500, -800, -2800, 1.5);
            dikes.add(dike);
        }
        if (dike != null && dike.forcedSpeed <= 0) {
            double over = sc.pressure() - DIKE_ARREST;
            double qd = over > 0 ? K_DIKE * over / (1 + K_DIKE * h * ksc) : 0;
            double before = dike.length();
            double room = (dike.rift.length() - 1500) * (dike.top - dike.bottom) * dike.width - dike.volume;
            qd = Math.min(qd, Math.max(0, room) / h);
            sc.dV -= qd * h;
            dike.volume += qd * h;
            intruded += qd * h;
            dike.speed = (dike.length() - before) / h;
            // Der Gang erstarrt, wenn der Nachschub nachlässt: unter 15 % des Anfangsüberdrucks
            if (qd * h < 1 || over < 0.15 * (DIKE_START - DIKE_ARREST)) {
                dike.active = false;
                dike.speed = 0;
                dike = null;
            }
        }
        // Beben
        double psc = sc.pressure();
        rateSc = (psc - prevPsc) / h;                                     // Pa/s
        prevPsc = psc;
        quakes(h);
        time += h;
    }

    private void quakes(double h) {
        double perHour = h / 3600;
        // Gipfel: Grundrauschen 0,5 je Stunde; mehr bei steigendem Druck (je 1 MPa/Tag das Zehnfache) und
        // nahe der Bruchgrenze; beim Absacken (fallender Druck) ebenfalls
        double mpaDay = rateSc * 86400 / 1e6;
        double near = Math.pow(Math.max(0, sc.pressure() / DIKE_START), 6);
        double summitRate = 0.5 * (1 + 10 * Math.abs(mpaDay) + 8 * near);
        owedSummit += summitRate * perHour;
        owedFlank += 0.3 * perHour;
        // Gang: Schwarm an der Spitze, je 0,1 m/s rund 60 Beben in der Stunde
        if (dike != null && dike.speed > 0) owedDike += 600 * dike.speed * perHour;
        while (owedSummit >= 1) { owedSummit -= 1; summitQuake(); }
        while (owedFlank >= 1) { owedFlank -= 1; flankQuake(); }
        while (owedDike >= 1) { owedDike -= 1; dikeQuake(); }
    }

    private double gr(double mMin, double mMax) {
        // Gutenberg–Richter, b = 1
        double m = mMin - Math.log10(1 - rnd.nextDouble());
        return Math.min(m, mMax);
    }

    private void summitQuake() {
        double a = rnd.nextDouble() * Math.PI * 2, r = 1500 * Math.sqrt(rnd.nextDouble());
        boolean deep = rnd.nextDouble() < 0.5;
        double cx = deep ? sc.x : hmm.x, cz = deep ? sc.z : hmm.z;
        double y = (deep ? sc.y : hmm.y) + (rnd.nextDouble() - 0.5) * 2000;
        add(new Quake(time, cx + Math.cos(a) * r, Math.min(y, 300), cz + Math.sin(a) * r, gr(0.8, 4.0), Quake.Kind.SUMMIT));
    }

    private void flankQuake() {
        // Südflanke: Gleitfläche 10–12 km tief [L], zwischen Hilina Pali und Küste
        double lat = 19.28 + rnd.nextDouble() * 0.08, lon = -155.35 + rnd.nextDouble() * 0.30;
        double y = -(10000 + rnd.nextDouble() * 2000) + (rnd.nextDouble() < 0.3 ? 4000 * rnd.nextDouble() : 0);
        add(new Quake(time, Geo.x(lat, lon), y, Geo.z(lat), gr(1.0, 4.5), Quake.Kind.FLANK));
    }

    private void dikeQuake() {
        double s = 1500 + dike.length() - rnd.nextDouble() * 1200;
        double[] p = dike.rift.at(s);
        double ang = rnd.nextDouble() * Math.PI * 2, r = 500 * rnd.nextDouble();
        double y = dike.bottom + (dike.top - dike.bottom) * rnd.nextDouble();
        add(new Quake(time, p[0] + Math.cos(ang) * r, y, p[1] + Math.sin(ang) * r, gr(1.0, 4.5), Quake.Kind.DIKE));
    }

    private void add(Quake q) {
        quakes.add(q);
        if (quakes.size() > 20000) quakes.subList(0, 5000).clear();
    }

    // ------------------------------------------------------------------ Abfragen

    public double time() { return time; }

    /** Zugeflossen, ausgeflossen (Gipfelschlot), in Gänge gegangen (m³). */
    public double supplied() { return supplied; }
    public double erupted() { return erupted; }
    public double intruded() { return intruded; }

    /** Gespeichertes Mehrvolumen beider Speicher (m³). */
    public double stored() { return sc.dV + hmm.dV; }

    /** Der Gang, der gerade läuft (oder null), und alle bisherigen. */
    public Dike activeDike() { return dike; }
    public List<Dike> dikes() { return dikes; }

    public boolean ventOpen() { return ventOpen; }

    /** Beben ab Zeit t0 (s). */
    public List<Quake> quakesSince(double t0) {
        List<Quake> r = new ArrayList<>();
        for (int i = quakes.size() - 1; i >= 0 && quakes.get(i).t() >= t0; i--) r.add(quakes.get(i));
        java.util.Collections.reverse(r);
        return r;
    }

    public int quakeCount() { return quakes.size(); }

    // ------------------------------------------------------------------ Phase 12: Drehbücher

    /** Ein ausgelöster Gang bleibt nach der Ankunft aktiv (er speist den Ausbruch), bis {@link #endDike}. */
    private boolean holdDike;

    /**
     * Einen Gang auslösen (Phase 12): er beginnt an der Wurzel beim Südcaldera-Speicher und läuft mit speed
     * (m/s) entlang der Riftzone, bis seine Spitze s Meter vom Gipfel entfernt ist (Bogenlänge der Riftzone).
     * 2018 lief der Gang in rund vier Tagen 20 km weit [L, USGS]; das sind rund 0,06 m/s. Ein laufender Gang
     * wird beendet.
     */
    public Dike intrude(Rift r, double s, double speed) { return intrude(r, s, speed, 1.5); }

    /** Wie {@link #intrude(Rift, double, double)}, mit der Dicke des Gangs (m); sie bestimmt, wie viel Magma er den Speichern nimmt. */
    public Dike intrude(Rift r, double s, double speed, double width) {
        endDike();
        Dike d = new Dike(r, 1500, -800, -2800, width);
        d.target = Math.max(0, Math.min(r.length(), s) - 1500);
        d.forcedSpeed = speed;
        dike = d;
        dikes.add(d);
        holdDike = true;
        return d;
    }

    /** Den laufenden Gang erstarren lassen (der Ausbruch ist vorbei). */
    public void endDike() {
        if (dike != null) { dike.active = false; dike.speed = 0; }
        dike = null;
        holdDike = false;
    }

    /**
     * Magma entnehmen, das an der Oberfläche ausgetreten ist (m³ dichtes Gestein), aus dem Halemaʻumaʻu-Speicher
     * (Gipfel, Phase 12) oder dem Südcaldera-Speicher (Riftzone). Der Boden sinkt und kippt dabei.
     */
    public void withdraw(double volume, boolean summit) {
        if (volume <= 0) return;
        (summit ? hmm : sc).dV -= volume;
        erupted += volume;
    }

    /**
     * Ein Beben von außen eintragen (Phase 12): die Einsturzbeben am Gipfel 2018 hatten fast täglich
     * Magnitude 5 bis 5,4 [L, USGS]. Lage x, z, Höhe y (m), Magnitude.
     */
    public void event(double x, double y, double z, double mag) {
        add(new Quake(time, x, y, z, mag, Quake.Kind.COLLAPSE));
    }

    /** Magma zuführen (m³), etwa um den Gipfel vor einer Episode schneller zu heben. */
    public void inject(double volume, boolean summit) {
        (summit ? hmm : sc).dV += volume;
        supplied += volume;
    }

    /** Zustand der Speicher und Gänge, um nach einem Drehbuch zurückzukehren. */
    public record Snapshot(double sc, double hmm, double supplyFactor, boolean summitVent, int dikes, double supplied, double erupted, double intruded) { }

    public Snapshot snapshot() {
        return new Snapshot(sc.dV, hmm.dV, supplyFactor, summitVent, dikes.size(), supplied, erupted, intruded);
    }

    /** Zurück zum gesicherten Zustand: Speicher, Zufluss, Gipfelschlot; Gänge des Drehbuchs verschwinden. Die Uhr und die Beben laufen weiter. */
    public void restore(Snapshot s) {
        sc.dV = s.sc();
        hmm.dV = s.hmm();
        supplyFactor = s.supplyFactor();
        summitVent = s.summitVent();
        ventOpen = false;
        dike = null;
        holdDike = false;
        while (dikes.size() > s.dikes()) dikes.remove(dikes.size() - 1);
        supplied = s.supplied();
        erupted = s.erupted();
        intruded = s.intruded();
        prevPsc = sc.pressure();
        nextRift = null;
    }

    /** Den Zufluss für eine Weile verstärken (Drehbücher, Knopf). */
    public void boost(double factor) { supplyFactor = factor; }

    // ------------------------------------------------------------------ Oberfläche (Mogi)

    static final double NU = 0.25;

    /** Hebung (m) an (x, z) durch eine Punktquelle mit Volumenänderung dV in Tiefe d unter der Oberfläche. */
    public static double mogiUplift(double dV, double d, double r) {
        return (1 - NU) * dV / Math.PI * d / Math.pow(d * d + r * r, 1.5);
    }

    /** Waagerechte Verschiebung (m) nach außen. */
    public static double mogiRadial(double dV, double d, double r) {
        return (1 - NU) * dV / Math.PI * r / Math.pow(d * d + r * r, 1.5);
    }

    /** Höhe der Oberfläche über den Speichern (für die Tiefe der Quellen), m. */
    static final double SURFACE = 1100;

    /** Hebung an (x, z) gegenüber dem Start (m). */
    public double uplift(double x, double z) {
        double u = 0;
        for (Reservoir r : reservoirs) u += mogiUplift(r.dV - r0(r), SURFACE - r.y, Math.hypot(x - r.x, z - r.z));
        return u;
    }

    /** Neigung an (x, z) gegenüber dem Start (µrad): Ostkomponente, Nordkomponente (positiv = Boden kippt nach Osten/Norden hinab). */
    public double[] tilt(double x, double z) {
        double e = 20;
        double gx = (uplift(x + e, z) - uplift(x - e, z)) / (2 * e), gz = (uplift(x, z + e) - uplift(x, z - e)) / (2 * e);
        // z zeigt nach Süden: Nordkomponente = −∂u/∂z
        return new double[]{-gx * 1e6, gz * 1e6};
    }

    /**
     * Verschiebung an (x, z) gegenüber dem Start (Phase 14, für GPS): Ost, Nord, Hebung (m), Summe der
     * Mogi-Quellen [A]. Waagerecht nach außen von jeder Quelle.
     */
    public double[] displacement(double x, double z) {
        double e = 0, n = 0, u = 0;
        for (Reservoir r : reservoirs) {
            double dv = r.dV - r0(r), d = SURFACE - r.y, dx = x - r.x, dz = z - r.z, rr = Math.hypot(dx, dz);
            u += mogiUplift(dv, d, rr);
            if (rr > 1e-6) {
                double h = mogiRadial(dv, d, rr);
                e += h * dx / rr;
                n -= h * dz / rr;     // z zeigt nach Süden
            }
        }
        return new double[]{e, n, u};
    }

    /** Mehrvolumen beim Start (Bezug für Hebung und Neigung). */
    private double r0(Reservoir r) { return r == sc ? startSc : startHmm; }

    /** Neigungsmesser UWE am Uēkahuna-Rand [L Lage, gerundet]. */
    public static final double[] UWE = {Geo.x(19.4211, -155.2938), Geo.z(19.4211)};

    /** Kurzer Zustand für Bedienfeld und Statuszeile. */
    public String summary() {
        double[] t = tilt(UWE[0], UWE[1]);
        double tiltMag = Math.hypot(t[0], t[1]);
        String d = dike != null ? String.format(java.util.Locale.GERMAN, "Gang in der %s: %.1f km, %.1f km/Tag", dike.rift.label, dike.length() / 1000, dike.speed * 86.4)
                : dikes.isEmpty() ? "kein Gang" : String.format(java.util.Locale.GERMAN, "letzter Gang %.1f km (%s)", dikes.get(dikes.size() - 1).length() / 1000, dikes.get(dikes.size() - 1).rift.label);
        int recent = quakesSince(time - 3600).size();
        return String.format(java.util.Locale.GERMAN, "Druck Südcaldera %.1f MPa, Halemaʻumaʻu %.1f MPa · Hebung Gipfel %+.0f cm · Neigung UWE %.0f µrad · %d Beben in der letzten Stunde · %s",
                sc.pressure() / 1e6, hmm.pressure() / 1e6, uplift(hmm.x, hmm.z) * 100, tiltMag, recent, d);
    }
}
