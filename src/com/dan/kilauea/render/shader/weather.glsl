// weather.glsl — Passatwetter (Phase 11): Karte mit Bedeckung, Regen, Vog und Hebung (atmos/Weather), daraus
// die Dichte der Wolken in der Schicht zwischen Untergrenze und Passatinversion und der Wolkenschatten auf
// dem Boden. Die Form der Wolken kommt aus einer kachelnden 3D-Rauschtextur, die mit dem Wind treibt.
// Eingaben: uWeatherMap, uWeatherRect, uCloudNoise, uCloudLayer, uCloudWind.
// Koordinaten: Welt (m). Ersatz: uWeatherRect.w = 0 → kein Wetter, kein Wolkenschatten.
uniform sampler2D uWeatherMap;
uniform vec4 uWeatherRect = vec4(0.0);        // x0, z0, Seite (m), an
uniform vec4 uCloudLayer = vec4(650.0, 2100.0, 0.0, 0.0);   // Untergrenze, Obergrenze (m), –, –
uniform vec2 uCloudWind = vec2(0.0);          // Versatz durch den Wind (m)
uniform sampler3D uCloudNoise;

vec4 weatherAt(vec2 xz) {
    if (uWeatherRect.w < 0.5) return vec4(0.0);
    vec2 uv = (xz - uWeatherRect.xy) / uWeatherRect.z;
    return textureLod(uWeatherMap, uv, 0.0);
}

// Wolkenform ohne 3D-Textur (für Schatten in allen Shadern): zwei Lagen Wertrauschen, mit dem Wind
float cloudShape2D(vec2 xz) {
    vec2 q = (xz - uCloudWind) / 2200.0;
    return 0.65 * vnoise(q) + 0.35 * vnoise(q * 2.7 + 11.3);
}

// Dichte der Wolke 0..1 an p — dieselbe Form für das Wolkenbild und den Schatten am Boden
float cloudDensity(vec3 p) {
    vec4 w = weatherAt(p.xz);
    float cov = w.r;
    if (cov < 0.03) return 0.0;
    float base = uCloudLayer.x;
    float top = mix(base + 400.0, uCloudLayer.y, smoothstep(0.15, 0.85, cov));   // dichte Bedeckung: hohe Türme
    float h = (p.y - base) / max(top - base, 1.0);
    if (h < 0.0 || h > 1.0) return 0.0;
    float prof = smoothstep(0.0, 0.10, h) * smoothstep(1.0, 0.5, h);
    vec3 q = p - vec3(uCloudWind.x, 0.0, uCloudWind.y);
    float big = texture(uCloudNoise, q / vec3(2800.0, 1400.0, 2800.0)).r;
    float fine = texture(uCloudNoise, q / 520.0 + 0.37).g;
    float shape = 0.7 * big + 0.3 * cloudShape2D(p.xz);
    float d = shape * prof - (1.0 - cov) * 0.85 - 0.22 * (1.0 - fine) * (1.0 - h * 0.5);
    return clamp(d * 3.0, 0.0, 1.0);
}

// Durchlässigkeit der Wolken für die Sonne an pos (1 = frei): drei Proben durch die Schicht zur Sonne
float cloudShadow(vec3 pos, vec3 sunDir) {
    if (uWeatherRect.w < 0.5 || sunDir.y < 0.03 || pos.y > uCloudLayer.y) return 1.0;
    float base = max(uCloudLayer.x, pos.y), top = uCloudLayer.y;
    float od = 0.0;
    for (int k = 0; k < 3; k++) {
        float y = mix(base, top, (float(k) + 0.5) / 3.0);
        vec3 q = pos + sunDir * ((y - pos.y) / sunDir.y);
        od += cloudDensity(q);
    }
    od *= 0.012 * (top - base) / 3.0 / max(sunDir.y, 0.2);
    // Streulicht aus der Wolke hellt den Schatten auf: höchstens rund 80 % weniger Sonne [G]
    return 1.0 - 0.8 * (1.0 - exp(-od));
}
