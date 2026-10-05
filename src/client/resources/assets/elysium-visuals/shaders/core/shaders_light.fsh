#version 330

// Shaders: ambient occlusion and light rays at reduced resolution.
// Output: r = how much ambient light reaches the pixel (1 = all), g = light ray intensity.

#moj_import <elysium-visuals:shaders_common.glsl>

in vec2 texCoord;
out vec4 fragColor;

const float GOLDEN = 2.39996323;

float ambientOcclusion(vec2 uv, float depth) {
    if (Flags1.z < 0.5 || isSky(depth)) {
        return 1.0;
    }
    vec3 p = positionAt(uv, depth);
    float dist = length(p);
    if (dist > 96.0) {
        return 1.0;
    }
    vec3 n = normalAt(uv, p);
    // A tangent frame around the normal.
    vec3 t = normalize(abs(n.y) < 0.99 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
    vec3 b = cross(n, t);
    float jitter = ign(gl_FragCoord.xy + Screen.w * 7.13) * 6.2831853;
    float samples = Steps.y;
    float radius = 1.1;
    // Depth precision drops with distance: a larger margin there avoids false occlusion.
    float bias = 0.02 + dist * 0.004;
    float occlusion = 0.0;
    for (int i = 0; i < 16; i++) {
        if (float(i) >= samples) {
            break;
        }
        float fi = (float(i) + 0.5) / samples;
        float a = float(i) * GOLDEN + jitter;
        float rr = sqrt(fi);
        // Cosine-weighted hemisphere, scaled so near samples dominate.
        vec3 dir = t * (cos(a) * rr) + b * (sin(a) * rr) + n * sqrt(max(0.0, 1.0 - fi));
        vec3 s = p + n * 0.03 + dir * radius * mix(0.25, 1.0, fi);
        vec3 su = project(s);
        if (su.z < 0.0 || su.x < 0.0 || su.x > 1.0 || su.y < 0.0 || su.y > 1.0) {
            continue;
        }
        float sd = texture(DepthSampler, su.xy).r;
        if (isSky(sd)) {
            continue;
        }
        float sceneDist = length(positionAt(su.xy, sd));
        float sampleDist = length(s);
        float diff = sampleDist - sceneDist;
        if (diff > bias) {
            // Range check: a wall far behind the sample doesn't occlude.
            occlusion += 1.0 - smoothstep(radius, radius * 2.5, diff);
        }
    }
    // Half the samples blocked is already a dark corner.
    float ao = 1.0 - clamp(occlusion / max(samples, 1.0) * 2.0, 0.0, 1.0) * Strength1.w;
    return clamp(ao, 0.0, 1.0);
}

float lightRays(vec2 uv) {
    if (Flags1.y < 0.5 || SunScreen.z <= 0.0) {
        return 0.0;
    }
    vec2 sun = SunScreen.xy;
    vec2 delta = (sun - uv);
    float samples = Steps.z;
    vec2 stepUv = delta / samples;
    float jitter = ign(gl_FragCoord.xy + Screen.w * 3.7);
    vec2 q = uv + stepUv * jitter;
    float decay = 1.0, sum = 0.0, weightSum = 0.0;
    for (int i = 0; i < 48; i++) {
        if (float(i) >= samples) {
            break;
        }
        q += stepUv;
        if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0) {
            break;
        }
        float sky = isSky(texture(DepthSampler, q).r) ? 1.0 : 0.0;
        sum += sky * decay;
        weightSum += decay;
        decay *= 0.965;
    }
    float rays = weightSum > 0.0 ? sum / weightSum : 0.0;
    // Strongest towards the sun; nothing where the sky is fully open (that is just sky).
    float aspect = Screen.y / Screen.x;
    vec2 d = delta * vec2(1.0, aspect);
    float falloff = exp(-dot(d, d) * 3.0);
    return rays * falloff * SunScreen.z * Strength1.z;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    fragColor = vec4(ambientOcclusion(texCoord, depth), lightRays(texCoord), 0.0, 1.0);
}
