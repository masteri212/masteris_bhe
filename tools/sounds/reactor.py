"""
Synthesizes the reactor's sounds into src/main/resources/assets/masteris_bhe/sounds/reactor/*.ogg.

Run from the project root:  python tools/sounds/reactor.py
Needs numpy and ffmpeg (with libvorbis) on the PATH, or FFMPEG set to its path.

Every sound is mono (Minecraft only places mono sounds in the world) at 44.1 kHz. Each is built from a few
oscillators, filtered noise and a convolution reverb; `random` is seeded so re-running gives the same files.
"""
import os
import shutil
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join("src", "main", "resources", "assets", "masteris_bhe", "sounds", "reactor")
rng = np.random.default_rng(7)


# ---------------------------------------------------------------------------- building blocks

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def phase(freq):
    """Phase (radians) of an oscillator whose frequency changes per sample."""
    return 2 * np.pi * np.cumsum(np.asarray(freq, dtype=float)) / SR


def osc(freq, n, harmonics=((1, 1.0),), detune=0.0):
    """A tone of `n` samples: freq may be a constant or an array (a sweep). harmonics: (multiple, amplitude)."""
    f = np.full(n, float(freq)) if np.isscalar(freq) else np.asarray(freq, dtype=float)
    ph = phase(f * (1 + detune))
    out = np.zeros(n)
    for mult, amp in harmonics:
        # Fade out partials that would go past Nyquist, so sweeps don't alias.
        alias = np.clip((SR / 2 - f * mult) / 2000, 0, 1)
        out += amp * np.sin(ph * mult) * alias
    return out


def saw_partials(count, roll=1.0):
    return tuple((k, 1.0 / k ** roll) for k in range(1, count + 1))


def square_partials(count):
    return tuple((k, 1.0 / k) for k in range(1, 2 * count, 2))


def noise(n):
    return rng.standard_normal(n)


def spectral(sig, shape):
    """Filters by multiplying the spectrum by shape(freqs). Circular, so a looped input stays seamless."""
    spec = np.fft.rfft(sig)
    f = np.fft.rfftfreq(len(sig), 1 / SR)
    return np.fft.irfft(spec * shape(f), len(sig))


def lowpass_shape(cut, order=2):
    return lambda f: 1 / np.sqrt(1 + (f / cut) ** (2 * order))


def highpass_shape(cut, order=2):
    return lambda f: 1 / np.sqrt(1 + (cut / np.maximum(f, 1e-3)) ** (2 * order))


def band_shape(center, q):
    return lambda f: 1 / np.sqrt(1 + (q * (f / center - center / np.maximum(f, 1e-3))) ** 2)


def lowpass(sig, cut, order=2):
    return spectral(sig, lowpass_shape(cut, order))


def highpass(sig, cut, order=2):
    return spectral(sig, highpass_shape(cut, order))


def bandpass(sig, center, q):
    return spectral(sig, band_shape(center, q))


def onepole_lp(sig, cutoff):
    """Lowpass with a cutoff that can change per sample (for sweeps)."""
    cut = np.broadcast_to(np.asarray(cutoff, dtype=float), sig.shape)
    a = 1 - np.exp(-2 * np.pi * cut / SR)
    out = np.empty_like(sig)
    y = 0.0
    for i in range(len(sig)):
        y += a[i] * (sig[i] - y)
        out[i] = y
    return out


def svf_bp(sig, center, q):
    """State-variable bandpass with a center that can change per sample (resonant sweeps)."""
    c = np.broadcast_to(np.asarray(center, dtype=float), sig.shape)
    f = 2 * np.sin(np.pi * np.minimum(c, SR / 6) / SR)
    damp = 1.0 / q
    low = band = 0.0
    out = np.empty_like(sig)
    for i in range(len(sig)):
        high = sig[i] - low - damp * band
        band += f[i] * high
        low += f[i] * band
        out[i] = band
    return out


def env_adsr(n, a, d, s, r, sustain_end=None):
    t = np.arange(n) / SR
    total = n / SR
    sustain_end = total - r if sustain_end is None else sustain_end
    e = np.where(t < a, t / max(a, 1e-6), 1.0)
    e = np.where((t >= a) & (t < a + d), 1 - (1 - s) * (t - a) / max(d, 1e-6), e)
    e = np.where((t >= a + d) & (t < sustain_end), s, e)
    e = np.where(t >= sustain_end, s * np.clip(1 - (t - sustain_end) / max(r, 1e-6), 0, 1), e)
    return e


