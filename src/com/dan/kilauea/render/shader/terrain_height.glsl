// terrain_height.glsl — Höhe des Geländes für Vertex-Shader (Gelände und Schatten).
// Zweck: Höhentextur mit kubischem B-Spline (wie HeightGrid.height in Java), in der Ferne aus gröberen
// Mip-Stufen (stetig nach der Entfernung, damit benachbarte Knoten dieselbe Fläche sehen), dazu das
// Feinrelief (wie Relief.java). Koordinaten: Welt, Meter. Braucht common.glsl (vnoise).
// Phase 8: dazu die Grube des Lavasees und die Lava dieses Laufs (lava.glsl), in derselben Reihenfolge
// wie Ground.solidHeight und Ground.height in Java. Phase 12: dazu die Verformung (deformAt in lava.glsl).
#include "lava.glsl"
uniform sampler2D uHeight;     // R32F, Höhe je 75-m-Zelle, mit Mips
uniform sampler2D uCover;      // RGBA8: Bewuchs, Baumanteil, Asche/Tephra, Küste
uniform sampler2D uGeo;        // RGBA8: Alterscode der Lava, ʻaʻā-Anteil, Flechten, Regen
uniform sampler2D uRoad;       // R8: Band um die Straßen (1 = kein Feinrelief)
uniform sampler2D uTiles;      // R16F: Atlas der Detailkacheln, Unterschied zum 75-m-Gelände (m)
uniform int uTileCount = 0;    // Zahl der Kacheln (höchstens 8)
uniform vec4 uTileRect[8];     // je Kachel: x0, z0 (Welt, m), Zellgröße (m), –
uniform vec4 uTileUV[8];       // je Kachel: Lage im Atlas (Punkte), Zellen nx, nz
uniform vec3 uGrid;            // X0, Z0 (Mitte der Zelle 0,0), Zellgröße
uniform float uGridN;          // Zellen je Seite
uniform vec3 uLodRange;        // Sichtweite der feinsten Stufe (m), Beginn des Gleitens (Anteil), Sichtweite in Knotengrößen
uniform float uRelief = 1.0;   // Feinrelief 0..1 (Qualitätsstufe)

// Lage in Zellen, so dass Zelle i ihre Mitte bei i + 0,5 hat
vec2 gridCoord(vec2 w) { return (w - uGrid.xy) / uGrid.z + 0.5; }

float bsplineLevel(vec2 c, float lvl) {
    float s = exp2(lvl), n = uGridN / s;
    vec2 t = c / s - 0.5;
    vec2 i = floor(t), f = t - i;
    vec2 f2 = f * f, f3 = f2 * f, it = 1.0 - f;
    vec2 w0 = it * it * it / 6.0, w1 = (3.0 * f3 - 6.0 * f2 + 4.0) / 6.0;
    vec2 w2 = (-3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0) / 6.0, w3 = f3 / 6.0;
    vec2 g0 = w0 + w1, g1 = w2 + w3;
    vec2 h0 = (i - 0.5 + w1 / g0) / n, h1 = (i + 1.5 + w3 / g1) / n;
    float a = textureLod(uHeight, vec2(h0.x, h0.y), lvl).r, b = textureLod(uHeight, vec2(h1.x, h0.y), lvl).r;
    float c0 = textureLod(uHeight, vec2(h0.x, h1.y), lvl).r, d = textureLod(uHeight, vec2(h1.x, h1.y), lvl).r;
    return g0.y * (g0.x * a + g1.x * b) + g1.y * (g0.x * c0 + g1.x * d);
}

// Mip-Stufe nach der Entfernung: ein Texel etwa so groß wie der Punktabstand dort
float heightLevel(float dist) {
    return clamp(log2(max(dist, 1.0) / (64.0 * uLodRange.z * uGrid.z)), 0.0, 7.0);
}

float baseHeight(vec2 w, float lvl) {
    vec2 c = gridCoord(w);
    float l0 = floor(lvl);
    float a = bsplineLevel(c, l0);
    if (lvl - l0 < 0.01) return a;
    return mix(a, bsplineLevel(c, l0 + 1.0), lvl - l0);
}

