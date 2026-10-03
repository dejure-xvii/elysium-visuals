#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 plasmaCoord;
out vec4 colorA;
out vec3 colorB;

// UV0: plasma coordinates (already scaled and scrolled on the CPU)
// Color: first theme color (alpha = opacity)
// Normal: second theme color, packed into -1..1
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    plasmaCoord = UV0;
    colorA = Color;
    colorB = Normal * 0.5 + 0.5;
}
