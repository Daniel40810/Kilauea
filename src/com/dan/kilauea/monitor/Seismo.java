package com.dan.kilauea.monitor;

import com.dan.kilauea.magma.Quake;

import java.util.List;

/**
 * Gerechnete Seismogramme (Phase 14), ohne Grafik. Die Beben kommen aus dem Magmamodell (Phase 6, Zeit, Ort,
 * Magnitude); hier wird daraus, was ein Seismometer an einer Station aufschreiben würde:
 * <ul>
 * <li><b>Ausschlag</b> eines Wood-Anderson-Seismographen nach der Magnitudenformel von Hutton und Boore (1987):
 * ML = log A + 1,11 log(r/100) + 0,00189 (r − 100) + 3,0, r in km [L, für Kalifornien bestimmt; für Hawaiʻi
 * in derselben Größenordnung, A].</li>
 * <li><b>Dauer</b> der Aufzeichnung nach der Dauer-Magnitude von Lee u. a. (1972): Md = −0,87 + 2 log τ +
 * 0,0035 Δ [L Formel, A Anwendung].</li>
 * <li><b>Laufzeit</b> mit 6,0 km/s (P) und 3,5 km/s (S) [A, Größenordnung der Kruste unter Hawaiʻi].</li>
 * <li><b>Tremor</b> beim Ausbruch: wächst mit Förderrate und Fontäne, nimmt mit dem Abstand ab [G; dass Tremor
 * mit der Fontäne steigt und nach ihr rasch abfällt, beschreibt HVO, L].</li>
 * <li><b>Bodenunruhe</b> durch Brandung und Wind [G].</li>
 * </ul>
 * Die Wellenform selbst (Frequenzen, Phasen, Hüllkurve) ist gestaltet [G]; sie hängt nur von Zeit und Beben ab,
 * also kann jedes Fenster jederzeit neu gezeichnet werden.
 */
public final class Seismo {
    private Seismo() { }

    /** Wellengeschwindigkeiten (m/s). */
    public static final double VP = 6000, VS = 3500;
    /** Bodenunruhe (mm Wood-Anderson). */
    public static final double NOISE = 0.04;
    /** Messbereich: größere Ausschläge werden abgeschnitten wie bei einem echten Gerät (mm) [G]. */
    public static final double CLIP = 60;
    /** RSAM-Zählwerte je mm mittlerem Ausschlag [G, Maßstab]. */
    public static final double COUNTS = 1000;

    /** Wood-Anderson-Ausschlag (mm) eines Bebens der Magnitude ml in r km (Herdentfernung). */
    public static double amplitudeMm(double ml, double rKm) {
        double r = Math.max(0.5, rKm);
        return Math.pow(10, ml - 1.11 * Math.log10(r / 100) - 0.00189 * (r - 100) - 3.0);
    }

    /** Magnitude aus Ausschlag (mm) und Entfernung (km), die Umkehrung von {@link #amplitudeMm}. */
    public static double magnitude(double aMm, double rKm) {
        double r = Math.max(0.5, rKm);
        return Math.log10(aMm) + 1.11 * Math.log10(r / 100) + 0.00189 * (r - 100) + 3.0;
    }

    /** Dauer der Aufzeichnung (s) nach Lee u. a. (1972), mindestens 2 s. */
    public static double durationS(double m, double rKm) {
        return Math.max(2, Math.pow(10, (m + 0.87 - 0.0035 * rKm) / 2));
    }

    /** Herdentfernung (km) von Station (sx, sy, sz) zum Beben. */
    public static double distanceKm(Quake q, double sx, double sy, double sz) {
        return Math.sqrt(sq(q.x() - sx) + sq(q.y() - sy) + sq(q.z() - sz)) / 1000;
    }

    // ------------------------------------------------------------------ Tremor

    /**
     * Stärke eines fördernden Schlots als Tremorquelle: √Rate × (1 + Fontäne/100 m) [G]. Ein ruhiger Lavasee mit
     * Gaskolben liefert rund 0,6 [G].
     */
    public static double tremorSource(double rate, double fountain) {
        if (rate <= 0) return 0;
        return Math.sqrt(rate) * (1 + Math.max(0, fountain) / 100);
    }

    public static final double LAKE_SOURCE = 0.6;

    /** Tremor (mm) in r km von einer Quelle der Stärke s [G]: rund 1/r, dazu Dämpfung. */
    public static double tremorAt(double s, double rKm) {
        double r = Math.max(0.6, rKm);
        return 0.15 * s / r * Math.exp(-0.02 * r);
    }

    // ------------------------------------------------------------------ Wellenform

