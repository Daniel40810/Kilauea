package com.dan.kilauea.atmos;

/**
 * Teilchen, die als weiche Wolken gezeichnet werden (Dampf, Gasfahne, Gischt): je Teilchen 8 Werte
 * x, y, z, Größe, Deckkraft, Zufall, Art (0 Gasfahne, 1 Dampf, 2 Gischt), –, von hinten nach vorn sortiert.
 */
public interface Puffs {
    float[] puffs();
    int puffCount();
}
