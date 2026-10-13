package com.dan.kilauea.flora;

import com.dan.forest.Species;

/**
 * Baumarten für Hawaiʻi, eingestellt nach Wuchsbeschreibungen [L: Little &amp; Skolmen, Common Forest
 * Trees of Hawaii, 1989; G: Werte genähert]. Alle sind immergrün; die Jahreszeiten aus
 * {@link com.dan.forest.Season} spielen keine Rolle.
 */
public final class HawaiiSpecies {
    private HawaiiSpecies() { }

    /**
     * ʻŌhiʻa lehua (Metrosideros polymorpha): der Pionierbaum auf junger Lava und der Hauptbaum des
     * Regenwalds. Knorrig, der Stamm teilt sich früh, unregelmäßige, offene Krone; kleine ledrige
     * graugrüne Blätter in Büscheln an den Zweigenden. Im Regenwald 15 bis 25 m, auf junger Lava ein
     * Strauch von 1 bis 4 m.
     */
    public static Species ohia() {
        Species s = new Species("ʻŌhiʻa lehua", "Metrosideros polymorpha");
        s.deciduous = false;
        s.heightMin = 9; s.heightMax = 20; s.trunkRatio = 0.03f; s.trunkEnd = 0.5f; s.crownBase = 0.35f;
        s.crown = Species.Crown.SPREADING; s.trunkWobble = 0.12f; s.levels = 3;
        s.count = new float[]{0, 7, 6, 5}; s.angle = new float[]{0, 42, 48, 45}; s.angleVar = new float[]{0, 20, 25, 25};
        s.lengthRatio = new float[]{0, 0.5f, 0.5f, 0.45f}; s.start = new float[]{0, 0.15f, 0.2f, 0.25f};
        s.tropism = new float[]{0, 0.04f, 0.02f, 0.06f}; s.radiusRatio = new float[]{0, 0.6f, 0.5f, 0.5f};
        s.leafLevel = 3; s.leafDensity = 13; s.leaf = Species.Leaf.SMALL; s.leafSize = 0.06f; s.leafAspect = 0.55f; s.cluster = 0.75f;
        s.flexTrunk = 0.4f; s.flexBranch = 0.7f; s.flutter = 0.6f;
        s.bark = new float[]{0.11f, 0.095f, 0.080f}; s.barkMarks = 0.3f; s.barkMark = new float[]{0.05f, 0.045f, 0.04f};
        s.spring = new float[]{0.050f, 0.085f, 0.035f};
        s.summer = new float[]{0.045f, 0.080f, 0.032f};
        s.autumn = new float[][]{{0.045f, 0.080f, 0.032f}};
        s.leafOut = 1; s.colorStart = 366; s.colorFull = 366; s.bare = 366;
        return s;
    }
}
