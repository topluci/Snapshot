#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform SnapshotOptics {
    vec4 Lens;
    vec4 DepthConfig;
    vec4 ImageConfig;
    vec4 Effects;
    vec4 Performance;
};

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const int TAP_COUNT = 18;

float linearDepth(float depth) {
    float nearPlane = DepthConfig.x;
    float farPlane = DepthConfig.y;
    float ndc = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) /
        max(0.0001, farPlane + nearPlane - ndc * (farPlane - nearPlane));
}

float stableCenterDepth(vec2 uv, vec2 texel) {
    float center = linearDepth(texture(MainDepthSampler, uv).r);
    float left = linearDepth(texture(MainDepthSampler, clamp(uv - vec2(texel.x, 0.0), vec2(0.001), vec2(0.999))).r);
    float right = linearDepth(texture(MainDepthSampler, clamp(uv + vec2(texel.x, 0.0), vec2(0.001), vec2(0.999))).r);
    float up = linearDepth(texture(MainDepthSampler, clamp(uv - vec2(0.0, texel.y), vec2(0.001), vec2(0.999))).r);
    float down = linearDepth(texture(MainDepthSampler, clamp(uv + vec2(0.0, texel.y), vec2(0.001), vec2(0.999))).r);
    float minimumDepth = min(center, min(min(left, right), min(up, down)));
    float maximumDepth = max(center, max(max(left, right), max(up, down)));
    float relativeRange = (maximumDepth - minimumDepth) / max(0.1, center);
    float coherent = 1.0 - smoothstep(0.015, 0.12, relativeRange);
    float neighborhood = (center * 2.0 + left + right + up + down) / 6.0;
    return mix(center, neighborhood, coherent * 0.42);
}

float circleOfConfusion(float subjectDistance) {
    float focusDistance = max(Lens.x, 0.06);
    float focalLength = Lens.y / 1000.0;
    float aperture = max(Lens.z, 1.0);
    float subject = max(subjectDistance, focalLength + 0.001);
    float focus = max(focusDistance, focalLength + 0.001);
    float circle = abs(focalLength * focalLength * (subject - focus) /
        max(0.00001, aperture * subject * (focus - focalLength)));
    return min(Lens.w, circle / 0.036 * float(textureSize(InSampler, 0).x) * 0.68);
}

float polygonRadius(float angle, float blades) {
    float sector = 2.0 * PI / max(3.0, blades);
    float local = mod(angle + sector * 0.5, sector) - sector * 0.5;
    return cos(PI / max(3.0, blades)) / max(0.25, cos(local));
}

void main() {
    vec2 uv = clamp(texCoord, vec2(0.001), vec2(0.999));
    vec4 centerSample = texture(InSampler, uv);
    vec2 inputTexel = 1.0 / vec2(textureSize(InSampler, 0));
    vec2 depthTexel = 1.0 / vec2(textureSize(MainDepthSampler, 0));
    float centerDistance = stableCenterDepth(uv, depthTexel);
    float centerBlur = circleOfConfusion(centerDistance);

    float foregroundSpill = 0.0;
    for (int index = 0; index < 8; index++) {
        float angle = 2.0 * PI * float(index) / 8.0;
        vec2 probeUv = clamp(uv + vec2(cos(angle), sin(angle)) * inputTexel * 7.0,
            vec2(0.001), vec2(0.999));
        float probeDistance = linearDepth(texture(MainDepthSampler, probeUv).r);
        float depthGap = (centerDistance - probeDistance) / max(0.1, centerDistance);
        float probeBlur = circleOfConfusion(probeDistance);
        if (depthGap > 0.12 && probeBlur > 0.65) {
            foregroundSpill = max(foregroundSpill, probeBlur * 0.42);
        }
    }

    float blurRadius = max(centerBlur, foregroundSpill);
    int requestedTaps = blurRadius < 0.75 ? 6 : (blurRadius < 2.4 ? 10 : (blurRadius < 4.0 ? 14 : TAP_COUNT));
    int qualityTaps = int(round(mix(6.0, float(TAP_COUNT), clamp(Performance.x, 0.0, 1.0))));
    int activeTaps = min(requestedTaps, qualityTaps);
    vec3 accumulated = centerSample.rgb;
    float totalWeight = 1.0;
    vec2 fromCenter = uv - 0.5;
    float edge = clamp(length(fromCenter) * 1.75, 0.0, 1.0);
    vec2 radial = normalize(fromCenter + vec2(0.00001));

    for (int index = 0; index < TAP_COUNT; index++) {
        if (index >= activeTaps) {
            break;
        }
        float fraction = (float(index) + 0.5) / float(activeTaps);
        float angle = float(index) * 2.39996323;
        float ring = sqrt(fraction);
        float shape = polygonRadius(angle, DepthConfig.z);
        vec2 apertureOffset = vec2(cos(angle), sin(angle)) * ring * shape;
        apertureOffset *= 1.0 - edge * DepthConfig.w * 0.28;
        apertureOffset -= radial * edge * DepthConfig.w * ring * 0.22;
        vec2 sampleUv = clamp(uv + apertureOffset * blurRadius * inputTexel, vec2(0.001), vec2(0.999));
        vec3 sampleColor = texture(InSampler, sampleUv).rgb;
        float sampleDistance = linearDepth(texture(MainDepthSampler, sampleUv).r);
        float sampleBlur = circleOfConfusion(sampleDistance);
        float foregroundWeight = sampleDistance < centerDistance ? 1.0 : 0.0;
        float regularWeight = smoothstep(0.15, 1.5, max(centerBlur, sampleBlur));
        float weight = max(0.08, mix(regularWeight, foregroundWeight, step(centerBlur + 0.2, foregroundSpill)));
        float fartherDepth = (sampleDistance - centerDistance) / max(0.1, centerDistance);
        float fartherSample = smoothstep(0.025, 0.16, fartherDepth);
        float centerIsSharp = 1.0 - smoothstep(0.28, 1.25, centerBlur);
        weight *= mix(1.0, 0.04, fartherSample * centerIsSharp);
        float highlight = max(0.0, dot(sampleColor, vec3(0.2126, 0.7152, 0.0722)) - 0.70);
        sampleColor += sampleColor * highlight * highlight * smoothstep(1.5, 10.0, sampleBlur) * 0.72;
        accumulated += sampleColor * weight;
        totalWeight += weight;
    }

    float blurMix = smoothstep(0.42, 1.75, blurRadius);
    vec3 blurred = mix(centerSample.rgb, accumulated / totalWeight, blurMix);
    fragColor = vec4(clamp(blurred, 0.0, 1.0), blurMix);
}
