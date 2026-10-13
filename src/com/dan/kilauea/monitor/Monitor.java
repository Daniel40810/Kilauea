package com.dan.kilauea.monitor;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaSim;
import com.dan.kilauea.lava.Vent;
import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.magma.Quake;
import com.dan.kilauea.terrain.Ground;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Messnetz (Phase 14): schreibt mit, was die Stationen über das Modell messen würden, und stellt es für die
 * Anzeige bereit. Ohne Grafik; die App ruft {@link #sample} einmal je Bild, das Werkzeug {@code MessLauf} und die
 * Prüfung nach jedem Rechenschritt.
 * <ul>
 * <li><b>Neigung</b> an UWD und SDH aus den Mogi-Quellen der zwei Speicher (Phase 6), als Komponente vom
 * Gipfel weg: aufwärts heißt, der Gipfel hebt sich [A].</li>
 * <li><b>GPS</b>: Abstand UWEV–CRIM quer über die Caldera; länger heißt, der Gipfel bläht sich auf [A; Lesart
 * wie bei HVO, L].</li>
 * <li><b>Hebung</b> über dem Halemaʻumaʻu-Speicher [A].</li>
 * <li><b>SO₂</b> nach {@link Gas} [A/L].</li>
 * <li><b>Tremor</b> an jedem Seismometer nach {@link Seismo} [G]; die Beben selbst liest die Anzeige aus dem
 * Magmamodell.</li>
 * </ul>
 * Neigung, Abstand und Hebung zählen ab dem Beginn der Aufzeichnung ({@link #clear}); so zeigt auch HVO
 * Änderungen, keine absoluten Werte. Die Aufzeichnung hat drei Stufen: alle 2 s über 2 h, jede Minute über
 * rund 3 Tage, alle 15 min über rund 90 Tage.
 */
public final class Monitor {
    public static final int CH_UWD = 0, CH_SDH = 1, CH_BASE = 2, CH_UPLIFT = 3, CH_SO2 = 4, CH_RATE = 5, CH_SEIS = 6;
    public static final int CHANNELS = CH_SEIS + Station.SEISMIC.size();
    static final double[] RES = {2, 60, 900};
    static final int[] CAP = {3600, 4400, 9000};
    /** Mögliche Zeitfenster der Anzeige (s). */
    static final double[] SPANS = {600, 3600, 6 * 3600, 86400, 3 * 86400, 7 * 86400, 30 * 86400, 90 * 86400};
    /** Bis zu diesem Tempo (Sekunden Simulation je Sekunde) zeigt der Seismograf die Trommel. */
    public static final double DRUM_RATE = 20;
    /** Die Trommel: Zeilen zu je 60 s. */
    public static final int DRUM_LINES = 6;
    public static final double DRUM_LINE = 60;

    private final MagmaSystem magma;
    private final LavaSim lava;
    /** Ruhige Entgasung des Gipfels (t/d), vom Drehbuch gesetzt; NaN = nach dem See. */
    public volatile double passiveSO2 = Double.NaN;
    /** Seismometer für die Anzeige (−1 = das nächste zum Blickziel). */
    public volatile int seisChoice = -1;
    /** Feste Rate (Simulationssekunden je Sekunde) für Werkzeuge ohne Wanduhr; NaN = gemessen. */
    public double rateOverride = Double.NaN;

    private final double[][] tt = new double[3][], vv = new double[3][];
    private final int[] head = new int[3], count = new int[3];
    private final double[] lastT = {-1e300, -1e300, -1e300};
    private final double[] now = new double[CHANNELS], base = new double[CHANNELS];
    private boolean haveBase;
    private double start, nowT, prevT = Double.NaN, rate = 1, span = 3 * 86400;
    private double[] stationY;
    private volatile String summary = "Messnetz: noch keine Werte.";

    public Monitor(MagmaSystem magma, LavaSim lava) {
        this.magma = magma;
        this.lava = lava;
        for (int i = 0; i < 3; i++) {
            tt[i] = new double[CAP[i]];
            vv[i] = new double[CAP[i] * CHANNELS];
        }
    }

    /** Aufzeichnung neu beginnen (Beginn und Ende eines Drehbuchs): Neigung und Abstand zählen ab hier. */
    public synchronized void clear() {
        for (int i = 0; i < 3; i++) { head[i] = count[i] = 0; lastT[i] = -1e300; }
        haveBase = false;
        synchronized (magma) { start = magma.time(); }
        nowT = start;
        prevT = Double.NaN;
    }

    // ------------------------------------------------------------------ Messen

    /** Einmal je Bild oder Rechenschritt: messen und mitschreiben. wallDt: Sekunden Wanduhr seit dem letzten Mal. */
    public synchronized void sample(double wallDt) {
        double t;
        double[] tu, ts, du, dc;
        double up;
        synchronized (magma) {
            t = magma.time();
            tu = magma.tilt(Station.UWD.x(), Station.UWD.z());
            ts = magma.tilt(Station.SDH.x(), Station.SDH.z());
            du = magma.displacement(Station.UWEV.x(), Station.UWEV.z());
            dc = magma.displacement(Station.CRIM.x(), Station.CRIM.z());
            up = magma.uplift(magma.hmm.x, magma.hmm.z);
        }
        double cx = magma.hmm.x, cz = magma.hmm.z;
        now[CH_UWD] = radial(tu, Station.UWD, cx, cz);
        now[CH_SDH] = radial(ts, Station.SDH, cx, cz);
        double bx = Station.CRIM.x() - Station.UWEV.x(), bn = -(Station.CRIM.z() - Station.UWEV.z()), bl = Math.hypot(bx, bn);
        now[CH_BASE] = ((dc[0] - du[0]) * bx + (dc[1] - du[1]) * bn) / bl * 100;          // cm
        now[CH_UPLIFT] = up * 100;                                                          // cm
        // Schlote: Förderung und Tremorquellen
        List<double[]> src = new ArrayList<>();
        double rateSum = 0;
        LavaField f = lava.field();
        if (f != null) {
            List<Vent> vs;
            synchronized (f) { vs = new ArrayList<>(f.vents()); }
            for (Vent v : vs) {
                double r = v.rate;
                if (r <= 0) continue;
                rateSum += r;
                src.add(new double[]{0.5 * (v.x0 + v.x1), 0.5 * (v.z0 + v.z1), Seismo.tremorSource(r, v.fountainHeight())});
            }
        }
        boolean lake = lakeOpen();
        if (lake) src.add(new double[]{lava.lake().cx, lava.lake().cz, Seismo.LAKE_SOURCE});
        now[CH_SO2] = Gas.so2(rateSum, lake, passiveSO2);
        now[CH_RATE] = rateSum;
        if (stationY == null && Ground.grid() != null) {
            stationY = new double[Station.SEISMIC.size()];
            for (int i = 0; i < stationY.length; i++) stationY[i] = Math.max(0, Ground.height(Station.SEISMIC.get(i).x(), Station.SEISMIC.get(i).z()));
        }
        for (int i = 0; i < Station.SEISMIC.size(); i++) {
            Station s = Station.SEISMIC.get(i);
            double a = 0;
            for (double[] q : src) a += Seismo.tremorAt(q[2], Math.hypot(q[0] - s.x(), q[1] - s.z()) / 1000);
            now[CH_SEIS + i] = a;
        }
        if (!haveBase) {
            System.arraycopy(now, 0, base, 0, CHANNELS);
            haveBase = true;
            start = Math.min(start, t);
        }
        // Tempo der Simulation (für die Wahl des Zeitfensters)
        if (!Double.isNaN(rateOverride)) rate = rateOverride;
        else if (wallDt > 1e-4 && !Double.isNaN(prevT)) {
            double inst = Math.max(0, (t - prevT) / wallDt);
            rate += (inst - rate) * (1 - Math.exp(-wallDt / 1.5));
        }
        prevT = t;
        nowT = t;
        for (int i = 0; i < 3; i++) {
            if (t - lastT[i] < RES[i] && count[i] > 0) continue;
            lastT[i] = t;
            int k = head[i];
            tt[i][k] = t;
            for (int c = 0; c < CHANNELS; c++) vv[i][k * CHANNELS + c] = rel(c, now[c]);
            head[i] = (k + 1) % CAP[i];
            count[i] = Math.min(CAP[i], count[i] + 1);
        }
        pickSpan();
        summary = String.format(Locale.GERMAN, "Neigung UWD %s · SDH %s · UWEV–CRIM %s · SO₂ %s",
                Reading.of("", rel(CH_UWD, now[CH_UWD]), "µrad", Prov.A, "-").formatted(),
                Reading.of("", rel(CH_SDH, now[CH_SDH]), "µrad", Prov.A, "-").formatted(),
                Reading.of("", rel(CH_BASE, now[CH_BASE]), "cm", Prov.A, "-").formatted(),
                Reading.of("", now[CH_SO2], "t/d", Prov.A, "-").formatted());
    }

    private boolean lakeOpen() {
        return lava.showLake && !Double.isNaN(lava.lake().rimY);
    }

    /** Komponente der Neigung vom Gipfel weg (µrad): positiv, wenn der Gipfel sich hebt. */
    static double radial(double[] tiltEN, Station s, double cx, double cz) {
        double e = s.x() - cx, n = -(s.z() - cz), l = Math.hypot(e, n);
        return (tiltEN[0] * e + tiltEN[1] * n) / l;
    }

    /** Neigung, Abstand, Hebung relativ zum Beginn; die übrigen Kanäle absolut. */
    private double rel(int c, double v) { return c <= CH_UPLIFT && haveBase ? v - base[c] : v; }

    private void pickSpan() {
        // nach dem Zeitraffer (rund 75 s Wanduhr im Fenster), aber nicht viel länger als die Aufzeichnung
        double want = Math.max(600, Math.min(rate * 75, (nowT - start) * 1.15));
        // Wechsel nur, wenn das Fenster zu klein oder viel zu groß ist (sonst springt die Achse)
        if (want > span || want < span / 6) {
            double s = SPANS[SPANS.length - 1];
            for (double c : SPANS) if (c >= want) { s = c; break; }
            span = s;
        }
    }

    public double time() { return nowT; }
    public double start() { return start; }
    public double rate() { return rate; }
    public double span() { return span; }
    public String summary() { return summary; }
    /** Letzter Wert eines Kanals (relativ, wie angezeigt). */
    public synchronized double latest(int ch) { return rel(ch, now[ch]); }

    // ------------------------------------------------------------------ Reihen

    /** Zeiten und Werte eines Kanals über das Fenster [t0, jetzt] aus der feinsten Stufe, die es ganz abdeckt. */
    public synchronized double[][] series(int ch, double t0) {
        int tier = 2;
        for (int i = 0; i < 3; i++) {
            if (count[i] == 0) continue;
            double oldest = tt[i][(head[i] - count[i] + CAP[i]) % CAP[i]];
            if (oldest <= Math.max(t0, start) + RES[i]) { tier = i; break; }
        }
        int n = count[tier];
        double[] ts = new double[n], vs = new double[n];
        int m = 0;
        for (int j = 0; j < n; j++) {
            int k = (head[tier] - n + j + CAP[tier]) % CAP[tier];
            // die letzte Probe vor dem Fenster gehört dazu: die Linie kommt dann vom linken Rand herein
            int kn = (head[tier] - n + j + 1 + CAP[tier]) % CAP[tier];
            if (tt[tier][k] < t0 && (j + 1 >= n || tt[tier][kn] < t0)) continue;
            ts[m] = tt[tier][k];
            vs[m] = vv[tier][k * CHANNELS + ch];
            m++;
        }
        return new double[][]{java.util.Arrays.copyOf(ts, m), java.util.Arrays.copyOf(vs, m)};
    }

    /** Wert eines Kanals zur Zeit t (linear zwischen den Proben der feinsten Stufe, die t enthält). */
    public synchronized double valueAt(int ch, double t) {
        for (int i = 0; i < 3; i++) {
            int n = count[i];
            if (n == 0) continue;
            int first = (head[i] - n + CAP[i]) % CAP[i];
            if (tt[i][first] > t && i < 2) continue;
            // binäre Suche im Ring
            int lo = 0, hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (tt[i][(first + mid) % CAP[i]] <= t) lo = mid; else hi = mid - 1;
            }
            int a = (first + lo) % CAP[i], b = (first + Math.min(n - 1, lo + 1)) % CAP[i];
            double ta = tt[i][a], tb = tt[i][b], va = vv[i][a * CHANNELS + ch], vb = vv[i][b * CHANNELS + ch];
            if (tb <= ta || t <= ta) return va;
            return va + (vb - va) * Math.min(1, (t - ta) / (tb - ta));
        }
        return 0;
    }

    // ------------------------------------------------------------------ Anzeige

    /** Alles, was die Tafel zeichnet, als Kopie (der Maler braucht keine Sperre). */
    public record View(double now, double span, double rate, boolean drum, Station seis, int seisIndex,
                       float[][] drumMin, float[][] drumMax, double drumStart,
                       double[] rsamT, double[] rsam, double rsamBin,
                       double[] quakeT, double[] quakeM,
                       double[][] uwd, double[][] sdh, double[][] base, double[][] so2,
                       Map<String, Reading> readings) {
        public List<Reading> all() { return new ArrayList<>(readings.values()); }
    }

    /**
     * Ansicht für die Tafel. targetX/Z: Blickziel (für das nächste Seismometer), drumPx: Bildpunkte je Zeile der
     * Trommel (0 = keine Trommel rechnen).
     */
    public View view(double targetX, double targetZ, int drumPx) {
        double t, sp, rt;
        synchronized (this) { t = nowT; sp = span; rt = rate; }
        int si = seisChoice >= 0 && seisChoice < Station.SEISMIC.size() ? seisChoice : Station.nearestSeismic(targetX, targetZ);
        Station st = Station.SEISMIC.get(si);
        double sy = stationY != null ? stationY[si] : 1000;
        double t0 = Math.max(start, t - sp);
        boolean drum = rt <= DRUM_RATE;
        List<Quake> qs;
        synchronized (magma) { qs = magma.quakesSince(Math.min(t0, t - DRUM_LINES * DRUM_LINE) - 1500); }
        // Trommel
        float[][] dmin = null, dmax = null;
        double drumStart = 0;
        if (drum && drumPx > 0) {
            double cur = Math.floor(t / DRUM_LINE) * DRUM_LINE;
            drumStart = cur - (DRUM_LINES - 1) * DRUM_LINE;
            List<Quake> near = new ArrayList<>();
            for (Quake q : qs) if (q.t() >= drumStart - 1500) near.add(q);
            if (near.size() > 3000) near = near.subList(near.size() - 3000, near.size());
            double[] r = new double[near.size()];
            for (int j = 0; j < r.length; j++) r[j] = Seismo.distanceKm(near.get(j), st.x(), sy, st.z());
            dmin = new float[DRUM_LINES][drumPx];
            dmax = new float[DRUM_LINES][drumPx];
            int ch = CH_SEIS + si;
            for (int l = 0; l < DRUM_LINES; l++) {
                double a = drumStart + l * DRUM_LINE, b = Math.min(t, a + DRUM_LINE);
                int px = (int) Math.round(drumPx * Math.max(0, b - a) / DRUM_LINE);
                if (px <= 0) { dmin[l] = new float[0]; dmax[l] = new float[0]; continue; }
                float[] lo = new float[px], hi = new float[px];
                Seismo.envelope(near, r, tm -> tm < start ? 0 : valueAt(ch, tm), a, b, lo, hi);
                dmin[l] = lo;
                dmax[l] = hi;
            }
        }
        // RSAM und Beben im Fenster
        double bin = sp <= 86400 ? 600 : sp <= 7 * 86400 ? 3600 : 6 * 3600;
        int nb = (int) Math.max(1, Math.ceil((t - t0) / bin));
        double[] rsT = new double[nb], rs = new double[nb];
        int ch = CH_SEIS + si;
        for (int b = 0; b < nb; b++) {
            double a = t0 + b * bin, e = Math.min(t, a + bin);
            rsT[b] = a;
            double trem = 0;
            for (int k = 0; k < 4; k++) trem += valueAt(ch, a + (e - a) * (k + 0.5) / 4) / 4;
            rs[b] = 2 / Math.PI * (Seismo.NOISE + trem);
        }
        List<double[]> dots = new ArrayList<>();
        int inWindow = 0;
        double maxM = Double.NaN;
        for (Quake q : qs) {
            if (q.t() < t0 || q.t() > t) continue;
            inWindow++;
            if (Double.isNaN(maxM) || q.mag() > maxM) maxM = q.mag();
            int b = (int) ((q.t() - t0) / bin);
            if (b >= 0 && b < nb) rs[b] += Seismo.meanAbs(q, Seismo.distanceKm(q, st.x(), sy, st.z()), bin);
            dots.add(new double[]{q.t(), q.mag()});
        }
        for (int b = 0; b < nb; b++) rs[b] *= Seismo.COUNTS;
        if (dots.size() > 1500) {   // nur die stärksten zeichnen
            dots.sort((x, y) -> Double.compare(y[1], x[1]));
            dots = new ArrayList<>(dots.subList(0, 1500));
        }
        double[] qT = new double[dots.size()], qM = new double[dots.size()];
        for (int j = 0; j < qT.length; j++) { qT[j] = dots.get(j)[0]; qM[j] = dots.get(j)[1]; }
        double[][] uwd = series(CH_UWD, t0), sdh = series(CH_SDH, t0), bs = series(CH_BASE, t0), so2 = series(CH_SO2, t0);
        // Werte mit Herkunft
        Map<String, Reading> rd = new LinkedHashMap<>();
        rd.put("span", Reading.word("Zeitfenster", spanLabel(sp), Prov.G, "Fenster nach dem Zeitraffer gewählt"));
        rd.put("seis.station", Reading.word("Seismometer", st.name(), Prov.G, "Lage gestaltet, das nächste zum Blickziel"));
        rd.put("seis.count", Reading.of("Beben im Fenster", inWindow, "", Prov.A, "Magmamodell, Gutenberg–Richter mit b = 1"));
        if (!Double.isNaN(maxM)) rd.put("seis.max", Reading.word("größtes Beben", String.format(Locale.GERMAN, "M %.1f", maxM), Prov.A, "Magmamodell; Einsturzbeben 2018 nach USGS"));
        double rsNow = rs[nb - 1];
        rd.put("seis.rsam", Reading.of("RSAM", rsNow, "", Prov.G, "mittlerer Ausschlag × 1 000, Maßstab gestaltet"));
        rd.put("seis.tremor", Reading.of("Tremor", latest(ch), "mm", Prov.G, "aus Förderrate und Fontäne"));
        rd.put("tilt.uwd", Reading.of("Neigung UWD", latest(CH_UWD), "µrad", Prov.A, "Mogi-Quellen der Speicher; Lage UWD nach HVO"));
        rd.put("tilt.sdh", Reading.of("Neigung SDH", latest(CH_SDH), "µrad", Prov.A, "Mogi-Quellen der Speicher; Lage SDH genähert"));
        rd.put("gps.base", Reading.of("Abstand UWEV–CRIM", latest(CH_BASE), "cm", Prov.A, "Mogi, waagerecht; länger = Gipfel bläht sich auf (HVO)"));
        rd.put("uplift", Reading.of("Hebung Halemaʻumaʻu", latest(CH_UPLIFT), "cm", Prov.A, "Mogi, senkrecht über dem flachen Speicher"));
        double rateNow = latest(CH_RATE);
        rd.put("so2", Gas.reading(rateNow, lakeOpen(), passiveSO2));
        rd.put("rate", Reading.of("Förderung", rateNow, "m³/s", Prov.G, "Raten der Drehbücher nach HVO-Größenordnung"));
        return new View(t, sp, rt, drum && drumPx > 0, st, si, dmin, dmax, drumStart, rsT, rs, bin, qT, qM, uwd, sdh, bs, so2, rd);
    }

    /** „letzte 3 Tage“, „letzte 6 h“, „letzte 10 min“. */
    public static String spanLabel(double s) {
        if (s >= 2 * 86400 - 1) return String.format(Locale.GERMAN, "letzte %.0f Tage", s / 86400);
        if (s >= 86400 - 1) return "letzter Tag";
        if (s >= 2 * 3600 - 1) return String.format(Locale.GERMAN, "letzte %.0f h", s / 3600);
        if (s >= 3600 - 1) return "letzte Stunde";
        return String.format(Locale.GERMAN, "letzte %.0f min", Math.max(1, s / 60));
    }
}
