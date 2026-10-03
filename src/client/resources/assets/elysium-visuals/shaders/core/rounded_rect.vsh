#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Uses the ENTITY vertex layout to carry shape parameters:
//   UV0 = position relative to the shape center (GUI units)
//   UV1 = half size * 16
//   UV2 = (corner radius * 16, mode * 16)
//         mode  > 0: outline of that thickness
//         mode == 0: filled
//         mode  < 0: outer glow with softness -mode
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 localPos;
flat out vec2 halfSize;
flat out float radius;
flat out float mode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    halfSize = vec2(UV1) / 16.0;
    radius = float(UV2.x) / 16.0;
    mode = float(UV2.y) / 16.0;
}
