package com.dan.kilauea.render;

/**
 * Qualitätsstufen. Die App startet auf jeder Karte mit OpenGL 4.3; teure Wirkungen kommen stufenweise
 * dazu. Die Werte sind Startpunkte [G]; gemessen wird an deinem Statusbild.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, 1024, false, false, 0.0, 1.6),
    MITTEL("Mittel", 0.85, 2048, true, true, 0.6, 2.0),
    HOCH("Hoch", 1.0, 2048, true, true, 1.0, 2.5),
    ULTRA("Ultra", 1.0, 4096, true, true, 1.0, 3.5);

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

    Quality(String label, double renderScale, int shadowSize, boolean bloom, boolean fxaa, double detail, double lodRange) {
        this.label = label;
        this.renderScale = renderScale;
        this.shadowSize = shadowSize;
        this.bloom = bloom;
        this.fxaa = fxaa;
        this.detail = detail;
        this.lodRange = lodRange;
    }

    @Override public String toString() { return label; }
}
