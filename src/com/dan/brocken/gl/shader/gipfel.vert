// gipfel.vert — Phase 14: die Bauten am Gipfel als fertiges Netz in der Szene (Ort, Normale, Farbe, Stoff, uv).
// uv an Wänden: Strecke längs der Wand (m), Höhe über dem Boden (m); der Bruchteil des Stoffs Fassade ist das Geschoss/10.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aMat;
layout(location = 4) in vec2 aUv;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vMat;
out vec2 vUv;

void main() {
    vWorld = aPos;
    vNormal = aNrm;
    vColor = aCol;
    vMat = aMat;
    vUv = aUv;
    gl_Position = uVP * vec4(aPos, 1.0);
}
