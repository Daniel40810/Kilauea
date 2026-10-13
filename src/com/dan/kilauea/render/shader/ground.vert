// ground.vert — Bodendecke (Farne, Gras, ʻōhelo, Steine) aus com.dan.ground, schon bewegt.
// Eingaben: Lage (Welt), Normale, Farbe (linear), fest (1 Stein/Erde, 0 dünn), Ausblenden 0..1.
#include "common.glsl"
#include "lava.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in float aSolid;
layout(location = 4) in float aFade;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
out vec3 vPos;
out vec3 vNormal;
out vec3 vColor;
out float vSolid;
out float vFade;
void main() {
    // Phase 8: unter Lava gibt es keine Bodendecke
    float lavaCov;
    lavaTopOnly(aPos.xz, lavaCov);
    // Phase 10: unter dem Meeresspiegel keine Bodendecke (Gras, Farn, Steine des Landes)
    if (abs(deformAt(aPos.xz)) > 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }   // Phase 12: Verformung
    if (lavaCov > 0.25 || aPos.y < 0.2) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }
    vPos = curved(aPos, uCamPos);
    vNormal = aNormal;
    vColor = aColor;
    vSolid = aSolid;
    vFade = aFade;
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
