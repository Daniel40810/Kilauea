// ocean.vert — der Pazifik als Ringnetz um die Kamera (bis 400 km), mit Erdkrümmung abgesenkt.
// Phase 9: in der Nähe hebt und senkt die Dünung die Fläche (sea.glsl), vor der Küste steilt sie auf und
// läuft am Ufer auf; weiter als rund 3 km bleibt die Fläche eben (das Netz wird dort grob).
// Eingabe: aRing (Halbmesser m, Winkel rad). Ausgabe: vPos (abgesenkt), vFlat (Lage in der Ebene), vEta.
// Koordinatenraum: Welt. Qualitätsstufe: alle. Ersatz: ohne Raster nur die Dünung im Tiefen.
#include "common.glsl"
#include "sea.glsl"
layout(location = 0) in vec2 aRing;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform float uSeaY = 0.0;
out vec3 vPos;
out vec2 vFlat;
out float vEta;

void main() {
    vec2 xz = uCamPos.xz + aRing.x * vec2(cos(aRing.y), sin(aRing.y));
    vFlat = xz;
    float fade = fadeOut(3500.0, 1200.0, aRing.x);
    float eta = 0.0;
    if (fade > 0.0) {
        SeaS S = seaAt(xz);
        eta = seaWaves(xz, S, fade).eta;
    }
    vEta = eta;
    vPos = curved(vec3(xz.x, uSeaY + eta, xz.y), uCamPos);
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
