// gipfel.frag — Phase 14: Licht wie im Gleis (Sonne als Bestrahlung, vorbelichtet; Himmel als Bestrahlung/π; Dunst).
// Fassaden mit Fenstern (Achsmaß 2,8 m, Geschoss aus dem Stoff), nachts zum Teil erleuchtet; Glasband des Turms,
// Metall der Kuppeln und Rohre mit Glanz, Hindernisfeuer blinken, Granit und Grus mit Körnung. Schnee legt sich auf
// alles, was nach oben schaut; Raueis wächst auf der Luvseite (Dicke uRime.x, Wind aus uRime.yz), mit der Höhe mehr.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vMat;
in vec2 vUv;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
uniform float uFogDist, uNacht, uZeit, uPre;   // uPre: Vorbelichtung (Leuchtdichte in cd/m² mal uPre = Bildwert)
uniform vec2 uSnowWet;
uniform vec4 uRime;          // Dicke (m), Richtung, aus der der Wind kommt (x, z)
uniform vec2 uRimeBand;      // ab dieser Höhe Eis, ab dieser volle Dicke (m)
out vec4 frag;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float hash3(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }

void main() {
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    if (dot(n, V) < 0.0) n = -n;
    float m = floor(vMat + 0.001), geschoss = fract(vMat + 0.001) * 10.0;
    vec3 alb = vColor;
    float spec = 0.0, emit = 0.0;
    vec3 emitCol = vec3(0.0);
    bool fenster = false;
    if (m == 1.0 && vUv.y > 0.2) {
        // Fenster: Achsmaß 2,8 m, je Geschoss 1,4 m hoch ab 0,9 m über dem Geschossboden
        float g = max(2.4, geschoss);
        vec2 c = vec2(vUv.x / 2.8, vUv.y / g);
        vec2 f = fract(c);
        fenster = f.x > 0.28 && f.x < 0.72 && f.y > 0.9 / g && f.y < (0.9 + 1.4) / g;
        if (fenster) {
            alb = vec3(0.03, 0.035, 0.04);
            spec = 1.0;
            float an = step(0.55, hash(floor(c) + floor(vWorld.xz * 0.05)));
            emit = uNacht * an;
            emitCol = vec3(1.0, 0.78, 0.45) * 0.9;
        }
    } else if (m == 2.0) {
        // Glasband des Aussichtsgeschosses: Pfosten alle 1,5 m
        float f = fract(vUv.x / 1.5);
        if (f > 0.08) { alb = vec3(0.04, 0.05, 0.06); spec = 1.0; emit = uNacht * 0.6; emitCol = vec3(1.0, 0.85, 0.6); fenster = true; }
    } else if (m == 3.0) {
        float blink = 0.5 + 0.5 * step(0.5, fract(uZeit * 0.5));
        // Hindernisfeuer: rund 2 000 cd/m² auf der Lampe [G], am Tag kaum zu sehen
        frag = vec4(vColor * 2000.0 * uPre * blink + vColor * 0.05, 1.0);
        return;
    } else if (m == 5.0) {
        spec = 0.35;
    } else if (m == 6.0) {
        float k = hash3(floor(vWorld * 6.0));
        alb *= 0.75 + 0.5 * k;
    } else if (m == 7.0) {
        // Grus und Pflaster: Steine von rund 30 cm, schwach, dazu feines Korn
        float k = hash(floor(vWorld.xz * 3.3)), f = hash(floor(vWorld.xz * 20.0));
        alb *= 0.85 + 0.2 * k + 0.1 * f;
    } else if (m == 8.0) {
        alb *= 0.85 + 0.3 * hash(floor(vWorld.xz * 3.0 + vWorld.y));
    }
    // Nässe dunkelt matte Flächen
    alb *= 1.0 - 0.3 * uSnowWet.y * step(spec, 0.5);
    // Schnee oben (nicht auf Glas), Raueis auf der Luvseite
    float sn = uSnowWet.x * smoothstep(0.55, 0.9, n.y) * (fenster ? 0.0 : 1.0);
    float band = smoothstep(uRimeBand.x, uRimeBand.y, vWorld.y);
    float luv = max(0.0, dot(normalize(vec3(n.x, 0.0, n.z) + vec3(1e-4, 0.0, 0.0)), vec3(uRime.y, 0.0, uRime.z)));
    float ri = band * smoothstep(0.0, 0.04, uRime.x) * (0.4 + 0.6 * luv) * (1.0 - 0.4 * abs(n.y));
    ri *= 0.6 + 0.4 * hash3(floor(vWorld * 4.0));
    alb = mix(alb, vec3(0.80, 0.83, 0.88), max(sn, clamp(ri, 0.0, 1.0)));
    spec *= 1.0 - max(sn, ri);
    float diff = max(dot(n, uSunDir), 0.0);
    vec3 col = alb * (uSunColor * diff / 3.14159265 + uSkyColor * (0.55 + 0.45 * n.y));
    vec3 H = normalize(uSunDir + V);
    float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
    if (spec > 0.0) {
        col = mix(col, uSkyColor * 3.14159265 * 0.7, fres * spec);
        col += uSunColor * spec * 0.3 * pow(max(dot(n, H), 0.0), 120.0);
    }
    col += emitCol * emit * 25.0 * uPre;   // erleuchtete Fenster rund 25 cd/m² [G]
    float d = length(uCam - vWorld);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(uFogDist, 1.0)));
    frag = vec4(col, 1.0);
}
