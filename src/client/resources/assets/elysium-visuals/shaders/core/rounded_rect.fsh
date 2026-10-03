#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in float radius;
flat in float mode;

out vec4 fragColor;

// Signed distance to a rounded rectangle centered at the origin.
float roundedRectSdf(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - (b - r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float sd = roundedRectSdf(localPos, halfSize, radius);
    // Width of one screen pixel in GUI units: gives 1px anti-aliasing at any GUI scale.
    float aa = max(fwidth(sd), 1e-4);

    float coverage;
    if (mode > 0.0) {
        coverage = clamp(0.5 - sd / aa, 0.0, 1.0) - clamp(0.5 - (sd + mode) / aa, 0.0, 1.0);
    } else if (mode < 0.0) {
        float t = clamp(sd / -mode, 0.0, 1.0);
        coverage = (1.0 - t) * (1.0 - t) * clamp(0.5 + sd / aa, 0.0, 1.0);
    } else {
        coverage = clamp(0.5 - sd / aa, 0.0, 1.0);
    }

    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
