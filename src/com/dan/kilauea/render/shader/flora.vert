// flora.vert — Bäume und Baumfarne als Instanzen. Zweck: Netz drehen, skalieren, im Wind biegen und
// auf das Gelände stellen (mit Erdkrümmung). Mit DEPTH_ONLY nur die Lage für die Schattenkarte.
// Eingaben: aPos, aNormal, aColor (Netz, Fuß bei 0); aInst (x, y, z, Drehung), aInst2 (Maßstab, Tönung, Phase, –).
// Ausgaben: vPos (abgesenkt), vNormal, vColor. Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lava.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in vec4 aInst;
layout(location = 4) in vec4 aInst2;

uniform vec3 uCamPos;
uniform float uTime;
uniform vec2 uWind;            // Wind (m/s) nach x und z
#ifdef DEPTH_ONLY
uniform mat4 uLightVP;
#else
uniform mat4 uViewProj;
out vec3 vPos;
out vec3 vNormal;
out vec3 vColor;
#endif

void main() {
    // Phase 8: unter Lava ist die Pflanze verbrannt (weg); dicht an heißer Lava verdorrt sie [G]
    float lavaCov;
    lavaTopOnly(aInst.xz, lavaCov);
    // Phase 12: wo das Gelände gerade verformt ist (Einsturz 2018), stehen keine Pflanzen
    if (abs(deformAt(aInst.xz)) > 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    if (lavaCov > 0.3 || aInst.y < 0.2) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }   // nicht im Meer (Phase 10)
    float c = cos(aInst.w), s = sin(aInst.w);
    vec3 p = aPos * aInst2.x;
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    // Wind: Biegung mit dem Quadrat der Höhe, Böen laufen über die Fläche
    float gust = 0.65 + 0.35 * sin(uTime * 0.9 + aInst2.z + dot(aInst.xz, vec2(0.021, 0.017)));
    float sway = 0.25 * sin(uTime * 2.3 + aInst2.z * 3.0) * smoothstep(0.0, 4.0, length(uWind));
    float hgt = max(p.y, 0.0);
    p.xz += (uWind * gust * 0.06 + vec2(sway) * 0.15) * hgt * hgt * 0.02;
    vec3 w = aInst.xyz + p;
    vec3 cp = curved(w, uCamPos);
#ifdef DEPTH_ONLY
    gl_Position = uLightVP * vec4(cp, 1.0);
#else
    vec3 n = aNormal;
    vNormal = vec3(c * n.x - s * n.z, n.y, s * n.x + c * n.z);
    // Tönung je Pflanze: heller, dunkler, gelblicher
    vColor = aColor * mix(vec3(0.82, 0.88, 0.85), vec3(1.18, 1.10, 0.95), aInst2.y);
    float heat = dot(lavaLight(aInst.xz), vec3(0.2126, 0.7152, 0.0722));
    vColor = mix(vColor, vec3(0.035, 0.028, 0.022), smoothstep(0.05, 0.6, heat) * 0.85);
    vPos = cp;
    gl_Position = uViewProj * vec4(cp, 1.0);
#endif
}
