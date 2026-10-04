#version 330

// Upscales the low-resolution clouds onto the full-resolution frame. The
// nearby cloud texels are weighted by a tent filter and by how well the
// terrain distance each was marched against matches this pixel's, so clouds
// don't bleed over the edges of blocks and mountains.

uniform sampler2D CloudSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform CompositeInfo {
    mat4 InvViewProj;
    // xy: low-res size, zw: 1 / low-res size
    vec4 LowSize;
    // x: 1 if NDC depth is 0..1
    vec4 Misc;
};

in vec2 texCoord;
out vec4 fragColor;

float distanceAt(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    if (depth <= 0.0) {
        return 1e6; // sky
    }
    float z = Misc.x > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 w = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return length(w.xyz / w.w);
}

void main() {
    float here = distanceAt(texCoord);
    // 3x3 low-res neighbourhood around this pixel: a soft tent filter (smooths the
    // raymarch noise) weighted by depth similarity (keeps block and mountain edges clean).
    vec2 lp = texCoord * LowSize.xy - 0.5;
    vec2 center = floor(lp + 0.5);
    vec4 sum = vec4(0.0);
    float wsum = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 cell = center + vec2(i, j);
            vec2 uv = (cell + 0.5) * LowSize.zw;
            vec2 d = abs(cell - lp);
            float tent = max(0.0, 1.5 - d.x) * max(0.0, 1.5 - d.y);
            float there = distanceAt(uv);
            float similar = 1.0 / (1e-3 + abs(there - here) / max(here, 1.0) * 40.0);
            float w = tent * min(similar, 1000.0) + 1e-6;
            sum += texture(CloudSampler, uv) * w;
            wsum += w;
        }
    }
    vec4 col = sum / wsum;
    if (col.a < 0.002) {
        discard;
    }
    fragColor = col;
}
