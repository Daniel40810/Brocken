// schatten.frag — Phase 3 (aus Geiranger): die Schatten der Kuppe und der Täler je Sonnenstand, Zeile für Zeile wie
// scene.Kuppenschatten. Je Texel einer Karte über dem Hauptraster oder dem Fernring (uShadowRect: Ecke x, z,
// Kantenlänge, Texelgröße) läuft ein Strahl in der Fläche zur Lichtquelle (uKey) über das Gelände, Schritte wachsend bis
// 30 km, mit Erdkrümmung; der steilste Winkel ist der Horizont. Ausgabe: Anteil direkten Lichts 0..1, weich um den
// Sonnenrand (R). Der Strahl beginnt 2 m über dem Grund (Fichten, Augenhöhe). Seit Phase 7 in G die Höhe (m), ab der die
// Sonne über das ganze Gelände scheint: der Schatten der Kuppe im Nebel (com.dan.fog, Light.sunHeightTexture).
#include "common.glsl"
#include "terrain_height.glsl"
out vec4 frag;
uniform vec4 uShadowRect;
uniform vec3 uKey;
const float SOFT = 0.012;

void main() {
    vec2 w = uShadowRect.xy + gl_FragCoord.xy * uShadowRect.w;
    float hl = length(uKey.xz);
    float alt = atan(uKey.y, hl);
    if (alt < -SOFT) { frag = vec4(0.0, 1e4, 0.0, 0.0); return; }
    if (hl < 1e-6) { frag = vec4(1.0, -1e4, 0.0, 0.0); return; }
    float ground = heightAt(w, 0.0);
    float y = ground + 2.0 + 0.25 * uShadowRect.w;
    vec2 d = uKey.xz / hl;
    float tanAlt = tan(max(alt, -0.2));
    // Phase 7: dazu die Höhe über dem Ort, ab der die Sonne über alles Gelände scheint (für den Schatten im Nebel)
    float maxTan = -1e9, need = -1e9, stp = 0.5 * uShadowRect.w, s = stp;
    for (int i = 0; i < 200; i++) {
        if (s > 30000.0) break;
        float lvl = clamp(log2(max(stp, 1.0) / uGrid.z) - 1.0, 0.0, 8.0);
        float g = heightAt(w + d * s, lvl) - s * s / (2.0 * EARTH_R);
        maxTan = max(maxTan, (g - y) / s);
        need = max(need, g - s * tanAlt);
        if (max(y, need) + s * tanAlt > 1300.0) break;
        stp *= 1.07;
        s += stp;
    }
    frag = vec4(smoothstep(-SOFT, SOFT, alt - atan(maxTan)), max(ground, need), 0.0, 0.0);
}