    /** Bodenbewegung (mm, mit Vorzeichen) eines Bebens an der Station zur Zeit t, ohne Abschneiden. */
    public static double quake(Quake q, double t, double rKm) {
        double tp = q.t() + rKm * 1000 / VP, ts = q.t() + rKm * 1000 / VS;
        if (t < tp) return 0;
        double a = amplitudeMm(q.mag(), rKm), d = durationS(q.mag(), rKm), tau = d / 3;
        long seed = Double.doubleToLongBits(q.t()) * 31 + Double.doubleToLongBits(q.x());
        // Frequenzen: kleine Beben 4–9 Hz, große und die Einsturzbeben tiefer (1–3 Hz) [G]
        double f0 = q.mag() >= 4 ? 1.0 + 1.5 * u(seed, 1) : 4 + 5 * u(seed, 1);
        double env;
        if (t < ts) env = 0.25 * Math.exp(-(t - tp) / (0.3 * tau + 0.2));
        else env = (1 - Math.exp(-(t - ts) / 0.25)) * Math.exp(-(t - ts) / tau) + 0.25 * Math.exp(-(t - tp) / (0.3 * tau + 0.2));
        double w = 0;
        for (int k = 0; k < 3; k++) {
            double f = f0 * (0.7 + 0.6 * u(seed, 10 + k));
            w += Math.sin(2 * Math.PI * f * (t - tp) + 6.283 * u(seed, 20 + k));
        }
        return a * env * w / 2.4;
    }

    /** Tremor als Wellenform: Rauschen im Band 1–5 Hz mit der Hüllkurve amp (mm) [G]. */
    public static double tremorWave(double amp, double t) {
        if (amp <= 0) return 0;
        double w = Math.sin(2 * Math.PI * 1.3 * t + 0.7 * Math.sin(0.11 * t)) + 0.8 * Math.sin(2 * Math.PI * 2.1 * t + 1.9 + 0.9 * Math.sin(0.07 * t))
                + 0.6 * Math.sin(2 * Math.PI * 3.3 * t + 0.4) + 0.4 * Math.sin(2 * Math.PI * 4.7 * t + 2.2);
        double mod = 0.75 + 0.25 * Math.sin(0.5 * t + 1.3 * Math.sin(0.17 * t));
        return amp * mod * w / 1.6;
    }

    /** Bodenunruhe (mm): Brandung um 0,2 Hz und hochfrequentes Rauschen [G]. */
    public static double noise(double t) {
        long i = (long) Math.floor(t * 25);
        double white = u(i, 7) - 0.5;
        return NOISE * (0.8 * Math.sin(2 * Math.PI * 0.18 * t + 0.3 * Math.sin(0.03 * t)) + 1.2 * white);
    }

    /** Zufallszahl 0..1 aus Startwert und Kanal (fest, ohne Zustand). */
    static double u(long seed, int ch) {
        long z = seed * 0x9E3779B97F4A7C15L + ch * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    // ------------------------------------------------------------------ Trommel und RSAM

    /** Auskunft über die Tremorstärke an der Station zur Zeit t (mm). */
    public interface Tremor { double at(double t); }

    /**
     * Hüllkurve für die Trommel: für jeden Bildpunkt von t0 bis t1 kleinster und größter Ausschlag (mm, nach dem
     * Abschneiden bei {@link #CLIP}). quakes: Beben mit Herdentfernung (km) in r.
     */
    public static void envelope(List<Quake> quakes, double[] r, Tremor tremor, double t0, double t1, float[] min, float[] max) {
        int n = min.length;
        double dt = (t1 - t0) / n;
        int per = Math.max(2, Math.min(10, (int) Math.ceil(dt * 25)));
        int qa = 0;
        for (int i = 0; i < n; i++) {
            double a = t0 + i * dt;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            double tr = tremor.at(a + dt / 2);
            for (int k = 0; k < per; k++) {
                double t = a + dt * (k + 0.5) / per;
                double v = noise(t) + tremorWave(tr, t);
                for (int j = qa; j < quakes.size(); j++) {
                    Quake q = quakes.get(j);
                    if (q.t() > t) break;
                    double end = q.t() + r[j] * 1000 / VS + 4 * durationS(q.mag(), r[j]) / 3;
                    if (t > end) continue;
                    v += quake(q, t, r[j]);
                }
                v = Math.max(-CLIP, Math.min(CLIP, v));
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
            min[i] = (float) lo;
            max[i] = (float) hi;
        }
    }

    /** Mittlerer Ausschlag (mm) eines Bebens über ein Fenster der Länge w (s), das bei seinem Einsatz beginnt. */
    public static double meanAbs(Quake q, double rKm, double w) {
        double a = Math.min(CLIP, amplitudeMm(q.mag(), rKm)), tau = durationS(q.mag(), rKm) / 3;
        return 2 / Math.PI * a * tau * (1 - Math.exp(-w / tau)) / w;
    }

    private static double sq(double v) { return v * v; }
}
