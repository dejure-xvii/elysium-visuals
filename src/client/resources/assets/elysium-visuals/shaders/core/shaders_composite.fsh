#version 330

// Shaders, full resolution: applies the reduced-resolution results (AO, light
// rays, reflections) and does the per-pixel work — wet surfaces with sun glints
// and puddles, the sky glow, underwater fog.

#moj_import <elysium-visuals:shaders_common.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D ReflectSampler;
uniform sampler2D LightSampler;

in vec2 texCoord;
out vec4 fragColor;

vec3 skyGlow(vec3 c, vec3 dir) {
    float day = SunDir.w;
    float mu = max(dot(dir, normalize(SunDir.xyz)), 0.0);
    // Deeper blue up high, brighter haze at the horizon, a soft glow around the sun.
    float up = clamp(dir.y, 0.0, 1.0);
    vec3 zenith = c * mix(vec3(1.0), vec3(0.82, 0.92, 1.12), day * smoothstep(0.1, 0.7, up));
    vec3 horizon = mix(zenith, zenith + SunColor.rgb * 0.04, (1.0 - up) * day);
    vec3 glow = SunColor.rgb * (pow(mu, 8.0) * 0.18 + pow(mu, 64.0) * 0.25) * (1.0 - SkyColor.w * 0.7);
    return horizon + glow;
}

// The reduced-resolution buffers, softened with 4 taps around the pixel (hides the
// upscaling and the sampling noise). Taps across a depth edge are left out.
vec4 soft(sampler2D s, vec2 uv, float depth) {
    vec2 t = Screen.xy * (Strength3.z > 1.5 ? 1.0 : 2.0);
    vec4 sum = texture(s, uv) * 2.0;
    float w = 2.0;
    float here = length(positionAt(uv, depth));
    vec2 offsets[4] = vec2[](vec2(t.x, t.y), vec2(-t.x, t.y), vec2(t.x, -t.y), vec2(-t.x, -t.y));
    for (int i = 0; i < 4; i++) {
        vec2 q = uv + offsets[i];
        float d = texture(DepthSampler, q).r;
        if (isSky(d) == isSky(depth) && abs(length(positionAt(q, d)) - here) < 0.6 + here * 0.03) {
            sum += texture(s, q);
            w += 1.0;
        }
    }
    return sum / w;
}

void main() {
    vec3 c = texture(SceneSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = isSky(depth);
    vec3 p = positionAt(texCoord, depth);
    vec3 dir = normalize(p);
    vec4 light = (Flags1.z > 0.5 || Flags1.y > 0.5) ? soft(LightSampler, texCoord, depth) : vec4(1.0, 0.0, 0.0, 1.0);

    if (sky) {
        if (Flags3.w > 0.5) {
            c = skyGlow(c, dir);
        }
    } else {
        vec3 n = normalAt(texCoord, p);
        // Ambient occlusion: darkens the ambient part (less in bright direct light).
        if (Flags1.z > 0.5) {
            c *= mix(1.0, light.r, 0.9);
        }
        // Wet surfaces: darker and more saturated, glints of the sun, puddles.
        float wet = wetnessAt(p, n);
        if (wet > 0.0) {
            float puddle = puddleMask(p, n) * Strength3.x;
            float dark = wet * 0.42 + puddle * 0.25;
            vec3 sat = mix(vec3(luma(c)), c, 1.0 + wet * 0.3);
            c = sat * (1.0 - dark);
            vec3 h = normalize(normalize(SunDir.xyz) - dir);
            float glint = pow(max(dot(n, h), 0.0), mix(48.0, 220.0, puddle)) * (wet * 0.6 + puddle * 1.4);
            c += SunColor.rgb * glint * (0.25 + 0.75 * SunDir.w) * (1.0 - SkyColor.w * 0.5);
        }
        // Reflections (and the puddle mirror).
        if (Flags1.x > 0.5 || Flags1.w > 0.5) {
            vec4 r = soft(ReflectSampler, texCoord, depth);
            c = mix(c, r.rgb, r.a);
        }
    }

    // Light rays from the sun through gaps in the terrain and the clouds.
    if (Flags1.y > 0.5) {
        c += SunColor.rgb * light.g * 0.32 * (1.0 - SkyColor.w * 0.6);
    }

    // Under water: a denser tinted fog that hides the distance.
    if (Flags2.w > 0.5 && FogColor.w > 0.5) {
        float dist = sky ? 200.0 : length(p);
        float f = 1.0 - exp(-dist * 0.055);
        c = mix(c * vec3(0.82, 0.95, 1.0), FogColor.rgb, f * 0.9);
    }
    fragColor = vec4(c, 1.0);
}
