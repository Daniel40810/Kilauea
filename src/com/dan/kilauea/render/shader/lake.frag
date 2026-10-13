// lake.frag — Oberfläche des Lavasees (Phase 8). Aus dem Bild des Lava-Threads (LakeImage): Platten
// (Voronoi über die Plattenmitten) mit dunkler Kruste, junge noch warm, darauf feine Risse; dazwischen
// die Nähte, in denen die Glut bei 1 150 °C zu sehen ist (breiter, wo sie sich öffnen); am Ufer schlägt
// die Lava an die Wand; Spritzer leuchten auf und verglühen in Sekunden. Glut nach Planck (lava.glsl).
// Eingaben: vPos, vWorld; Uniforms unten. Ausgabe: HDR-Farbe (vorbelichtet).
// Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lighting.glsl"
#include "lava.glsl"
in vec3 vPos;
in vec3 vWorld;
out vec4 frag;
uniform vec3 uCamPos;
uniform sampler2D uLakeTex;     // R Naht, G Alter der Kruste, B Platte, A auf dem See
uniform vec4 uLakeTexRect;      // x0, z0, Seite (m)
uniform vec4 uSpatter[16];      // x, z, Alter (s), Stärke
uniform int uSpatterN = 0;

vec3 lakeVoro(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0, id = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 r = g + hash22(i + g) - f;
            float d = dot(r, r);
            if (d < d1) { d2 = d1; d1 = d; id = hash12(i + g + 3.1); } else if (d < d2) d2 = d;
        }
    return vec3(sqrt(d1), sqrt(d2) - sqrt(d1), id);
}

void main() {
    if (cutAway(vPos)) discard;
    vec2 p = vWorld.xz;
    float s = lakeShape(p);
    if (s > 1.07) discard;
    vec4 t = texture(uLakeTex, (p - uLakeTexRect.xy) / uLakeTexRect.z);
    float fw = max(length(fwidth(p)), 1e-3);
    float age = t.g * t.g * 7200.0;
    // Kruste: Oberfläche nach Alter wie an Strömen (Rheology.surfaceFactor), Kern 1 150 °C
    float surfT = 20.0 + 1130.0 / (1.0 + pow(max(age, 0.0) / 10.0, 0.25));
    float share = crackShare(surfT, 1150.0);
    float plate = mix(0.7, 2.6, t.g);
    vec2 drift = vec2(sin(uLavaTime * 0.01 + t.b * 6.0), cos(uLavaTime * 0.013 + t.b * 4.0)) * 0.3;
    vec3 v = lakeVoro((p + drift) / plate);
    float wd = clamp(share / 2.2, 0.0, 0.6);
    float crack = smoothstep(wd + 0.03, max(wd - 0.03, 0.0), v.y);
    crack = mix(crack, share, smoothstep(0.3, 1.5, fw / plate));
    float seam = smoothstep(0.05, 0.6, t.r) * t.a;
    float shore = smoothstep(0.965, 1.0, s) * (0.35 + 0.65 * smoothstep(0.35, 0.8, vnoise(p * 0.12 + uLavaTime * 0.3)));
    float open = max(crack, max(seam, shore));
    vec3 e = mix(glowRGB(surfT), glowRGB(1150.0), open);
    for (int i = 0; i < 16; i++) {
        if (i >= uSpatterN) break;
        vec4 q = uSpatter[i];
        float r = 2.0 + 6.0 * q.w;
        e += glowRGB(1140.0) * smoothstep(r, 0.0, length(p - q.xy)) * exp(-q.z / 4.0) * 1.5 * q.w;
    }
    // die Kruste selbst: dunkel, etwas glasig, Platten leicht verschieden
    Surf sf;
    sf.albedo = vec3(0.032, 0.029, 0.027) * (0.8 + 0.4 * fract(t.b * 7.3));
    sf.rough = 0.45;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    vec3 n = normalize(vec3((vnoise(p * 0.4) - 0.5) * 0.15, 1.0, (vnoise(p * 0.4 + 7.0) - 0.5) * 0.15));
    vec3 V = normalize(uCamPos - vPos);
    // ohne den eigenen Lichtschein (der See beleuchtet sich nicht selbst von oben)
    vec3 c = shadeSurface(sf, n, V, 1.0, 0.6);
    c += e * uLavaExposure;
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);   // nie NaN in den Bloom
    frag = vec4(c, 1.0);
}
