// lava.glsl — die Lava dieses Laufs (Phase 8) und die Grube des Lavasees, für Höhe und Aussehen.
// Zweck: Zellen des Lava-Modells (10 m) aus einem Blockatlas lesen: eine Tabelle sagt je Block aus
// 32 × 32 Zellen, wo er im Atlas liegt (−1 = nicht belegt). Dazu die Glut nach Planck (Tabelle aus
// lava/Glow.java, Engine-Einheiten wie das Sonnenlicht) und der Lichtschein auf die Umgebung (Glutkarte).
// Eingaben: Uniforms unten (LavaLayer.bind). Koordinaten: Welt, Meter. Ausgaben: Funktionen.
// Qualitätsstufe: alle. Ersatz: uLavaOn = 0 → alles wie ohne Lava.
uniform int uLavaOn = 0;
uniform isampler2D uLavaTable;   // R32I: Block im Atlas oder −1
uniform sampler2D uLavaA;        // RGBA32F: Oberfläche (m ü. M.), Dicke über dem Gelände, bewegliche Lava, Kerntemperatur (°C)
uniform sampler2D uLavaC;        // RGBA32F: Oberflächentemperatur (°C), ʻaʻā, Merkmale, Alter der Kruste (s)
uniform sampler2D uLavaV;        // RG16F: Fließen nach x und z (m/s)
uniform vec4 uLavaGrid;          // Ursprung x, z (Ecke der Zelle 0, 0), Zellgröße, Blöcke je Seite
uniform vec4 uLavaBox = vec4(1.0, 1.0, -1.0, -1.0);   // belegtes Gebiet x0, z0, x1, z1
uniform sampler2D uGlowTex;      // RGB16F: Lichtschein (Bestrahlungsstärke) in rund 12 m Höhe
uniform vec4 uGlowRect = vec4(0.0);   // x0, z0, Seite (m), an (1)
uniform vec3 uGlowLut[48];       // Strahldichte RGB von 400 °C in Schritten von 18,5 °C
uniform float uLavaExposure = 1.0;   // Belichtung (das Bild ist vorbelichtet)
uniform float uLavaTime = 0.0;   // Sekunden (für die treibende Kruste)
uniform int uLakeOn = 0;
uniform vec4 uLakeGeo;           // Mitte x, z, halbe Länge a, halbe Breite b
uniform vec4 uLakeGeo2;          // cos, sin der Drehung, Kraterboden (m ü. M.), Tiefe der Grube

// Phase 12: Verformung des Geländes (Einsturz 2018), wie terrain/Deformation.java
uniform sampler2D uDeform;       // RG32F: Unterschied Gipfel vor 2018 − heute (m), Ringmaß 0 (Mitte) … 1 (Ring)
uniform vec4 uDeformRect = vec4(0.0);   // x0, z0, Seite (m), an (1)
uniform float uDeformF = 1.0;    // Fortschritt 0 (vor dem Einsturz) … 1 (heute)

float deformAt(vec2 w) {
    if (uDeformRect.w < 0.5) return 0.0;
    vec2 uv = (w - uDeformRect.xy) / uDeformRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    vec2 d = textureLod(uDeform, uv, 0.0).rg;
    return d.r * (1.0 - clamp(1.6 * uDeformF - 0.6 * d.g, 0.0, 1.0));
}

const int LAVA_TUBE = 1, LAVA_SKY = 2, LAVA_VENT = 4, LAVA_SEA = 8, LAVA_DRAINED = 16;

// Glut einer Fläche der Temperatur t (°C), Engine-Einheiten (noch nicht vorbelichtet)
vec3 glowRGB(float t) {
    float u = (t - 400.0) / 18.5;
    if (u <= 0.0) return vec3(0.0);
    int i = min(46, int(u));
    return mix(uGlowLut[i], uGlowLut[i + 1], clamp(u - float(i), 0.0, 1.0));
}

// Anteil offener Risse nach Oberfläche und Kern (wie GlowMap.crackShare)
float crackShare(float surf, float core) {
    if (core <= 21.0) return 0.0;
    float e = clamp((surf - 20.0) / (core - 20.0), 0.0, 1.0);
    float s = smoothstep(0.2, 0.9, e);
    return 0.003 + 0.997 * pow(s, 2.5);
}

bool lavaInBox(vec2 w) {
    return uLavaOn != 0 && w.x > uLavaBox.x && w.y > uLavaBox.y && w.x < uLavaBox.z && w.y < uLavaBox.w;
}

// Atlas-Punkt einer Zelle (globale Zellkoordinaten) oder (−1, −1)
ivec2 lavaTexel(ivec2 cell) {
    int side = int(uLavaGrid.w);
    ivec2 b = cell / 32;
    if (cell.x < 0 || cell.y < 0 || b.x >= side || b.y >= side) return ivec2(-1);
    int id = texelFetch(uLavaTable, b, 0).r;
    if (id < 0) return ivec2(-1);
    return ivec2(id % 32, id / 32) * 32 + (cell - b * 32);
}

