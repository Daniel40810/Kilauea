package com.dan.kilauea.lava;

/**
 * Glühende Teilchen, die aufaddiert gezeichnet werden (Fontäne, Glutbrocken, Lavafälle): je Teilchen 8 Werte
 * x, y, z, Größe (negativ: weicher Kernfleck), vx, vy, vz, Temperatur (°C; −1 = Peles Haar).
 */
public interface Sparks {
    float[] sparks();
    int sparkCount();
}
