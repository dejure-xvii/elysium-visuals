#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Additive particles (blend ONE, ONE): the output is premultiplied, so texture
// alpha and the fade-in/out alpha scale how much light the sprite adds.
// Fog fades the light out instead of tinting it, so distant sparks just vanish.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    float a = color.a * (1.0 - fog * FogColor.a);
    fragColor = vec4(color.rgb * a, a);
}
