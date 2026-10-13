// cars.glsl — Licht der Scheinwerfer (Phase 15): die acht nächsten Autos mit Licht beleuchten, was vor
// ihnen liegt. Abblendlicht als Kegel nach vorn (rund ±30° breit), oben scharf abgeschnitten, nach
// unten weich [G]; zwei Lampen zusammen rund 40 000 cd [L Größenordnung], in Einheiten des
// Sonnenlichts (1 ≈ 5 000 lx) [A]. Eingaben: Uniforms unten. Qualitätsstufe: alle. Ersatz: keiner.
uniform vec4 uCarLight[8];      // Lampenmitte x, y, z; Lichtstärke (Sonnen-Einheiten · m²)
uniform vec4 uCarDir[8];        // Fahrtrichtung x, z (waagerecht)
uniform int uCarLights = 0;
uniform float uCarExposure = 1.0;

vec3 carLight(vec3 p, vec3 n) {
    vec3 e = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= uCarLights) break;
        vec3 d = p - uCarLight[i].xyz;
        float r2 = max(dot(d, d), 0.5);
        vec3 l = d * inversesqrt(r2);
        float hl = length(l.xz);
        if (hl < 1e-4) continue;
        float hcos = dot(l.xz / hl, uCarDir[i].xy);
        float side = smoothstep(0.82, 0.96, hcos);
        float vert = smoothstep(-0.40, -0.03, l.y) * (1.0 - smoothstep(-0.005, 0.02, l.y));
        e += uCarLight[i].w * side * vert / r2 * max(dot(n, -l), 0.0);
    }
    return e * vec3(1.0, 0.94, 0.82) * uCarExposure;
}
