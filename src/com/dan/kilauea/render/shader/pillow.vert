// pillow.vert — Kissenlava (Phase 10) als verformte Kugeln, je Kissen eine Instanz: unten flach, oben
// gewölbt, leicht beulig; beim Knospen bläht es sich aus seinem Ansatz heraus auf.
// Eingaben: aPos (Einheitskugel), je Instanz Lage, Länge, Breite, Höhe (m), Richtung (rad), Alter (s),
// Aufblähen 0..1, Glut der Haut und der Risse, Zufall. Ausgaben: vPos, vN, vLocal, vGlow.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aI0;   // x, y, z, Länge
layout(location = 2) in vec4 aI1;   // Breite, Höhe, Richtung, Alter
layout(location = 3) in vec4 aI2;   // Aufblähen, Glut Haut, Glut Risse, Zufall
uniform mat4 uViewProj;
uniform vec3 uCamPos;
out vec3 vPos;
out vec3 vN;
out vec3 vLocal;
out vec4 vGlow;     // Haut, Risse, Zufall, Alter

void main() {
    vec3 hax = vec3(aI0.w, aI1.y, aI1.x) * 0.5 * aI2.x;
    vec3 p = aPos;
    float seed = aI2.w * 37.0;
    float lump = 1.0 + 0.10 * sin(p.x * 4.0 + seed) * sin(p.z * 3.0 + seed * 0.7) + 0.05 * sin(p.y * 7.0 + seed * 1.3);
    vec3 q = p;
    if (q.y < 0.0) q.y *= 0.45;                     // flacher Bauch auf dem Grund
    q *= lump;
    vec3 l = q * hax;
    // beim Knospen: aus dem Ansatz (hinten) nach vorn wachsen
    l.x += (1.0 - aI2.x) * -aI0.w * 0.4;
    float c = cos(aI1.z), s = sin(aI1.z);
    vec3 w = aI0.xyz + vec3(l.x * c - l.z * s, l.y, l.x * s + l.z * c);
    vec3 nl = normalize(vec3(p.x / max(hax.x, 1e-3), (p.y < 0.0 ? p.y / 0.45 : p.y) / max(hax.y, 1e-3), p.z / max(hax.z, 1e-3)));
    vN = vec3(nl.x * c - nl.z * s, nl.y, nl.x * s + nl.z * c);
    vLocal = vec3(p.x * aI0.w, p.y * aI1.y, p.z * aI1.x);
    vGlow = vec4(aI2.y, aI2.z, aI2.w, aI1.w);
    vPos = curved(w, uCamPos);
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
