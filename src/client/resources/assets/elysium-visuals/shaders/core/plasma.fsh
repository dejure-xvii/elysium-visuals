#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 plasmaCoord;
in vec4 colorA;
in vec3 colorB;

out vec4 fragColor;

// Animated two-color plasma fill (premultiplied, for additive blending).
void main() {
    vec2 p = plasmaCoord;
    float v = sin(p.x * 3.1) + sin(p.y * 2.7 + p.x * 0.6) + sin((p.x - p.y) * 2.2)
            + sin(length(fract(p * 0.25) - 0.5) * 9.0 - p.x);
    float t = 0.5 + 0.5 * sin(v * 1.4);
    vec3 col = mix(colorA.rgb, colorB, t);
    // Bright filaments where the waves cross.
    col += pow(1.0 - abs(sin(v * 2.0)), 6.0) * 0.35;
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    float a = colorA.a * (0.75 + 0.25 * t) * (1.0 - fog * FogColor.a) * ColorModulator.a;
    fragColor = vec4(col * a, a);
}
