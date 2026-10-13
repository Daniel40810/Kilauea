// overlay.frag — ein fertiges Bild (vormultipliziert) über das Ziel legen (Phase 12).
// Zweck: (1) Dampf, Gischt und Asche aus dem Puffer in halber Auflösung ins HDR-Bild zurück, (2) die
// Infotafel der Drehbücher über das fertige Bild. Eingaben: uTex (RGBA, vormultipliziert), uFlipY (1 = Bild
// von oben nach unten, wie aus Java2D), uGain (Faktor). Raum: Bildschirm, über den Viewport gestreckt.
// Stufe: alle. Ersatz: keiner nötig. Mischung außen: ONE, ONE_MINUS_SRC_ALPHA.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uTex;
uniform int uFlipY = 0;
uniform float uGain = 1.0;

void main() {
    if (vUV.x > 1.0 || vUV.y > 1.0) discard;
    vec2 uv = uFlipY == 1 ? vec2(vUV.x, 1.0 - vUV.y) : vUV;
    frag = textureLod(uTex, uv, 0.0) * uGain;
}
