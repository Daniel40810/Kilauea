// section.vert — Flächen des aufgeklappten Blocks (Phase 6): die Schnittebene, die beiden Stirnseiten
// und der Boden. Zweck: ein Einheitsgitter (u, v) auf die Fläche legen, mit Erdkrümmung.
// Eingaben: aUV (0..1). Ausgaben: Lage, Welt, (h, y) in der Fläche.
#include "common.glsl"
layout(location = 0) in vec2 aUV;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform vec2 uFaceOrigin;   // Ursprung der Fläche (x, z)
uniform vec2 uFaceDir;      // Richtung von h (x, z)
uniform vec2 uFaceSide;     // nur Boden: Richtung von v (x, z)
uniform vec4 uFaceRange;    // h0, h1, y0, y1 (Boden: v0, v1 statt y)
uniform int uFloor;         // 1 = waagerechter Boden in Höhe uFloorY
uniform float uFloorY;
out vec3 vPos;
out vec3 vWorld;
out vec2 vAY;
void main() {
    float h = mix(uFaceRange.x, uFaceRange.y, aUV.x);
    float v = mix(uFaceRange.z, uFaceRange.w, aUV.y);
    vec3 w;
    if (uFloor == 1) w = vec3(uFaceOrigin.x + uFaceDir.x * h + uFaceSide.x * v, uFloorY, uFaceOrigin.y + uFaceDir.y * h + uFaceSide.y * v);
    else w = vec3(uFaceOrigin.x + uFaceDir.x * h, v, uFaceOrigin.y + uFaceDir.y * h);
    vWorld = w;
    vAY = vec2(h, w.y);
    vPos = curved(w, uCamPos);
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
