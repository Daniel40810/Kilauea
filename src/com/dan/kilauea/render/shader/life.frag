// life.frag — Licht auf den Figuren (Phase 15): beidseitige Flächen (Flügel, Blätter der Rotoren), Sonne mit
// Schatten aus den Kaskaden, Himmel, Lichtschein der Lava, Luftperspektive; Scheinwerfer und Blitzlicht
// leuchten selbst. Mit THERMAL die Temperatur für das Wärmebild (Mischung MAX): Körper und Motoren
// wärmer als die Luft, das Abgas des Hubschraubers heiß [G].
// Eingaben: vPos, vNormal, vColor, vEmit, vHot. Ausgabe: HDR-Farbe bzw. Temperatur und Deckung.
// Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lighting.glsl"
#include "cars.glsl"
#include "shadow.glsl"
#include "lava.glsl"
in vec3 vPos;
in vec3 vNormal;
in vec3 vColor;
in float vEmit;
in float vHot;
out vec4 frag;
uniform vec3 uCamPos;
uniform float uExposure = 1.0;
uniform float uWarm = 10.0;     // Oberfläche wärmer als die Luft (K) [G]
uniform float uHotT = 450.0;    // Abgas (°C) [G]

void main() {
    if (cutAway(vPos)) discard;
#ifdef THERMAL
    float air = 24.0 - 0.0065 * max(vPos.y, 0.0);
    float T = mix(air + uWarm, uHotT, vHot);
    frag = vec4(T, 2.0, 0.0, 1.0);   // Deckung 2: das Wärmebild erkennt eine Figur
    return;
#endif
    vec3 V = normalize(uCamPos - vPos);
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    if (dot(n, V) < 0.0) n = normalize(n - 1.2 * dot(n, V) * V);
    Surf sf;
    sf.albedo = vColor;
    sf.rough = 0.5;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos + uSunDir * 0.15, n, uCamPos, gl_FragCoord.xy);
    vec3 c = shadeSurface(sf, n, V, 1.0, sv);
    c += sf.albedo * lavaLight(vPos.xz) / PI * uLavaExposure;
    c += sf.albedo / PI * carLight(vPos, n);
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    // selbst leuchtend: Scheinwerfer, Rücklichter, Blitzlicht
    c += vColor * vEmit * uExposure * exp(-dist * (uBetaM + 0.5 * dot(uBetaR, vec3(0.333))));
    if (any(isnan(c))) c = vec3(0.0);
    frag = vec4(c, 1.0);
}
