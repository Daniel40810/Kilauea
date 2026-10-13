package com.dan.kilauea.sound;

/**
 * Bausteine der Klangsynthese: Zufall, rosa und braunes Rauschen, Filter nach dem „Audio EQ Cookbook“
 * (R. Bristow-Johnson) [L], einpolige Tiefpässe, Hüllkurven. Alles für einen Kanal und je Abtastwert,
 * ohne Speicher anzulegen.
 */
public final class Dsp {
    private Dsp() { }

    public static final int RATE = 44100;
    public static final double TAU = 2 * Math.PI;

    /** Schneller Zufall (xorshift), gleich verteilt in [−1, 1). */
    public static final class Rand {
        private int s;
        public Rand(int seed) { s = seed == 0 ? 0x9E3779B9 : seed; }
        public float next() {
            s ^= s << 13; s ^= s >>> 17; s ^= s << 5;
            return (s >>> 8) * (1f / (1 << 23)) - 1f;
        }
        /** gleich verteilt in [0, 1) */
        public float unit() { return 0.5f * (next() + 1f); }
        /** exponentiell verteilt mit Mittel 1 */
        public float exp() { return (float) -Math.log(Math.max(1e-6, unit())); }
    }

    /** Rosa Rauschen (Paul Kellets Filter, rund 1/f), Pegel um ±1. */
    public static final class Pink {
        private final Rand r;
        private float b0, b1, b2, b3, b4, b5, b6;
        public Pink(int seed) { r = new Rand(seed); }
        public float next() {
            float w = r.next();
            b0 = 0.99886f * b0 + w * 0.0555179f; b1 = 0.99332f * b1 + w * 0.0750759f; b2 = 0.96900f * b2 + w * 0.1538520f;
            b3 = 0.86650f * b3 + w * 0.3104856f; b4 = 0.55000f * b4 + w * 0.5329522f; b5 = -0.7616f * b5 - w * 0.0168980f;
            float p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f;
            b6 = w * 0.115926f;
            return p * 0.11f;
        }
    }

    /** Braunes Rauschen (Zufallsweg mit Leck), tief, Pegel um ±1. */
    public static final class Brown {
        private final Rand r;
        private float y;
        public Brown(int seed) { r = new Rand(seed); }
        public float next() {
            y = 0.997f * y + 0.06f * r.next();
            return y * 2.2f;
        }
    }

    /** Biquad-Filter: Tiefpass, Hochpass, Bandpass (konstante Spitze 0 dB). */
    public static final class Biquad {
        public static final int LP = 0, HP = 1, BP = 2;
        private double b0, b1, b2, a1, a2, x1, x2, y1, y2;
        public Biquad set(int type, double f, double q) {
            f = Math.max(10, Math.min(RATE * 0.45, f));
            double w = TAU * f / RATE, cw = Math.cos(w), sw = Math.sin(w), al = sw / (2 * Math.max(0.05, q));
            double a0;
            switch (type) {
                case LP -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2; }
                case HP -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2; }
                default -> { b0 = al; b1 = 0; b2 = -al; }
            }
            a0 = 1 + al;
            a1 = -2 * cw / a0; a2 = (1 - al) / a0;
            b0 /= a0; b1 /= a0; b2 /= a0;
            return this;
        }
        public float process(float x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            if (Double.isNaN(y1) || Math.abs(y1) > 1e6) { x1 = x2 = y1 = y2 = 0; return 0; }
            return (float) y;
        }
        public void reset() { x1 = x2 = y1 = y2 = 0; }
    }

    /** Einpoliger Tiefpass. */
    public static final class OnePole {
        private float a = 1, y;
        public OnePole cutoff(double f) { a = (float) (1 - Math.exp(-TAU * Math.max(5, f) / RATE)); return this; }
        public float process(float x) { y += a * (x - y); return y; }
        public float value() { return y; }
    }

    /** Zahl langsam nachführen (gegen Knacken bei Sprüngen). */
    public static double approach(double cur, double target, double rate, double dt) {
        return cur + (target - cur) * (1 - Math.exp(-rate * dt));
    }

    public static double dbToAmp(double db) { return Math.pow(10, db / 20); }

    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
