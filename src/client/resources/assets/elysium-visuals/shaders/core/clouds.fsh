#version 330

// Volumetric cumulus clouds, raymarched at reduced resolution.

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;

layout(std140) uniform CloudInfo {
    // NDC -> camera-relative world position (camera rotation + projection, no translation).
    mat4 InvViewProj;
    // xyz: camera position (x/z wrapped to the noise period), w: time (s)
    vec4 Camera;
    // x: layer bottom (y), y: thickness, z: coverage 0..1 (with weather), w: density
    vec4 Layer;
    // x: wind offset (blocks), y: 1 = blocky style, z: shape evolution, w: max distance (blocks)
    vec4 Wind;
    // xyz: direction to the sun or moon, w: daylight 0..1
    vec4 LightDir;
    // rgb: sun/moon light, w: albedo (lower in rain)
    vec4 LightColor;
    // rgb: sky light from above, w: primary steps
    vec4 AmbientTop;
    // rgb: light under the clouds, w: light steps
    vec4 AmbientBottom;
    // x: 1 if NDC depth is 0..1, y: 1 = detail erosion, z: jitter seed, w: haze amount
    vec4 Misc;
};

in vec2 texCoord;
out vec4 fragColor;

const float NOISE_SIZE = 64.0;
const float TILE = 66.0;
const float ATLAS = 528.0;

// ---- noise atlas ---------------------------------------------------------------

vec2 tileUv(vec2 xy, float slice) {
    float tx = mod(slice, 8.0), ty = floor(slice / 8.0);
    return (vec2(tx, ty) * TILE + 1.0 + xy) / ATLAS;
}

// Tileable 3D noise; p in noise texels (period 64).
vec4 noise3(vec3 p) {
    p = mod(p, NOISE_SIZE);
    float z0 = floor(p.z);
    float fz = p.z - z0;
    float z1 = mod(z0 + 1.0, NOISE_SIZE);
    return mix(texture(NoiseSampler, tileUv(p.xy, z0)), texture(NoiseSampler, tileUv(p.xy, z1)), fz);
}

float remap(float v, float lo, float hi, float a, float b) {
    return a + (v - lo) / max(hi - lo, 1e-4) * (b - a);
}

// Interleaved gradient noise: a smooth, well-spread jitter of the ray start.
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

// ---- cloud density --------------------------------------------------------------

// Large-scale coverage (where cloud fields are), 0..1: soft round patches.
float coverageAt(vec2 xz) {
    float c = noise3(vec3(xz / 48.0, 11.0 + Wind.z * 0.3)).r * 0.65
            + noise3(vec3(xz / 24.0, 37.0 - Wind.z * 0.2)).r * 0.35;
    float threshold = 1.0 - Layer.z;
    return smoothstep(threshold - 0.12, threshold + 0.22, c);
}

// Extinction density at a world position (camera-relative y is absolute here).
float density(vec3 p, bool detail) {
    if (Wind.y > 0.5) {
        // Blocky style: the density is constant inside 12x6x12 cells (like vanilla clouds, but in 3D).
        vec3 cell = vec3(12.0, 6.0, 12.0);
        vec3 shifted = p + vec3(Wind.x, 0.0, 0.0);
        p = (floor(shifted / cell) + 0.5) * cell - vec3(Wind.x, 0.0, 0.0);
        detail = false;
    }
    float h = (p.y - Layer.x) / Layer.y;
    if (h <= 0.0 || h >= 1.0) {
        return 0.0;
    }
    vec2 q = p.xz + vec2(Wind.x, 0.0);
    float cov = coverageAt(q);
    if (cov <= 0.0) {
        return 0.0;
    }
    // Cumulus profile: flat base, rounded top; denser coverage grows taller towers.
    float top = 0.25 + 0.75 * cov;
    float profile = smoothstep(0.0, 0.06, h) * (1.0 - smoothstep(top * 0.45, top, h));
    // Billows: Perlin-Worley shape, carved where coverage is thin.
    float shape = noise3(vec3(q.x / 7.0, p.y / 7.0 + Wind.z, q.y / 7.0)).r;
    float billow = clamp(remap(shape, 0.25, 0.85, 0.0, 1.0), 0.0, 1.0);
    float d = clamp(remap(billow * profile, 1.0 - cov, 1.0, 0.0, 1.0), 0.0, 1.0) * (0.35 + 0.65 * cov);
    if (detail && d > 0.0) {
        // Worley detail eats into the edges (more at the base: wispy bottoms, crisp tops).
        vec4 n = noise3(vec3(q.x / 1.6, p.y / 1.6 - Wind.z * 2.0, q.y / 1.6));
        float erode = n.g * 0.625 + n.b * 0.25 + n.a * 0.125;
        d = clamp(remap(d, erode * mix(0.45, 0.2, h), 1.0, 0.0, 1.0), 0.0, 1.0);
    }
    if (Wind.y > 0.5) {
        // Blocky: a cell is either cloud or air, so the boxes get crisp sides.
        d = d > 0.12 ? 0.75 : 0.0;
    }
    return d * Layer.w;
}

// Two-lobe Henyey-Greenstein: forward scattering (silver lining) plus some back-scatter.
float hg(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * 3.14159265 * pow(1.0 + g2 - 2.0 * g * c, 1.5));
}

