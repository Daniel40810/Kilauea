package com.dan.kilauea.render;

/**
 * Qualitätsstufen. Die App startet auf jeder Karte mit OpenGL 4.3; teure Wirkungen kommen stufenweise
 * dazu. Die Werte sind Startpunkte [G]; gemessen wird an deinem Statusbild. Phase 17: dazu Schattenproben,
 * anisotrope Filterung und Wolkenschritte je Stufe.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, 1024, false, false, 0.0, 1.6, 4, 2, 12),
    MITTEL("Mittel", 0.85, 2048, true, true, 0.6, 2.0, 8, 4, 16),
    HOCH("Hoch", 1.0, 2048, true, true, 1.0, 2.5, 12, 8, 28),
    ULTRA("Ultra", 1.0, 4096, true, true, 1.0, 3.5, 12, 16, 36);

    public final String label;
    /** Anteil der Bildgröße, in dem gerechnet wird. */
    public final double renderScale;
    /** Kantenlänge einer Schattenkarte. */
    public final int shadowSize;
    public final boolean bloom, fxaa;
    /** Feinrelief und Wellenzüge 0..1. */
    public final double detail;
    /** Sichtweite jeder Detailstufe des Geländes, in Knotengrößen (größer = feiner in der Ferne). */
    public final double lodRange;
    /** Phase 17: höchstens so viele Schattenproben je Punkt in der nahen Kaskade (12, 8, 6 in den drei Kaskaden bei Hoch). */
    public final int shadowTaps;
    /** Phase 17: anisotrope Filterung der Bodenkarten (Bewuchs, Alter) im 75-m-Raster. */
    public final int anisotropy;
    /** Phase 17: Schritte durch die Wolkenschicht je Bildpunkt (halbe Auflösung). */
    public final int cloudSteps;

    Quality(String label, double renderScale, int shadowSize, boolean bloom, boolean fxaa, double detail, double lodRange,
            int shadowTaps, int anisotropy, int cloudSteps) {
        this.label = label;
        this.renderScale = renderScale;
        this.shadowSize = shadowSize;
        this.bloom = bloom;
        this.fxaa = fxaa;
        this.detail = detail;
        this.lodRange = lodRange;
        this.shadowTaps = shadowTaps;
        this.anisotropy = anisotropy;
        this.cloudSteps = cloudSteps;
    }

    /** Was die Stufe einstellt, als eine Zeile (Bedienfeld, README). */
    public String summary() {
        return String.format(java.util.Locale.GERMAN, "%s: %d %% der Auflösung, Schattenkarten %d², bis %d Schattenproben, %s, %s, Feinrelief %s, "
                        + "Gelände bis %.1f Knoten weit fein, %d-fach anisotrop, %d Wolkenschritte",
                label, Math.round(renderScale * 100), shadowSize, shadowTaps, bloom ? "Bloom" : "ohne Bloom", fxaa ? "FXAA" : "ohne FXAA",
                detail >= 0.99 ? "voll" : detail > 0 ? String.format(java.util.Locale.GERMAN, "%.0f %%", detail * 100) : "aus", lodRange, anisotropy, cloudSteps);
    }

    @Override public String toString() { return label; }
}
