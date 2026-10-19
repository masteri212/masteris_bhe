#version 150

// "Inside the black hole": the view inside a dark energy zone.
//  - space swirls around the middle of the screen and bends outward (gravitational lensing)
//  - colors split apart toward the edges (chromatic aberration)
//  - light gathers into a faint, slowly turning photon ring
//  - the world is drained toward violet and the screen edges fall into the dark
// Everything scales with Intensity (0 at the zone's edge, up to 1 at the crater).

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Intensity;
uniform float Seconds;

in vec2 texCoord;

out vec4 fragColor;

const vec3 VIOLET = vec3(0.55, 0.35, 1.0);
const vec3 RING = vec3(0.85, 0.75, 1.0);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec2 rotate(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

// Screen point (centered, aspect-corrected) -> texture coordinate, with the lensing applied.
vec2 lens(vec2 p, float aspect, float scale) {
    float r = length(p);
    // Swirl: stronger toward the edges, slowly breathing.
    float swirl = Intensity * (0.35 + 0.08 * sin(Seconds * 0.4)) * r * r;
    p = rotate(p, swirl);
    // Barrel bend: the edges of the view are pulled outward, as if space curves away.
    p *= (1.0 + Intensity * 0.18 * r * r) * scale;
    return vec2(p.x / aspect, p.y) + 0.5;
}

void main() {
    float aspect = InSize.x / InSize.y;
    vec2 p = (texCoord - 0.5) * vec2(aspect, 1.0);
    float r = length(p);
    float angle = atan(p.y, p.x);

    // Chromatic aberration: red, green and blue lensed by slightly different amounts.
    float split = Intensity * 0.014 * r;
    vec3 color;
    color.r = texture(DiffuseSampler, clamp(lens(p, aspect, 1.0 + split), 0.0, 1.0)).r;
    color.g = texture(DiffuseSampler, clamp(lens(p, aspect, 1.0), 0.0, 1.0)).g;
    color.b = texture(DiffuseSampler, clamp(lens(p, aspect, 1.0 - split), 0.0, 1.0)).b;

    // Drain the color toward violet.
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, gray * VIOLET * 1.25, Intensity * 0.55);

    // Infalling light streaks spiraling around the view.
    float streaks = pow(0.5 + 0.5 * sin(angle * 5.0 + r * 14.0 - Seconds * 0.9), 6.0);
    color += VIOLET * streaks * smoothstep(0.25, 0.9, r) * Intensity * 0.12;

    // Photon ring: light trapped in orbit, a thin bright band that slowly turns.
    float ringRadius = 0.72 + 0.02 * sin(Seconds * 0.6);
    float d = (r - ringRadius) / 0.035; // squared by hand: pow() is undefined for a negative base
    float ring = exp(-d * d);
    ring *= 0.6 + 0.4 * sin(angle * 3.0 + Seconds * 0.5);
    color += RING * ring * Intensity * 0.3;

    // The edges of the screen fall into the dark, pulsing slowly.
    float pulse = 0.5 + 0.5 * sin(Seconds * 0.8);
    float vignette = smoothstep(0.35 - 0.05 * pulse, 1.05, r);
    color = mix(color, vec3(0.01, 0.0, 0.02), vignette * Intensity * 0.9);

    // A little grain, like static from the event horizon.
    color += (hash(texCoord * InSize + Seconds) - 0.5) * 0.05 * Intensity;

    fragColor = vec4(color, 1.0);
}
