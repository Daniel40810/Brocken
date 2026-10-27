// terrain_height.glsl — Höhe des Geländes für Vertex- und Fragment-Shader (Phase 2, aus Geiranger, mit Kernraster).
// Drei Raster als R16 (normiert, mit Mip-Stufen), Höhe über NHN = Wert · uHMap.x + uHMap.y, bilinear wie Terrain.height
// in Java, damit Kamera und Bild dieselbe Fläche sehen:
//   Kern (DGM1, 1536² zu 2 m, Einheit 11; uKernOn = 0 ohne DGM1) blendet auf den letzten uKNaht m ins Hauptraster über,
//   Hauptraster (GLO-30, 1024² zu 12,5 m, Einheit 8) blendet auf den letzten uNaht m in den Fernring über,
//   Fernring (DGM200, 1024² zu 200 m, Einheit 10).
// In der Ferne kommen gröbere Mip-Stufen (stetig nach der Entfernung, damit Nachbarknoten dieselbe Fläche sehen).
uniform sampler2D uHeight;
uniform sampler2D uFern;
uniform sampler2D uKern;
uniform usampler2D uSource;    // Nutzungsart je Zelle des Hauptrasters (Terrain.ART)
uniform vec3 uGrid;            // X0, Z0 (Nordwestecke), Zellgröße des Hauptrasters
uniform float uGridN;          // Zellen je Seite
uniform vec3 uFernGrid;        // dasselbe für den Fernring
uniform float uFernN;
uniform vec3 uKernGrid;        // dasselbe für den Kern
uniform float uKernN;
uniform int uKernOn;
uniform float uNaht, uKNaht;   // Breite der Überblendbänder (m)
uniform vec2 uHMap;            // Spanne, kleinste Höhe
uniform sampler2D uTrasse;     // Phase 12: Bahnkörper, Änderung der Höhe (m, R32F, Raster zu 2 m um das Gleis)
uniform vec4 uTrasseRect;      // Ecke x, z, Breite, Tiefe
uniform int uTrasseOn;
uniform vec3 uLodRange;        // Sichtweite der feinsten Stufe (m), Beginn des Gleitens (Anteil), Sichtweite in Knotengrößen

vec2 gridUV(vec2 w) { return (w - uGrid.xy) / (uGrid.z * uGridN); }
vec2 fernUV(vec2 w) { return (w - uFernGrid.xy) / (uFernGrid.z * uFernN); }
vec2 kernUV(vec2 w) { return (w - uKernGrid.xy) / (uKernGrid.z * uKernN); }

// Mip-Stufe nach der Entfernung, bezogen auf das Hauptraster: ein Texel etwa so groß wie der Punktabstand dort. Nicht
// begrenzt: das feinere Kernraster braucht die Werte unter 0, der Fernring die über 4 (heightAt begrenzt je Raster).
float heightLevel(float dist) { return clamp(log2(max(dist, 1.0) / (64.0 * uLodRange.z * uGrid.z)), -8.0, 14.0); }

// Abstand zum Rand eines Rasters (m, positiv innen)
float insideOf(vec2 w, vec3 g, float n) {
    vec2 d = min(w - g.xy, g.xy + g.z * n - w);
    return min(d.x, d.y);
}
float insideDist(vec2 w) { return insideOf(w, uGrid, uGridN); }

float heightAt(vec2 w, float lvl) {
    float f = 1.0 - smoothstep(0.0, uNaht, insideDist(w));
    float hm = f < 1.0 ? textureLod(uHeight, gridUV(w), clamp(lvl, 0.0, 10.0)).r : 0.0;
    // der Fernring ist 16-mal gröber: dieselbe Entfernung braucht dort eine um 4 kleinere Stufe
    float hf = f > 0.0 ? textureLod(uFern, fernUV(w), clamp(lvl - 4.0, 0.0, 10.0)).r : 0.0;
    float h = mix(hm, hf, f);
    if (uKernOn == 1) {
        float k = smoothstep(0.0, uKNaht, insideOf(w, uKernGrid, uKernN));
        // der Kern ist 6,25-mal feiner: eine um log2(6,25) = 2,64 höhere Stufe
        if (k > 0.0) h = mix(h, textureLod(uKern, kernUV(w), clamp(lvl + 2.64, 0.0, 10.0)).r, k);
    }
    h = h * uHMap.x + uHMap.y;
    // Phase 12: Damm und Einschnitt der Bahn (wie Terrain.height mit der Korrektur in Java)
    if (uTrasseOn == 1) {
        vec2 tu = (w - uTrasseRect.xy) / uTrasseRect.zw;
        if (all(greaterThan(tu, vec2(0.0))) && all(lessThan(tu, vec2(1.0)))) h += textureLod(uTrasse, tu, 0.0).r;
    }
    return h;
}

float terrainHeight(vec2 w, float dist) { return heightAt(w, heightLevel(dist)); }

uint artAt(vec2 w) {
    if (insideDist(w) <= 0.0) return 0u;
    ivec2 c = clamp(ivec2(floor((w - uGrid.xy) / uGrid.z)), ivec2(0), ivec2(int(uGridN) - 1));
    return texelFetch(uSource, c, 0).r;
}

// Art weich: Anteil der Nachbarzellen (2 × 2, bilinear) mit dieser Art – keine Treppen an den Rändern
float artShare(vec2 w, uint a) {
    if (insideDist(w) <= 0.0) return a == 0u ? 1.0 : 0.0;
    vec2 u = (w - uGrid.xy) / uGrid.z - 0.5;
    ivec2 i = ivec2(floor(u));
    vec2 f = u - vec2(i);
    float s = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int c = 0; c <= 1; c++) {
            ivec2 q = clamp(i + ivec2(c, b), ivec2(0), ivec2(int(uGridN) - 1));
            float wt = (c == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
            s += wt * (texelFetch(uSource, q, 0).r == a ? 1.0 : 0.0);
        }
    return s;
}
