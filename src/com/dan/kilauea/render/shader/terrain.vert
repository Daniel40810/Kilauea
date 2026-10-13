// terrain.vert — Gelände aus dem Viererbaum (CDLOD). Ein Gitter je Knoten, Lage und Größe je Instanz.
// Eingaben: aGrid (0..1 im Knoten), aNode (x0, z0, Kante in m, Stufe). Höhe aus terrain_height.glsl.
// Geomorphing: im äußeren Teil ihres Bereichs gleiten die ungeraden Punkte auf die Lage der gröberen
// Stufe; Höhe, Mip-Stufe und Relief hängen nur von der Endlage ab, darum passen Nachbarknoten.
// Mit DEPTH_ONLY nur die Lage für die Schattenkarte (uLightVP).
// Ausgaben: vPos (mit Erdkrümmung), vWorld (eben), vNormal, vMorph (Prüfbild).
// Qualitätsstufe: alle (Sichtweite und Relief über Uniforms). Ersatz: keiner.
#include "common.glsl"
#include "terrain_height.glsl"
layout(location = 0) in vec2 aGrid;
layout(location = 1) in vec4 aNode;

uniform vec3 uCamPos;
uniform float uGridDim;
uniform float uClipY = -1.0e7;
#ifdef DEPTH_ONLY
uniform mat4 uLightVP;
invariant gl_Position;
#else
uniform mat4 uViewProj;
out vec3 vPos;
out vec3 vWorld;
out vec3 vNormal;
out float vLod;
#endif

void main() {
    vec2 w = aNode.xy + aGrid * aNode.z;
    // Entfernung für das Gleiten aus einer groben Höhe (gleiche Lage → gleicher Wert in jedem Knoten)
    float hc = textureLod(uHeight, gridCoord(w) / uGridN, 2.0).r;
    float d0 = distance(vec3(w.x, hc, w.y), uCamPos);
    float end = uLodRange.x * exp2(aNode.w), start = end * uLodRange.y;
    float morph = clamp((d0 - start) / (end * 0.97 - start), 0.0, 1.0);
    vec2 frac = fract(aGrid * uGridDim * 0.5) * 2.0 / uGridDim;
    w -= frac * aNode.z * morph;
    hc = textureLod(uHeight, gridCoord(w) / uGridN, 2.0).r;
    float d = distance(vec3(w.x, hc, w.y), uCamPos);
    float h = terrainHeight(w, d);
    vec3 world = vec3(w.x, h, w.y);
    vec3 p = curved(world, uCamPos);
    gl_ClipDistance[0] = p.y - uClipY;
#ifdef DEPTH_ONLY
    gl_Position = uLightVP * vec4(p, 1.0);
#else
    // Normale aus Nachbarhöhen, Abstand wächst mit der Entfernung (stetig, also nahtlos)
    float e = clamp(d / (64.0 * uLodRange.z), 0.75, 600.0);
    float hx0 = terrainHeight(w - vec2(e, 0.0), d), hx1 = terrainHeight(w + vec2(e, 0.0), d);
    float hz0 = terrainHeight(w - vec2(0.0, e), d), hz1 = terrainHeight(w + vec2(0.0, e), d);
    vNormal = normalize(vec3(hx0 - hx1, 2.0 * e, hz0 - hz1));
    vWorld = world;
    vPos = p;
    vLod = aNode.w + morph;
    gl_Position = uViewProj * vec4(p, 1.0);
#endif
}
