package com.dan.kilauea.tools;

import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.magma.Rift;

import java.util.Locale;

/**
 * Ein Lauf des Magmamodells ohne Grafik, als Tabelle (Semikolon, ein Wert je Stunde): 20 Tage normaler
 * Zufluss, dann zwanzigfach bis zum Gang, danach wieder normal bis Tag 60.
 * Aufruf: MagmaLauf [Startwert] &gt; lauf.csv
 */
public final class MagmaLauf {
    private MagmaLauf() { }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 2026;
        MagmaSystem m = new MagmaSystem(seed);
        m.nextRift = Rift.EAST;
        System.out.println("Tag;Druck Suedcaldera MPa;Druck Halemaumau MPa;Hebung Gipfel cm;Neigung UWE urad;Beben je Stunde;Gang km");
        for (int h = 0; h <= 60 * 24; h++) {
            double day = h / 24.0;
            if (day >= 20 && m.dikes().isEmpty()) m.boost(20);
            else m.boost(1);
            double t0 = m.time();
            m.step(3600);
            double[] t = m.tilt(MagmaSystem.UWE[0], MagmaSystem.UWE[1]);
            double dike = m.dikes().isEmpty() ? 0 : m.dikes().get(m.dikes().size() - 1).length() / 1000;
            System.out.printf(Locale.ROOT, "%.3f;%.3f;%.3f;%.2f;%.1f;%d;%.2f%n", day, m.sc.pressure() / 1e6, m.hmm.pressure() / 1e6,
                    m.uplift(m.hmm.x, m.hmm.z) * 100, Math.hypot(t[0], t[1]), m.quakesSince(t0).size(), dike);
        }
    }
}
