// fountain.vert — Teilchen der Lavafontäne (Phase 8) als Streifen längs der Bewegung (Belichtungszeit
// rund 1/30 s), quer zur Blickrichtung. Eingaben je Instanz: Lage, Größe (m), Geschwindigkeit, Temperatur
// (°C; −1 = Peles Haar). Ausgaben: vUV (−1..1), vTemp, vPos. Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
layout(location = 0) in vec4 aPosSize;
layout(location = 1) in vec4 aVelTemp;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
out vec2 vUV;
out float vTemp;
out vec3 vPos;
out float vCore;
void main() {
    vec2 c = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID & 2) == 0 ? -1.0 : 1.0);
    vec3 p = aPosSize.xyz;
    vec3 vel = aVelTemp.xyz;
    vec3 view = normalize(p - uCamPos);
    float sp = length(vel);
    vec3 along = sp > 1e-3 ? vel / sp : vec3(0.0, 1.0, 0.0);
    vec3 side = cross(along, view);
    side = length(side) > 1e-4 ? normalize(side) : vec3(1.0, 0.0, 0.0);
    bool core = aPosSize.w < 0.0;                // Kern des Strahls: großer weicher Fleck, kaum gestreckt
    float size = abs(aPosSize.w);
    float len = core ? size * 1.6 : size + sp / 30.0;          // Streifen aus der Bewegung
    float wid = core ? size * 0.5 : size * (aVelTemp.w < 0.0 ? 0.08 : 0.5);   // Haar: dünn
    vec3 w = p + along * c.y * len * 0.5 + side * c.x * wid;
    vPos = curved(w, uCamPos);
    vUV = c;
    vTemp = aVelTemp.w;
    vCore = core ? 1.0 : 0.0;
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
