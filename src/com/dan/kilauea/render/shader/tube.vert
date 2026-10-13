// tube.vert — Inneres der Lavaröhre Nāhuku. Zweck: Lage mit Erdkrümmung, Normale und Lage im Querschnitt
// weiterreichen. Eingaben: aPos (Welt), aNormal (nach innen), aSV (Bogenlänge s, Lage im Ring v oder −1 am Boden).
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aSV;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
out vec3 vPos;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vSV;
void main() {
    vWorld = aPos;
    vPos = curved(aPos, uCamPos);
    vNormal = aNormal;
    vSV = aSV;
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
