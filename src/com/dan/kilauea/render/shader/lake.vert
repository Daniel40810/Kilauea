// lake.vert — Spiegel des Lavasees im Halemaʻumaʻu (Phase 8): eine Scheibe in Seekoordinaten (u, v),
// gedreht und verschoben in die Welt, auf der Höhe des Spiegels. Ausgaben: vPos (abgesenkt), vWorld.
// Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
layout(location = 0) in vec2 aUV;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform vec4 uLakeGeoV;     // Mitte x, z, cos, sin der Drehung
uniform float uLakeY;       // Spiegel (m ü. M.)
out vec3 vPos;
out vec3 vWorld;
void main() {
    vec2 w = uLakeGeoV.xy + vec2(aUV.x * uLakeGeoV.z - aUV.y * uLakeGeoV.w, aUV.x * uLakeGeoV.w + aUV.y * uLakeGeoV.z);
    vWorld = vec3(w.x, uLakeY, w.y);
    vPos = curved(vWorld, uCamPos);
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
