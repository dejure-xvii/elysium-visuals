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

// Rings spreading where drops hit a puddle (world xz). Each 1.4-block cell gets a
// drop now and then at a random spot; returns x = ring brightness, yz = the
// direction the ring pushes the surface (to wobble the reflection).
vec3 ripples(vec2 xz) {
    const float CELL = 1.4;
    vec2 id = floor(xz / CELL);
    vec3 sum = vec3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 cid = id + vec2(x, y);
            float h = hash12(cid);
            float t = Camera.w * (0.7 + h * 0.6) + h * 7.3;
            float cycle = floor(t), phase = fract(t);
            // Not every cell gets a drop every cycle: more of them the stronger the drops.
            if (hash12(cid + cycle * 1.37) > SunScreen.w * 0.9 + 0.05) {
                continue;
            }
            vec2 center = (cid + 0.2 + 0.6 * vec2(hash12(cid + cycle + 3.1), hash12(cid + cycle + 7.7))) * CELL;
            vec2 d = xz - center;
            float len = length(d);
            float radius = phase * 0.75;
            float ring = exp(-pow((len - radius) * 16.0, 2.0)) * (1.0 - phase) * (1.0 - phase);
            sum.x += ring;
            sum.yz += (len > 1e-3 ? d / len : vec2(0.0)) * ring;
        }
    }
    return sum;
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
        vec2 wobble = vec2(0.0);
        if (wet > 0.0) {
            float puddle = puddleMask(p, n) * Strength3.x;
            // Drops: rings on the puddles that bend the reflection a little.
            float ring = 0.0;
            if (SunScreen.w > 0.001 && puddle > 0.02 && length(p) < 48.0) {
                vec3 rp = ripples(p.xz + Camera.xz);
                ring = rp.x * puddle * smoothstep(48.0, 20.0, length(p));
                wobble = rp.yz * ring * 0.006;
            }
            float dark = wet * 0.42 + puddle * 0.25;
            vec3 sat = mix(vec3(luma(c)), c, 1.0 + wet * 0.3);
            c = sat * (1.0 - dark);
            vec3 h = normalize(normalize(SunDir.xyz) - dir);
            float glint = pow(max(dot(n, h), 0.0), mix(48.0, 220.0, puddle)) * (wet * 0.6 + puddle * 1.4);
            c += SunColor.rgb * glint * (0.25 + 0.75 * SunDir.w) * (1.0 - SkyColor.w * 0.5);
            // The ring edges catch the light of the sky.
            c += (SkyColor.rgb * 0.5 + 0.15) * ring * 0.35;
        }
        // Reflections (and the puddle mirror).
        if (Flags1.x > 0.5 || Flags1.w > 0.5) {
            vec4 r = soft(ReflectSampler, texCoord + wobble, depth);
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