def decay(n, tau, attack=0.002, start=0.0):
    t = np.arange(n) / SR - start
    e = np.exp(-np.maximum(t, 0) / tau) * np.clip(t / attack, 0, 1)
    return np.where(t < 0, 0, e)


def place(dst, src, at):
    i = int(at * SR)
    j = min(len(dst), i + len(src))
    dst[i:j] += src[: j - i]
    return dst


def reverb(sig, seconds=1.6, mix=0.3, bright=4000, predelay=0.012):
    """Convolution with a synthetic room: decaying, darkening noise."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = noise(n) * np.exp(-t * 6.9 / seconds)
    # Darker toward the tail.
    ir = 0.6 * lowpass(ir, bright) + 0.4 * lowpass(ir, bright / 4)
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2))
    size = len(sig) + len(ir)
    wet = np.fft.irfft(np.fft.rfft(sig, size) * np.fft.rfft(ir, size), size)
    out = np.concatenate([sig, np.zeros(len(ir))]) * (1 - mix) + wet * mix * 2.2
    return out


def soft_clip(sig, drive=1.0):
    return np.tanh(sig * drive) / np.tanh(drive)


def trim(sig, floor=1e-3):
    """Cuts trailing silence (the reverb tail below -60 dB) and fades the last few ms."""
    idx = np.nonzero(np.abs(sig) > floor * np.max(np.abs(sig)))[0]
    end = idx[-1] + 1 if len(idx) else len(sig)
    sig = sig[:end].copy()
    fade = min(len(sig), int(0.02 * SR))
    sig[-fade:] *= np.linspace(1, 0, fade)
    return sig


def normalize(sig, peak=0.9):
    sig = sig - np.mean(sig)
    return sig * peak / max(1e-9, np.max(np.abs(sig)))


# ---------------------------------------------------------------------------- the sounds

def hum():
    """Running loop: a deep two-voice drone with a slow beat, a breathing noise bed and a faint high whine.
    Every frequency is a whole number of cycles per loop, and the noise is filtered circularly: seamless."""
    L = 4.0
    n = int(L * SR)
    t = t_axis(L)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t)                       # 2 cycles a loop
    slow = 0.5 + 0.5 * np.sin(2 * np.pi * 0.25 * t + 1)                  # 1 cycle a loop
    drone = osc(55.0, n, ((1, 1.0), (2, 0.55), (3, 0.3), (4, 0.18), (6, 0.08)))
    beat = osc(55.25, n, ((1, 0.7), (2, 0.35), (3, 0.15)))                # beats at 0.25 Hz against the drone
    sub = osc(27.5, n, ((1, 0.6),))
    whine = osc(1760.0, n, ((1, 1.0), (2, 0.2))) * (0.04 + 0.03 * slow)
    shimmer = osc(880.5, n, ((1, 1.0),)) * 0.03 * lfo
    bed = spectral(noise(n), band_shape(220, 1.2)) * (0.6 + 0.4 * lfo)
    bed = bed / np.std(bed) * 0.18
    crackle = spectral(noise(n), band_shape(3200, 2.0))
    crackle = crackle / np.std(crackle) * 0.025 * (0.5 + 0.5 * np.sin(2 * np.pi * 1.5 * t) ** 8)
    sig = 0.55 * drone * (0.85 + 0.15 * lfo) + 0.45 * beat + 0.5 * sub + whine + shimmer + bed + crackle
    return normalize(soft_clip(sig * 0.8, 1.4), 0.75)


def ignite():
    """Ignition: a spinning-up whine and a noise riser, then a deep detonation as the field closes, and a ring."""
    L = 4.2
    n = int(L * SR)
    t = t_axis(L)
    ramp = np.clip(t / 2.6, 0, 1)
    f = 50 * (8 ** (ramp ** 1.6))                                        # 50 Hz -> 400 Hz
    whine = osc(f, n, saw_partials(10, 1.2)) * np.clip(t / 0.4, 0, 1) * np.where(t < 2.6, 0.2 + 0.8 * ramp, np.exp(-(t - 2.6) / 0.25))
    riser = svf_bp(noise(n), 200 + 4000 * ramp ** 2, 4) * ramp ** 2 * np.where(t < 2.6, 1, np.exp(-(t - 2.6) / 0.08)) * 0.5
    boom_n = int(1.6 * SR)
    bt = np.arange(boom_n) / SR
    boom = np.sin(phase(30 + 70 * np.exp(-bt / 0.12))) * np.exp(-bt / 0.55)
    boom += lowpass(noise(boom_n), 300) * np.exp(-bt / 0.12) * 0.25
    ring = osc(220, boom_n, ((1, 1.0), (2, 0.5), (3.01, 0.3), (4.02, 0.15))) * np.exp(-bt / 0.9) * 0.25
    ring += osc(440.6, boom_n, ((1, 1),)) * np.exp(-bt / 1.2) * 0.12
    sig = 0.5 * whine + riser
    place(sig, boom * 1.3 + ring, 2.6)
    return normalize(soft_clip(reverb(sig, 2.2, 0.28), 1.3))


def power_down():
    """Shutting down: a clunk, then the whine falls away under a dying drone."""
    L = 3.6
    n = int(L * SR)
    t = t_axis(L)
    f = 420 * (0.09 ** np.clip(t / 3.0, 0, 1) ** 0.7)                    # 420 Hz -> ~40 Hz
    whine = osc(f, n, saw_partials(8, 1.3)) * np.exp(-t / 1.3)
    drone = osc(55, n, ((1, 1), (2, 0.4))) * np.exp(-t / 0.9) * 0.6
    air = svf_bp(noise(n), 2500 * np.exp(-t / 0.8) + 150, 3) * np.exp(-t / 0.7) * 0.4
    clunk_n = int(0.4 * SR)
    ct = np.arange(clunk_n) / SR
    clunk = np.sin(phase(60 + 60 * np.exp(-ct / 0.03))) * np.exp(-ct / 0.09)
    clunk += osc(612, clunk_n, ((1, 1), (2.7, 0.5))) * np.exp(-ct / 0.05) * 0.3
    sig = 0.45 * whine + drone + air
    place(sig, clunk, 0.0)
    return normalize(reverb(sig, 1.8, 0.3))


def alarm():
    """Alarm: a two-tone klaxon, hard-edged but not shrill."""
    L = 1.0
    n = int(L * SR)
    sig = np.zeros(n)
    for start, freq in ((0.0, 740), (0.32, 554)):
        m = int(0.28 * SR)
        tone = osc(freq, m, square_partials(9)) * env_adsr(m, 0.008, 0.05, 0.8, 0.04)
        tone = lowpass(tone, 3000)
        place(sig, tone, start)
    return normalize(reverb(sig, 1.0, 0.22))


def alarm_meltdown():
    """Meltdown alarm: a lower whooping siren over a growl."""
    L = 1.6
    n = int(L * SR)
    t = t_axis(L)
    f = 260 + 300 * np.sin(np.pi * np.clip(t / 1.2, 0, 1)) ** 2
    siren = osc(f, n, saw_partials(14, 1.0)) * env_adsr(n, 0.02, 0.1, 0.9, 0.35, 1.2)
    siren = lowpass(siren, 2600)
    growl = osc(f / 4, n, saw_partials(6, 0.8)) * env_adsr(n, 0.05, 0.1, 0.8, 0.4, 1.2) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t))
    return normalize(soft_clip(reverb(siren * 0.7 + growl * 0.5, 1.2, 0.25), 1.8))


def fault():
    """Fault: an electrical crack, a heavy thud and a struck-metal ring."""
    L = 1.6
    n = int(L * SR)
    t = t_axis(L)
    gate = (rng.random(n) < 0.08).astype(float)
    gate = lowpass(gate, 900) * 4
    crackle = highpass(noise(n), 1800) * np.clip(gate, 0, 1) * np.exp(-t / 0.12)
    thud = np.sin(phase(45 + 80 * np.exp(-t / 0.04))) * np.exp(-t / 0.25)
    metal = np.zeros(n)
    for freq, amp, tau in ((523, 1.0, 0.6), (1307, 0.6, 0.35), (2211, 0.4, 0.2), (887, 0.5, 0.5), (3315, 0.2, 0.12)):
        metal += amp * np.sin(2 * np.pi * freq * t) * np.exp(-t / tau)
    sig = 0.7 * crackle + 1.0 * thud + 0.25 * metal * decay(n, 10, 0.001)
    return normalize(soft_clip(reverb(sig, 1.6, 0.3), 1.5))


def stall():
    """Disc stall: a heavy clank, then a grinding wind-down."""
    L = 2.8
    n = int(L * SR)
    t = t_axis(L)
    clank = np.sin(phase(40 + 90 * np.exp(-t / 0.05))) * np.exp(-t / 0.35)
    for freq, amp, tau in ((311, 1.0, 0.5), (743, 0.7, 0.3), (1290, 0.4, 0.2), (1977, 0.25, 0.12)):
        clank += 0.3 * amp * np.sin(2 * np.pi * freq * t) * np.exp(-t / tau)
    rough = np.clip(lowpass(noise(n), 25) * 3, 0, None)
    grind = svf_bp(noise(n), 900 * np.exp(-t / 1.4) + 120, 5) * rough * np.clip((t - 0.1) / 0.2, 0, 1) * np.exp(-t / 1.1)
    fall = osc(220 * np.exp(-t / 0.9) + 25, n, saw_partials(6, 1.2)) * np.exp(-t / 1.0) * 0.35
    sig = clank + 0.8 * grind + fall
    return normalize(soft_clip(reverb(sig, 1.8, 0.3), 1.4))


def ring_strain(seed):
    """The containment ring under load: a metal groan with stick-slip judder."""
    r = np.random.default_rng(seed)
    L = 1.5
    n = int(L * SR)
    t = t_axis(L)
    base = 60 + 25 * r.random()
    f = base * (1 + 0.08 * np.sin(2 * np.pi * (0.6 + r.random()) * t) + 0.03 * lowpass(r.standard_normal(n), 6) * 8)
    src = osc(f, n, saw_partials(40, 0.9))
    stick = (r.random(n) < (14 + 8 * r.random()) / SR).astype(float)
    stick = np.clip(lowpass(stick, 60) * 400, 0, 1)
    body = np.zeros(n)
    for c, q, a in ((300 + 40 * r.random(), 18, 1.0), (720 + 60 * r.random(), 22, 0.8), (1280 + 90 * r.random(), 25, 0.5), (2150, 30, 0.25)):
        body += a * bandpass(src, c, q)
    env = env_adsr(n, 0.12, 0.2, 0.8, 0.5) * (0.55 + 0.45 * stick)
    sig = body * env + 0.15 * lowpass(src, 200) * env
    return normalize(reverb(sig, 1.4, 0.3))


def arc(seed):
    """An electrical arc: a snap and a short, jittery mains buzz."""
    r = np.random.default_rng(seed)
    L = 0.45
    n = int(L * SR)
    t = t_axis(L)
    snap = highpass(r.standard_normal(n), 2500) * np.exp(-t / 0.012)
    jitter = np.clip(lowpass(r.standard_normal(n), 120) * 5, 0, 1)
    buzz = osc(100 + 40 * r.random(), n, saw_partials(30, 0.7)) * jitter * np.exp(-t / (0.06 + 0.06 * r.random()))
    hiss = highpass(r.standard_normal(n), 5000) * jitter * np.exp(-t / 0.05) * 0.3
    sig = snap * 1.2 + highpass(buzz, 300) * 0.6 + hiss
    return normalize(reverb(sig, 0.6, 0.18, bright=7000))


def beep(freqs, each=0.09, gap=0.02, shape=None, tone_tau=None, partials=((1, 1.0), (2, 0.25), (3, 0.12))):
    total = (len(freqs) - 1) * (each + gap) + each + 0.05
    n = int(total * SR)
    sig = np.zeros(n)
    for k, freq in enumerate(freqs):
        m = int(each * SR)
        e = env_adsr(m, 0.004, 0.02, 0.8, 0.02) if tone_tau is None else decay(m, tone_tau, 0.003)
        place(sig, osc(freq, m, partials) * e, k * (each + gap))
    return sig


def beep_confirm():
    return normalize(reverb(beep((1046.5, 1568.0)), 0.5, 0.2, bright=6000), 0.7)


def beep_deny():
    sig = beep((233.1, 196.0), each=0.16, partials=square_partials(8))
    return normalize(reverb(lowpass(sig, 2400), 0.5, 0.15), 0.75)


def beep_ready():
    sig = beep((1046.5, 1318.5, 1568.0, 2093.0), each=0.32, gap=-0.22, tone_tau=0.25,
               partials=((1, 1.0), (2, 0.3), (3.01, 0.15), (4.2, 0.08)))
    return normalize(reverb(sig, 1.2, 0.3, bright=6000), 0.7)


def console():
    """A console key: a soft click and a short blip."""
    n = int(0.12 * SR)
    t = t_axis(0.12)
    click = highpass(noise(n), 3000) * np.exp(-t / 0.003) * 0.6
    blip = osc(1975.5, n, ((1, 1.0), (2, 0.2))) * np.clip((t - 0.008) / 0.004, 0, 1) * np.exp(-np.maximum(t - 0.008, 0) / 0.03) * 0.5
    return normalize(reverb(click + blip, 0.3, 0.15), 0.6)


def breaker_charge():
    """A breaker overcharging: a capacitor whine that rises and clicks shut."""
    L = 0.8
    n = int(L * SR)
    t = t_axis(L)
    f = 300 * (12 ** np.clip(t / 0.55, 0, 1) ** 1.5)
    whine = osc(f, n, ((1, 1.0), (2, 0.3), (3, 0.15))) * np.clip(t / 0.05, 0, 1) * np.where(t < 0.55, 0.3 + 0.7 * t / 0.55, np.exp(-(t - 0.55) / 0.05))
    click = highpass(noise(n), 2000) * decay(n, 0.006, 0.001, 0.55)
    thump = np.sin(2 * np.pi * 90 * t) * decay(n, 0.05, 0.002, 0.55) * 0.6
    return normalize(reverb(0.4 * whine + click + thump, 0.6, 0.2), 0.75)


def servo():
    """Realigning a stabilizer emitter: a servo whirr settling into place."""
    L = 0.7
    n = int(L * SR)
    t = t_axis(L)
    f = 180 + 140 * np.sin(np.pi * np.clip(t / 0.55, 0, 1))
    motor = osc(f, n, saw_partials(20, 1.1)) * env_adsr(n, 0.03, 0.05, 0.9, 0.08, 0.5)
    motor = bandpass(motor, 1200, 1.5) + 0.3 * lowpass(motor, 400)
    lock = highpass(noise(n), 2500) * decay(n, 0.008, 0.001, 0.56) * 0.8
    lock += np.sin(2 * np.pi * 1400 * t) * decay(n, 0.04, 0.001, 0.56) * 0.3
    return normalize(reverb(motor * 0.6 + lock, 0.7, 0.2), 0.75)


def emitter_crack():
    """A stabilizer lens cracking: a glassy snap, high pings and a spit of sparks."""
    L = 1.0
    n = int(L * SR)
    t = t_axis(L)
    snap = highpass(noise(n), 3000) * np.exp(-t / 0.01)
    pings = np.zeros(n)
    for k in range(9):
        freq = 2500 + 6000 * rng.random()
        start = 0.005 + 0.06 * rng.random()
        pings += np.sin(2 * np.pi * freq * t) * decay(n, 0.05 + 0.15 * rng.random(), 0.001, start) * (0.3 + 0.5 * rng.random())
    sparks = highpass(noise(n), 4000) * (rng.random(n) < 0.004) * 30
    sparks = lowpass(sparks, 9000) * np.exp(-t / 0.25)
    sig = snap * 1.2 + pings * 0.5 + sparks * 0.4
    return normalize(reverb(sig, 1.0, 0.25, bright=8000))


def breach():
    """Containment breach: a huge blast, a sub drop and the ring shearing apart."""
    L = 4.0
    n = int(L * SR)
    t = t_axis(L)
    blast = lowpass(noise(n), 1200) * np.exp(-t / 0.18) * 1.4
    blast += lowpass(noise(n), 250) * np.exp(-t / 0.7) * 1.2
    sub = np.sin(phase(22 + 60 * np.exp(-t / 0.25))) * np.exp(-t / 1.1) * 1.3
    shear = np.zeros(n)
    for freq, amp, tau in ((187, 1.0, 1.1), (433, 0.7, 0.8), (911, 0.5, 0.6), (1621, 0.3, 0.4)):
        shear += amp * np.sin(2 * np.pi * freq * t * (1 - 0.04 * t)) * np.exp(-t / tau)
    debris = highpass(noise(n), 1500) * np.clip(lowpass((rng.random(n) < 0.002).astype(float), 400) * 60, 0, 1) * np.exp(-t / 0.9) * 0.6
    sig = blast + sub + 0.25 * shear + debris
    return normalize(soft_clip(reverb(sig, 3.0, 0.35, bright=2500), 2.2))


def implode():
    """The core imploding: everything sucked inward in a rising rush, cut off dead, then a deep thoom."""
    L = 3.6
    n = int(L * SR)
    t = t_axis(L)
    cut = 3.0
    x = np.clip(t / cut, 0, 1)
    rush = svf_bp(noise(n), 150 + 5000 * x ** 3, 2.5) * x ** 3
    tone = osc(40 * (12 ** x ** 2), n, saw_partials(12, 1.1)) * x ** 2.5 * 0.4
    sub = np.sin(phase(30 + 20 * x)) * x ** 1.5 * 0.6
    sig = (rush + tone + sub) * np.where(t < cut, 1, 0)
    thoom_n = int(0.6 * SR)
    tt = np.arange(thoom_n) / SR
    thoom = np.sin(phase(28 + 50 * np.exp(-tt / 0.04))) * np.exp(-tt / 0.2)
    place(sig, thoom * 1.2, cut)
    return normalize(soft_clip(reverb(sig, 2.0, 0.22, bright=3000), 1.6))


def absorb(seed):
    """Something swallowed: a downward whoosh into a soft low gulp."""
    r = np.random.default_rng(seed)
    L = 0.7
    n = int(L * SR)
    t = t_axis(L)
    x = np.clip(t / 0.35, 0, 1)
    whoosh = svf_bp(r.standard_normal(n), 3000 * (1 - x) ** 2 + 200, 3) * np.sin(np.pi * x) ** 1.5 * 0.7
    gulp = np.sin(phase((500 + 200 * r.random()) * np.exp(-t / 0.08) + 45)) * decay(n, 0.12, 0.01, 0.25)
    sig = whoosh + 0.8 * gulp * np.where(t > 0.25, 1, 0)
    return normalize(reverb(sig, 0.9, 0.28, bright=3000), 0.8)


def vent_strain():
    """The black hole straining against the lance: a slow, pulsing groan."""
    L = 3.2
    n = int(L * SR)
    t = t_axis(L)
    f = 38 + 6 * np.sin(2 * np.pi * 0.4 * t)
    groan = osc(f, n, saw_partials(30, 1.0))
    groan = bandpass(groan, 180, 1.2) + 0.5 * lowpass(groan, 90)
    pulse = 0.4 + 0.6 * np.abs(np.sin(np.pi * 1.25 * t)) ** 3
    air = svf_bp(noise(n), 400 + 200 * np.sin(2 * np.pi * 0.3 * t), 4) * 0.3
    sig = (groan + air) * pulse * env_adsr(n, 0.4, 0.2, 0.9, 0.8)
    return normalize(soft_clip(reverb(sig, 2.0, 0.3, bright=2000), 1.6))


def vent_release():
    """The vent bursting open: a roaring jet that climbs and settles into a long blast."""
    L = 4.5
    n = int(L * SR)
    t = t_axis(L)
    x = np.clip(t / 0.8, 0, 1)
    jet = svf_bp(noise(n), 300 + 2200 * x ** 1.5 - 600 * np.clip((t - 1.5) / 3, 0, 1), 1.6)
    jet = jet * env_adsr(n, 0.05, 0.6, 0.65, 2.2)
    roar = lowpass(noise(n), 160) * env_adsr(n, 0.02, 0.4, 0.6, 2.5) * 1.4
    tone = osc(110 * (1 + x), n, saw_partials(10, 1.2)) * env_adsr(n, 0.1, 0.5, 0.3, 2.0) * 0.25
    hit = np.sin(phase(35 + 60 * np.exp(-t / 0.05))) * np.exp(-t / 0.3)
    sig = jet + roar + tone + hit
    return normalize(soft_clip(reverb(sig, 2.5, 0.3, bright=3500), 1.8))


def rebound():
    """A rebound during an emergency vent: a deep thunderous thump."""
    L = 1.8
    n = int(L * SR)
    t = t_axis(L)
    thump = np.sin(phase(30 + 70 * np.exp(-t / 0.06))) * np.exp(-t / 0.4)
    rumble = lowpass(noise(n), 180) * np.exp(-t / 0.5) * 0.9
    crack = highpass(noise(n), 1500) * np.exp(-t / 0.02) * 0.5
    return normalize(soft_clip(reverb(thump + rumble + crack, 2.2, 0.35, bright=2000), 1.8))


def collapse():
    """The final collapse: the biggest blast, with a long rolling rumble behind it."""
    L = 7.0
    n = int(L * SR)
    t = t_axis(L)
    blast = lowpass(noise(n), 900) * np.exp(-t / 0.3) * 1.5
    roll = lowpass(noise(n), 120) * (0.6 + 0.4 * np.clip(lowpass(noise(n), 3) * 6, -1, 1)) * np.exp(-t / 2.2) * 1.3
    sub = np.sin(phase(18 + 50 * np.exp(-t / 0.4))) * np.exp(-t / 1.6) * 1.4
    crack = highpass(noise(n), 2000) * np.exp(-t / 0.03)
    sig = blast + roll + sub + crack
    return normalize(soft_clip(reverb(sig, 4.0, 0.35, bright=1800), 2.5))


def capture():
    """A black hole caught in a Singularity Container: an inward rush that resolves into a bright chord."""
    L = 2.0
    n = int(L * SR)
    t = t_axis(L)
    x = np.clip(t / 0.5, 0, 1)
    rush = svf_bp(noise(n), 4000 * (1 - x) + 300, 3) * np.sin(np.pi * x) * 0.6
    chord = np.zeros(n)
    for freq in (523.3, 659.3, 784.0, 1046.5, 1568.0):
        chord += osc(freq, n, ((1, 1.0), (2, 0.2), (3.01, 0.1))) * decay(n, 0.8, 0.02, 0.45) * 0.3
    return normalize(reverb(rush + chord, 2.0, 0.35, bright=6000))


SOUNDS = {
    "hum": hum,
    "ignite": ignite,
    "power_down": power_down,
    "alarm": alarm,
    "alarm_meltdown": alarm_meltdown,
    "fault": fault,
    "stall": stall,
    "ring_strain1": lambda: ring_strain(1),
    "ring_strain2": lambda: ring_strain(2),
    "ring_strain3": lambda: ring_strain(3),
    "arc1": lambda: arc(11),
    "arc2": lambda: arc(12),
    "arc3": lambda: arc(13),
    "arc4": lambda: arc(14),
    "beep_confirm": beep_confirm,
    "beep_deny": beep_deny,
    "beep_ready": beep_ready,
    "console": console,
    "breaker_charge": breaker_charge,
    "servo": servo,
    "emitter_crack": emitter_crack,
    "breach": breach,
    "implode": implode,
    "absorb1": lambda: absorb(21),
    "absorb2": lambda: absorb(22),
    "absorb3": lambda: absorb(23),
    "vent_strain": vent_strain,
    "vent_release": vent_release,
    "rebound": rebound,
    "collapse": collapse,
    "capture": capture,
}

LOOPS = {"hum"}  # not trimmed: the loop's length is exact


def write_ogg(sig, path, ffmpeg):
    pcm = (np.clip(sig, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", "-ac", "1", path], check=True)


def main():
    ffmpeg = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if not ffmpeg:
        raise SystemExit("ffmpeg not found: put it on the PATH or set FFMPEG")
    os.makedirs(OUT, exist_ok=True)
    for name, make in SOUNDS.items():
        sig = make()
        if name not in LOOPS:
            sig = trim(sig)
        write_ogg(sig, os.path.join(OUT, name + ".ogg"), ffmpeg)
        print(f"{name:16s} {len(sig) / SR:5.2f}s")


if __name__ == "__main__":
    main()
