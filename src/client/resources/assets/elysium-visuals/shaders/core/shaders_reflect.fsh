#version 330

// Shaders: screen-space reflections at reduced resolution. Output: rgb the
// reflected colour, a how much of it the surface shows (fresnel, flat ground,
// wetness and puddles). Rays that leave the screen fall back to the sky colour.

#moj_import <elysium-visuals:shaders_common.glsl>

uniform sampler2D SceneSampler;

in vec2 texCoord;
out vec4 fragColor;

vec3 skyFallback(vec3 dir) {
    float up = clamp(dir.y, 0.0, 1.0);
    return mix(SkyColor.rgb * 0.75, SkyColor.rgb, up);
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (isSky(depth) || (Flags1.x < 0.5 && Flags1.w < 0.5)) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 p = positionAt(texCoord, depth);
    float dist = length(p);
    if (dist > 160.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 n = normalAt(texCoord, p);
    vec3 v = p / dist;
    vec3 r = reflect(v, n);

    float wet = wetnessAt(p, n);
    float puddle = wet > 0.0 ? puddleMask(p, n) * Strength3.x : 0.0;
    float up = smoothstep(0.55, 0.95, n.y);
    float cosv = clamp(dot(-v, n), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosv, 5.0);
    // No materials here, so roughness is guessed from the texture: busy textures
    // (grass, leaves, gravel) are rough and reflect little, smooth ones (water,
    // polished stone, glass) like a mirror.
    vec2 o = Screen.xy * 2.0;
    float l0 = luma(texture(SceneSampler, texCoord).rgb);
    float l1 = luma(texture(SceneSampler, texCoord + vec2(o.x, 0.0)).rgb), l2 = luma(texture(SceneSampler, texCoord - vec2(o.x, 0.0)).rgb);
    float l3 = luma(texture(SceneSampler, texCoord + vec2(0.0, o.y)).rgb), l4 = luma(texture(SceneSampler, texCoord - vec2(0.0, o.y)).rgb);
    float detail = (abs(l1 - l0) + abs(l2 - l0) + abs(l3 - l0) + abs(l4 - l0)) * 0.25;
    float rough = smoothstep(0.03, 0.1, detail);
    // Far away the texture detail is averaged out by mipmaps: green plants still count as rough.
    vec3 albedo = texture(SceneSampler, texCoord).rgb;
    rough = max(rough, smoothstep(0.04, 0.12, albedo.g - max(albedo.r, albedo.b)));
    // Dry ground reflects only a little (and less further away); wet ground more, puddles like water.
    float base = Flags1.x > 0.5 ? Strength1.x * (0.1 + 0.5 * up) * mix(1.0, 0.1, rough) * (1.0 - smoothstep(24.0, 80.0, dist)) : 0.0;
    // Water soaks into grass: fewer and duller puddles there.
    puddle *= mix(1.0, 0.4, rough);
    float weight = fresnel * (base + wet * mix(0.7, 0.2, rough)) + puddle * mix(0.45, 0.9, fresnel);
    weight = clamp(weight, 0.0, 0.92) * (1.0 - smoothstep(110.0, 160.0, dist));
    if (weight < 0.01) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 color = skyFallback(r);
    float steps = Steps.x;
    if (steps > 0.5 && r.y > -0.6) {
        float maxDist = Strength1.y;
        float stepLen = maxDist / steps;
        // A fixed start (no per-pixel jitter): steadier, and the refinement removes the banding.
        float jitter = 0.5;
        vec3 origin = p + n * (0.04 + dist * 0.002);
        float t = stepLen * (0.3 + jitter);
        bool hit = false;
        vec2 hitUv = vec2(0.0);
        for (int i = 0; i < 64; i++) {
            if (float(i) >= steps) {
                break;
            }
            vec3 q = origin + r * t;
            vec3 s = project(q);
            if (s.z < 0.0 || s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0) {
                break;
            }
            float sd = texture(DepthSampler, s.xy).r;
            if (!isSky(sd)) {
                vec3 scenePos = positionAt(s.xy, sd);
                float sceneDist = length(scenePos);
                float rayDist = length(q);
                // About one step thick: thicker and the ray "hits" things it passed behind (striped copies).
                float thickness = stepLen * 1.3 + 0.15;
                // A "hit" on the reflecting surface itself is depth imprecision at a grazing
                // angle (it showed as stripes across puddles): only things off that plane count.
                bool offPlane = dot(scenePos - p, n) > 0.25 + dist * 0.01;
                if (offPlane && sceneDist < rayDist && rayDist - sceneDist < thickness) {
                    // Refine between the last two steps.
                    float a = t - stepLen, b = t;
                    for (int k = 0; k < 4; k++) {
                        float m = (a + b) * 0.5;
                        vec3 qm = origin + r * m;
                        vec3 sm = project(qm);
                        float dm = length(positionAt(sm.xy));
                        if (dm < length(qm)) { b = m; } else { a = m; }
                    }
                    hitUv = project(origin + r * b).xy;
                    hit = true;
                    break;
                }
            }
            t += stepLen;
            stepLen *= 1.06;
        }
        if (hit) {
            // Fade out near the screen edges and towards the end of the ray.
            vec2 edge = smoothstep(vec2(0.0), vec2(0.15), hitUv) * smoothstep(vec2(0.0), vec2(0.15), 1.0 - hitUv);
            float fade = edge.x * edge.y * (1.0 - smoothstep(0.6, 1.0, t / maxDist));
            color = mix(color, texture(SceneSampler, hitUv).rgb, fade);
        } else if (r.y < 0.05) {
            // Missed downwards: the surroundings are darker than the sky.
            color *= 0.45;
        }
    }
    fragColor = vec4(color, weight);
}
