// shadow_depth.vert — Tiefe aus Sicht der Sonne für Netze mit fester Lage (spätere Phasen: Bauten,
// Pflanzen). Das Gelände nimmt terrain.vert mit DEPTH_ONLY. Rechnet dieselbe Erdkrümmung.
layout(location = 0) in vec3 aPos;
uniform mat4 uLightVP;
uniform mat4 uModel = mat4(1.0);
uniform float uClipY = -1.0e7;   // Schnitt unter dieser Höhe (Spiegelung)
uniform vec3 uCamPos;
uniform float uCurve = 1.0;
invariant gl_Position;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vec2 d = wp.xz - uCamPos.xz;
    wp.y -= uCurve * dot(d, d) / (2.0 * 6371008.8);
    gl_ClipDistance[0] = wp.y - uClipY;
    gl_Position = uLightVP * wp;
}
