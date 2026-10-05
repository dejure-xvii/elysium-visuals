#version 330

// Shaders: bloom prefilter at half resolution — the parts brighter than the
// threshold (soft knee), averaged over 4 taps so single pixels don't flicker.

#moj_import <elysium-visuals:shaders_common.glsl>

uniform sampler2D SceneSampler;

in vec2 texCoord;
out vec4 fragColor;

vec3 bright(vec3 c) {
    float l = max(c.r, max(c.g, c.b));
    float t = Strength2.y, knee = 0.12;
    float soft = clamp(l - t + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float w = max(soft, l - t) / max(l, 1e-4);
    return c * w;
}

// The open sky is bright everywhere: it glows only a little, or the whole image goes milky.
vec3 tap(vec2 uv) {
    vec3 b = bright(texture(SceneSampler, uv).rgb);
    return isSky(texture(DepthSampler, uv).r) ? b * 0.25 : b;
}

void main() {
    vec2 o = Screen.xy;
    vec3 sum = tap(texCoord + vec2(-o.x, -o.y)) + tap(texCoord + vec2(o.x, -o.y))
             + tap(texCoord + vec2(-o.x, o.y)) + tap(texCoord + vec2(o.x, o.y));
    fragColor = vec4(sum * 0.25, 1.0);
}
