package com.dan.kilauea.camera;

import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.LavaTube;

/** Wo man gehen kann: auf dem Gelände oder, wer darin steht, auf dem Boden der Lavaröhre; ihre Wände halten auf. */
public final class WalkWorld {
    private WalkWorld() { }

    public static void install() {
        OrbitCamera.surface = (x, z, foot) -> {
            double g = Ground.height(x, z);
            LavaTube t = LavaTube.get();
            if (t == null) return g;
            double f = t.walkFloor(x, z, foot);
            if (Double.isNaN(f)) return g;
            return (foot < g - 2.5 || Math.abs(foot - f) < Math.abs(foot - g)) ? f : g;
        };
        OrbitCamera.block = (x, z, foot) -> {
            LavaTube t = LavaTube.get();
            if (t == null || foot > Ground.height(x, z) - 2.5) return false;
            double[] q = t.locate(x, z);
            return q[2] > t.radiusAt(q[0]) - 0.6;
        };
    }
}
