// underwater.glsl — Licht unter Wasser (Phase 10), für Gelände, Kissenlava, Meer von unten und den
// Nebel unter Wasser. Zweck: klares Pazifikwasser schluckt Rot zuerst (Absorption und Streuung zusammen
// rund 0,38 / 0,065 / 0,035 je Meter, wie im Meer-Shader [A nach Pope & Fry 1997, Smith & Baker 1981]);
// vor einem Meereseintritt trübt Glassand das Wasser bräunlich [G]. Kaustik: das Wellenmuster der
// Oberfläche bündelt das Sonnenlicht auf dem Grund zu hellen Netzen [Erscheinung L, Muster G].
// Eingaben: uEntry (Meereseintritt: x, z, Wärmestrom MW), uWaterTime (s). Koordinaten: Welt (m, Meer bei y = 0).
uniform vec4 uEntry[8];
uniform int uEntryCount = 0;
uniform float uWaterTime = 0.0;

const vec3 WATER_SIGMA = vec3(0.38, 0.065, 0.035);
const float WATER_N = 1.333;

// Trübung vor dem Eintritt: 0 im klaren Wasser, bis rund 1,2 in der braunen Wolke (rund 50 m weit)
float turbidity(vec2 p) {
    float t = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= uEntryCount) break;
        float d = length(p - uEntry[i].xy);
        t += clamp(sqrt(max(uEntry[i].z, 0.0)) / 80.0, 0.0, 0.8) * exp(-d / 50.0);
    }
    return min(t, 1.2);
}

// Schwächung je Meter: klares Wasser plus bräunliche Trübe (Streuung, kaum farbig)
vec3 waterSigma(vec2 p) {
    return WATER_SIGMA + vec3(0.30, 0.34, 0.40) * turbidity(p);
}

// Farbe des Streulichts im Wasser (Albedo der Wassersäule): blau im klaren, oliv-braun im trüben
vec3 waterScatter(vec2 p) {
    float t = clamp(turbidity(p) / 2.0, 0.0, 1.0);
    return mix(vec3(0.006, 0.028, 0.070), vec3(0.045, 0.040, 0.025), t);
}

// Kaustik: zwei bewegte Wellenmuster, deren scharfe Kämme sich zu einem Netz überlagern
float caustic(vec2 p, float t) {
    vec2 q = p * 0.55;
    float c = 0.0;
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        vec2 r = q * (1.0 + 0.37 * fi) + vec2(t * (0.21 + 0.1 * fi), -t * (0.17 - 0.05 * fi));
        vec2 w = vec2(sin(r.x + 1.7 * sin(r.y * 0.8 + t * 0.3)), sin(r.y * 1.1 + 1.3 * sin(r.x * 0.9 - t * 0.25)));
        c += pow(1.0 - abs(w.x * w.y), 6.0);
    }
    return c * 0.5;
}

// Richtung der Sonne unter Wasser (gebrochen); y < 0, wenn sie nicht hineinscheint
vec3 sunUnderDir(vec3 sunDir) {
    if (sunDir.y <= 0.0) return vec3(0.0, -1.0, 0.0);
    return normalize(refract(-sunDir, vec3(0.0, 1.0, 0.0), 1.0 / WATER_N)) * -1.0;
}

// Licht auf einer Fläche unter Wasser: c ist die Farbe wie über Wasser gerechnet. Das Sonnen- und
// Himmelslicht hat auf dem Weg hinab die Tiefe durchquert; dazu die Kaustik.
vec3 underwaterLight(vec3 c, vec3 albedo, vec3 P, vec3 N, float sunVis, vec3 sunColor, vec3 sunDir, float time) {
    float depth = max(-P.y, 0.0);
    if (depth <= 0.0) return c;
    vec3 sd = sunUnderDir(sunDir);
    float path = depth / max(sd.y, 0.2);
    vec3 att = exp(-waterSigma(P.xz) * path);
    float k = caustic(P.xz + sd.xz / max(sd.y, 0.2) * depth, time) * smoothstep(30.0, 0.5, depth) * (1.0 - clamp(turbidity(P.xz) * 0.5, 0.0, 1.0));
    vec3 caus = albedo * sunColor * sunVis * max(dot(N, sd), 0.0) * k * 1.6 / 3.14159;
    return (c + caus) * att;
}
