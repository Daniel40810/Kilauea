package com.dan.kilauea.sound;

/**
 * Eine Folge von Tönen für Rufe und Gesang: je Ton Dauer, Anfangs- und Endfrequenz, Lautstärke, Schnarren
 * (Amplitudenmodulation) und eine Pause danach. Klang aus Grundton und zwei Obertönen; weich ein- und
 * ausgeblendet, damit nichts knackt.
 */
final class Tone {
    static final int MAX = 48;
    final double[] dur = new double[MAX], f0 = new double[MAX], f1 = new double[MAX], amp = new double[MAX], buzz = new double[MAX], gap = new double[MAX];
    double h2 = 0.18, h3 = 0.06;
    int notes;
    // Spielstand
    private int idx = -1;
    private double tIn, phase, buzzPh;
    double start = -1;
    boolean active;

    Tone clear() { notes = 0; idx = -1; active = false; return this; }

    Tone note(double d, double a, double b, double am, double bz, double g) {
        if (notes >= MAX) return this;
        dur[notes] = d; f0[notes] = a; f1[notes] = b; amp[notes] = am; buzz[notes] = bz; gap[notes] = g;
        notes++;
        return this;
    }

    /** Beginnt zur Klangzeit when. */
    void play(double when) { start = when; idx = -1; active = notes > 0; }

    /** Nächster Abtastwert zur Klangzeit t (0 vor dem Beginn und nach dem Ende). */
    float next(double t) {
        if (!active || t < start) return 0;
        if (idx < 0) { idx = 0; tIn = 0; }
        double dt = 1.0 / Dsp.RATE;
        tIn += dt;
        double total = dur[idx] + gap[idx];
        while (tIn > total) {
            tIn -= total;
            idx++;
            if (idx >= notes) { active = false; return 0; }
            total = dur[idx] + gap[idx];
        }
        if (tIn > dur[idx]) return 0;
        double u = tIn / dur[idx];
        double f = f0[idx] + (f1[idx] - f0[idx]) * u;
        phase += 2 * Math.PI * f * dt;
        if (phase > 1e4) phase -= 2 * Math.PI * Math.floor(phase / (2 * Math.PI));
        double env = Math.min(1, Math.min(tIn / 0.006, (dur[idx] - tIn) / 0.012));
        double v = Math.sin(phase) + h2 * Math.sin(2 * phase) + h3 * Math.sin(3 * phase);
        if (buzz[idx] > 0) { buzzPh += 2 * Math.PI * buzz[idx] * dt; v *= 0.55 + 0.45 * Math.sin(buzzPh); }
        return (float) (v * env * amp[idx]);
    }
}