float phase(float c) {
    return mix(hg(c, 0.65), hg(c, -0.2), 0.35) * 4.0 * 3.14159265 * 0.55 + 0.25;
}

// Optical depth towards the light.
float lightMarch(vec3 p, int steps) {
    float stepLen = Layer.y * 0.45 / float(steps);
    float sum = 0.0;
    for (int i = 0; i < steps; i++) {
        p += LightDir.xyz * stepLen;
        sum += density(p, false) * stepLen;
    }
    return sum;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = depth <= 0.0; // reverse depth: nothing drawn
    vec2 ndc = texCoord * 2.0 - 1.0;
    float z = Misc.x > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 world = InvViewProj * vec4(ndc, sky ? (Misc.x > 0.5 ? 0.5 : 0.0) : z, 1.0);
    vec3 rel = world.xyz / world.w;
    vec3 dir = normalize(rel);
    float terrain = sky ? 1e9 : length(rel);

    float camY = Camera.y;
    float bottom = Layer.x, top = Layer.x + Layer.y;
    float t0, t1;
    if (camY < bottom) {
        if (dir.y <= 1e-4) { fragColor = vec4(0.0); return; }
        t0 = (bottom - camY) / dir.y;
        t1 = (top - camY) / dir.y;
    } else if (camY > top) {
        if (dir.y >= -1e-4) { fragColor = vec4(0.0); return; }
        t0 = (top - camY) / dir.y;
        t1 = (bottom - camY) / dir.y;
    } else {
        t0 = 0.0;
        t1 = dir.y > 1e-4 ? (top - camY) / dir.y : dir.y < -1e-4 ? (bottom - camY) / dir.y : Wind.w;
    }
    float maxDist = Wind.w;
    t1 = min(t1, min(terrain, maxDist));
    if (t0 >= t1) {
        fragColor = vec4(0.0);
        return;
    }

    int steps = int(AmbientTop.w);
    int lightSteps = int(AmbientBottom.w);
    bool detail = Misc.y > 0.5;
    // Outside the layer: even steps through it. Inside it: steps that start fine
    // next to the camera and grow geometrically, still reaching the end of the
    // ray, so the fog around you is smooth and distant clouds are still seen.
    bool inside = t0 <= 0.0;
    if (inside) {
        steps = min(128, steps * 3 / 2);
    }
    float span = t1 - t0;
    float jitter = ign(gl_FragCoord.xy + Misc.z * 5.588238);
    const float GROWTH = 3.0;
    float growthNorm = exp(GROWTH) - 1.0;

    vec3 camPos = Camera.xyz;
    float cosTheta = dot(dir, LightDir.xyz);
    float ph = phase(cosTheta);
    float transmittance = 1.0;
    vec3 color = vec3(0.0);
    float weightedT = 0.0, weightSum = 0.0;
    const float SIGMA = 0.09;

    for (int i = 0; i < 128; i++) {
        if (i >= steps || transmittance < 0.02) {
            break;
        }
        float sa = (float(i) + jitter) / float(steps), sb = (float(i) + 1.0 + jitter) / float(steps);
        float ta = inside ? span * (exp(GROWTH * sa) - 1.0) / growthNorm : t0 + span * sa;
        float tb = inside ? span * (exp(GROWTH * sb) - 1.0) / growthNorm : t0 + span * sb;
        if (ta >= t1) {
            break;
        }
        tb = min(tb, t1);
        float stepLen = tb - ta;
        float t = (ta + tb) * 0.5;
        vec3 p = camPos + dir * t;
        float d = density(p, detail);
        if (d > 0.002) {
            float h = clamp((p.y - bottom) / Layer.y, 0.0, 1.0);
            float sigma = d * SIGMA;
            float lightDepth = lightMarch(p, lightSteps) * SIGMA;
            // Beer-Lambert plus two softer terms that stand in for multiple scattering,
            // which keeps the inside of a cloud bright grey-white instead of black.
            float beer = exp(-lightDepth) * 0.6 + exp(-lightDepth * 0.25) * 0.25 + exp(-lightDepth * 0.07) * 0.15;
            // Powder: dark cores, bright edges facing the light.
            float powder = 1.0 - exp(-d * 2.0);
            vec3 direct = LightColor.rgb * beer * ph * mix(0.55, 1.0, powder);
            vec3 ambient = mix(AmbientBottom.rgb, AmbientTop.rgb, smoothstep(0.0, 0.85, h)) * 1.15;
            vec3 scattered = (direct + ambient) * LightColor.w;

            float stepT = exp(-sigma * stepLen);
            color += transmittance * scattered * (1.0 - stepT);
            weightedT += t * transmittance * (1.0 - stepT);
            weightSum += transmittance * (1.0 - stepT);
            transmittance *= stepT;
        }
    }

    float alpha = 1.0 - transmittance;
    if (alpha < 0.002) {
        fragColor = vec4(0.0);
        return;
    }
    // Fade far clouds into haze and out towards the draw distance.
    float dist = weightSum > 0.0 ? weightedT / weightSum : t0;
    float fade = 1.0 - smoothstep(maxDist * 0.6, maxDist, dist);
    float haze = smoothstep(maxDist * 0.15, maxDist, dist) * Misc.w;
    color = mix(color, AmbientTop.rgb * alpha * 1.1, haze);
    fragColor = vec4(color * fade, alpha * fade);
}
