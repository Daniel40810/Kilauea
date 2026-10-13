// steam.vert — Dampfteilchen als zur Kamera gedrehte Quadrate. Eingaben je Instanz: Lage und Größe (m),
// Deckkraft, Zufall, Art (0 Gasfahne, 1 Dampf aus Spalten). Ausgaben: vUV (−1..1), Lage, Werte.
#include "common.glsl"
layout(location = 0) in vec4 aPosSize;
layout(location = 1) in vec4 aAlpha;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform vec3 uCamRight, uCamUp;
out vec2 vUV;
out vec3 vPos;
out vec4 vData;
void main() {
    vec2 c = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID & 2) == 0 ? -1.0 : 1.0);
    // je Teilchen etwas gedreht, damit sich die Muster nicht wiederholen
    float a = aAlpha.y * 6.2832;
    vec2 r = mat2(cos(a), -sin(a), sin(a), cos(a)) * c;
    vec3 w = aPosSize.xyz + (uCamRight * r.x + uCamUp * r.y) * aPosSize.w * 0.5;
    vPos = curved(w, uCamPos);
    vUV = c;
    vData = aAlpha;
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
