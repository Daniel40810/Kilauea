// flora.frag — Licht auf Bäumen und Farnen: Blätter beidseitig (Normale zur Kamera gedreht), dazu
// Durchscheinen, wenn die Sonne hinter dem Blatt steht; Schatten aus den Kaskaden; Luftperspektive.
// Eingaben: vPos, vNormal, vColor (linear, mit Umgebungslicht im Netz). Ausgabe: HDR-Farbe.
// Qualitätsstufe: alle. Ersatz: keiner.
#include "common.glsl"
#include "lighting.glsl"
#include "cars.glsl"
#include "shadow.glsl"
in vec3 vPos;
in vec3 vNormal;
in vec3 vColor;
out vec4 frag;
uniform vec3 uCamPos;

void main() {
    if (cutAway(vPos)) discard;
    vec3 V = normalize(uCamPos - vPos);
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    // Normale, die von der Kamera weg zeigt, über die Blickebene hinaus zur Kamera kippen. Nur auf die
    // Ebene projiziert (nv = 0) stünde jedes solche Blatt streifend: volle Fresnel-Spiegelung, weißes Laub.
    if (dot(n, V) < 0.0) n = normalize(n - 1.35 * dot(n, V) * V);
    Surf sf;
    sf.albedo = vColor;
    sf.rough = 0.65;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    // Probe etwas zur Sonne hin: Laub schattet sich sonst in der groben Schattenkarte selbst
    float sv = sunShadow(vPos + uSunDir * 1.2, n, uCamPos, gl_FragCoord.xy);
    // Sonne nur diffus: der GGX-Glanz wird bei Gegenlicht auf kleinen, schräg stehenden Blättern
    // (Normale fast quer zu Sonne und Blick) riesig und färbt das Laub weiß. Himmel wie gehabt.
    vec3 c = shadeSurface(sf, n, V, 1.0, 0.0) + sf.albedo / PI * uSunColor * sv * max(dot(n, uSunDir), 0.0);
    // Durchscheinen nur bei Blättern und Wedeln (grün: G deutlich über R), nicht bei Stamm und Ästen;
    // gegen die Sonne leuchtet ein Blatt grünlich, nicht weiß
    float leafy = smoothstep(1.05, 1.35, vColor.g / max(vColor.r, 1e-3));
    float back = max(dot(-n, uSunDir), 0.0) * max(dot(-V, uSunDir), 0.0);
    c += uSunColor * sv * vColor * vec3(0.7, 1.0, 0.35) * back * 0.2 * leafy;
    c += sf.albedo / PI * carLight(vPos, n);   // Phase 15: Scheinwerfer
    vec3 vv = vPos - uCamPos;
    float dist = length(vv);
    c = aerial(c, vv / dist, dist, uSkyLut, uCamPos.y);
    frag = vec4(c, 1.0);
}
