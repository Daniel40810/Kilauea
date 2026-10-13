// road.vert — Parkstraße auf dem Gelände. Zweck: Punkt der Straße (x, z, Querlage u, Bogenlänge s) mit
// derselben Höhenfunktion wie das Gelände anheben (das Feinrelief ist im Straßenband aus), ein wenig
// darüber legen (mit der Entfernung mehr, weil grobe Geländestufen von der Funktion abweichen).
// Ausgaben: vPos (abgesenkt), vWorld, vUS, vNormal. Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "terrain_height.glsl"
layout(location = 0) in vec4 aRoad;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
out vec3 vPos;
out vec3 vWorld;
out vec2 vUS;
out vec3 vNormal;
out float vLava;               // Phase 8: Lava deckt die Straße zu

void main() {
    vec2 w = aRoad.xy;
    float hc = textureLod(uHeight, gridCoord(w) / uGridN, 2.0).r;
    float d = distance(vec3(w.x, hc, w.y), uCamPos);
    float h = terrainHeight(w, d);
    float e = clamp(d / (64.0 * uLodRange.z), 0.75, 600.0);
    float hx0 = terrainHeight(w - vec2(e, 0.0), d), hx1 = terrainHeight(w + vec2(e, 0.0), d);
    float hz0 = terrainHeight(w - vec2(0.0, e), d), hz1 = terrainHeight(w + vec2(0.0, e), d);
    vNormal = normalize(vec3(hx0 - hx1, 2.0 * e, hz0 - hz1));
    vec3 world = vec3(w.x, h + 0.04 + d * 0.0015, w.y);
    lavaTopOnly(w, vLava);
    vWorld = world;
    vUS = aRoad.zw;
    vPos = curved(world, uCamPos);
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
