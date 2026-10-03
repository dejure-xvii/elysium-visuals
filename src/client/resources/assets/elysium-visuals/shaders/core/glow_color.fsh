#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

// Untextured glowing geometry (lines, ribbons, shapes). The output is
// premultiplied, so with additive blending alpha controls how much light is added.
void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    float a = color.a * (1.0 - fog * FogColor.a);
    fragColor = vec4(color.rgb * a, a);
}
