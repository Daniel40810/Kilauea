package com.dan.kilauea.sound;

/**
 * Fontänen: tiefes Brausen wie ein Strahltriebwerk, das mit der Höhe heller und lauter wird, stoßweise
 * Gasblasen (dumpfe Schläge, wenn eine Blase platzt) und das Prasseln der zurückfallenden Brocken.
 * Pegel rund 95 dB in 100 m bei einer 100-m-Fontäne, +10 dB je Verzehnfachung der Höhe [G]; Klangbild
 * nach Beschreibungen der Fontänen als „Brüllen“ und Pulsen [G].
 */
final class FountainSound implements SoundEngine.Synth {
    static final int MAX = 4;
    private final Spot[] spot = new Spot[MAX];
    private final Dsp.Brown[] brown = new Dsp.Brown[MAX];
    private final Dsp.Pink[] pink = new Dsp.Pink[MAX];
    private final Dsp.Biquad[] lo = new Dsp.Biquad[MAX], mid = new Dsp.Biquad[MAX], hi = new Dsp.Biquad[MAX];
    private final Dsp.Rand rnd = new Dsp.Rand(31);
    private final double[] burst = new double[MAX], thump = new double[MAX], clatter = new double[MAX];
    private final boolean[] live = new boolean[MAX];

    FountainSound() {
        for (int i = 0; i < MAX; i++) {
            spot[i] = new Spot();
            brown[i] = new Dsp.Brown(101 + i);
            pink[i] = new Dsp.Pink(201 + i);
            lo[i] = new Dsp.Biquad();
            mid[i] = new Dsp.Biquad().set(Dsp.Biquad.BP, 650, 0.6);
            hi[i] = new Dsp.Biquad().set(Dsp.Biquad.HP, 2500, 0.7);
        }
    }

    /** Schalldruck in 100 m für eine Fontäne der Höhe h (m) [G]. */
    static double spl100(double h, double len) {
        return 95 + 10 * Math.log10(Math.max(h, 5) / 100) + 3 * Math.log10(Math.max(len, 10) / 10);
    }

    @Override public void block(Scene s, float[] L, float[] R, int n, double t) {
        for (int k = 0; k < MAX; k++) {
            boolean on = k < s.fountainCount;
            if (!on && !live[k]) continue;
            double h = 0;
            if (on) {
                double x = s.fountains[5 * k], y = s.fountains[5 * k + 1], z = s.fountains[5 * k + 2];
                h = s.fountains[5 * k + 3];
                double len = s.fountains[5 * k + 4];
                spot[k].aim(s, x, y + h * 0.3, z, spl100(h, len), 100, n);
                lo[k].set(Dsp.Biquad.LP, 160 + Math.min(h, 500) * 1.1, 0.7);
            } else spot[k].silence(n);
            live[k] = on;
            double burstRate = (1.2 + h / 160) / Dsp.RATE, clatterRate = (30 + h * 0.4) / Dsp.RATE;
            float bDecay = (float) Math.exp(-1 / (0.35 * Dsp.RATE)), cDecay = (float) Math.exp(-1 / (0.002 * Dsp.RATE));
            for (int i = 0; i < n; i++) {
                if (rnd.unit() < burstRate) burst[k] = Math.min(2.2, burst[k] + 0.5 + rnd.exp() * 0.6);
                if (rnd.unit() < clatterRate) clatter[k] += rnd.unit() * 0.9;
                burst[k] *= bDecay;
                clatter[k] *= cDecay;
                thump[k] += 2 * Math.PI * (38 + 25 * burst[k]) / Dsp.RATE;
                float roar = lo[k].process(brown[k].next()) * 1.2f + mid[k].process(pink[k].next()) * 0.4f;
                float out = roar * (float) (0.6 + 0.35 * burst[k])
                        + (float) (Math.sin(thump[k]) * burst[k] * 0.45)
                        + hi[k].process(rnd.next()) * (float) clatter[k] * 0.8f;
                spot[k].mix(out * 1.05f, L, R, i);   // geeicht: 95 dB in 100 m
            }
        }
    }
}