float fbm2(vec2 p) {
    float s = 0.5 * vnoise(p);
    p = p * 2.03 + vec2(17.1, 9.7);
    s += 0.25 * vnoise(p);
    return s / 0.75;
}

// Alter in Jahren aus dem Code (wie Geology.ageOf: logarithmisch bis 300 000 Jahre)
float lavaAge(float code01) { return exp(code01 * log(300001.0)) - 1.0; }
float youngOf(float age) { return 1.0 - smoothstep(30.0, 300.0, age); }

// Feinrelief (m), Stufen in der Ferne ausgeblendet, bevor das Gitter sie nicht mehr trägt.
// young: 1 junge Lava; aa: ʻaʻā-Anteil (raue feine Stufen); veg: Bewuchs (glättet). Wie Relief.java.
float relief(vec2 w, float young, float aa, float veg, float h, float dist) {
    float amp = (1.0 + 0.4 * young) * (1.0 - 0.55 * veg) * smoothstep(-60.0, -8.0, h) * uRelief;
    if (amp <= 0.0) return 0.0;
    const float WAVE[4] = float[](420.0, 95.0, 24.0, 6.5);
    const float AMP[4] = float[](7.0, 2.4, 0.75, 0.22);
    float s = 0.0;
    for (int k = 0; k < 4; k++) {
        float fade = smoothstep(WAVE[k] * 20.0 * uLodRange.z, WAVE[k] * 8.0 * uLodRange.z, dist);
        if (fade <= 0.0) continue;
        vec2 p = w / WAVE[k] + vec2(17.3, -9.1) * float(k);
        float m = k >= 2 ? 1.0 + 1.2 * aa : 1.0;
        s += AMP[k] * m * (fbm2(p) - 0.5) * 2.0 * fade;
    }
    return s * amp;
}

// Höhe mit Relief an w; dist bestimmt Mip-Stufe und Ausblendung
// Detailkacheln (bilinear, Gitterpunkt i auf Texelmitte, wie DetailTile.at); in der Ferne aus gröberen
// Mips; Kacheln überlappen nicht, außerhalb aller Kacheln 0
float detailDelta(vec2 w, float dist) {
    float d = 0.0;
    vec2 atlas = vec2(textureSize(uTiles, 0));
    for (int i = 0; i < 8; i++) {
        if (i >= uTileCount) break;
        vec2 c = (w - uTileRect[i].xy) / uTileRect[i].z;
        if (any(lessThan(c, vec2(0.0))) || any(greaterThan(c, uTileUV[i].zw - 1.0))) continue;
        float lvl = clamp(log2(max(dist, 1.0) / (64.0 * uLodRange.z * uTileRect[i].z)), 0.0, 6.0);
        d += textureLod(uTiles, (uTileUV[i].xy + c + 0.5) / atlas, lvl).r;
    }
    return d;
}

float terrainHeight(vec2 w, float dist) {
    float lvl = heightLevel(dist);
    float h = baseHeight(w, lvl) + detailDelta(w, dist);
    vec2 uv = gridCoord(w) / uGridN;
    float veg = textureLod(uCover, uv, lvl).x;
    vec2 ge = textureLod(uGeo, uv, lvl).xy;
    float roadFlat = smoothstep(0.3, 0.7, textureLod(uRoad, uv, 0.0).r);
    h += relief(w, youngOf(lavaAge(ge.x)), ge.y, veg, h, dist) * (1.0 - roadFlat) + deformAt(w);
    return withLava(w, lakePit(w, h));
}

// Höhe ohne Lava dieses Laufs (mit der Grube): das feste Gelände
float solidHeight(vec2 w, float dist) {
    float lvl = heightLevel(dist);
    float h = baseHeight(w, lvl) + detailDelta(w, dist);
    vec2 uv = gridCoord(w) / uGridN;
    float veg = textureLod(uCover, uv, lvl).x;
    vec2 ge = textureLod(uGeo, uv, lvl).xy;
    float roadFlat = smoothstep(0.3, 0.7, textureLod(uRoad, uv, 0.0).r);
    h += relief(w, youngOf(lavaAge(ge.x)), ge.y, veg, h, dist) * (1.0 - roadFlat) + deformAt(w);
    return lakePit(w, h);
}
