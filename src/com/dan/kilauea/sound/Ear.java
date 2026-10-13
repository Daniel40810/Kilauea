package com.dan.kilauea.sound;

/**
 * Vom Schalldruck am Ohr zum Pegel im Lautsprecher, und wie eine Quelle im Raum klingt:
 * <ul>
 * <li>Ausbreitung kugelförmig, −6 dB je Entfernungsverdopplung [L], dazu Luftdämpfung: bei 20 °C und
 * 70 % Feuchte rund 0,5 dB je 100 m bei 1 kHz, 1 dB bei 2 kHz, 3 dB bei 4 kHz, 10 dB bei 8 kHz
 * (Größenordnung nach ISO 9613-1) [L]. Daraus ein Tiefpass, dessen Eckfrequenz mit der Entfernung sinkt,
 * und eine breitbandige Dämpfung bei 1 kHz [A].</li>
 * <li>Stereo nach dem Winkel zur Blickrichtung (gleiche Leistung), hinter dem Kopf etwas dumpfer [G].</li>
 * <li>Laufzeit: Schall 343 m/s [L].</li>
 * <li>Abbildung des Schalldrucks (dB SPL) auf den Pegel (dBFS) gestaucht, damit leise Wälder hörbar
 * bleiben und Fontänen nicht übersteuern: dBFS = −56 + 0,62 · (SPL − 25) [G].</li>
 * </ul>
 */
public final class Ear {
    private Ear() { }

    public static final double C = 343;

    /** Pegel im Lautsprecher (linear) für einen Schalldruck in dB SPL. */
    public static double level(double spl) {
        if (spl < 0) return 0;
        double db = -56 + 0.62 * (Math.min(spl, 125) - 25);
        return Math.pow(10, db / 20);
    }

    /** Schalldruck in Entfernung r (m), wenn er bei r0 spl0 beträgt (Kugelwelle + Dämpfung bei 1 kHz). */
    public static double spl(double spl0, double r0, double r) {
        r = Math.max(r, r0 * 0.25);
        return spl0 - 20 * Math.log10(r / r0) - 0.005 * Math.max(0, r - r0);
    }

    /** Eckfrequenz (Hz), bei der die Luftdämpfung über r Meter rund 6 dB erreicht. */
    public static double cutoff(double r) {
        // Dämpfung α(f) in dB/m: 1 kHz 0,005 · 2 kHz 0,01 · 4 kHz 0,03 · 8 kHz 0,1 → f mit α·r = 6 dB
        if (r < 50) return 18000;
        double a = 6 / r;
        double f;
        if (a > 0.1) f = 8000 + (a - 0.1) * 1e5;
        else if (a > 0.03) f = 4000 + (a - 0.03) / 0.07 * 4000;
        else if (a > 0.01) f = 2000 + (a - 0.01) / 0.02 * 2000;
        else if (a > 0.005) f = 1000 + (a - 0.005) / 0.005 * 1000;
        else f = Math.max(250, 1000 * a / 0.005);
        return Math.min(18000, f);
    }

    /** Lage einer Quelle relativ zum Hörer: Entfernung, links/rechts, hinten. */
    public static final class Where {
        public double dist, pan, behind, gl, gr, dx, dy, dz;

        /** Hörer bei (lx, ly, lz) mit Blick (fx, fz) waagerecht; Quelle (x, y, z). */
        public Where at(Scene s, double x, double y, double z) {
            dx = x - s.x; dy = y - s.y; dz = z - s.z;
            dist = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-6;
            double h = Math.hypot(dx, dz) + 1e-6;
            // rechts des Blicks: (−fz, fx) in (x, z) mit z nach Süden
            double right = (dx * s.rx + dz * s.rz) / h, front = (dx * s.fx + dz * s.fz) / h;
            double elev = Math.abs(dy) / dist;
            pan = right * (1 - 0.6 * elev);
            behind = Math.max(0, -front) * (1 - elev);
            double a = (pan + 1) * Math.PI / 4;
            gl = Math.cos(a) * Math.sqrt(2) * 0.85;
            gr = Math.sin(a) * Math.sqrt(2) * 0.85;
            return this;
        }
    }
}
