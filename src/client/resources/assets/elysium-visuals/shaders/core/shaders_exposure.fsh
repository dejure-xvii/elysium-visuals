#version 330

// Shaders: auto exposure, rendered into a 1x1 target. Averages the scene's
// brightness over a grid, works out the exposure that brings it to mid grey
// and moves the previous value towards it (eyes adapt over a second or two).
// The value is stored as exposure / 4 in two 8-bit channels (high and low byte).

#moj_import <elysium-visuals:shaders_common.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

float decodeExposure(vec4 v) {
    return (v.r + v.g / 255.0) * 4.0;
}

vec4 encodeExposure(float e) {
    float x = clamp(e / 4.0, 0.0, 0.9999);
    float hi = floor(x * 255.0) / 255.0;
    float lo = fract(x * 255.0);
    return vec4(hi, lo, 0.0, 1.0);
}

void main() {
    float sum = 0.0, weight = 0.0;
    for (int y = 0; y < 12; y++) {
        for (int x = 0; x < 12; x++) {
            vec2 uv = (vec2(x, y) + 0.5) / 12.0;
            // The middle of the screen counts more (what you look at).
            float w = 1.0 - 0.6 * length(uv - 0.5);
            vec3 c = texture(SceneSampler, uv).rgb;
            float l = luma(pow(c, vec3(2.2)));
            sum += log(l + 0.004) * w;
            weight += w;
        }
    }
    float avg = exp(sum / weight);
    // Brighten dark scenes (caves) more than darken bright ones, and never blow out the sky.
    float target = clamp(0.16 / (avg + 0.01), 0.65, 1.6);
    float previous = decodeExposure(texture(PreviousSampler, vec2(0.5)));
    if (previous <= 0.01) {
        previous = 1.0;
    }
    // Steps.w holds the adaptation factor for this frame here.
    float e = mix(previous, target, clamp(Steps.w, 0.0, 1.0));
    fragColor = encodeExposure(e);
}