struct LavaS {
    float cover;     // 0..1: wie viel der Stelle Lava oder neues Gestein ist
    float top;       // Oberfläche (m ü. M.)
    float add;       // Dicke über dem Gelände (m)
    float h;         // bewegliche Lava (m)
    float core;      // Kerntemperatur (°C)
    float surf;      // Oberflächentemperatur (°C)
    float aa;        // ʻaʻā-Anteil
    int flags;       // Merkmale der nächsten Zelle
    float age;       // Alter der Kruste (s)
    vec2 vel;        // Fließen (m/s)
    vec2 center;     // Mitte der nächsten Zelle (Welt)
};

// Nur die Höhe (für Vertex-Shader): Oberfläche der bedeckten Nachbarzellen, gewichtet, und die Bedeckung
float lavaTopOnly(vec2 w, out float cover) {
    cover = 0.0;
    if (!lavaInBox(w)) return -1.0e9;
    vec2 c = (w - uLavaGrid.xy) / uLavaGrid.z - 0.5;
    ivec2 i0 = ivec2(floor(c));
    vec2 f = c - vec2(i0);
    float top = 0.0;
    for (int k = 0; k < 4; k++) {
        ivec2 o = ivec2(k & 1, k >> 1);
        ivec2 t = lavaTexel(i0 + o);
        if (t.x < 0) continue;
        vec4 a = texelFetch(uLavaA, t, 0);
        if (a.y < 0.02) continue;
        float wk = (o.x == 0 ? 1.0 - f.x : f.x) * (o.y == 0 ? 1.0 - f.y : f.y);
        cover += wk;
        top += wk * a.x;
    }
    return cover > 1e-4 ? top / cover : -1.0e9;
}

// Alles (für Fragment-Shader)
LavaS lavaSample(vec2 w) {
    LavaS L;
    L.cover = 0.0; L.top = 0.0; L.add = 0.0; L.h = 0.0; L.core = 0.0; L.surf = 20.0; L.aa = 0.0; L.flags = 0; L.age = 0.0;
    L.vel = vec2(0.0); L.center = w;
    if (!lavaInBox(w)) return L;
    vec2 c = (w - uLavaGrid.xy) / uLavaGrid.z - 0.5;
    ivec2 i0 = ivec2(floor(c));
    vec2 f = c - vec2(i0);
    float best = -1.0;
    float sw = 0.0;
    for (int k = 0; k < 4; k++) {
        ivec2 o = ivec2(k & 1, k >> 1);
        ivec2 t = lavaTexel(i0 + o);
        if (t.x < 0) continue;
        vec4 a = texelFetch(uLavaA, t, 0);
        if (a.y < 0.02) continue;
        vec4 b = texelFetch(uLavaC, t, 0);
        vec2 v = texelFetch(uLavaV, t, 0).xy;
        float wk = (o.x == 0 ? 1.0 - f.x : f.x) * (o.y == 0 ? 1.0 - f.y : f.y);
        sw += wk;
        L.top += wk * a.x; L.add += wk * a.y; L.h += wk * a.z; L.core += wk * a.w;
        L.surf += wk * (b.x - 20.0); L.aa += wk * b.y; L.age += wk * b.w; L.vel += wk * v;
        if (wk > best) {
            best = wk;
            L.flags = int(b.z + 0.5);
            L.center = uLavaGrid.xy + (vec2(i0 + o) + 0.5) * uLavaGrid.z;
        }
    }
    L.cover = sw;
    if (sw > 1e-4) {
        float iw = 1.0 / sw;
        L.top *= iw; L.add *= iw; L.h *= iw; L.core *= iw; L.surf = 20.0 + (L.surf - 20.0) * iw; L.aa *= iw; L.age *= iw; L.vel *= iw;
    }
    return L;
}

// ------------------------------------------------------------ Grube des Lavasees (wie LavaLake.pit)
float lakeShape(vec2 w) {
    vec2 d = w - uLakeGeo.xy;
    float u = d.x * uLakeGeo2.x + d.y * uLakeGeo2.y, v = -d.x * uLakeGeo2.y + d.y * uLakeGeo2.x;
    float th = atan(v / uLakeGeo.w, u / uLakeGeo.z);
    float wob = 1.0 + 0.05 * sin(3.0 * th + 1.1) + 0.03 * sin(7.0 * th + 2.3);
    return length(vec2(u / uLakeGeo.z, v / uLakeGeo.w)) / wob;
}

float lakePit(vec2 w, float h) {
    if (uLakeOn == 0) return h;
    float s = lakeShape(w);
    if (s >= 1.06) return h;
    float t = smoothstep(0.985, 1.06, s);
    return min(h, uLakeGeo2.z - uLakeGeo2.w * (1.0 - t));
}

// Höhe mit Lava: über dem Gelände die Oberfläche der Lava, am Rand ausgeblendet
float withLava(vec2 w, float h) {
    float cov;
    float top = lavaTopOnly(w, cov);
    if (cov <= 0.0) return h;
    return mix(h, max(h, top), smoothstep(0.15, 0.55, cov));
}

// Lichtschein der Lava an w (Bestrahlungsstärke, Engine-Einheiten, noch nicht vorbelichtet)
vec3 lavaLight(vec2 w) {
    if (uGlowRect.w < 0.5) return vec3(0.0);
    vec2 uv = (w - uGlowRect.xy) / uGlowRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec3(0.0);
    return texture(uGlowTex, uv).rgb;
}
