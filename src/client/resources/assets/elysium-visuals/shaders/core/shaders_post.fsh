#version 330

// Shaders, last pass: depth of field, chromatic aberration, sharpening, bloom,
// auto exposure, tonemapping and saturation.

#moj_import <elysium-visuals:shaders_common.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
uniform sampler2D ExposureSampler;

in vec2 texCoord;
out vec4 fragColor;

const float GOLDEN = 2.39996323;

float viewDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    return isSky(d) ? 512.0 : length(positionAt(uv, d));
}

vec3 sampleScene(vec2 uv) {
    if (Flags3.y > 0.5) {
        // Chromatic aberration: red and blue pulled apart towards the edges.
        vec2 off = (uv - 0.5) * 0.0045;
        return vec3(texture(SceneSampler, uv + off).r, texture(SceneSampler, uv).g, texture(SceneSampler, uv - off).b);
    }
    return texture(SceneSampler, uv).rgb;
}

vec3 depthOfField(vec2 uv, vec3 c) {
    float focus = viewDistance(vec2(0.5));
    float dist = viewDistance(uv);
    float coc = clamp(abs(dist - focus) / (focus * 0.6 + 6.0) - 0.08, 0.0, 1.0);
    if (coc <= 0.01) {
        return c;
    }
    float radius = coc * 7.0;
    float taps = Steps.w;
    vec3 sum = c;
    float wsum = 1.0;
    float jitter = ign(gl_FragCoord.xy) * 6.2831853;
    for (int i = 0; i < 24; i++) {
        if (float(i) >= taps) {
            break;
        }
        float f = (float(i) + 0.5) / taps;
        float a = float(i) * GOLDEN + jitter;
        vec2 o = vec2(cos(a), sin(a)) * sqrt(f) * radius * Screen.xy;
        // Sharp things in front don't bleed into the blur behind them.
        float sd = viewDistance(uv + o);
        float w = sd < dist - 1.0 && abs(sd - focus) < abs(dist - focus) ? 0.3 : 1.0;
        sum += sampleScene(uv + o) * w;
        wsum += w;
    }
    return sum / wsum;
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec3 c = sampleScene(texCoord);
    if (Flags3.x > 0.5) {
        c = depthOfField(texCoord, c);
    }
    if (Flags3.z > 0.5) {
        // Contrast-adaptive sharpening (light): pushes away from the neighbours' average.
        vec2 t = Screen.xy;
        vec3 n = texture(SceneSampler, texCoord + vec2(t.x, 0.0)).rgb + texture(SceneSampler, texCoord - vec2(t.x, 0.0)).rgb
               + texture(SceneSampler, texCoord + vec2(0.0, t.y)).rgb + texture(SceneSampler, texCoord - vec2(0.0, t.y)).rgb;
        vec3 detail = c - n * 0.25;
        float amount = 0.55 * (1.0 - smoothstep(0.1, 0.4, length(detail)));
        c = max(c + detail * amount, 0.0);
    }

    vec3 lin = pow(max(c, 0.0), vec3(2.2));
    if (Flags2.x > 0.5) {
        vec3 bloom = texture(BloomSampler, texCoord).rgb;
        lin += pow(bloom, vec3(2.2)) * Strength2.x * 1.4;
    }
    if (Flags2.z > 0.5) {
        vec4 e = texture(ExposureSampler, vec2(0.5));
        float exposure = (e.r + e.g / 255.0) * 4.0;
        lin *= mix(1.0, clamp(exposure, 0.4, 3.0), Strength2.z);
    }
    if (Flags2.y > 0.5) {
        // Filmic curve on the brightness only (the hue and saturation stay, so a
        // blue sky doesn't fade to white), normalised so white stays white:
        // deeper shadows, a soft shoulder, mid tones about where they were.
        const float K = 0.6;
        float l = luma(lin);
        float mapped = aces(vec3(l * K)).x / aces(vec3(K)).x;
        lin = lin * (mapped / max(l, 1e-4));
        // Colours pushed past 1 slide towards white instead of clipping into a different hue.
        float peak = max(lin.r, max(lin.g, lin.b));
        if (peak > 1.0) {
            lin = mix(lin / peak, vec3(1.0), clamp((peak - 1.0) * 0.5, 0.0, 1.0));
        }
    }
    c = pow(clamp(lin, 0.0, 1.0), vec3(1.0 / 2.2));
    c = mix(vec3(luma(c)), c, Strength2.w);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
