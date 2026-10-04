#version 330

#define MAX_RECTS 64

uniform sampler2D BlurSampler;

layout(std140) uniform MaskInfo {
    // xy: screen size in pixels, z: number of rects
    vec4 Info;
    // x, y (bottom-left origin), width, height in pixels
    vec4 Rects[MAX_RECTS];
    // x: corner radius in pixels, y: opacity
    vec4 Shapes[MAX_RECTS];
};

in vec2 texCoord;
out vec4 fragColor;

float roundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// The blurred world, only under the HUD plates (anti-aliased rounded corners).
void main() {
    vec2 px = texCoord * Info.xy;
    float coverage = 0.0;
    int n = int(Info.z);
    for (int i = 0; i < n; i++) {
        vec4 rect = Rects[i];
        vec2 halfSize = rect.zw * 0.5;
        float r = min(Shapes[i].x, min(halfSize.x, halfSize.y));
        float d = roundedBox(px - (rect.xy + halfSize), halfSize, r);
        coverage = max(coverage, clamp(0.5 - d, 0.0, 1.0) * Shapes[i].y);
    }
    if (coverage <= 0.002) {
        discard;
    }
    fragColor = vec4(texture(BlurSampler, texCoord).rgb, coverage);
}
