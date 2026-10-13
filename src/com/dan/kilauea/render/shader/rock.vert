// rock.vert — Felsen als festes Netz (Felsbogen Hōlei). Zweck: Lage mit Erdkrümmung, Normale weiterreichen;
// mit DEPTH_ONLY nur die Lage für die Schattenkarte. Eingaben: aPos (Welt), aNormal.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform vec3 uCamPos;
#ifdef DEPTH_ONLY
uniform mat4 uLightVP;
#else
uniform mat4 uViewProj;
out vec3 vPos;
out vec3 vWorld;
out vec3 vNormal;
#endif
void main() {
    vec3 cp = curved(aPos, uCamPos);
#ifdef DEPTH_ONLY
    gl_Position = uLightVP * vec4(cp, 1.0);
#else
    vPos = cp;
    vWorld = aPos;
    vNormal = aNormal;
    gl_Position = uViewProj * vec4(cp, 1.0);
#endif
}
