#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 color = source.rgb * vec3(1.045, 1.0, 0.94) + vec3(0.012, 0.008, 0.018);
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, 0.94);
    color = (color - 0.5) * 0.96 + 0.5;
    vec3 highlights = max(color - 0.72, 0.0);
    color -= highlights * highlights * 0.18;
    fragColor = vec4(clamp(color, 0.0, 1.0), source.a);
}
